package com.langchain4j.chathealth.service;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Service;

import com.langchain4j.chathealth.service.rag.RagAssistant;

@Service
public class RagService {

    private final RagAssistant ragAssistant;
    private final ContentRetriever contentRetriever;

    // O RagAssistant agora é construído uma vez e injetado aqui
    public RagService(ChatModel chatModel, ContentRetriever contentRetriever) {
        this.ragAssistant = AiServices.builder(RagAssistant.class)
                .chatModel(chatModel)
                .contentRetriever(contentRetriever)
                .build();
        this.contentRetriever = contentRetriever;
    }

    public String ask(String question) {
        // Busca os resultados relevantes
        var result = contentRetriever.retrieve(dev.langchain4j.rag.query.Query.from(question));
        if (result == null || ((result instanceof java.util.Collection) && ((java.util.Collection<?>) result).isEmpty())) {
            return "Nenhum conteúdo relacionado foi encontrado para sua pergunta.";
        }
        return ragAssistant.augmentedChat(question);
    }
}