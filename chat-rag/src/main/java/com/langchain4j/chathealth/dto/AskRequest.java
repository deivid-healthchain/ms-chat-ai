package com.langchain4j.chathealth.dto;

public class AskRequest {
    public String message;

    public String getPergunta() {
        return message;
    }

    public void setPergunta(String message) {
        this.message = message;
    }
}
