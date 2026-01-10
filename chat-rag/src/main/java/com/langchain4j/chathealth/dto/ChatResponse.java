package com.langchain4j.chathealth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class ChatResponse {
    
    @JsonProperty("answer")
    private String answer;
    
    @JsonProperty("source")
    private String source; // "rag" ou "mcp"
    
    @JsonProperty("tool_used")
    private String toolUsed;
    
    @JsonProperty("confidence")
    private double confidence;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
    @JsonProperty("timestamp")
    private String timestamp;

    // Construtores
    public ChatResponse() {}

    public ChatResponse(String answer, String source, double confidence) {
        this.answer = answer;
        this.source = source;
        this.confidence = confidence;
        this.timestamp = java.time.Instant.now().toString();
    }

    public ChatResponse(String answer, String source, String toolUsed, 
                       double confidence, Map<String, Object> metadata) {
        this.answer = answer;
        this.source = source;
        this.toolUsed = toolUsed;
        this.confidence = confidence;
        this.metadata = metadata;
        this.timestamp = java.time.Instant.now().toString();
    }

    // Getters e Setters
    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getToolUsed() {
        return toolUsed;
    }

    public void setToolUsed(String toolUsed) {
        this.toolUsed = toolUsed;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
