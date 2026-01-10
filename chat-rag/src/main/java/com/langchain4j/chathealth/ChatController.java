package com.langchain4j.chathealth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
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
 * Integrado com ms-chat-orchestrator para chamar MCPs
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private final RagService ragService;
    private final RestTemplate restTemplate;

    @Value("${orchestrator.url:http://localhost:3000}")
    private String orchestratorUrl;

    public ChatController(RagService ragService, RestTemplate restTemplate) {
        this.ragService = ragService;
        this.restTemplate = restTemplate;
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
     * 
     * IMPORTANTE: Este endpoint agora chama o ms-chat-orchestrator
     * que por sua vez chama os MCPs (ms-procedures, ms-audit, ms-guide)
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
            // Chamar o ms-chat-orchestrator para processar a pergunta com MCPs
            logger.info("Calling orchestrator at: {}/chat", orchestratorUrl);
            
            ResponseEntity<ChatResponse> orchestratorResponse = restTemplate.postForEntity(
                orchestratorUrl + "/chat",
                request,
                ChatResponse.class
            );
            
            if (orchestratorResponse.getStatusCode().is2xxSuccessful()) {
                logger.info("Orchestrator response received successfully");
                return orchestratorResponse;
            } else {
                logger.warn("Orchestrator returned non-success status: {}", orchestratorResponse.getStatusCode());
                // Fallback para RAG se orquestrador falhar
                return fallbackToRag(request);
            }
            
        } catch (Exception e) {
            logger.error("Error calling orchestrator, falling back to RAG", e);
            // Fallback para RAG se orquestrador não estiver disponível
            return fallbackToRag(request);
        }
    }

    /**
     * Fallback para RAG quando o orquestrador não está disponível
     */
    private ResponseEntity<?> fallbackToRag(ChatRequest request) {
        try {
            logger.info("Using RAG fallback for question: {}", request.getQuestion());
            
            String answer = ragService.ask(request.getQuestion());
            
            ChatResponse response = new ChatResponse(
                answer,
                "rag",                    // source - RAG fallback
                "rag_service",            // toolUsed
                0.7,                      // confidence - menor que MCP
                new java.util.HashMap<>() // metadata
            );
            
            logger.info("RAG fallback response generated successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error in RAG fallback", e);
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
