package uz.fundgate.valuation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import uz.fundgate.valuation.dto.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrator service that runs one or all valuation methods
 * and combines results.
 *
 * Mirrors the Python app.py orchestration logic where each method
 * can be called individually or all three can run in parallel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValuationOrchestratorService {

    private final BerkusService berkusService;
    private final ScorecardService scorecardService;
    private final RiskFactorService riskFactorService;

    /**
     * Run the Berkus Method evaluation.
     */
    public ValuationResponse evaluateBerkus(ValuationRequest request) {
        log.info("Orchestrating Berkus evaluation for: {}", request.getStartupName());

        BerkusResult result = berkusService.evaluate(request);

        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("soundIdea", result.getSoundIdea().getEvaluation());
        scores.put("prototype", result.getPrototype().getEvaluation());
        scores.put("qualityTeam", result.getQualityTeam().getEvaluation());
        scores.put("strategicRelationships", result.getStrategicRelationships().getEvaluation());
        scores.put("productRollout", result.getProductRollout().getEvaluation());

        // Berkus range: total +/- 20%
        long total = result.getTotalValuation();
        long minVal = Math.round(total * 0.8);
        long maxVal = Math.round(total * 1.2);

        return ValuationResponse.builder()
                .method("berkus")
                .startupName(request.getStartupName())
                .valuation(ValuationResponse.ValuationRange.builder()
                        .min(minVal).max(maxVal).best(total).build())
                .scores(scores)
                .details(result.getDetails())
                .berkusResult(result)
                .status("completed")
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Run the Scorecard Method evaluation.
     */
    public ValuationResponse evaluateScorecard(ValuationRequest request) {
        log.info("Orchestrating Scorecard evaluation for: {}", request.getStartupName());

        ScorecardResult result = scorecardService.evaluate(request);

        Map<String, Object> scores = new LinkedHashMap<>();
        if (result.getFactors() != null) {
            for (ScorecardResult.ScorecardFactor factor : result.getFactors()) {
                scores.put(factor.getName(), Map.of(
                        "weight", factor.getWeight(),
                        "multiplier", factor.getMultiplier(),
                        "score", factor.getScore()
                ));
            }
        }
        scores.put("totalMultiplier", result.getTotalMultiplier());

        // Scorecard range: adjusted +/- 15%
        long adjusted = result.getAdjustedValuation();
        long minVal = Math.round(adjusted * 0.85);
        long maxVal = Math.round(adjusted * 1.15);

        return ValuationResponse.builder()
                .method("scorecard")
                .startupName(request.getStartupName())
                .valuation(ValuationResponse.ValuationRange.builder()
                        .min(minVal).max(maxVal).best(adjusted).build())
                .scores(scores)
                .details(result.getDetails())
                .scorecardResult(result)
                .status("completed")
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Run the Risk Factor Summation evaluation.
     */
    public ValuationResponse evaluateRiskFactor(ValuationRequest request) {
        log.info("Orchestrating Risk Factor evaluation for: {}", request.getStartupName());

        RiskFactorResult result = riskFactorService.evaluate(request);

        Map<String, Object> scores = new LinkedHashMap<>();
        if (result.getRiskFactors() != null) {
            for (RiskFactorResult.RiskFactor rf : result.getRiskFactors()) {
                scores.put(rf.getName(), Map.of(
                        "rating", rf.getRating(),
                        "adjustment", rf.getAdjustment()
                ));
            }
        }
        scores.put("totalAdjustment", result.getTotalAdjustment());

        // Risk Factor range: adjusted +/- 10%
        long adjusted = result.getAdjustedValuation();
        long minVal = Math.round(adjusted * 0.9);
        long maxVal = Math.round(adjusted * 1.1);

        return ValuationResponse.builder()
                .method("riskFactor")
                .startupName(request.getStartupName())
                .valuation(ValuationResponse.ValuationRange.builder()
                        .min(minVal).max(maxVal).best(adjusted).build())
                .scores(scores)
                .details(result.getDetails())
                .riskFactorResult(result)
                .status("completed")
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Run all 3 methods in parallel and combine the results.
     */
    public ValuationResponse evaluateFull(ValuationRequest request) {
        log.info("Orchestrating FULL evaluation (all 3 methods) for: {}", request.getStartupName());
        long startTime = System.currentTimeMillis();

        // Run all methods in parallel
        CompletableFuture<BerkusResult> berkusFuture = CompletableFuture.supplyAsync(
                () -> berkusService.evaluate(request));
        CompletableFuture<ScorecardResult> scorecardFuture = CompletableFuture.supplyAsync(
                () -> scorecardService.evaluate(request));
        CompletableFuture<RiskFactorResult> riskFactorFuture = CompletableFuture.supplyAsync(
                () -> riskFactorService.evaluate(request));

        BerkusResult berkusResult = berkusFuture.join();
        ScorecardResult scorecardResult = scorecardFuture.join();
        RiskFactorResult riskFactorResult = riskFactorFuture.join();

        // Calculate combined valuation as weighted average of all 3 methods
        long berkusVal = berkusResult.getTotalValuation();
        long scorecardVal = scorecardResult.getAdjustedValuation();
        long riskFactorVal = riskFactorResult.getAdjustedValuation();

        // Weighted average: Berkus 30%, Scorecard 40%, Risk Factor 30%
        long bestValuation = Math.round(berkusVal * 0.30 + scorecardVal * 0.40 + riskFactorVal * 0.30);

        // Min/Max from all methods
        long minVal = Math.min(berkusVal, Math.min(scorecardVal, riskFactorVal));
        long maxVal = Math.max(berkusVal, Math.max(scorecardVal, riskFactorVal));

        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("berkus_valuation", berkusVal);
        scores.put("scorecard_valuation", scorecardVal);
        scores.put("riskfactor_valuation", riskFactorVal);
        scores.put("combined_valuation", bestValuation);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Full evaluation completed for {} in {}ms. Combined: ${}", request.getStartupName(), elapsed, bestValuation);

        return ValuationResponse.builder()
                .method("full")
                .startupName(request.getStartupName())
                .valuation(ValuationResponse.ValuationRange.builder()
                        .min(minVal).max(maxVal).best(bestValuation).build())
                .scores(scores)
                .details(String.format("Combined Valuation: $%,d (Berkus: $%,d, Scorecard: $%,d, Risk Factor: $%,d)",
                        bestValuation, berkusVal, scorecardVal, riskFactorVal))
                .berkusResult(berkusResult)
                .scorecardResult(scorecardResult)
                .riskFactorResult(riskFactorResult)
                .status("completed")
                .timestamp(Instant.now())
                .build();
    }
}
