package uz.fundgate.spam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.fundgate.common.dto.ApiResponse;
import uz.fundgate.spam.dto.BatchSpamCheckRequest;
import uz.fundgate.spam.dto.SpamCheckRequest;
import uz.fundgate.spam.dto.SpamCheckResponse;
import uz.fundgate.spam.service.SpamCheckService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST controller for spam checking endpoints.
 *
 * Ported from Python spam_checker/app.py FastAPI endpoints:
 * - POST /check        -> Batch spam check
 * - POST /check-single -> Single entry spam check
 * - GET  /health       -> Health check
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Spam Checker", description = "AI-powered spam detection endpoints")
public class SpamController {

    private final SpamCheckService spamCheckService;

    @PostMapping("/check")
    @Operation(summary = "Batch spam check",
            description = "Check multiple entries for spam. " +
                    "Uses heuristic pre-filters and Claude AI for deep analysis.")
    public ResponseEntity<ApiResponse<List<SpamCheckResponse>>> checkBatch(
            @Valid @RequestBody BatchSpamCheckRequest request) {
        log.info("[SPAM] Batch check request - {} entries", request.getEntries().size());

        List<SpamCheckResponse> results = spamCheckService.checkBatch(request);

        long spamCount = results.stream().filter(SpamCheckResponse::isSpam).count();
        log.info("[SPAM] Batch result: {}/{} spam detected",
                spamCount, request.getEntries().size());

        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @PostMapping("/check-single")
    @Operation(summary = "Single spam check",
            description = "Check a single entry for spam. " +
                    "Returns spam classification, confidence score, and reasons.")
    public ResponseEntity<ApiResponse<SpamCheckResponse>> checkSingle(
            @Valid @RequestBody SpamCheckRequest request) {
        log.info("[SPAM] Single check request - content length: {}",
                request.getContent() != null ? request.getContent().length() : 0);

        SpamCheckResponse result = spamCheckService.checkSingle(request);

        if (result.isSpam()) {
            log.info("[SPAM] Spam detected: confidence={}, reasons={}",
                    result.getConfidence(), result.getReasons());
        }

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the spam checker service is running.")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "smartval-spam",
                "timestamp", Instant.now().toString()
        ));
    }
}
