package com.langchain4j.chathealth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.langchain4j.chathealth.dto.AskRequest;
import com.langchain4j.chathealth.dto.AskResponse;
import com.langchain4j.chathealth.dto.ChatRequest;
import com.langchain4j.chathealth.dto.ChatResponse;
import com.langchain4j.chathealth.dto.ErrorResponse;
import com.langchain4j.chathealth.service.RagService;

/**
 * Chat Controller com suporte a Virtual Threads (Java 21)
 * Otimizado para alta concorrência com baixo overhead
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        logger.info("Health check endpoint called");
        return ResponseEntity.ok("API Chat Health RAG ativa");
    }

    /**
     * Endpoint original - Manter compatibilidade com RAG direto
     * Executado em Virtual Thread para melhor performance
     */
    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody AskRequest request) {
        logger.info("Ask endpoint called with question: {}", request.getPergunta());
        
        if (request == null || request.getPergunta() == null || request.getPergunta().isBlank()) {
            logger.warn("Invalid request - empty question");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("O campo 'message' é obrigatório."));
        }
        
        try {
            String answer = ragService.ask(request.getPergunta());
            return ResponseEntity.ok(new AskResponse(request.getPergunta(), answer));
        } catch (Exception e) {
            logger.error("Error processing ask request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Erro ao processar pergunta: " + e.getMessage()));
        }
    }

    /**
     * Novo endpoint - Chamado pelo orquestrador
     * Retorna resposta estruturada com metadados
     * Otimizado com Virtual Threads para alta concorrência
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        logger.info("Chat endpoint called with question: {} [Thread: {}]", 
            request.getQuestion(), 
            Thread.currentThread().getName());
        
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            logger.warn("Invalid request - empty question");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("O campo 'question' é obrigatório."));
        }
        
        try {
            // Processar pergunta com RAG
            String answer = ragService.ask(request.getQuestion());
            
            // Criar resposta estruturada
            ChatResponse response = new ChatResponse(
                answer,
                "rag",                    // source
                "rag_service",            // toolUsed
                0.9,                      // confidence
                new java.util.HashMap<>() // metadata
            );
            
            logger.info("Chat response generated successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error processing chat request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Erro ao processar pergunta: " + e.getMessage()));
        }
    }

    /**
     * Endpoint para analytics - Pode ser expandido
     */
    @GetMapping("/analytics/{type}")
    public ResponseEntity<?> getAnalytics(@PathVariable String type) {
        logger.info("Analytics endpoint called for type: {}", type);
        
        try {
            // Implementar lógica de analytics conforme necessário
            return ResponseEntity.ok(new java.util.HashMap<>());
        } catch (Exception e) {
            logger.error("Error getting analytics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Erro ao obter analytics: " + e.getMessage()));
        }
    }
}
