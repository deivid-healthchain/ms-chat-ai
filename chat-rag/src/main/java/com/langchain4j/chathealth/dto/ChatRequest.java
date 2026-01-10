package com.langchain4j.chathealth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChatRequest {
    
    @JsonProperty("question")
    private String question;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("conversationId")
    private String conversationId;
    
    @JsonProperty("context")
    private java.util.Map<String, Object> context;

    // Construtores
    public ChatRequest() {}

    public ChatRequest(String question) {
        this.question = question;
    }

    public ChatRequest(String question, String userId, String conversationId) {
        this.question = question;
        this.userId = userId;
        this.conversationId = conversationId;
    }

    // Getters e Setters
    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public java.util.Map<String, Object> getContext() {
        return context;
    }

    public void setContext(java.util.Map<String, Object> context) {
        this.context = context;
    }
}
