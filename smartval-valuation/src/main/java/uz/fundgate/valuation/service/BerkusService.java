package uz.fundgate.valuation.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.fundgate.valuation.dto.BerkusResult;
import uz.fundgate.valuation.dto.BerkusResult.FactorEvaluation;
import uz.fundgate.valuation.dto.ValuationRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Berkus Method Valuation Service.
 *
 * Implements the classic Berkus Method for pre-revenue startup valuation.
 * Evaluates 5 factors, each worth up to $100,000 (max total $500,000):
 *
 * 1. Sound Idea - quality of the problem and solution
 * 2. Prototype - technology risk reduction (MVP, users, traction)
 * 3. Quality Management Team - team composition, experience, advisors
 * 4. Strategic Relationships (GTM) - go-to-market strategy, LoI, pilots
 * 5. Product Rollout (Market Risks) - risk identification and mitigation
 *
 * Factors 1, 4, and 5 use Claude AI for evaluation.
 * Factors 2 and 3 use heuristic logic (deterministic scoring).
 *
 * Translated from Python: smartval_claude/berkus.py
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BerkusService {

    private final BedrockClientService bedrockClient;

    @Value("${valuation.berkus.max-idea:100000}")
    private long maxIdea;

    @Value("${valuation.berkus.max-prototype:100000}")
    private long maxPrototype;

    @Value("${valuation.berkus.max-team:100000}")
    private long maxTeam;

    @Value("${valuation.berkus.max-strategic-relationships:100000}")
    private long maxGtm;

    @Value("${valuation.berkus.max-product-rollout:100000}")
    private long maxMarketRisks;

    @Value("${valuation.berkus.total-max:500000}")
    private long totalMax;

    // =========================================================================
    // Prompts - translated from Python FALLBACK_PROMPTS
    // =========================================================================

    private static final String IDEA_PROMPT = """
            You are a venture analyst with experience evaluating pre-seed startups. \
            Your task is to evaluate the quality of the startup's idea and problem using the classic Berkus Method.

            ## METHODOLOGY CONTEXT
            The Berkus Method evaluates pre-revenue startups across 5 factors, each worth up to $100,000. \
            Sound Idea is the first factor, evaluating the quality of the problem and solution.

            ## INPUT DATA
            - Startup name and description
            - Problem description the product solves
            - Why the problem is urgent right now
            - Quantitative problem estimation (pain in dollars/time)
            - How the solution is better than existing alternatives

            ## EVALUATION CRITERIA
            1. **Problem Clarity** - is the customer pain clearly articulated
            2. **Validation** - evidence of problem existence (interviews, data, research)
            3. **Urgency** - how urgent is the problem (hair-on-fire vs nice-to-have)
            4. **Pain Size** - quantitative estimation ($ losses, hours of time)

            ## SCORING SCALE (in dollars)
            - **$0**: Unclear idea, no problem confirmation, solution looking for a problem
            - **$25,000**: Problem described but without facts, data, or validation
            - **$50,000**: Clear pain + initial validation (interviews, surveys)
            - **$75,000**: Confirmed pain + quantitative data + competitive analysis
            - **$100,000**: Hair-on-fire problem + data + pilots + clear solution superiority

            ## RESPONSE FORMAT
            - evaluation: amount in dollars (0-100000, multiple of 25000)
            - is_spam: false (if data is correct)
            - comment_ru: professional idea analysis (2-3 sentences) in Russian
            - comment_en: same analysis in English
            - comment_uz: same analysis in Uzbek (Latin script)
            - recommendations_ru/en/uz: specific improvement recommendations

            Provide specific observations about the problem and solution in comments.""";

    private static final String GTM_PROMPT = """
            You are a go-to-market strategy expert with experience scaling startups. \
            Your task is to evaluate the GTM strategy using the classic Berkus Method.

            ## METHODOLOGY CONTEXT
            Strategic Relationships is a Berkus Method factor evaluating the startup's ability \
            to reach the market and acquire customers. Maximum $100,000.

            ## INPUT DATA
            - Startup name and description
            - Customer acquisition strategy
            - Presence and number of LoI (Letters of Intent)
            - Presence of pilot projects
            - Marketing budget/plan availability

            ## EVALUATION CRITERIA
            1. **Channels** - are specific acquisition channels defined with justification
            2. **Testing** - have channel tests been conducted with results
            3. **Metrics** - are there CAC, conversion, ROI data
            4. **Confirmation** - are there LoI, pilots, first customers

            ## SCORING SCALE (in dollars)
            - **$0**: No GTM strategy, "we will sell to everyone"
            - **$25,000**: General GTM idea without specific channels and tests
            - **$50,000**: Plan + defined channels + first experiments
            - **$75,000**: Plan + channel tests + measurable metrics
            - **$100,000**: LoI/pilots + partnerships + confirmed metrics + first customers

            ## RESPONSE FORMAT
            - evaluation: amount in dollars (0-100000, multiple of 25000)
            - is_spam: false
            - comment_ru: GTM strategy analysis (2-3 sentences) in Russian
            - comment_en: same analysis in English
            - comment_uz: same analysis in Uzbek (Latin script)
            - recommendations_ru/en/uz: GTM improvement recommendations

            Provide specific channels and metrics in comments.""";

    private static final String MARKET_RISKS_PROMPT = """
            You are a risk management expert in venture investments. \
            Your task is to evaluate the startup's market risks using the classic Berkus Method.

            ## METHODOLOGY CONTEXT
            Risk Reduction is a Berkus Method factor evaluating how well the team identifies \
            and mitigates risks. Maximum $100,000.

            ## INPUT DATA
            - Startup name and description
            - Top 3 risks identified by the team
            - Legal/regulatory barriers
            - IP protection (patents, trade secrets)
            - Risk mitigation actions taken

            ## EVALUATION CRITERIA
            1. **Identification** - does the team recognize key risks
            2. **Mitigation** - is there a concrete risk reduction plan
            3. **IP Protection** - patents, trade secrets, barriers to copying
            4. **Regulatory** - readiness for regulatory requirements and changes

            ## SCORING SCALE (in dollars)
            - **$0**: High uncertainty, risks not identified, no plan
            - **$25,000**: Risks named but no mitigation strategy
            - **$50,000**: Partial mitigation measures, regulatory understanding
            - **$75,000**: Risk control + IP protection + worst-case scenario plans
            - **$100,000**: Risks minimized + strong IP position + regulatory compliance

            ## RESPONSE FORMAT
            - evaluation: amount in dollars (0-100000, multiple of 25000)
            - is_spam: false
            - comment_ru: risk analysis (2-3 sentences) in Russian
            - comment_en: same analysis in English
            - comment_uz: same analysis in Uzbek (Latin script)
            - recommendations_ru/en/uz: risk reduction recommendations

            Provide specific risks and mitigation measures in comments.""";

    // =========================================================================
    // Tool schemas
    // =========================================================================

    private static Map<String, Object> buildEvaluationSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("evaluation", Map.of("type", "integer", "description", "Evaluation in dollars (0-100000, multiple of 25000)"));
        props.put("is_spam", Map.of("type", "boolean", "description", "Whether input is spam/gibberish"));
        props.put("comment_ru", Map.of("type", "string", "description", "Analysis comment in Russian"));
        props.put("comment_en", Map.of("type", "string", "description", "Analysis comment in English"));
        props.put("comment_uz", Map.of("type", "string", "description", "Analysis comment in Uzbek (Latin)"));
        props.put("recommendations_ru", Map.of("type", "string", "description", "Recommendations in Russian"));
        props.put("recommendations_en", Map.of("type", "string", "description", "Recommendations in English"));
        props.put("recommendations_uz", Map.of("type", "string", "description", "Recommendations in Uzbek (Latin)"));

        return Map.of(
                "type", "object",
                "properties", props,
                "required", List.of("evaluation", "is_spam", "comment_ru", "comment_en", "comment_uz",
                        "recommendations_ru", "recommendations_en", "recommendations_uz")
        );
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Run the full Berkus Method evaluation for a startup.
     * Evaluates all 5 factors and combines them.
     */
    public BerkusResult evaluate(ValuationRequest request) {
        log.info("Starting Berkus evaluation for: {}", request.getStartupName());
        long startTime = System.currentTimeMillis();

        // Run AI-based evaluations in parallel, heuristic evaluations synchronously
        CompletableFuture<FactorEvaluation> ideaFuture = CompletableFuture.supplyAsync(
                () -> evaluateIdea(request));
        CompletableFuture<FactorEvaluation> gtmFuture = CompletableFuture.supplyAsync(
                () -> evaluateGtm(request));
        CompletableFuture<FactorEvaluation> marketRisksFuture = CompletableFuture.supplyAsync(
                () -> evaluateMarketRisks(request));

        FactorEvaluation prototypeResult = evaluatePrototype(request);
        FactorEvaluation teamResult = evaluateTeam(request);

        // Wait for AI evaluations
        FactorEvaluation ideaResult = ideaFuture.join();
        FactorEvaluation gtmResult = gtmFuture.join();
        FactorEvaluation marketRisksResult = marketRisksFuture.join();

        long totalValuation = ideaResult.getEvaluation() + prototypeResult.getEvaluation()
                + teamResult.getEvaluation() + gtmResult.getEvaluation()
                + marketRisksResult.getEvaluation();

        // Cap at total max
        totalValuation = Math.min(totalValuation, totalMax);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Berkus evaluation completed for {} in {}ms. Total: ${}", request.getStartupName(), elapsed, totalValuation);

        return BerkusResult.builder()
                .soundIdea(ideaResult)
                .prototype(prototypeResult)
                .qualityTeam(teamResult)
                .strategicRelationships(gtmResult)
                .productRollout(marketRisksResult)
                .totalValuation(totalValuation)
                .details(String.format("Berkus Method Valuation: $%,d (Idea: $%,d, Prototype: $%,d, Team: $%,d, GTM: $%,d, Risks: $%,d)",
                        totalValuation, ideaResult.getEvaluation(), prototypeResult.getEvaluation(),
                        teamResult.getEvaluation(), gtmResult.getEvaluation(), marketRisksResult.getEvaluation()))
                .build();
    }

    // =========================================================================
    // Factor 1: Sound Idea (AI-based)
    // =========================================================================

    private FactorEvaluation evaluateIdea(ValuationRequest request) {
        log.info("Evaluating Berkus Idea for: {}", request.getStartupName());

        try {
            String input = String.format("""
                    Startup name: %s
                    Startup description: %s
                    Industry: %s
                    Problem description: %s
                    Why this problem is important now: %s
                    Quantitative problem estimation (pain in dollars/time): %s
                    How the solution is better than alternatives: %s
                    """,
                    sanitize(request.getStartupName()),
                    sanitize(request.getStartupDescription()),
                    sanitize(request.getStartupIndustry()),
                    sanitize(request.getIdeaProblemDescription()),
                    sanitize(request.getIdeaProblemUrgency()),
                    sanitize(request.getIdeaMoneyTimeEstimation()),
                    sanitize(request.getIdeaSolutionAdvantage()));

            JsonNode result = bedrockClient.callClaudeWithTool(
                    IDEA_PROMPT, input,
                    "berkus_idea_output", "Return Berkus idea evaluation",
                    buildEvaluationSchema());

            return parseFactorEvaluation(result, maxIdea);

        } catch (Exception e) {
            log.error("Error evaluating Berkus Idea: {}", e.getMessage(), e);
            return fallbackEvaluation(25000, "Error evaluating idea", "Idea evaluation error", "Ideya baholashda xato");
        }
    }

    // =========================================================================
    // Factor 2: Prototype (Heuristic - no AI)
    // =========================================================================

    /**
     * Evaluates prototype/MVP status using deterministic heuristic logic.
     * Mirrors the Python evaluate_berkus_prototype function.
     */
    private FactorEvaluation evaluatePrototype(ValuationRequest request) {
        log.info("Evaluating Berkus Prototype for: {}", request.getStartupName());

        boolean hasPrototype = Boolean.TRUE.equals(request.getPrototypeHasPrototypeMvp());
        int numUsers = request.getPrototypeNumUsersTests() != null ? request.getPrototypeNumUsersTests() : 0;

        long evaluation;
        String commentRu;
        String recommendationsRu;

        if (!hasPrototype) {
            evaluation = 0;
            commentRu = "No prototype or MVP available.";
            recommendationsRu = "1) Create minimum viable product\n2) Test with first users\n3) Collect feedback and iterate";
        } else if (numUsers < 10) {
            evaluation = (long) (maxPrototype * 0.25);
            commentRu = "MVP available but few or no active users (<10).";
            recommendationsRu = "1) Launch pilot program with first customers\n2) Improve product based on feedback\n3) Expand testing to 50+ users";
        } else if (numUsers < 50) {
            evaluation = (long) (maxPrototype * 0.5);
            commentRu = String.format("MVP with first users (%d).", numUsers);
            recommendationsRu = "1) Scale testing to 100+ users\n2) Optimize product based on data\n3) Prepare scaling plan";
        } else if (numUsers < 200) {
            evaluation = (long) (maxPrototype * 0.75);
            commentRu = String.format("MVP actively tested (%d users/pilot customers).", numUsers);
            recommendationsRu = "1) Prepare product for commercial launch\n2) Develop monetization strategy\n3) Plan team scaling";
        } else {
            evaluation = maxPrototype;
            commentRu = String.format("Strong product with active user base (%d+).", numUsers);
            recommendationsRu = "1) Scale team and infrastructure\n2) Optimize unit economics\n3) Prepare for next funding round";
        }

        return FactorEvaluation.builder()
                .evaluation(evaluation)
                .isSpam(false)
                .commentRu(commentRu)
                .commentEn(commentRu) // English fallback
                .commentUz(commentRu) // Uzbek fallback
                .recommendationsRu(recommendationsRu)
                .recommendationsEn(recommendationsRu)
                .recommendationsUz(recommendationsRu)
                .build();
    }

    // =========================================================================
    // Factor 3: Quality Management Team (Heuristic - no AI)
    // =========================================================================

    /**
     * Evaluates team quality using deterministic heuristic logic.
     * Mirrors the Python evaluate_berkus_team function.
     */
    private FactorEvaluation evaluateTeam(ValuationRequest request) {
        log.info("Evaluating Berkus Team for: {}", request.getStartupName());

        int numCofounders = request.getTeamNumCofounders() != null ? request.getTeamNumCofounders() : 0;
        boolean hasCto = Boolean.TRUE.equals(request.getTeamHasCto());
        boolean hasAdvisors = Boolean.TRUE.equals(request.getTeamHasAdvisorsMentors());
        String experienceDesc = request.getTeamExperienceDescription() != null
                ? request.getTeamExperienceDescription().toLowerCase() : "";

        // Check for CEO and CTO in cofounders list
        boolean ceoFound = false;
        boolean ctoFound = hasCto;
        int fulltimeCount = 0;
        boolean strongExperience = experienceDesc.contains("exit") || experienceDesc.contains("startup")
                || experienceDesc.contains("experience");

        if (request.getTeamCofounders() != null) {
            for (ValuationRequest.CofounderInfo cofounder : request.getTeamCofounders()) {
                String role = cofounder.getRole() != null ? cofounder.getRole().toLowerCase() : "";
                if (role.contains("ceo") || role.contains("chief executive")) {
                    ceoFound = true;
                }
                if (role.contains("cto") || role.contains("chief technology")) {
                    ctoFound = true;
                }
                String workType = cofounder.getWorkType() != null ? cofounder.getWorkType().toLowerCase() : "";
                if (workType.contains("full")) {
                    fulltimeCount++;
                }
            }
        }

        // Check advisors for strong credentials
        boolean strongAdvisors = false;
        if (hasAdvisors && request.getTeamAdvisorsMentors() != null) {
            for (ValuationRequest.AdvisorInfo advisor : request.getTeamAdvisorsMentors()) {
                String comment = advisor.getComment() != null ? advisor.getComment().toLowerCase() : "";
                if (comment.contains("expert") || comment.contains("exit") || comment.contains("experience")) {
                    strongAdvisors = true;
                    break;
                }
            }
        }

        long evaluation;
        String commentRu;
        String recommendationsRu;
        boolean allFulltime = (numCofounders > 0 && fulltimeCount == numCofounders);
        boolean threeOrFourCofounders = numCofounders >= 3 && numCofounders <= 4;

        if (numCofounders == 1 && !ctoFound) {
            evaluation = 0;
            commentRu = "Solo founder without CTO.";
            recommendationsRu = "1) Find a technical cofounder (CTO)\n2) Attract experienced advisors\n3) Develop team skills";
        } else if (numCofounders == 2 && !strongExperience) {
            evaluation = (long) (maxTeam * 0.25);
            commentRu = "Two founders, basic experience.";
            recommendationsRu = "1) Expand team to 3-4 people\n2) Attract experienced advisors\n3) Define clear roles and responsibilities";
        } else if (numCofounders == 3 && ceoFound && ctoFound) {
            evaluation = (long) (maxTeam * 0.5);
            commentRu = "Three founders, key roles covered.";
            recommendationsRu = "1) Attract experienced advisors\n2) Develop expertise in key areas\n3) Plan team scaling";
        } else if (strongExperience && ctoFound && allFulltime) {
            evaluation = (long) (maxTeam * 0.75);
            commentRu = "Experienced founders + CTO full-time.";
            recommendationsRu = "1) Prepare hiring plan for key employees\n2) Develop corporate culture\n3) Create motivation system";
        } else if (threeOrFourCofounders && (strongExperience || strongAdvisors) && allFulltime) {
            evaluation = maxTeam;
            commentRu = "Strong team (3-4 cofounders, exits/advisors, full-time).";
            recommendationsRu = "1) Plan team scaling\n2) Develop leadership qualities\n3) Prepare knowledge transfer plan";
        } else {
            evaluation = (long) (maxTeam * 0.25);
            commentRu = "Basic team structure.";
            recommendationsRu = "1) Expand team to 3-4 people\n2) Attract experienced advisors\n3) Define clear roles";
        }

        return FactorEvaluation.builder()
                .evaluation(evaluation)
                .isSpam(false)
                .commentRu(commentRu)
                .commentEn(commentRu)
                .commentUz(commentRu)
                .recommendationsRu(recommendationsRu)
                .recommendationsEn(recommendationsRu)
                .recommendationsUz(recommendationsRu)
                .build();
    }

    // =========================================================================
    // Factor 4: Strategic Relationships / GTM (AI-based)
    // =========================================================================

    private FactorEvaluation evaluateGtm(ValuationRequest request) {
        log.info("Evaluating Berkus GTM for: {}", request.getStartupName());

        try {
            String input = String.format("""
                    Startup name: %s
                    Startup description: %s
                    Industry: %s
                    Customer acquisition strategy: %s
                    Has signed Letters of Intent (LoI): %s
                    Number of LoI: %s
                    Has pilot projects: %s
                    Pilot projects description: %s
                    Has marketing budget/plan: %s
                    Marketing budget/plan description: %s
                    """,
                    sanitize(request.getStartupName()),
                    sanitize(request.getStartupDescription()),
                    sanitize(request.getStartupIndustry()),
                    sanitize(request.getGtmCustomerAcquisitionStrategy()),
                    Boolean.TRUE.equals(request.getGtmHasLoi()),
                    request.getGtmNumLoi() != null ? request.getGtmNumLoi() : "Not specified",
                    Boolean.TRUE.equals(request.getGtmHasPilotsProjects()),
                    sanitize(request.getGtmPilotsProjectsDescription()),
                    Boolean.TRUE.equals(request.getGtmHasMarketingBudgetPlan()),
                    sanitize(request.getGtmMarketingBudgetPlanDescription()));

            JsonNode result = bedrockClient.callClaudeWithTool(
                    GTM_PROMPT, input,
                    "berkus_gtm_output", "Return Berkus GTM evaluation",
                    buildEvaluationSchema());

            return parseFactorEvaluation(result, maxGtm);

        } catch (Exception e) {
            log.error("Error evaluating Berkus GTM: {}", e.getMessage(), e);
            return fallbackEvaluation(0, "Error evaluating GTM strategy", "GTM evaluation error", "GTM baholashda xato");
        }
    }

    // =========================================================================
    // Factor 5: Product Rollout / Market Risks (AI-based)
    // =========================================================================

    private FactorEvaluation evaluateMarketRisks(ValuationRequest request) {
        log.info("Evaluating Berkus Market Risks for: {}", request.getStartupName());

        try {
            String legalBarriersInfo = Boolean.TRUE.equals(request.getMarketRisksHasLegalRegulatoryBarriers())
                    ? "Yes. Description: " + sanitize(request.getMarketRisksLegalRegulatoryBarriersDescription())
                    : "No";
            String ipProtectionInfo = Boolean.TRUE.equals(request.getMarketRisksHasIpProtection())
                    ? "Yes. Description: " + sanitize(request.getMarketRisksIpProtectionDescription())
                    : "No";

            String input = String.format("""
                    Startup name: %s
                    Startup description: %s
                    Industry: %s
                    Top 3 risks identified: %s
                    Legal/regulatory barriers: %s
                    IP protection: %s
                    Risk mitigation actions taken: %s
                    """,
                    sanitize(request.getStartupName()),
                    sanitize(request.getStartupDescription()),
                    sanitize(request.getStartupIndustry()),
                    sanitize(request.getMarketRisksTop3Risks()),
                    legalBarriersInfo,
                    ipProtectionInfo,
                    sanitize(request.getMarketRisksRiskMitigationActions()));

            JsonNode result = bedrockClient.callClaudeWithTool(
                    MARKET_RISKS_PROMPT, input,
                    "berkus_market_risks_output", "Return Berkus market risks evaluation",
                    buildEvaluationSchema());

            return parseFactorEvaluation(result, maxMarketRisks);

        } catch (Exception e) {
            log.error("Error evaluating Berkus Market Risks: {}", e.getMessage(), e);
            return fallbackEvaluation(0, "Error evaluating market risks", "Market risks evaluation error", "Bozor risklari baholashda xato");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private FactorEvaluation parseFactorEvaluation(JsonNode result, long maxValue) {
        long evaluation = result.path("evaluation").asLong(0);
        evaluation = Math.max(0, Math.min(evaluation, maxValue));

        return FactorEvaluation.builder()
                .evaluation(evaluation)
                .isSpam(result.path("is_spam").asBoolean(false))
                .commentRu(result.path("comment_ru").asText(""))
                .commentEn(result.path("comment_en").asText(""))
                .commentUz(result.path("comment_uz").asText(""))
                .recommendationsRu(result.path("recommendations_ru").asText(""))
                .recommendationsEn(result.path("recommendations_en").asText(""))
                .recommendationsUz(result.path("recommendations_uz").asText(""))
                .build();
    }

    private FactorEvaluation fallbackEvaluation(long evaluation, String commentRu, String commentEn, String commentUz) {
        return FactorEvaluation.builder()
                .evaluation(evaluation)
                .isSpam(false)
                .commentRu(commentRu)
                .commentEn(commentEn)
                .commentUz(commentUz)
                .recommendationsRu("1) Check data correctness\n2) Retry evaluation\n3) Contact support")
                .recommendationsEn("1) Check data correctness\n2) Retry evaluation\n3) Contact support")
                .recommendationsUz("1) Ma'lumotlar to'g'riligini tekshiring\n2) Baholashni qaytaring\n3) Qo'llab-quvvatlash bilan bog'laning")
                .build();
    }

    private String sanitize(String input) {
        if (input == null) return "Not provided";
        // Basic sanitization - truncate and strip control chars
        String sanitized = input.length() > 5000 ? input.substring(0, 5000) : input;
        return sanitized.replaceAll("[\\p{Cntrl}&&[^\n\t\r]]", "").trim();
    }
}
