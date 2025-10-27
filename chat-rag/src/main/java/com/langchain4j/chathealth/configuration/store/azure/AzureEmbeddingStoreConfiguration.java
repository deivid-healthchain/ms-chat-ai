package com.langchain4j.chathealth.configuration.store.azure;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.azure.search.AzureAiSearchEmbeddingStore;
import io.github.cdimascio.dotenv.Dotenv;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.SearchClientBuilder;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;

@Configuration
// @Profile("azure")
public class AzureEmbeddingStoreConfiguration {

    @Value("${AZURE_SEARCH_ENDPOINT}")
    private String azureSearchEndpoint;

    @Value("${AZURE_SEARCH_KEY}")
    private String azureSearchKey;

    @Value("${AZURE_AISEARCH_INDEX_NAME}")
    private String indexName;

    @Bean
    EmbeddingStore<TextSegment> embeddingStore() {
        return AzureAiSearchEmbeddingStore.builder()
                .endpoint(azureSearchEndpoint)
                .apiKey(azureSearchKey)
                .indexName(indexName) // Assumindo que este nome permaneceu
                .createOrUpdateIndex(false)
                .dimensions(1536)
                .build();
    }

    @Bean
    public SearchIndexClient searchIndexClient() {
        return new SearchIndexClientBuilder()
                .endpoint(azureSearchEndpoint)
                .credential(new AzureKeyCredential(azureSearchKey))
                .buildClient();
    }

    @Bean
    public SearchClient searchClient() {
        Dotenv dotenv = Dotenv.load();
        return new SearchClientBuilder()
                .endpoint(dotenv.get("AZURE_SEARCH_ENDPOINT"))
                .credential(new AzureKeyCredential(dotenv.get("AZURE_SEARCH_KEY")))
                .indexName(dotenv.get("AZURE_AISEARCH_INDEX_NAME"))
                .buildClient();
    }
}
