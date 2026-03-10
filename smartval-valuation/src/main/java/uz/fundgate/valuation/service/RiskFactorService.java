package uz.fundgate.valuation.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.fundgate.valuation.dto.RiskFactorResult;
import uz.fundgate.valuation.dto.RiskFactorResult.RiskFactor;
import uz.fundgate.valuation.dto.ValuationRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Risk Factor Summation Valuation Service.
 *
 * Implements the Risk Factor Summation method for startup valuation.
 * Evaluates 12 risk categories, each rated from -2 to +2:
 *
 *  1. Product Stage
 *  2. Team Completeness
 *  3. Technology Risk
 *  4. Market Demand
 *  5. Competition
 *  6. Sales Channels
 *  7. Finances
 *  8. Legal
 *  9. Intellectual Property
 * 10. Data Security
 * 11. ESG (Environmental, Social, Governance)
 * 12. AI / Automation Risk
 *
 * Formula: Adjusted Valuation = Base Pre-Money + (Sum of ratings * Adjustment Step)
 * Where Adjustment Step is typically $250,000.
 *
 * Translated from Python: smartval_claude/riskfactor.py
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskFactorService {

    private final BedrockClientService bedrockClient;

    @Value("${valuation.riskfactor.base-pre-money:2000000}")
    private long basePreMoney;

    @Value("${valuation.riskfactor.adjustment-step:250000}")
    private long adjustmentStep;

    @Value("${valuation.riskfactor.min-valuation:500000}")
    private long minValuation;

    @Value("${valuation.riskfactor.max-valuation:5000000}")
    private long maxValuation;

    // =========================================================================
    // Prompts - translated from Python riskfactor.py instructions
    // =========================================================================

    private static final String TECH_RISK_PROMPT = """
            You are a senior venture analyst with 10+ years of experience evaluating technology startups. \
            Your task is to conduct a deep technology risk analysis using the Risk Factor Summation method.

            ## METHODOLOGY CONTEXT
            Risk Factor Summation is a professional startup valuation method where each risk factor \
            is rated from -2 to +2 points. Technology risk is one of the key factors affecting \
            the final valuation.

            ## EVALUATION CRITERIA
            1. **Technical maturity** - development stage, stability, scalability
            2. **Technical barriers** - difficulty for competitors to reproduce the technology
            3. **Innovation** - uniqueness of the technological approach
            4. **Tech debt and risks** - architectural issues, dependencies on outdated technologies

            ## SCORING SCALE
            - **-2**: Critical risk - technology unproven, no MVP, high failure probability
            - **-1**: High risk - MVP unstable, significant technical problems
            - **0**: Neutral - product works, standard tech stack without special advantages
            - **+1**: Low risk - unique technologies, proven scalability
            - **+2**: Minimal risk - patented technology, significant barriers for competitors

            ## RESPONSE FORMAT
            - evaluation: integer from -2 to +2
            - comment_ru: professional comment (2-3 sentences) in Russian
            - comment_en: same comment in English
            - comment_uz: same comment in Uzbek (Latin script)""";

    private static final String MARKET_DEMAND_PROMPT = """
            You are a senior venture fund analyst specializing in market potential assessment. \
            Your task is to conduct a comprehensive market and confirmed demand analysis \
            using the Risk Factor Summation method.

            ## EVALUATION CRITERIA
            1. **Market size** - TAM must be sufficient for venture scaling
            2. **SAM/SOM realism** - adequacy of addressable market estimates
            3. **Demand confirmation** - real customers, LoI, pilots
            4. **Market trends** - growing vs stagnating market

            ## SCORING SCALE
            - **-2**: TAM < $100M or complete lack of demand evidence
            - **-1**: Medium market ($100M-$1B TAM), no LoI or pilots
            - **0**: Sufficient market, minimal demand confirmation
            - **+1**: Large market ($1B+ TAM) with LoI or active pilots
            - **+2**: Huge market with paying customers and proven product-market fit

            ## RESPONSE FORMAT
            - evaluation: integer from -2 to +2
            - comment_ru/en/uz: professional analysis""";

    private static final String COMPETITION_PROMPT = """
            You are a competitive analysis expert in the venture industry. \
            Your task is to evaluate the startup's competitive position using the Risk Factor Summation method.

            ## EVALUATION CRITERIA
            1. **Competition intensity** - number and strength of competitors (Blue vs Red Ocean)
            2. **Differentiation sustainability** - is the advantage easy to copy
            3. **Entry barriers** - patents, network effects, scale effects, unique data
            4. **Timing** - is there a market opportunity window

            ## SCORING SCALE
            - **-2**: Red Ocean - many strong competitors, product easily reproduced
            - **-1**: Serious competitors exist, differentiation weak or temporary
            - **0**: Moderate competition, noticeable differences
            - **+1**: Strong differentiation, initial competitor barriers
            - **+2**: Blue Ocean or powerful protective barriers

            ## RESPONSE FORMAT
            - evaluation: integer from -2 to +2
            - comment_ru/en/uz: competitive analysis""";

    private static final String SALES_CHANNELS_PROMPT = """
            You are a go-to-market strategy expert with venture fund experience. \
            Your task is to evaluate sales channels and GTM strategy using the Risk Factor Summation method.

            ## EVALUATION CRITERIA
            1. **Strategy clarity** - understanding of target customer and path to them
            2. **Channel testing** - have hypotheses been tested in practice
            3. **Channel unit economics** - CAC, conversion data
            4. **Scalability** - can current channels be scaled

            ## SCORING SCALE
            - **-2**: No GTM strategy, "we will sell to everyone" or unrealistic plans
            - **-1**: Plan on paper but channels not tested
            - **0**: Initial channel testing, uncertain results
            - **+1**: Tested channels with measurable results
            - **+2**: Working channels with paying customers and clear unit economics

            ## RESPONSE FORMAT
            - evaluation: integer from -2 to +2
            - comment_ru/en/uz: GTM analysis""";

    private static final String FINANCES_PROMPT = """
            You are a venture fund financial analyst specializing in early-stage startup evaluation. \
            Your task is to evaluate financial health using the Risk Factor Summation method.

            ## EVALUATION CRITERIA
            1. **Runway** - enough time to reach milestones (minimum 12-18 months)
            2. **Burn Rate** - is spending level adequate for development stage
            3. **Unit economics (LTV/CAC)** - healthy ratio from 3:1
            4. **Capital efficiency** - are funds used rationally

            ## SCORING SCALE
            - **-2**: Runway < 6 months, uncontrolled burn, LTV/CAC < 1
            - **-1**: Runway 6-12 months, high burn, LTV/CAC 1-2
            - **0**: Runway 12+ months, moderate burn, no unit economics data
            - **+1**: Runway 18+ months, efficient burn, LTV/CAC 2-3
            - **+2**: Runway 24+ months, positive unit economics (LTV/CAC > 3), clear path to profitability

            ## RESPONSE FORMAT
            - evaluation: integer from -2 to +2
            - comment_ru/en/uz: financial analysis""";

    private static final String LEGAL_PROMPT = """
            You are a legal consultant with venture deal due diligence experience. \
            Your task is to evaluate legal risks using the Risk Factor Summation method.

            ## EVALUATION CRITERIA
            1. **Corporate structure** - is the company registered, clean cap table
            2. **Licenses and permits** - all necessary industry licenses obtained
            3. **Disputes and lawsuits** - current or potential legal problems
            4. **Regulatory risks** - compliance (especially for fintech, health, crypto)

            ## SCORING SCALE
            - **-2**: Active lawsuits, serious regulatory violations
            - **-1**: Company not registered or missing critical licenses
            - **0**: Basic legal structure in order, licenses in process
            - **+1**: Clean corporate structure, all necessary licenses obtained
            - **+2**: Impeccable legal history, structure optimized for investment

            ## RESPONSE FORMAT
            - evaluation: integer from -2 to +2
            - comment_ru/en/uz: legal risk analysis""";

    private static final String IP_PROMPT = """
            You are an intellectual property expert with venture industry experience. \
            Your task is to evaluate IP protection using the Risk Factor Summation method.

            ## EVALUATION CRITERIA
            1. **Patents** - filed applications or granted patents
            2. **Trademarks** - brand protection
            3. **Know-how** - unique technologies/algorithms
            4. **Infringement risks** - does the startup violate others' IP rights

            ## SCORING SCALE
            - **-2**: Potential risk of infringing others' patents
            - **-1**: No IP protection, easily copyable product
            - **0**: Basic protection (trademark), no unique technologies
            - **+1**: Patent applications filed, unique know-how exists
            - **+2**: Granted patents, strong IP protection, significant copy barriers

            ## RESPONSE FORMAT
            - evaluation: integer from -2 to +2
            - comment_ru/en/uz: IP analysis""";

    private static final String SECURITY_PROMPT = """
            You are an information security and compliance expert. \
            Your task is to evaluate data protection using the Risk Factor Summation method.

            ## EVALUATION CRITERIA
            1. **Infrastructure** - data storage and transmission security
            2. **Compliance** - GDPR, HIPAA, PCI-DSS compliance
            3. **Security policies** - documented procedures
            4. **Incidents** - security breach history

            ## SCORING SCALE
            - **-2**: Critical vulnerabilities, no personal data protection, breach history
            - **-1**: Minimal protection, no basic compliance
            - **0**: Basic data protection, working on compliance
            - **+1**: Security policies implemented, partial standards compliance
            - **+2**: Full GDPR/industry standards compliance, certifications (SOC2, ISO 27001)

            ## RESPONSE FORMAT
            - evaluation: integer from -2 to +2
            - comment_ru/en/uz: security analysis""";

    private static final String ESG_PROMPT = """
            You are an ESG analyst with impact investment evaluation experience. \
            Your task is to evaluate environmental, social impact and reputation \
            using the Risk Factor Summation method.

            ## EVALUATION CRITERIA
            1. **Environmental** - ecological impact, carbon footprint
            2. **Social** - social benefit, community impact
            3. **Governance** - transparency, ethical business practices
            4. **Reputation** - history of public scandals or problems

            ## SCORING SCALE
            - **-2**: Serious reputational problems, scandals, negative societal impact
            - **-1**: Moderate reputational risks or ethical concerns
            - **0**: Neutral - no special ESG impact (neither positive nor negative)
            - **+1**: Positive social or environmental impact
            - **+2**: Strong impact component, SDG-aligned business, B-Corp potential

            ## RESPONSE FORMAT
            - evaluation: integer from -2 to +2
            - comment_ru/en/uz: ESG analysis""";

    private static final String AI_AUTOMATION_PROMPT = """
            You are a technology analyst specializing in AI trends and their business impact. \
            Your task is to evaluate AI/Automation risks and opportunities \
            using the Risk Factor Summation method.

            ## EVALUATION CRITERIA
            1. **AI disruption** - how vulnerable is the product to AI replacement
            2. **AI in product** - does the startup use modern AI/ML technologies
            3. **Unique data** - proprietary data that is difficult to reproduce
            4. **AI expertise** - ML engineers, data scientists on the team

            ## SCORING SCALE
            - **-2**: High disruption risk - product easily replaced by ChatGPT/Claude
            - **-1**: Moderate disruption risk, startup doesn't use AI, competitors adopting AI
            - **0**: Neutral - AI not critical for industry, basic usage
            - **+1**: Active AI usage in product, ML expertise on team
            - **+2**: AI-first product, unique proprietary data, strong AI barriers

            ## RESPONSE FORMAT
            - evaluation: integer from -2 to +2
            - comment_ru/en/uz: AI risk analysis""";

    // =========================================================================
    // Tool schema for risk factor evaluation
    // =========================================================================

    private static Map<String, Object> buildRiskFactorSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("evaluation", Map.of("type", "integer", "description", "Risk factor rating from -2 to +2"));
        props.put("comment_ru", Map.of("type", "string", "description", "Analysis comment in Russian"));
        props.put("comment_en", Map.of("type", "string", "description", "Analysis comment in English"));
        props.put("comment_uz", Map.of("type", "string", "description", "Analysis comment in Uzbek (Latin)"));

        return Map.of(
                "type", "object",
                "properties", props,
                "required", List.of("evaluation", "comment_ru", "comment_en", "comment_uz")
        );
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Run the full Risk Factor Summation evaluation for a startup.
     */
    public RiskFactorResult evaluate(ValuationRequest request) {
        log.info("Starting Risk Factor evaluation for: {}", request.getStartupName());
        long startTime = System.currentTimeMillis();

        // Heuristic evaluations
        RiskFactor productStage = evaluateProductStage(request);
        RiskFactor teamCompleteness = evaluateTeamCompleteness(request);

        // AI-based evaluations in parallel
        CompletableFuture<RiskFactor> techRiskFuture = CompletableFuture.supplyAsync(
                () -> evaluateWithAI("Technology Risk", TECH_RISK_PROMPT, buildTechRiskInput(request)));
        CompletableFuture<RiskFactor> marketDemandFuture = CompletableFuture.supplyAsync(
                () -> evaluateWithAI("Market Demand", MARKET_DEMAND_PROMPT, buildMarketDemandInput(request)));
        CompletableFuture<RiskFactor> competitionFuture = CompletableFuture.supplyAsync(
                () -> evaluateWithAI("Competition", COMPETITION_PROMPT, buildCompetitionInput(request)));
        CompletableFuture<RiskFactor> salesChannelsFuture = CompletableFuture.supplyAsync(
                () -> evaluateWithAI("Sales Channels", SALES_CHANNELS_PROMPT, buildSalesChannelsInput(request)));
        CompletableFuture<RiskFactor> financesFuture = CompletableFuture.supplyAsync(
                () -> evaluateWithAI("Finances", FINANCES_PROMPT, buildFinancesInput(request)));
        CompletableFuture<RiskFactor> legalFuture = CompletableFuture.supplyAsync(
                () -> evaluateWithAI("Legal", LEGAL_PROMPT, buildLegalInput(request)));
        CompletableFuture<RiskFactor> ipFuture = CompletableFuture.supplyAsync(
                () -> evaluateWithAI("Intellectual Property", IP_PROMPT, buildIpInput(request)));
        CompletableFuture<RiskFactor> securityFuture = CompletableFuture.supplyAsync(
                () -> evaluateWithAI("Data Security", SECURITY_PROMPT, buildSecurityInput(request)));
        CompletableFuture<RiskFactor> esgFuture = CompletableFuture.supplyAsync(
                () -> evaluateWithAI("ESG", ESG_PROMPT, buildEsgInput(request)));
        CompletableFuture<RiskFactor> aiRiskFuture = CompletableFuture.supplyAsync(
                () -> evaluateWithAI("AI / Automation", AI_AUTOMATION_PROMPT, buildAiAutomationInput(request)));

        // Collect all risk factors
        List<RiskFactor> riskFactors = new ArrayList<>();
        riskFactors.add(productStage);
        riskFactors.add(teamCompleteness);
        riskFactors.add(techRiskFuture.join());
        riskFactors.add(marketDemandFuture.join());
        riskFactors.add(competitionFuture.join());
        riskFactors.add(salesChannelsFuture.join());
        riskFactors.add(financesFuture.join());
        riskFactors.add(legalFuture.join());
        riskFactors.add(ipFuture.join());
        riskFactors.add(securityFuture.join());
        riskFactors.add(esgFuture.join());
        riskFactors.add(aiRiskFuture.join());

        // Calculate total adjustment
        int totalRating = 0;
        for (RiskFactor rf : riskFactors) {
            rf.setAdjustment(rf.getRating() * adjustmentStep);
            totalRating += rf.getRating();
        }

        long totalDollarAdjustment = totalRating * adjustmentStep;
        long adjustedValuation = basePreMoney + totalDollarAdjustment;

        // Clamp to min/max
        adjustedValuation = Math.max(minValuation, Math.min(maxValuation, adjustedValuation));

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Risk Factor evaluation completed for {} in {}ms. Base: ${}, Adjustment: ${}, Final: ${}",
                request.getStartupName(), elapsed, basePreMoney, totalDollarAdjustment, adjustedValuation);

        return RiskFactorResult.builder()
                .preMoneyValuation(basePreMoney)
                .adjustedValuation(adjustedValuation)
                .riskFactors(riskFactors)
                .totalAdjustment(totalRating)
                .adjustmentStep(adjustmentStep)
                .details(String.format("Risk Factor Valuation: $%,d (Base: $%,d + %d factors x $%,d = %+$,d)",
                        adjustedValuation, basePreMoney, totalRating, adjustmentStep, totalDollarAdjustment))
                .build();
    }

    // =========================================================================
    // Heuristic evaluations (no AI)
    // =========================================================================

    /**
     * Product Stage - evaluated heuristically based on prototype/MVP status.
     * Mirrors the Python product_stage function.
     */
    private RiskFactor evaluateProductStage(ValuationRequest request) {
        int rating;
        String comment;

        boolean hasPrototype = Boolean.TRUE.equals(request.getPrototypeHasPrototypeMvp());
        int numUsers = request.getPrototypeNumUsersTests() != null ? request.getPrototypeNumUsersTests() : 0;
        boolean hasRevenue = request.getRevenue() != null && request.getRevenue() > 0;

        if (!hasPrototype) {
            rating = -2;
            comment = "No prototype or MVP. Idea stage only.";
        } else if (numUsers == 0) {
            rating = -1;
            comment = "MVP exists but no users or testing.";
        } else if (numUsers < 50 && !hasRevenue) {
            rating = 0;
            comment = String.format("MVP with %d users, no revenue yet.", numUsers);
        } else if (numUsers >= 50 || hasRevenue) {
            rating = 1;
            comment = String.format("Product with %d users and initial traction.", numUsers);
        } else {
            rating = 0;
            comment = "Product at early testing stage.";
        }

        if (hasRevenue && numUsers >= 100) {
            rating = 2;
            comment = "Product with revenue and strong user base.";
        }

        return RiskFactor.builder()
                .name("Product Stage")
                .rating(rating)
                .adjustment(rating * adjustmentStep)
                .commentRu(comment)
                .commentEn(comment)
                .commentUz(comment)
                .build();
    }

    /**
     * Team Completeness - evaluated heuristically.
     * Mirrors the Python team_completeness function.
     */
    private RiskFactor evaluateTeamCompleteness(ValuationRequest request) {
        int numCofounders = request.getTeamNumCofounders() != null ? request.getTeamNumCofounders() : 0;
        boolean hasCto = Boolean.TRUE.equals(request.getTeamHasCto());
        boolean hasAdvisors = Boolean.TRUE.equals(request.getTeamHasAdvisorsMentors());
        boolean hasExperience = request.getTeamExperienceDescription() != null
                && !request.getTeamExperienceDescription().isBlank();

        int rating;
        String comment;

        if (numCofounders <= 1) {
            rating = -2;
            comment = "Solo founder without key roles covered.";
        } else if (numCofounders >= 2 && !hasCto) {
            rating = -1;
            comment = "Small team, key gaps remain (no CTO).";
        } else if (numCofounders >= 2 && hasCto && !hasExperience) {
            rating = 0;
            comment = "Team has key roles but limited experience.";
        } else if (numCofounders >= 2 && hasCto && hasExperience && !hasAdvisors) {
            rating = 1;
            comment = "Core roles covered with relevant experience.";
        } else if (numCofounders >= 2 && hasCto && hasExperience && hasAdvisors) {
            rating = 2;
            comment = "Strong team with key roles, experience, and advisors/mentors.";
        } else {
            rating = 0;
            comment = "Incomplete team information provided.";
        }

        return RiskFactor.builder()
                .name("Team Completeness")
                .rating(rating)
                .adjustment(rating * adjustmentStep)
                .commentRu(comment)
                .commentEn(comment)
                .commentUz(comment)
                .build();
    }

    // =========================================================================
    // AI-based evaluation (generic)
    // =========================================================================

    private RiskFactor evaluateWithAI(String factorName, String systemPrompt, String input) {
        log.info("Evaluating Risk Factor '{}' with AI", factorName);

        try {
            String toolName = factorName.toLowerCase().replaceAll("[^a-z0-9]", "_") + "_output";

            JsonNode result = bedrockClient.callClaudeWithTool(
                    systemPrompt, input,
                    toolName, "Return risk factor evaluation for " + factorName,
                    buildRiskFactorSchema());

            int rating = result.path("evaluation").asInt(0);
            rating = Math.max(-2, Math.min(2, rating));

            return RiskFactor.builder()
                    .name(factorName)
                    .rating(rating)
                    .adjustment(rating * adjustmentStep)
                    .commentRu(result.path("comment_ru").asText(""))
                    .commentEn(result.path("comment_en").asText(""))
                    .commentUz(result.path("comment_uz").asText(""))
                    .build();

        } catch (Exception e) {
            log.error("Error evaluating risk factor '{}': {}", factorName, e.getMessage(), e);
            return RiskFactor.builder()
                    .name(factorName)
                    .rating(0)
                    .adjustment(0)
                    .commentRu("Evaluation error")
                    .commentEn("Evaluation error")
                    .commentUz("Baholash xatosi")
                    .build();
        }
    }

    // =========================================================================
    // Input builders
    // =========================================================================

    private String buildTechRiskInput(ValuationRequest req) {
        return String.format("""
                Startup name: %s
                Startup description: %s
                Industry: %s
                Has technical barriers: %s
                Technical barriers comment: %s
                Has product confirmation: %s
                Product confirmation comment: %s
                """,
                s(req.getStartupName()), s(req.getStartupDescription()), s(req.getStartupIndustry()),
                Boolean.TRUE.equals(req.getHasTechBarriers()), s(req.getTechBarriersComment()),
                Boolean.TRUE.equals(req.getHasProductConfirmation()), s(req.getProductConfirmationComment()));
    }

    private String buildMarketDemandInput(ValuationRequest req) {
        return String.format("""
                Startup name: %s
                Startup description: %s
                Industry: %s
                TAM: %s, comment: %s
                SAM: %s, comment: %s
                SOM: %s, comment: %s
                Has LoI: %s
                Has demand confirmation: %s
                Demand confirmation comment: %s
                """,
                s(req.getStartupName()), s(req.getStartupDescription()), s(req.getStartupIndustry()),
                req.getTamAmount(), s(req.getTamComment()),
                req.getSamAmount(), s(req.getSamComment()),
                req.getSomAmount(), s(req.getSomComment()),
                Boolean.TRUE.equals(req.getHasLoi()),
                Boolean.TRUE.equals(req.getHasDemandConfirmation()), s(req.getDemandConfirmationComment()));
    }

    private String buildCompetitionInput(ValuationRequest req) {
        String competitorsStr = "Not provided";
        if (req.getCompetitors() != null && !req.getCompetitors().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ValuationRequest.CompetitorInfo comp : req.getCompetitors()) {
                sb.append(String.format("- %s: %s\n", comp.getName(), comp.getDescription()));
            }
            competitorsStr = sb.toString();
        }
        return String.format("""
                Startup name: %s
                Startup description: %s
                Industry: %s
                Competitors: %s
                Differentiation: %s
                """,
                s(req.getStartupName()), s(req.getStartupDescription()), s(req.getStartupIndustry()),
                competitorsStr, s(req.getDifferentiation()));
    }

    private String buildSalesChannelsInput(ValuationRequest req) {
        return String.format("""
                Startup name: %s
                Startup description: %s
                Industry: %s
                GTM strategy: %s
                Customer acquisition strategy: %s
                """,
                s(req.getStartupName()), s(req.getStartupDescription()), s(req.getStartupIndustry()),
                s(req.getGtmStrategy()), s(req.getGtmCustomerAcquisitionStrategy()));
    }

    private String buildFinancesInput(ValuationRequest req) {
        return String.format("""
                Startup name: %s
                Startup description: %s
                Industry: %s
                Burn rate: %s
                Runway (months): %s
                CAC: %s
                LTV: %s
                """,
                s(req.getStartupName()), s(req.getStartupDescription()), s(req.getStartupIndustry()),
                req.getBurnRate(), req.getRunway(), req.getCac(), req.getLtv());
    }

    private String buildLegalInput(ValuationRequest req) {
        return String.format("""
                Startup name: %s
                Startup description: %s
                Industry: %s
                Has licenses: %s, comment: %s
                Has disputes: %s, comment: %s
                """,
                s(req.getStartupName()), s(req.getStartupDescription()), s(req.getStartupIndustry()),
                Boolean.TRUE.equals(req.getHasLicenses()), s(req.getLicensesComment()),
                Boolean.TRUE.equals(req.getHasDisputes()), s(req.getDisputesComment()));
    }

    private String buildIpInput(ValuationRequest req) {
        return String.format("""
                Startup name: %s
                Startup description: %s
                Industry: %s
                Has IP: %s
                IP comment: %s
                """,
                s(req.getStartupName()), s(req.getStartupDescription()), s(req.getStartupIndustry()),
                Boolean.TRUE.equals(req.getHasIp()), s(req.getIpComment()));
    }

    private String buildSecurityInput(ValuationRequest req) {
        return String.format("""
                Startup name: %s
                Startup description: %s
                Industry: %s
                Data storage description: %s
                GDPR compliance: %s
                """,
                s(req.getStartupName()), s(req.getStartupDescription()), s(req.getStartupIndustry()),
                s(req.getDataStorageDescription()), Boolean.TRUE.equals(req.getGdprCompliance()));
    }

    private String buildEsgInput(ValuationRequest req) {
        return String.format("""
                Startup name: %s
                Startup description: %s
                Industry: %s
                Has positive environmental/social impact: %s, comment: %s
                Has negative reputation: %s, comment: %s
                """,
                s(req.getStartupName()), s(req.getStartupDescription()), s(req.getStartupIndustry()),
                Boolean.TRUE.equals(req.getHasPositiveImpact()), s(req.getPositiveImpactComment()),
                Boolean.TRUE.equals(req.getHasNegativeReputation()), s(req.getNegativeReputationComment()));
    }

    private String buildAiAutomationInput(ValuationRequest req) {
        return String.format("""
                Startup name: %s
                Startup description: %s
                Industry: %s
                Team experience: %s
                Product differentiation: %s
                Has tech barriers: %s
                """,
                s(req.getStartupName()), s(req.getStartupDescription()), s(req.getStartupIndustry()),
                s(req.getTeamExperienceDescription()), s(req.getProductDifferentiation()),
                Boolean.TRUE.equals(req.getHasTechBarriers()));
    }

    private String s(String input) {
        if (input == null) return "Not provided";
        String sanitized = input.length() > 5000 ? input.substring(0, 5000) : input;
        return sanitized.replaceAll("[\\p{Cntrl}&&[^\n\t\r]]", "").trim();
    }
}
