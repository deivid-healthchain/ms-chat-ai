package com.langchain4j.chathealth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.langchain4j.chathealth.dto.AskRequest;
import com.langchain4j.chathealth.dto.AskResponse;
import com.langchain4j.chathealth.dto.ErrorResponse;
import com.langchain4j.chathealth.service.RagService;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("API Chat Health RAG ativa");
    }

    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody AskRequest request) {
        // Validação de entrada
        if (request == null || request.getPergunta() == null || request.getPergunta().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("O campo 'pergunta' é obrigatório."));
        }
        
        // Delegação para o serviço
        String answer = ragService.ask(request.getPergunta());
        return ResponseEntity.ok(new AskResponse(request.getPergunta(), answer));
    }
}