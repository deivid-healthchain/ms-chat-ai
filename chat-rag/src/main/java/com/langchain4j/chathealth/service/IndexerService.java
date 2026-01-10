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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serviço de indexação de documentos PDF no Azure AI Search
 * Utiliza variáveis de ambiente em vez de arquivo .env
 */
@Service
public class IndexerService {

    private static final Logger logger = LoggerFactory.getLogger(IndexerService.class);

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
                          SearchClient searchClient,
                          @Value("${azure.search.index-name:}") String indexName,
                          @Value("${azure.search.embedding-dimension:1536}") int embeddingDimension,
                          @Value("${azure.storage.container-name:}") String containerName) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.searchIndexClient = searchIndexClient;
        this.blobServiceClient = blobServiceClient;
        this.searchClient = searchClient;
        this.indexName = indexName;
        this.embeddingDimension = embeddingDimension;
        this.containerName = containerName;
        
        logger.info("✅ IndexerService inicializado com indexName: {}, containerName: {}", indexName, containerName);
    }

    public void process() throws InterruptedException, NoSuchAlgorithmException, IOException {
        if (indexName == null || indexName.isEmpty() || containerName == null || containerName.isEmpty()) {
            logger.warn("⚠️ Configurações de índice ou container não definidas. Pulando processamento.");
            return;
        }
        
        logger.info("Iniciando processo de sincronização do índice por hash: {}", indexName);
        ensureIndexExists();

        Map<String, String> storageFileHashes = getFileHashesFromBlobStorage();
        logger.info("Encontrados {} arquivos no Blob Storage.", storageFileHashes.size());
        
        Map<String, String> indexFileHashes = getIngestedFileHashesFromIndex();
        logger.info("Encontrados {} arquivos já processados no índice.", indexFileHashes.size());

        List<FileInfo> filesToIngest = storageFileHashes.entrySet().stream()
                .filter(entry -> !entry.getValue().equals(indexFileHashes.get(entry.getKey())))
                .map(entry -> new FileInfo(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        List<String> filesToDelete = indexFileHashes.keySet().stream()
                .filter(filename -> !storageFileHashes.containsKey(filename))
                .collect(Collectors.toList());
        
        if (!filesToDelete.isEmpty()) {
            logger.info("Arquivos para deletar do índice: {}", filesToDelete);
            deleteDocumentsByFilename(filesToDelete);
        } else {
             logger.info("Nenhum arquivo para deletar.");
        }
        
        if (filesToIngest.isEmpty()) {
            logger.info("Nenhum arquivo novo ou modificado para ingerir.");
        } else {
            logger.info("Arquivos novos ou modificados para ingerir: {}", 
                filesToIngest.stream().map(FileInfo::filename).collect(Collectors.toList()));
            List<String> modifiedFiles = filesToIngest.stream()
                .map(FileInfo::filename)
                .filter(indexFileHashes::containsKey)
                .collect(Collectors.toList());
            if (!modifiedFiles.isEmpty()) {
                logger.info("Deletando versões antigas de arquivos modificados: {}", modifiedFiles);
                deleteDocumentsByFilename(modifiedFiles);
            }
            ingestNewDocuments(filesToIngest);
        }

        logger.info("Sincronização concluída com sucesso!");
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
            logger.warn("Aviso: Não foi possível obter os hashes do índice (pode estar vazio). {}", e.getMessage());
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
            logger.info("  -> Processando para ingestão manual: {}", fileInfo.filename());

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
                logger.info("  -> {} segmentos para '{}' foram enviados manualmente para o índice.", 
                    docsToUpload.size(), fileInfo.filename());
            } catch (Exception e) {
                logger.error("Erro ao enviar documentos para o índice: {}", e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void deleteDocumentsByFilename(List<String> filenames) {
        String filter = filenames.stream()
                .map(name -> "metadata/source eq '" + name.replace("'", "''") + "'")
                .collect(Collectors.joining(" or "));

        logger.info("Deletando documentos com filtro: {}", filter);
        try {
            List<String> idsToDelete = new ArrayList<>();
            SearchOptions options = new SearchOptions().setFilter(filter).setSelect("id");
            searchClient.search(null, options, null)
                        .forEach(result -> idsToDelete.add(result.getDocument(Map.class).get("id").toString()));
            
            if (!idsToDelete.isEmpty()) {
                embeddingStore.removeAll(idsToDelete);
                logger.info("{} segmentos de texto foram deletados.", idsToDelete.size());
            }
        } catch (Exception e) {
            logger.error("Erro ao deletar documentos antigos: {}", e.getMessage());
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
            logger.info("Índice não encontrado. Criando novo índice...");
            searchIndexClient.createIndex(buildSearchIndex());
            logger.info("Aguardando provisionamento do índice...");
            Thread.sleep(8000);
            logger.info("Índice criado e pronto para uso.");
        } else {
            logger.info("Índice existente encontrado. Prosseguindo com a sincronização.");
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
