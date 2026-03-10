package uz.fundgate.valuation.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.fundgate.valuation.dto.ScorecardResult;
import uz.fundgate.valuation.dto.ScorecardResult.ScorecardFactor;
import uz.fundgate.valuation.dto.ValuationRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Scorecard Method Valuation Service.
 *
 * Implements the Scorecard Method for startup valuation.
 * Evaluates 6 weighted factors, each producing a multiplier:
 *
 * - Team Experience (30% weight)
 * - Product / Barriers (25% weight)
 * - Market Size (15% weight)
 * - Competition (10% weight)
 * - GTM / Sales Channels (10% weight)
 * - Traction (10% weight)
 *
 * Formula: Adjusted Valuation = Base Pre-Money * Sum(factor_multiplier * factor_weight)
 *
 * Translated from Python: smartval_claude/scorecard.py
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScorecardService {

    private final BedrockClientService bedrockClient;

    @Value("${valuation.scorecard.base-valuation:2000000}")
    private long baseValuation;

    @Value("${valuation.scorecard.min-multiplier:0.5}")
    private double minMultiplier;

    @Value("${valuation.scorecard.max-multiplier:2.0}")
    private double maxMultiplier;

    // Factor weights matching the Python implementation
    private static final double WEIGHT_TEAM = 0.30;
    private static final double WEIGHT_PRODUCT = 0.25;
    private static final double WEIGHT_MARKET = 0.15;
    private static final double WEIGHT_COMPETITION = 0.10;
    private static final double WEIGHT_GTM = 0.10;
    private static final double WEIGHT_TRACTION = 0.10;

    // =========================================================================
    // Prompts - translated from Python scorecard.py instructions
    // =========================================================================

    private static final String TEAM_EXPERIENCE_PROMPT = """
            You are a venture analyst with expertise in evaluating founding teams. \
            Your task is to evaluate the team experience and qualifications using the Scorecard Method.

            ## METHODOLOGY CONTEXT
            The Scorecard Method uses weighted multipliers for startup evaluation. Team is the most \
            important factor (weight 30%), as investors often say: "Bet on the jockey, not the horse".

            ## EVALUATION CRITERIA
            ### Strong signals (increase score):
            - Successful exits (company sale, IPO)
            - Experience in unicorn startups or $100M+ companies
            - Leadership roles (CEO, CTO, CPO) in relevant industry
            - Specific achievements with metrics (revenue growth, team scaling)
            - Experience at FAANG or top industry companies

            ### Weak signals (decrease score):
            - Generic phrases without specifics ("experienced entrepreneur")
            - No relevant industry experience
            - Only academic or corporate experience without startups
            - No mention of specific results

            ## SCORING SCALE (multiplier)
            - **0.7**: No CTO, first startup without relevant experience
            - **0.8**: Team exists but weak relevant experience
            - **1.0**: Complete team with basic startup experience
            - **1.2**: Strong industry experience, successful projects
            - **1.3**: Exits or major corporate projects
            - **1.5**: Serial entrepreneurs with exits + strong advisors

            ## RESPONSE FORMAT
            - evaluation: numeric multiplier (0.7-1.5)
            - comment_ru: team analysis (2-3 sentences) in Russian
            - comment_en: same analysis in English
            - comment_uz: same analysis in Uzbek (Latin script)
            - recommendations_ru/en/uz: team strengthening recommendations""";

    private static final String PRODUCT_BARRIERS_PROMPT = """
            You are a product strategy expert with experience evaluating technology startups. \
            Your task is to evaluate the product and defensive barriers using the Scorecard Method.

            ## METHODOLOGY CONTEXT
            Product is the second most important factor in Scorecard (weight 25%). Key question: \
            "How difficult is it for competitors to copy this product?"

            ## EVALUATION CRITERIA
            ### Strong barriers:
            - Patents or filed patent applications
            - Proprietary algorithms/ML models
            - Unique datasets (difficult or expensive to reproduce)
            - Network effects (more users = more valuable product)
            - Deep technology integration with customers (switching costs)
            - Regulatory barriers (licenses, certifications)

            ### Weak barriers:
            - UI/UX advantages only (easy to copy)
            - No technological uniqueness
            - "We're first to market" without other barriers
            - Marketing advantages without technological foundation

            ## SCORING SCALE (multiplier)
            - **0.8**: Easily copyable product, no defensive barriers
            - **1.0**: Has differentiation but barriers are weak
            - **1.2**: Unique algorithms or proprietary data
            - **1.3**: Strong IP protection or initial network effects
            - **1.4-1.5**: Patents + network effects + unique data

            ## RESPONSE FORMAT
            - evaluation: numeric multiplier (0.8-1.5)
            - comment_ru/en/uz: product analysis
            - recommendations_ru/en/uz: barrier strengthening recommendations""";

    private static final String MARKET_SIZE_PROMPT = """
            You are a market analyst with experience verifying market sizing in venture funds. \
            Your task is to evaluate the quality of TAM/SAM/SOM justification using the Scorecard Method.

            ## METHODOLOGY CONTEXT
            Market is an important factor in Scorecard (weight 15%). Investors check not only \
            market size but also the quality of calculations. Unrealistic numbers are a red flag.

            ## EVALUATION CRITERIA
            ### Good justification:
            - References to authoritative sources (Gartner, McKinsey, CB Insights, Statista)
            - Specific calculation methodology (bottom-up vs top-down)
            - Geographic and time frame specification
            - Realistic ratio TAM > SAM > SOM

            ### Weak justification:
            - No sources or references to unknown reports
            - Unrealistic numbers (SAM > TAM, overly optimistic SOM)
            - Generic phrases without specific calculations
            - No methodology

            ## SCORING SCALE (multiplier)
            - **0.5**: No justification, fabricated numbers
            - **0.8**: Basic justification, general sources
            - **1.0**: Normal justification with logical methodology
            - **1.2**: Good justification with specific sources
            - **1.5**: Excellent justification with authoritative sources

            ## RESPONSE FORMAT
            - evaluation: numeric multiplier (0.5-1.5)
            - comment_ru/en/uz: market analysis
            - recommendations_ru/en/uz: calculation improvement recommendations""";

    private static final String COMPETITION_PROMPT = """
            You are a strategic consultant with competitive analysis experience. \
            Your task is to evaluate the startup's competitive position using the Scorecard Method.

            ## METHODOLOGY CONTEXT
            Competition is a factor (weight 10%). Investors want to understand: \
            "Why will customers choose exactly this product?"

            ## EVALUATION CRITERIA
            ### Blue Ocean (high score):
            - Creating a new category or segment
            - Unique business model
            - Technological superiority difficult to copy

            ### Red Ocean (low score):
            - Many direct competitors
            - Differentiation only by price
            - Easily copyable advantages
            - Competing with large players without clear advantages

            ## SCORING SCALE (multiplier)
            - **0.8**: Red Ocean, many competitors, weak differentiation
            - **1.0**: Has competitors but also differences
            - **1.1**: Noticeable differentiation, clear advantages
            - **1.2**: Strong uniqueness, technological superiority
            - **1.3**: Blue Ocean or dominant competitive advantages

            ## RESPONSE FORMAT
            - evaluation: multiplier (0.8-1.3)
            - comment_ru/en/uz: competitive position analysis
            - recommendations_ru/en/uz: position strengthening recommendations""";

    private static final String GTM_CHANNELS_PROMPT = """
            You are a growth marketing expert with startup scaling experience. \
            Your task is to evaluate the Go-to-Market strategy using the Scorecard Method.

            ## METHODOLOGY CONTEXT
            GTM strategy is a factor (weight 10%). Key question: \
            "How does the company plan to acquire customers and scale sales?"

            ## EVALUATION CRITERIA
            ### Strong GTM:
            - Clear ICP (Ideal Customer Profile)
            - Tested channels with measurable results
            - Clear unit economics (CAC, LTV, conversions)
            - First paying customers or strong LoI
            - Scalable acquisition model

            ### Weak GTM:
            - "We will sell to everyone" - no focus
            - Channels not tested
            - No metrics or KPI
            - Dependency on single channel without alternatives

            ## SCORING SCALE (multiplier)
            - **0.8**: No clear GTM strategy, channels not defined
            - **1.0**: Plan exists, channels defined but not tested
            - **1.2**: Channels being tested, initial results
            - **1.3**: First leads, clear sales funnel
            - **1.4**: LoI or pilot projects with customers
            - **1.5**: Paying customers with clear unit economics

            ## RESPONSE FORMAT
            - evaluation: multiplier (0.8-1.5)
            - comment_ru/en/uz: GTM analysis
            - recommendations_ru/en/uz: GTM improvement recommendations""";

    // =========================================================================
    // Tool schema for multiplier-based evaluation
    // =========================================================================

    private static Map<String, Object> buildMultiplierSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("evaluation", Map.of("type", "number", "description", "Evaluation multiplier (0.5-1.5)"));
        props.put("comment_ru", Map.of("type", "string", "description", "Analysis comment in Russian"));
        props.put("comment_en", Map.of("type", "string", "description", "Analysis comment in English"));
        props.put("comment_uz", Map.of("type", "string", "description", "Analysis comment in Uzbek (Latin)"));
        props.put("recommendations_ru", Map.of("type", "string", "description", "Recommendations in Russian"));
        props.put("recommendations_en", Map.of("type", "string", "description", "Recommendations in English"));
        props.put("recommendations_uz", Map.of("type", "string", "description", "Recommendations in Uzbek (Latin)"));

        return Map.of(
                "type", "object",
                "properties", props,
                "required", List.of("evaluation", "comment_ru", "comment_en", "comment_uz",
                        "recommendations_ru", "recommendations_en", "recommendations_uz")
        );
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Run the full Scorecard Method evaluation for a startup.
     */
    public ScorecardResult evaluate(ValuationRequest request) {
        log.info("Starting Scorecard evaluation for: {}", request.getStartupName());
        long startTime = System.currentTimeMillis();

        // Run all AI evaluations in parallel
        CompletableFuture<ScorecardFactor> teamFuture = CompletableFuture.supplyAsync(
                () -> evaluateTeamExperience(request));
        CompletableFuture<ScorecardFactor> productFuture = CompletableFuture.supplyAsync(
                () -> evaluateProductBarriers(request));
        CompletableFuture<ScorecardFactor> marketFuture = CompletableFuture.supplyAsync(
                () -> evaluateMarketSize(request));
        CompletableFuture<ScorecardFactor> competitionFuture = CompletableFuture.supplyAsync(
                () -> evaluateCompetition(request));
        CompletableFuture<ScorecardFactor> gtmFuture = CompletableFuture.supplyAsync(
                () -> evaluateGtmChannels(request));

        // Traction is evaluated heuristically
        ScorecardFactor tractionFactor = evaluateTraction(request);

        // Collect all factors
        List<ScorecardFactor> factors = new ArrayList<>();
        factors.add(teamFuture.join());
        factors.add(productFuture.join());
        factors.add(marketFuture.join());
        factors.add(competitionFuture.join());
        factors.add(gtmFuture.join());
        factors.add(tractionFactor);

        // Calculate weighted sum
        double totalMultiplier = 0;
        for (ScorecardFactor factor : factors) {
            factor.setScore(factor.getWeight() * factor.getMultiplier());
            totalMultiplier += factor.getScore();
        }

        // Clamp multiplier
        totalMultiplier = Math.max(minMultiplier, Math.min(maxMultiplier, totalMultiplier));

        // Calculate final valuation
        long adjustedValuation = Math.round(baseValuation * totalMultiplier);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Scorecard evaluation completed for {} in {}ms. Base: ${}, Multiplier: {:.2f}, Adjusted: ${}",
                request.getStartupName(), elapsed, baseValuation, totalMultiplier, adjustedValuation);

        return ScorecardResult.builder()
                .baseValuation(baseValuation)
                .adjustedValuation(adjustedValuation)
                .factors(factors)
                .totalMultiplier(Math.round(totalMultiplier * 100.0) / 100.0)
                .details(String.format("Scorecard Valuation: $%,d (Base: $%,d x %.2f multiplier)",
                        adjustedValuation, baseValuation, totalMultiplier))
                .build();
    }

    // =========================================================================
    // Factor evaluations
    // =========================================================================

    private ScorecardFactor evaluateTeamExperience(ValuationRequest request) {
        try {
            String input = String.format("""
                    Startup name: %s
                    Startup description: %s
                    Industry: %s
                    Team founder experience: %s
                    Team experience description: %s
                    """,
                    sanitize(request.getStartupName()),
                    sanitize(request.getStartupDescription()),
                    sanitize(request.getStartupIndustry()),
                    sanitize(request.getTeamFounderExperience()),
                    sanitize(request.getTeamExperienceDescription()));

            JsonNode result = bedrockClient.callClaudeWithTool(
                    TEAM_EXPERIENCE_PROMPT, input,
                    "scorecard_team_output", "Return team experience multiplier",
                    buildMultiplierSchema());

            return buildFactor("Team Experience", WEIGHT_TEAM, result, 0.7, 1.5);

        } catch (Exception e) {
            log.error("Error evaluating Scorecard Team: {}", e.getMessage(), e);
            return buildFallbackFactor("Team Experience", WEIGHT_TEAM, 1.0);
        }
    }

    private ScorecardFactor evaluateProductBarriers(ValuationRequest request) {
        try {
            String input = String.format("""
                    Startup name: %s
                    Startup description: %s
                    Industry: %s
                    Product differentiation: %s
                    Unique data/technology: %s
                    Barriers description: %s
                    """,
                    sanitize(request.getStartupName()),
                    sanitize(request.getStartupDescription()),
                    sanitize(request.getStartupIndustry()),
                    sanitize(request.getProductDifferentiation()),
                    sanitize(request.getProductUniqueData()),
                    sanitize(request.getProductBarriersDescription()));

            JsonNode result = bedrockClient.callClaudeWithTool(
                    PRODUCT_BARRIERS_PROMPT, input,
                    "scorecard_product_output", "Return product barriers multiplier",
                    buildMultiplierSchema());

            return buildFactor("Product / Barriers", WEIGHT_PRODUCT, result, 0.8, 1.5);

        } catch (Exception e) {
            log.error("Error evaluating Scorecard Product: {}", e.getMessage(), e);
            return buildFallbackFactor("Product / Barriers", WEIGHT_PRODUCT, 1.0);
        }
    }

    private ScorecardFactor evaluateMarketSize(ValuationRequest request) {
        try {
            String input = String.format("""
                    TAM: $%s million, comment: %s
                    SAM: $%s million, comment: %s
                    SOM: $%s million, comment: %s
                    Market growth rate: %s%%
                    Market growth comment: %s
                    """,
                    request.getTamAmount() != null ? request.getTamAmount() : "Not specified",
                    sanitize(request.getTamComment()),
                    request.getSamAmount() != null ? request.getSamAmount() : "Not specified",
                    sanitize(request.getSamComment()),
                    request.getSomAmount() != null ? request.getSomAmount() : "Not specified",
                    sanitize(request.getSomComment()),
                    request.getMarketGrowthRate() != null ? request.getMarketGrowthRate() : "Not specified",
                    sanitize(request.getMarketGrowthComment()));

            JsonNode result = bedrockClient.callClaudeWithTool(
                    MARKET_SIZE_PROMPT, input,
                    "scorecard_market_output", "Return market size multiplier",
                    buildMultiplierSchema());

            return buildFactor("Market Size", WEIGHT_MARKET, result, 0.5, 1.5);

        } catch (Exception e) {
            log.error("Error evaluating Scorecard Market: {}", e.getMessage(), e);
            return buildFallbackFactor("Market Size", WEIGHT_MARKET, 1.0);
        }
    }

    private ScorecardFactor evaluateCompetition(ValuationRequest request) {
        try {
            String competitorsStr = "Not provided";
            if (request.getCompetitors() != null && !request.getCompetitors().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (ValuationRequest.CompetitorInfo comp : request.getCompetitors()) {
                    sb.append(String.format("- %s: %s\n", comp.getName(), comp.getDescription()));
                }
                competitorsStr = sb.toString();
            }

            String input = String.format("""
                    Startup name: %s
                    Startup description: %s
                    Industry: %s
                    Competitors: %s
                    Differentiation: %s
                    """,
                    sanitize(request.getStartupName()),
                    sanitize(request.getStartupDescription()),
                    sanitize(request.getStartupIndustry()),
                    competitorsStr,
                    sanitize(request.getDifferentiation()));

            JsonNode result = bedrockClient.callClaudeWithTool(
                    COMPETITION_PROMPT, input,
                    "scorecard_competition_output", "Return competition multiplier",
                    buildMultiplierSchema());

            return buildFactor("Competition", WEIGHT_COMPETITION, result, 0.8, 1.3);

        } catch (Exception e) {
            log.error("Error evaluating Scorecard Competition: {}", e.getMessage(), e);
            return buildFallbackFactor("Competition", WEIGHT_COMPETITION, 1.0);
        }
    }

    private ScorecardFactor evaluateGtmChannels(ValuationRequest request) {
        try {
            String input = String.format("""
                    Startup name: %s
                    Startup description: %s
                    Industry: %s
                    GTM channels: %s
                    Channels comment: %s
                    Has tested channels: %s
                    Has first leads: %s
                    Has LoI: %s
                    Has pilot projects: %s
                    """,
                    sanitize(request.getStartupName()),
                    sanitize(request.getStartupDescription()),
                    sanitize(request.getStartupIndustry()),
                    request.getGtmChannels() != null ? String.join(", ", request.getGtmChannels()) : "Not specified",
                    sanitize(request.getGtmChannelsComment()),
                    Boolean.TRUE.equals(request.getGtmHasTestedChannels()),
                    Boolean.TRUE.equals(request.getGtmHasFirstLeads()),
                    Boolean.TRUE.equals(request.getGtmHasLoi()),
                    Boolean.TRUE.equals(request.getGtmHasPilotsProjects()));

            JsonNode result = bedrockClient.callClaudeWithTool(
                    GTM_CHANNELS_PROMPT, input,
                    "scorecard_gtm_output", "Return GTM channels multiplier",
                    buildMultiplierSchema());

            return buildFactor("GTM / Sales Channels", WEIGHT_GTM, result, 0.8, 1.5);

        } catch (Exception e) {
            log.error("Error evaluating Scorecard GTM: {}", e.getMessage(), e);
            return buildFallbackFactor("GTM / Sales Channels", WEIGHT_GTM, 1.0);
        }
    }

    /**
     * Traction is evaluated heuristically based on available data.
     * Mirrors the Python approach for traction evaluation.
     */
    private ScorecardFactor evaluateTraction(ValuationRequest request) {
        double multiplier = 1.0;
        String comment = "Basic traction assessment based on available data.";

        // Check for users/tests
        int numUsers = request.getPrototypeNumUsersTests() != null ? request.getPrototypeNumUsersTests() : 0;
        boolean hasRevenue = request.getRevenue() != null && request.getRevenue() > 0;
        boolean hasFunding = request.getFunding() != null && request.getFunding() > 0;
        boolean hasLoi = Boolean.TRUE.equals(request.getGtmHasLoi());
        boolean hasPilots = Boolean.TRUE.equals(request.getGtmHasPilotsProjects());

        if (hasRevenue && numUsers >= 100) {
            multiplier = 1.5;
            comment = "Strong traction with revenue and active user base.";
        } else if (hasRevenue || numUsers >= 50) {
            multiplier = 1.3;
            comment = "Good traction with early revenue or significant user base.";
        } else if (hasLoi || hasPilots || numUsers >= 10) {
            multiplier = 1.1;
            comment = "Initial traction with LoI/pilots or early users.";
        } else if (hasFunding || numUsers > 0) {
            multiplier = 1.0;
            comment = "Minimal traction, early stage.";
        } else {
            multiplier = 0.8;
            comment = "No traction indicators available.";
        }

        return ScorecardFactor.builder()
                .name("Traction")
                .weight(WEIGHT_TRACTION)
                .multiplier(multiplier)
                .score(WEIGHT_TRACTION * multiplier)
                .commentRu(comment)
                .commentEn(comment)
                .commentUz(comment)
                .build();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ScorecardFactor buildFactor(String name, double weight, JsonNode result,
                                        double minVal, double maxVal) {
        double multiplier = result.path("evaluation").asDouble(1.0);
        multiplier = Math.max(minVal, Math.min(maxVal, multiplier));

        return ScorecardFactor.builder()
                .name(name)
                .weight(weight)
                .multiplier(Math.round(multiplier * 100.0) / 100.0)
                .score(Math.round(weight * multiplier * 100.0) / 100.0)
                .commentRu(result.path("comment_ru").asText(""))
                .commentEn(result.path("comment_en").asText(""))
                .commentUz(result.path("comment_uz").asText(""))
                .recommendationsRu(result.path("recommendations_ru").asText(""))
                .recommendationsEn(result.path("recommendations_en").asText(""))
                .recommendationsUz(result.path("recommendations_uz").asText(""))
                .build();
    }

    private ScorecardFactor buildFallbackFactor(String name, double weight, double multiplier) {
        return ScorecardFactor.builder()
                .name(name)
                .weight(weight)
                .multiplier(multiplier)
                .score(weight * multiplier)
                .commentRu("Evaluation error, default value used")
                .commentEn("Evaluation error, default value used")
                .commentUz("Baholash xatosi, standart qiymat ishlatildi")
                .build();
    }

    private String sanitize(String input) {
        if (input == null) return "Not provided";
        String sanitized = input.length() > 5000 ? input.substring(0, 5000) : input;
        return sanitized.replaceAll("[\\p{Cntrl}&&[^\n\t\r]]", "").trim();
    }
}
