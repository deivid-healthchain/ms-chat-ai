package com.langchain4j.chathealth.configuration.rag;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.langchain4j.chathealth.service.rag.RagAssistant;

@Configuration
public class RagConfiguration {

    @Value("${rag.retriever.max-results}")
    private int maxResults;

    @Value("${rag.retriever.min-score}")
    private double minScore;

    // Cria o bean que sabe como buscar conteúdo relevante
    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
    }

    // Cria a implementação da sua interface RagAssistant
    @Bean
    public RagAssistant ragAssistant(ChatModel chatModel, ContentRetriever contentRetriever) {
        return AiServices.builder(RagAssistant.class)
                .chatModel(chatModel)
                .contentRetriever(contentRetriever)
                .build();
    }
}