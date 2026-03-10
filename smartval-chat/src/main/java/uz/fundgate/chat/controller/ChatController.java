package uz.fundgate.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import uz.fundgate.chat.dto.ChatRequest;
import uz.fundgate.chat.dto.ChatResponse;
import uz.fundgate.chat.dto.DocumentGenerateRequest;
import uz.fundgate.chat.dto.DocumentResponse;
import uz.fundgate.chat.service.ChatService;
import uz.fundgate.chat.service.DocumentService;
import uz.fundgate.chat.service.PitchDeckService;
import uz.fundgate.chat.service.TranscriptionService;
import uz.fundgate.common.dto.ApiResponse;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * REST controller for ChatKit backend functionality.
 *
 * Ported from Python chatkit-backend FastAPI endpoints:
 * - POST /api/custom-chat         -> SSE streaming AI chat
 * - POST /api/create-document     -> Document generation (Word/Excel/PPTX)
 * - POST /api/analyze-pitch-deck  -> Pitch deck PDF analysis
 * - POST /api/transcribe          -> Audio transcription placeholder
 * - GET  /api/validate-url        -> URL validation
 * - GET  /health                  -> Health check
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "ChatKit", description = "AI chat and document generation endpoints")
public class ChatController {

    private final ChatService chatService;
    private final DocumentService documentService;
    private final PitchDeckService pitchDeckService;
    private final TranscriptionService transcriptionService;

    // =========================================================================
    // Chat Endpoints
    // =========================================================================

    @PostMapping(value = "/custom-chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "AI Chat (SSE streaming)",
            description = "Send a message and receive AI response via Server-Sent Events streaming. " +
                    "Supports multiple AI models via AWS Bedrock Claude.")
    public SseEmitter customChat(@Valid @RequestBody ChatRequest request) {
        log.info("[CUSTOM-CHAT] New request - model: {}, conversationId: {}, message: {}...",
                request.getModel(),
                request.getConversationId(),
                request.getMessage().substring(0, Math.min(100, request.getMessage().length())));

        return chatService.streamChat(request);
    }

    // =========================================================================
    // Document Endpoints
    // =========================================================================

    @PostMapping("/create-document")
    @Operation(summary = "Generate document",
            description = "Generate Word (docx), Excel (xlsx), or PowerPoint (pptx) documents. " +
                    "Returns download URL and file metadata.")
    public ResponseEntity<ApiResponse<DocumentResponse>> createDocument(
            @Valid @RequestBody DocumentGenerateRequest request) {
        log.info("[DOCUMENT] Creating {} document: {}", request.getFormat(), request.getTitle());

        DocumentResponse response = documentService.generateAndSave(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/download-document/{fileId}/{fileName}")
    @Operation(summary = "Download generated document",
            description = "Download a previously generated document by its file ID.")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable String fileId,
            @PathVariable String fileName) {
        log.info("[DOCUMENT] Download request: {}/{}", fileId, fileName);

        byte[] fileBytes = documentService.getDocument(fileId, fileName);
        String format = fileName.contains(".") ?
                fileName.substring(fileName.lastIndexOf('.') + 1) : "bin";
        String mimeType = documentService.getMimeType(format);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .body(fileBytes);
    }

    // =========================================================================
    // Pitch Deck Endpoints
    // =========================================================================

    @PostMapping(value = "/analyze-pitch-deck", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Analyze pitch deck",
            description = "Upload a PDF pitch deck for AI analysis. " +
                    "Extracts startup information for FundGate form auto-fill using Claude Vision.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzePitchDeck(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "language", defaultValue = "ru") String language) {
        log.info("[PITCH-DECK] Analyzing: {}, size: {} bytes",
                file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("File is required. Please upload a PDF pitch deck."));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null ||
                (!originalFilename.toLowerCase().endsWith(".pdf") &&
                        !originalFilename.toLowerCase().endsWith(".pptx"))) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid file type. Please upload a PDF or PPTX file."));
        }

        try {
            Map<String, Object> result = pitchDeckService.analyzePitchDeck(
                    file.getBytes(), originalFilename);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("[PITCH-DECK] Analysis failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Pitch deck analysis failed: " + e.getMessage()));
        }
    }

    // =========================================================================
    // Transcription Endpoints
    // =========================================================================

    @PostMapping("/transcribe")
    @Operation(summary = "Transcribe audio",
            description = "Transcribe audio data. Currently a placeholder - " +
                    "integrate with AWS Transcribe for production use.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> transcribe(
            @RequestBody Map<String, String> request) {
        log.info("[TRANSCRIBE] New transcription request");

        String audioBase64 = request.get("audio");
        String languageCode = request.getOrDefault("languageCode", "ru-RU");

        if (audioBase64 == null || audioBase64.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Audio data is required"));
        }

        Map<String, Object> result = transcriptionService.transcribe(audioBase64, languageCode);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // =========================================================================
    // Utility Endpoints
    // =========================================================================

    @GetMapping("/validate-url")
    @Operation(summary = "Validate URL",
            description = "Check if a URL is accessible and returns a valid response.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateUrl(
            @RequestParam("url") String url) {
        log.info("[VALIDATE-URL] Checking: {}", url);

        try {
            URI uri = URI.create(url);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            boolean isValid = responseCode >= 200 && responseCode < 400;

            Map<String, Object> result = Map.of(
                    "url", url,
                    "valid", isValid,
                    "statusCode", responseCode
            );

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            Map<String, Object> result = Map.of(
                    "url", url,
                    "valid", false,
                    "error", e.getMessage()
            );
            return ResponseEntity.ok(ApiResponse.success(result));
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the chat service is running.")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "smartval-chat",
                "timestamp", Instant.now().toString()
        ));
    }
}
