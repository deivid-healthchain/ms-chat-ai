package com.langchain4j.chathealth.configuration.store.azure;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.azure.search.AzureAiSearchEmbeddingStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.SearchClientBuilder;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;

/**
 * Configuração do Azure AI Search para Embedding Store
 * Utiliza variáveis de ambiente em vez de arquivo .env
 */
@Configuration
// @Profile("azure")
public class AzureEmbeddingStoreConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AzureEmbeddingStoreConfiguration.class);

    @Value("${azure.search.endpoint:#{null}}")
    private String azureSearchEndpoint;

    @Value("${azure.search.key:#{null}}")
    private String azureSearchKey;

    @Value("${azure.search.index-name:#{null}}")
    private String indexName;

    @Bean
    EmbeddingStore<TextSegment> embeddingStore() {
        // Validar se as configurações estão presentes
        if (azureSearchEndpoint == null || azureSearchEndpoint.isEmpty() ||
            azureSearchKey == null || azureSearchKey.isEmpty() ||
            indexName == null || indexName.isEmpty()) {
            
            logger.warn("⚠️ Configurações do Azure Search não encontradas. Embedding Store não será inicializado.");
            return null;
        }
        
        logger.info("✅ Inicializando Azure AI Search Embedding Store");
        return AzureAiSearchEmbeddingStore.builder()
                .endpoint(azureSearchEndpoint)
                .apiKey(azureSearchKey)
                .indexName(indexName)
                .createOrUpdateIndex(false)
                .dimensions(1536)
                .build();
    }

    @Bean
    public SearchIndexClient searchIndexClient() {
        if (azureSearchEndpoint == null || azureSearchEndpoint.isEmpty() ||
            azureSearchKey == null || azureSearchKey.isEmpty()) {
            
            logger.warn("⚠️ SearchIndexClient não será inicializado - credenciais não configuradas");
            return null;
        }
        
        return new SearchIndexClientBuilder()
                .endpoint(azureSearchEndpoint)
                .credential(new AzureKeyCredential(azureSearchKey))
                .buildClient();
    }

    @Bean
    public SearchClient searchClient() {
        if (azureSearchEndpoint == null || azureSearchEndpoint.isEmpty() ||
            azureSearchKey == null || azureSearchKey.isEmpty() ||
            indexName == null || indexName.isEmpty()) {
            
            logger.warn("⚠️ SearchClient não será inicializado - credenciais não configuradas");
            return null;
        }
        
        return new SearchClientBuilder()
                .endpoint(azureSearchEndpoint)
                .credential(new AzureKeyCredential(azureSearchKey))
                .indexName(indexName)
                .buildClient();
    }
}
