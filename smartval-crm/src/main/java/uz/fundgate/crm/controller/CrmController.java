package uz.fundgate.crm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import uz.fundgate.crm.dto.*;
import uz.fundgate.crm.service.CrmAiService;

import java.time.Instant;
import java.util.Map;

/**
 * REST controller for CRM AI Service endpoints.
 * Provides startup analysis, file analysis, and streaming chat.
 *
 * Ported from Python: FastAPI endpoints in main.py
 * - POST /api/analyze -> analyze startup
 * - POST /api/analyze-file -> analyze uploaded file
 * - POST /api/chat -> SSE streaming chat with Claude tools
 * - GET /health -> health check
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "CRM AI", description = "CRM AI Service - Startup analysis and chat assistant")
@CrossOrigin(origins = {
        "http://localhost:5173", "http://localhost:5174", "http://localhost:5175",
        "http://localhost:3000", "http://127.0.0.1:5173", "http://127.0.0.1:5174",
        "https://funds-crm.fundgate.uz", "https://fundgate-funds-crm.web.app",
        "https://fundgate-funds-crm.firebaseapp.com", "https://crm.fundgate.uz",
        "https://fundgate.uz"
})
public class CrmController {

    private final CrmAiService crmAiService;

    /**
     * Analyze startup data using Claude AI.
     * Ported from Python: POST /api/analyze
     */
    @PostMapping("/api/analyze")
    @Operation(summary = "Analyze startup", description = "Analyze startup data and generate AI assessment with score, valuation, strengths, weaknesses")
    public ResponseEntity<CrmResponse> analyze(@Valid @RequestBody AnalyzeRequest request) {
        log.info("[CRM] POST /api/analyze - query length: {}, startupId: {}",
                request.getQuery().length(), request.getStartupId());

        CrmResponse response = crmAiService.analyze(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Analyze an uploaded file (image, PDF, DOCX) using Claude.
     * Ported from Python: POST /api/analyze-file
     */
    @PostMapping("/api/analyze-file")
    @Operation(summary = "Analyze file", description = "Analyze uploaded file (image via Claude vision, PDF/DOCX via text extraction)")
    public ResponseEntity<CrmResponse> analyzeFile(@Valid @RequestBody AnalyzeFileRequest request) {
        log.info("[CRM] POST /api/analyze-file - fileName: {}", request.getFileName());

        CrmResponse response = crmAiService.analyzeFile(request);
        return ResponseEntity.ok(response);
    }

    /**
     * SSE streaming chat with Claude tool use.
     * Streams AI responses with real-time tool execution (web_search, get_all_startups, etc).
     * Ported from Python: POST /api/chat with StreamingResponse
     */
    @PostMapping(value = "/api/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Chat with AI assistant", description = "SSE streaming chat with Claude tool use for CRM operations")
    public SseEmitter chat(@Valid @RequestBody CrmChatRequest request) {
        log.info("[CRM] POST /api/chat - message: '{}...', conversationId: {}",
                request.getMessage().length() > 50 ? request.getMessage().substring(0, 50) : request.getMessage(),
                request.getConversationId());

        return crmAiService.chat(request);
    }

    /**
     * Health check endpoint.
     * Ported from Python: GET /health
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Service health status")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "smartval-crm",
                "model", "Claude Haiku 4.5",
                "provider", "AWS Bedrock",
                "timestamp", Instant.now().toString()
        ));
    }
}
