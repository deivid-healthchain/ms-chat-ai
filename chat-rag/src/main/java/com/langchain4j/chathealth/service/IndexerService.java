package com.langchain4j.chathealth.service;

import com.azure.search.documents.SearchClient;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.models.*;
import com.azure.search.documents.models.SearchOptions;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class IndexerService {

    private record FileInfo(String filename, String hash) {}

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final SearchIndexClient searchIndexClient;
    private final BlobServiceClient blobServiceClient;
    private final SearchClient searchClient;

    private final String indexName;
    private final int embeddingDimension;
    private final String containerName;

    public IndexerService(EmbeddingModel embeddingModel,
                          EmbeddingStore<TextSegment> embeddingStore,
                          SearchIndexClient searchIndexClient,
                          BlobServiceClient blobServiceClient,
                          SearchClient searchClient) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.searchIndexClient = searchIndexClient;
        this.blobServiceClient = blobServiceClient;
        this.searchClient = searchClient;

        Dotenv dotenv = Dotenv.load();
        this.indexName = dotenv.get("AZURE_AISEARCH_INDEX_NAME");
        this.embeddingDimension = Integer.parseInt(dotenv.get("AZURE_AISEARCH_EMBEDDING_DIMENSION"));
        this.containerName = dotenv.get("AZURE_STORAGE_CONTAINER_NAME");
    }

    public void process() throws InterruptedException, NoSuchAlgorithmException, IOException {
        System.out.println("Iniciando processo de sincronização do índice por hash: " + indexName);
        ensureIndexExists();

        Map<String, String> storageFileHashes = getFileHashesFromBlobStorage();
        System.out.println("Encontrados " + storageFileHashes.size() + " arquivos no Blob Storage.");
        Map<String, String> indexFileHashes = getIngestedFileHashesFromIndex();
        System.out.println("Encontrados " + indexFileHashes.size() + " arquivos já processados no índice.");

        List<FileInfo> filesToIngest = storageFileHashes.entrySet().stream()
                .filter(entry -> !entry.getValue().equals(indexFileHashes.get(entry.getKey())))
                .map(entry -> new FileInfo(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        List<String> filesToDelete = indexFileHashes.keySet().stream()
                .filter(filename -> !storageFileHashes.containsKey(filename))
                .collect(Collectors.toList());
        
        if (!filesToDelete.isEmpty()) {
            System.out.println("Arquivos para deletar do índice: " + filesToDelete);
            deleteDocumentsByFilename(filesToDelete);
        } else {
             System.out.println("Nenhum arquivo para deletar.");
        }
        
        if (filesToIngest.isEmpty()) {
            System.out.println("Nenhum arquivo novo ou modificado para ingerir.");
        } else {
            System.out.println("Arquivos novos ou modificados para ingerir: " + filesToIngest.stream().map(FileInfo::filename).collect(Collectors.toList()));
            List<String> modifiedFiles = filesToIngest.stream()
                .map(FileInfo::filename)
                .filter(indexFileHashes::containsKey)
                .collect(Collectors.toList());
            if (!modifiedFiles.isEmpty()) {
                System.out.println("Deletando versões antigas de arquivos modificados: " + modifiedFiles);
                deleteDocumentsByFilename(modifiedFiles);
            }
            ingestNewDocuments(filesToIngest);
        }

        System.out.println("Sincronização concluída com sucesso!");
    }

    private Map<String, String> getFileHashesFromBlobStorage() throws NoSuchAlgorithmException, IOException {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        Map<String, String> fileHashes = new java.util.HashMap<>();
        for (BlobItem blob : containerClient.listBlobs()) {
            if (blob.getName().toLowerCase().endsWith(".pdf")) {
                try (InputStream inputStream = containerClient.getBlobClient(blob.getName()).openInputStream()) {
                    String hash = calculateSha256(inputStream);
                    fileHashes.put(blob.getName(), hash);
                }
            }
        }
        return fileHashes;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getIngestedFileHashesFromIndex() {
        SearchOptions options = new SearchOptions().setSelect("metadata/source", "metadata/file_hash");
        Map<String, String> fileHashes = new java.util.HashMap<>();
        
        try {
            searchClient.search(null, options, null).forEach(result -> {
                Map<String, Object> document = result.getDocument(Map.class);
                if (document.get("metadata") instanceof Map) {
                    Map<String, Object> metadata = (Map<String, Object>) document.get("metadata");
                    Object sourceValue = metadata.get("source");
                    Object hashValue = metadata.get("file_hash");
                    if (sourceValue != null && hashValue != null) {
                        fileHashes.put(sourceValue.toString(), hashValue.toString());
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("Aviso: Não foi possível obter os hashes do índice (pode estar vazio). " + e.getMessage());
        }
        return fileHashes.entrySet().stream()
                         .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1));
    }
    
    // MÉTODO COMPLETAMENTE REESCRITO PARA CONTROLE TOTAL, ABANDONANDO O EmbeddingStoreIngestor
    private void ingestNewDocuments(List<FileInfo> filesToIngest) {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        ApachePdfBoxDocumentParser documentParser = new ApachePdfBoxDocumentParser();
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 100);

        for (FileInfo fileInfo : filesToIngest) {
            System.out.println("  -> Processando para ingestão manual: " + fileInfo.filename());

            // 1. Carrega o documento
            Document document = documentParser.parse(
                    containerClient.getBlobClient(fileInfo.filename()).openInputStream()
            );

            // 2. Divide o documento em segmentos
            List<TextSegment> segments = splitter.split(document);

            // 3. ENRIQUECE CADA SEGMENTO MANUALMENTE COM OS METADADOS
            List<Map<String, Object>> docsToUpload = new ArrayList<>();
            // Gera embeddings para todos os segmentos
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            int idx = 0;
            for (TextSegment segment : segments) {
                Map<String, Object> doc = new java.util.HashMap<>();
                // Gera id seguro: substitui tudo que não for [a-zA-Z0-9_-] por '_'
                String safeFilename = fileInfo.filename().replaceAll("[^a-zA-Z0-9_-]", "_");
                doc.put("id", safeFilename + "_" + idx);
                doc.put("content", segment.text());
                // Cria objeto metadata conforme schema
                Map<String, Object> metadata = new java.util.HashMap<>();
                metadata.put("source", fileInfo.filename());
                metadata.put("file_hash", fileInfo.hash());
                doc.put("metadata", metadata);
                // Adiciona o vetor de embedding
                doc.put("content_vector", embeddings.get(idx).vector());
                docsToUpload.add(doc);
                idx++;
            }

            // 4. Upload manual para o Azure Search
            try {
                searchClient.uploadDocuments(docsToUpload);
                System.out.println("  -> " + docsToUpload.size() + " segmentos para '" + fileInfo.filename() + "' foram enviados manualmente para o índice.");
            } catch (Exception e) {
                System.err.println("Erro ao enviar documentos para o índice: " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void deleteDocumentsByFilename(List<String> filenames) {
        String filter = filenames.stream()
                .map(name -> "metadata/source eq '" + name.replace("'", "''") + "'")
                .collect(Collectors.joining(" or "));

        System.out.println("Deletando documentos com filtro: " + filter);
        try {
            List<String> idsToDelete = new ArrayList<>();
            SearchOptions options = new SearchOptions().setFilter(filter).setSelect("id");
            searchClient.search(null, options, null)
                        .forEach(result -> idsToDelete.add(result.getDocument(Map.class).get("id").toString()));
            
            if (!idsToDelete.isEmpty()) {
                embeddingStore.removeAll(idsToDelete);
                System.out.println(idsToDelete.size() + " segmentos de texto foram deletados.");
            }
        } catch (Exception e) {
            System.err.println("Erro ao deletar documentos antigos: " + e.getMessage());
        }
    }
    
    private String calculateSha256(InputStream inputStream) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }
        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder(2 * hashBytes.length);
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    private void ensureIndexExists() throws InterruptedException {
        if (!searchIndexClient.listIndexes().stream().anyMatch(index -> index.getName().equalsIgnoreCase(indexName))) {
            System.out.println("Índice não encontrado. Criando novo índice...");
            searchIndexClient.createIndex(buildSearchIndex());
            System.out.println("Aguardando provisionamento do índice...");
            Thread.sleep(8000);
            System.out.println("Índice criado e pronto para uso.");
        } else {
            System.out.println("Índice existente encontrado. Prosseguindo com a sincronização.");
        }
    }

    private SearchIndex buildSearchIndex() {
        String vectorSearchProfile = "my-vector-profile";
        String vectorSearchHnswConfig = "my-hnsw-vector-config";

        List<SearchField> attributeFields = List.of(
                new SearchField("key", SearchFieldDataType.STRING)
                    .setFilterable(true).setSearchable(true),
                new SearchField("value", SearchFieldDataType.STRING)
                    .setFilterable(true).setSearchable(true)
        );

        List<SearchField> metadataFields = List.of(
                new SearchField("source", SearchFieldDataType.STRING)
                    .setFilterable(true).setSearchable(true),
                new SearchField("file_hash", SearchFieldDataType.STRING)
                    .setFilterable(true),
                new SearchField("attributes", SearchFieldDataType.collection(SearchFieldDataType.COMPLEX))
                    .setFields(attributeFields)
        );

        return new SearchIndex(indexName)
                .setFields(List.of(
                        new SearchField("id", SearchFieldDataType.STRING)
                            .setKey(true).setFilterable(true).setSortable(true).setFacetable(true).setSearchable(true),
                        new SearchField("content", SearchFieldDataType.STRING)
                            .setSearchable(true).setFilterable(true).setSortable(true).setFacetable(true),
                        new SearchField("content_vector", SearchFieldDataType.collection(SearchFieldDataType.SINGLE))
                            .setSearchable(true)
                            .setVectorSearchDimensions(embeddingDimension)
                            .setVectorSearchProfileName(vectorSearchProfile),
                        new SearchField("metadata", SearchFieldDataType.COMPLEX)
                            .setFields(metadataFields)
                ))
                .setVectorSearch(new VectorSearch()
                        .setProfiles(List.of(new VectorSearchProfile(vectorSearchProfile, vectorSearchHnswConfig)))
                        .setAlgorithms(List.of(new HnswAlgorithmConfiguration(vectorSearchHnswConfig)))
                );
    }
}