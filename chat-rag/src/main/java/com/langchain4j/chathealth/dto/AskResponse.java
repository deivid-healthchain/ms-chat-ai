package com.langchain4j.chathealth.dto;

public class AskResponse {
    public String pergunta;
    public String resposta;

    public AskResponse(String pergunta, String resposta) {
        this.pergunta = pergunta;
        this.resposta = resposta;
    }

}
