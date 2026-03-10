package uz.fundgate.fundgate.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.fundgate.fundgate.dto.FundGateResponse;
import uz.fundgate.fundgate.dto.SubmissionRequest;
import uz.fundgate.fundgate.service.FirebaseStorageService;
import uz.fundgate.fundgate.service.ScoringOrchestrator;

import java.time.Instant;
import java.util.Map;

/**
 * FundGate API Controller.
 *
 * Endpoints:
 * - POST /fundgate/analyze - Main startup analysis endpoint
 * - GET /fundgate/results/{analysisId} - Get results by ID
 * - GET /health - Health check
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "FundGate", description = "AI-powered startup evaluation using Claude on AWS Bedrock")
public class FundgateController {

    private final ScoringOrchestrator scoringOrchestrator;
    private final FirebaseStorageService firebaseStorageService;

    /**
     * Main analysis endpoint.
     * Evaluates startup submission using 6 AI agents and returns scores.
     *
     * Scoring Categories:
     * | Category | Max Points | What it evaluates           |
     * |----------|------------|-----------------------------|
     * | A        | 20         | Data completeness           |
     * | B        | 20         | Pitch deck quality (vision) |
     * | C        | 20         | Traction metrics            |
     * | D        | 15         | Team composition            |
     * | E        | 15         | Product and technology      |
     * | F        | 10         | Materials quality           |
     *
     * Total: 100 points
     *
     * Status Thresholds:
     * - ready_to_route (75+): Ready for fund matching
     * - needs_improvement (50-74): Needs work before routing
     * - blocked (<50 or has blockers): Critical issues to address
     */
    @PostMapping("/fundgate/analyze")
    @Operation(summary = "Analyze startup submission",
            description = "Evaluates startup using 6 AI agents (completeness, pitch deck, traction, team, product, materials) and returns comprehensive scoring")
    public ResponseEntity<FundGateResponse> analyzeStartup(@Valid @RequestBody SubmissionRequest request) {
        try {
            log.info("Analyzing startup: {}", request.getSubmission().getName());

            FundGateResponse result = scoringOrchestrator.evaluate(request);

            log.info("Evaluation complete: {} - total={}, status={}",
                    request.getSubmission().getName(), result.getTotal(), result.getStatus());

            // Save to Firebase if startup_id and owner_id provided
            if (request.getStartupId() != null && !request.getStartupId().isBlank()
                    && request.getOwnerId() != null && !request.getOwnerId().isBlank()) {
                log.info("Saving results to Firebase for startup_id: {}", request.getStartupId());
                boolean saved = firebaseStorageService.saveAnalysisResult(
                        request.getStartupId(), request.getOwnerId(), result);
                if (saved) {
                    log.info("Results saved to Firebase successfully");
                } else {
                    log.warn("Failed to save results to Firebase");
                }
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error analyzing startup: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(FundGateResponse.builder()
                            .module("fundgate")
                            .version("1.0.0")
                            .status("error")
                            .processedAt(Instant.now())
                            .build());
        }
    }

    /**
     * Get analysis results by startup ID.
     * Retrieves previously saved results from Firestore.
     */
    @GetMapping("/fundgate/results/{analysisId}")
    @Operation(summary = "Get analysis results",
            description = "Retrieve previously saved analysis results by startup/analysis ID")
    public ResponseEntity<?> getResults(@PathVariable String analysisId) {
        try {
            Map<String, Object> result = firebaseStorageService.getAnalysisResult(analysisId);
            if (result != null) {
                return ResponseEntity.ok(result);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Analysis not found", "analysisId", analysisId));
        } catch (Exception e) {
            log.error("Error retrieving results: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve results"));
        }
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns service health status")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "FundGate Agents API",
                "version", "1.0.0",
                "engine", "Claude on AWS Bedrock",
                "timestamp", Instant.now().toEpochMilli()
        ));
    }

    /**
     * Service info endpoint.
     */
    @GetMapping("/")
    @Operation(summary = "Service info", description = "Returns service information and available endpoints")
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
                "service", "FundGate Agents API",
                "version", "1.0.0",
                "engine", "Claude on AWS Bedrock",
                "status", "running",
                "features", java.util.List.of(
                        "6-category scoring (A-F)",
                        "Vision-based pitch deck analysis",
                        "Trilingual output (ru/en/uz)",
                        "Parallel agent execution"
                ),
                "endpoints", Map.of(
                        "GET /", "Service info",
                        "GET /health", "Health check",
                        "POST /fundgate/analyze", "Analyze startup submission",
                        "GET /fundgate/results/{id}", "Get analysis results"
                )
        ));
    }
}
