package uz.fundgate.valuation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.fundgate.common.dto.ApiResponse;
import uz.fundgate.valuation.dto.ValuationRequest;
import uz.fundgate.valuation.dto.ValuationResponse;
import uz.fundgate.valuation.service.ValuationOrchestratorService;

import java.time.Instant;
import java.util.Map;

/**
 * REST controller for startup valuation endpoints.
 *
 * Provides endpoints for three valuation methods:
 * - Berkus Method (5 factors, max $500K)
 * - Scorecard Method (weighted multipliers)
 * - Risk Factor Summation (12 risk categories)
 * - Full evaluation (all 3 methods combined)
 *
 * Mirrors the Python Flask routes from app.py
 */
@Slf4j
@RestController
@RequestMapping("/api/valuation")
@RequiredArgsConstructor
@Tag(name = "Valuation", description = "Startup valuation using Berkus, Scorecard, and Risk Factor methods")
@CrossOrigin(origins = {
        "https://fundgate.uz",
        "https://www.fundgate.uz",
        "https://crm.fundgate.uz",
        "https://platform.fundgate.uz",
        "https://fundgate-platform.web.app",
        "https://fundgate-crm.web.app",
        "http://localhost:5173",
        "http://localhost:3000"
})
public class ValuationController {

    private final ValuationOrchestratorService orchestratorService;

    /**
     * Evaluate a startup using the Berkus Method.
     * 5 factors, each worth up to $100,000 (max total $500K).
     */
    @PostMapping("/berkus")
    @Operation(summary = "Berkus Method valuation",
            description = "Evaluates startup across 5 factors: Sound Idea, Prototype, Quality Team, " +
                    "Strategic Relationships, Product Rollout. Max $500K total.")
    public ResponseEntity<ApiResponse<ValuationResponse>> evaluateBerkus(
            @Valid @RequestBody ValuationRequest request) {
        log.info("POST /api/valuation/berkus - startup: {}", request.getStartupName());

        try {
            ValuationResponse result = orchestratorService.evaluateBerkus(request);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Berkus evaluation failed for {}: {}", request.getStartupName(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Berkus evaluation failed: " + e.getMessage()));
        }
    }

    /**
     * Evaluate a startup using the Scorecard Method.
     * Weighted multipliers against a base pre-money valuation.
     */
    @PostMapping("/scorecard")
    @Operation(summary = "Scorecard Method valuation",
            description = "Evaluates startup using weighted multipliers: Team (30%), Product (25%), " +
                    "Market (15%), Competition (10%), GTM (10%), Traction (10%).")
    public ResponseEntity<ApiResponse<ValuationResponse>> evaluateScorecard(
            @Valid @RequestBody ValuationRequest request) {
        log.info("POST /api/valuation/scorecard - startup: {}", request.getStartupName());

        try {
            ValuationResponse result = orchestratorService.evaluateScorecard(request);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Scorecard evaluation failed for {}: {}", request.getStartupName(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Scorecard evaluation failed: " + e.getMessage()));
        }
    }

    /**
     * Evaluate a startup using the Risk Factor Summation method.
     * 12 risk categories rated -2 to +2, each adjusting valuation by $250K.
     */
    @PostMapping("/riskfactor")
    @Operation(summary = "Risk Factor Summation valuation",
            description = "Evaluates 12 risk categories: Product Stage, Team, Tech Risk, Market Demand, " +
                    "Competition, Sales Channels, Finances, Legal, IP, Security, ESG, AI/Automation.")
    public ResponseEntity<ApiResponse<ValuationResponse>> evaluateRiskFactor(
            @Valid @RequestBody ValuationRequest request) {
        log.info("POST /api/valuation/riskfactor - startup: {}", request.getStartupName());

        try {
            ValuationResponse result = orchestratorService.evaluateRiskFactor(request);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Risk Factor evaluation failed for {}: {}", request.getStartupName(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Risk Factor evaluation failed: " + e.getMessage()));
        }
    }

    /**
     * Run all 3 valuation methods in parallel and combine results.
     * Returns individual results for each method plus a weighted combined valuation.
     */
    @PostMapping("/full")
    @Operation(summary = "Full valuation (all 3 methods)",
            description = "Runs Berkus, Scorecard, and Risk Factor methods in parallel. " +
                    "Returns combined valuation as weighted average.")
    public ResponseEntity<ApiResponse<ValuationResponse>> evaluateFull(
            @Valid @RequestBody ValuationRequest request) {
        log.info("POST /api/valuation/full - startup: {}", request.getStartupName());

        try {
            ValuationResponse result = orchestratorService.evaluateFull(request);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Full evaluation failed for {}: {}", request.getStartupName(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Full evaluation failed: " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns service health status")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "smartval-valuation",
                "version", "1.0.0",
                "timestamp", Instant.now().toString(),
                "endpoints", Map.of(
                        "berkus", "POST /api/valuation/berkus",
                        "scorecard", "POST /api/valuation/scorecard",
                        "riskfactor", "POST /api/valuation/riskfactor",
                        "full", "POST /api/valuation/full"
                )
        ));
    }
}
