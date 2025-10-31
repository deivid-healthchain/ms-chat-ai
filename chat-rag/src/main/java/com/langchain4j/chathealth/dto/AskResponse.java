package com.langchain4j.chathealth.dto;

public class AskResponse {
    public String message;
    public String resposta;

    public AskResponse(String message, String resposta) {
        this.message = message;
        this.resposta = resposta;
    }

}
