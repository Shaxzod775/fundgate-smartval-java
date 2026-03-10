package uz.fundgate.fundgate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.fundgate.fundgate.agent.*;
import uz.fundgate.fundgate.dto.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Orchestrates all 6 agents to produce the final FundGate evaluation score.
 *
 * Pipeline:
 * 1. Check for hard blockers
 * 2. Run all 6 agents in parallel using CompletableFuture
 * 3. Aggregate scores (A-F, total 100)
 * 4. Generate summary via SummaryAgent
 * 5. Build and return FundGateResponse
 */
@Slf4j
@Service
public class ScoringOrchestrator {

    private static final Map<String, Integer> MAX_SCORES = Map.of(
            "A", 20, "B", 20, "C", 20, "D", 15, "E", 15, "F", 10
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?[0-9]{7,15}$");

    private final CompletenessAgent completenessAgent;
    private final PitchDeckAgent pitchDeckAgent;
    private final TractionAgent tractionAgent;
    private final TeamAgent teamAgent;
    private final ProductAgent productAgent;
    private final MaterialsAgent materialsAgent;
    private final SummaryAgent summaryAgent;
    private final ExecutorService executor;

    @Value("${fundgate.thresholds.ready-to-route:75}")
    private int thresholdReadyToRoute;

    @Value("${fundgate.thresholds.needs-improvement:50}")
    private int thresholdNeedsImprovement;

    public ScoringOrchestrator(CompletenessAgent completenessAgent,
                               PitchDeckAgent pitchDeckAgent,
                               TractionAgent tractionAgent,
                               TeamAgent teamAgent,
                               ProductAgent productAgent,
                               MaterialsAgent materialsAgent,
                               SummaryAgent summaryAgent) {
        this.completenessAgent = completenessAgent;
        this.pitchDeckAgent = pitchDeckAgent;
        this.tractionAgent = tractionAgent;
        this.teamAgent = teamAgent;
        this.productAgent = productAgent;
        this.materialsAgent = materialsAgent;
        this.summaryAgent = summaryAgent;
        this.executor = Executors.newFixedThreadPool(6);

        log.info("ScoringOrchestrator initialized with all 6 agents");
    }

    /**
     * Run complete evaluation pipeline.
     *
     * @param request submission request with startup data
     * @return complete FundGateResponse with scores and analysis
     */
    public FundGateResponse evaluate(SubmissionRequest request) {
        long startTime = System.currentTimeMillis();
        String analysisId = UUID.randomUUID().toString();

        log.info("Starting evaluation for: {} (analysisId: {})",
                request.getSubmission().getName(), analysisId);

        SubmissionData submission = request.getSubmission();
        FileUrls fileUrls = request.getFileUrls() != null ? request.getFileUrls() : new FileUrls();
        String locale = request.getLocale() != null ? request.getLocale() : "ru";

        // Step 1: Check blockers
        List<Blocker> blockers = checkBlockers(submission, fileUrls);
        if (!blockers.isEmpty()) {
            log.info("Blockers found: {}", blockers.size());
            return buildBlockedResponse(blockers, analysisId);
        }

        // Step 2: Run all agents in parallel
        log.info("Running 6 agents in parallel...");
        EvaluationResults results = runAllAgents(submission, fileUrls);

        // Step 3: Calculate scores
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("A", Math.min(results.completeness != null ? results.completeness.getTotalScore() : 0, 20));
        scores.put("B", Math.min(results.pitchDeck != null ? results.pitchDeck.getTotalScore() : 0, 20));
        scores.put("C", Math.min(results.traction != null ? results.traction.getTotalScore() : 0, 20));
        scores.put("D", Math.min(results.team != null ? results.team.getTotalScore() : 0, 15));
        scores.put("E", Math.min(results.product != null ? results.product.getTotalScore() : 0, 15));
        scores.put("F", Math.min(results.materials != null ? results.materials.getTotalScore() : 0, 10));

        int total = scores.values().stream().mapToInt(Integer::intValue).sum();
        log.info("Scores calculated: {} | Total: {}", scores, total);

        // Step 4: Determine status
        String status = determineStatus(total, false);

        // Step 5: Generate summary
        SummaryResult summary;
        try {
            summary = summaryAgent.generateHeuristic(submission, scores, total);
        } catch (Exception e) {
            log.error("Summary generation failed: {}", e.getMessage());
            summary = summaryAgent.createErrorSummary(e.getMessage());
        }

        // Step 6: Generate recommendations
        List<String> recommendations = generateRecommendations(scores, locale);

        // Step 7: Build startup comment from summary based on locale
        StartupComment startupComment = buildStartupComment(summary, locale);

        // Step 8: Build category details
        Map<String, CategoryScore> categoryDetails = buildCategoryDetails(results);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Evaluation complete: {} - total={}, status={}, elapsed={}ms",
                submission.getName(), total, status, elapsed);

        return FundGateResponse.builder()
                .module("fundgate")
                .version("1.0.0")
                .blockers(new ArrayList<>())
                .scores(scores)
                .total(total)
                .status(status)
                .recommendations(recommendations)
                .startupComment(startupComment)
                .categoryDetails(categoryDetails)
                .analysisId(analysisId)
                .processedAt(Instant.now())
                .build();
    }

    // --- Private helpers ---

    private List<Blocker> checkBlockers(SubmissionData submission, FileUrls fileUrls) {
        List<Blocker> blockers = new ArrayList<>();

        // No pitch deck
        if (fileUrls.getPitchDeck() == null || fileUrls.getPitchDeck().isBlank()) {
            blockers.add(Blocker.builder()
                    .code("no_pitch_deck")
                    .message("Pitch deck is required. Upload a PDF with your investor presentation.")
                    .build());
        }

        // Invalid email
        String email = submission.getEmail() != null ? submission.getEmail().trim() : "";
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            blockers.add(Blocker.builder()
                    .code("invalid_email")
                    .message("Valid email address is required (format: user@domain.com)")
                    .build());
        }

        // Invalid phone
        String phone = submission.getPhone() != null ? submission.getPhone().trim().replaceAll("[\\s()-]", "") : "";
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            blockers.add(Blocker.builder()
                    .code("invalid_phone")
                    .message("Valid phone in international format required (e.g., +998901234567)")
                    .build());
        }

        // Insufficient description
        String description = submission.getDescription() != null ? submission.getDescription() : "";
        if (description.length() < 30) {
            blockers.add(Blocker.builder()
                    .code("insufficient_description")
                    .message("Description must be at least 30 characters. Explain your problem and solution.")
                    .build());
        }

        return blockers;
    }

    private EvaluationResults runAllAgents(SubmissionData submission, FileUrls fileUrls) {
        EvaluationResults results = new EvaluationResults();

        // Launch all agents in parallel
        CompletableFuture<CompletenessResult> completenessF = CompletableFuture.supplyAsync(
                () -> safeEvaluate(() -> completenessAgent.evaluateHeuristic(submission),
                        () -> (CompletenessResult) completenessAgent.createErrorOutput("Agent failed")),
                executor);

        CompletableFuture<PitchDeckResult> pitchDeckF = CompletableFuture.supplyAsync(
                () -> {
                    String pdfUrl = fileUrls.getPitchDeck();
                    if (pdfUrl == null || pdfUrl.isBlank()) {
                        return pitchDeckAgent.createNoDeckResult();
                    }
                    try {
                        return pitchDeckAgent.evaluateWithVision(pdfUrl);
                    } catch (Exception e) {
                        log.error("Pitch deck agent error: {}", e.getMessage());
                        return (PitchDeckResult) pitchDeckAgent.createErrorOutput(e.getMessage());
                    }
                }, executor);

        CompletableFuture<TractionResult> tractionF = CompletableFuture.supplyAsync(
                () -> safeEvaluate(() -> tractionAgent.evaluateHeuristic(submission),
                        () -> (TractionResult) tractionAgent.createErrorOutput("Agent failed")),
                executor);

        CompletableFuture<TeamResult> teamF = CompletableFuture.supplyAsync(
                () -> safeEvaluate(() -> teamAgent.evaluateHeuristic(submission),
                        () -> (TeamResult) teamAgent.createErrorOutput("Agent failed")),
                executor);

        CompletableFuture<ProductResult> productF = CompletableFuture.supplyAsync(
                () -> safeEvaluate(() -> productAgent.evaluateHeuristic(submission),
                        () -> (ProductResult) productAgent.createErrorOutput("Agent failed")),
                executor);

        CompletableFuture<MaterialsResult> materialsF = CompletableFuture.supplyAsync(
                () -> safeEvaluate(() -> materialsAgent.evaluateHeuristic(submission, fileUrls),
                        () -> (MaterialsResult) materialsAgent.createErrorOutput("Agent failed")),
                executor);

        // Wait for all to complete
        CompletableFuture.allOf(completenessF, pitchDeckF, tractionF, teamF, productF, materialsF).join();

        results.completeness = completenessF.join();
        results.pitchDeck = pitchDeckF.join();
        results.traction = tractionF.join();
        results.team = teamF.join();
        results.product = productF.join();
        results.materials = materialsF.join();

        return results;
    }

    private <T> T safeEvaluate(java.util.function.Supplier<T> evaluator, java.util.function.Supplier<T> fallback) {
        try {
            return evaluator.get();
        } catch (Exception e) {
            log.error("Agent evaluation failed: {}", e.getMessage());
            return fallback.get();
        }
    }

    private String determineStatus(int total, boolean hasBlockers) {
        if (hasBlockers) return "blocked";
        if (total >= thresholdReadyToRoute) return "ready_to_route";
        if (total >= thresholdNeedsImprovement) return "needs_improvement";
        return "blocked";
    }

    private List<String> generateRecommendations(Map<String, Integer> scores, String locale) {
        Map<String, Map<String, String>> recs = Map.of(
                "A", Map.of(
                        "ru", "Заполните все обязательные поля и проверьте корректность контактных данных",
                        "en", "Complete all required fields and verify contact information",
                        "uz", "Barcha majburiy maydonlarni to'ldiring va kontakt ma'lumotlarini tekshiring"),
                "B", Map.of(
                        "ru", "Улучшите pitch deck - добавьте слайды Problem, Solution, Market, Team, Financials, Ask",
                        "en", "Improve pitch deck - add Problem, Solution, Market, Team, Financials, Ask slides",
                        "uz", "Pitch deck ni yaxshilang - Problem, Solution, Market, Team, Financials, Ask slaydlarini qo'shing"),
                "C", Map.of(
                        "ru", "Покажите больше тракшена - добавьте метрики пользователей или выручки",
                        "en", "Show more traction - add user metrics or revenue data",
                        "uz", "Ko'proq traction ko'rsating - foydalanuvchi yoki daromad ma'lumotlarini qo'shing"),
                "D", Map.of(
                        "ru", "Расширьте команду или добавьте информацию о ключевых ролях и опыте",
                        "en", "Expand team or add information about key roles and experience",
                        "uz", "Jamoani kengaytiring yoki asosiy rollar va tajriba haqida ma'lumot qo'shing"),
                "E", Map.of(
                        "ru", "Развивайте продукт и создавайте защищаемые преимущества (IP, уникальные данные)",
                        "en", "Develop product further and build defensible advantages (IP, unique data)",
                        "uz", "Mahsulotni rivojlantiring va himoyalanadigan afzalliklarni yarating (IP, noyob ma'lumotlar)"),
                "F", Map.of(
                        "ru", "Улучшите качество материалов - добавьте one-pager, финмодель, рабочие ссылки",
                        "en", "Improve materials quality - add one-pager, financial model, working links",
                        "uz", "Materiallar sifatini yaxshilang - one-pager, moliyaviy model, ishlaydigan havolalar qo'shing")
        );

        List<String> recommendations = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            String cat = entry.getKey();
            int score = entry.getValue();
            int maxScore = MAX_SCORES.getOrDefault(cat, 0);
            double percentage = maxScore > 0 ? (score * 100.0 / maxScore) : 0;

            if (percentage < 50 && recs.containsKey(cat)) {
                String rec = recs.get(cat).getOrDefault(locale, recs.get(cat).get("en"));
                if (rec != null) recommendations.add(rec);
            }
        }

        // Max 5 recommendations
        return recommendations.size() > 5 ? recommendations.subList(0, 5) : recommendations;
    }

    private StartupComment buildStartupComment(SummaryResult summary, String locale) {
        return switch (locale) {
            case "ru" -> StartupComment.builder()
                    .strengths(summary.getStrengthsRu())
                    .weaknesses(summary.getWeaknessesRu())
                    .overallComment(summary.getOverallCommentRu())
                    .detailedComment(summary.getDetailedCommentRu())
                    .recommendation(summary.getRecommendationRu())
                    .build();
            case "uz" -> StartupComment.builder()
                    .strengths(summary.getStrengthsUz())
                    .weaknesses(summary.getWeaknessesUz())
                    .overallComment(summary.getOverallCommentUz())
                    .detailedComment(summary.getDetailedCommentUz())
                    .recommendation(summary.getRecommendationUz())
                    .build();
            default -> StartupComment.builder()
                    .strengths(summary.getStrengthsEn())
                    .weaknesses(summary.getWeaknessesEn())
                    .overallComment(summary.getOverallCommentEn())
                    .detailedComment(summary.getDetailedCommentEn())
                    .recommendation(summary.getRecommendationEn())
                    .build();
        };
    }

    private Map<String, CategoryScore> buildCategoryDetails(EvaluationResults results) {
        Map<String, CategoryScore> details = new LinkedHashMap<>();

        if (results.completeness != null) {
            details.put("A", CategoryScore.builder()
                    .score(results.completeness.getTotalScore()).maxScore(20)
                    .percentage(results.completeness.getTotalScore() / 20.0 * 100)
                    .comment(results.completeness.getCommentEn()).build());
        }
        if (results.pitchDeck != null) {
            details.put("B", CategoryScore.builder()
                    .score(results.pitchDeck.getTotalScore()).maxScore(20)
                    .percentage(results.pitchDeck.getTotalScore() / 20.0 * 100)
                    .comment(results.pitchDeck.getCommentEn()).build());
        }
        if (results.traction != null) {
            details.put("C", CategoryScore.builder()
                    .score(results.traction.getTotalScore()).maxScore(20)
                    .percentage(results.traction.getTotalScore() / 20.0 * 100)
                    .comment(results.traction.getCommentEn()).build());
        }
        if (results.team != null) {
            details.put("D", CategoryScore.builder()
                    .score(results.team.getTotalScore()).maxScore(15)
                    .percentage(results.team.getTotalScore() / 15.0 * 100)
                    .comment(results.team.getCommentEn()).build());
        }
        if (results.product != null) {
            details.put("E", CategoryScore.builder()
                    .score(results.product.getTotalScore()).maxScore(15)
                    .percentage(results.product.getTotalScore() / 15.0 * 100)
                    .comment(results.product.getCommentEn()).build());
        }
        if (results.materials != null) {
            details.put("F", CategoryScore.builder()
                    .score(results.materials.getTotalScore()).maxScore(10)
                    .percentage(results.materials.getTotalScore() / 10.0 * 100)
                    .comment(results.materials.getCommentEn()).build());
        }

        return details;
    }

    private FundGateResponse buildBlockedResponse(List<Blocker> blockers, String analysisId) {
        Map<String, Integer> zeroScores = new LinkedHashMap<>();
        zeroScores.put("A", 0);
        zeroScores.put("B", 0);
        zeroScores.put("C", 0);
        zeroScores.put("D", 0);
        zeroScores.put("E", 0);
        zeroScores.put("F", 0);

        List<String> recommendations = blockers.stream()
                .map(Blocker::getMessage)
                .toList();

        return FundGateResponse.builder()
                .module("fundgate")
                .version("1.0.0")
                .blockers(blockers)
                .scores(zeroScores)
                .total(0)
                .status("blocked")
                .recommendations(recommendations)
                .startupComment(null)
                .analysisId(analysisId)
                .processedAt(Instant.now())
                .build();
    }

    /**
     * Container for all agent evaluation results.
     */
    private static class EvaluationResults {
        CompletenessResult completeness;
        PitchDeckResult pitchDeck;
        TractionResult traction;
        TeamResult team;
        ProductResult product;
        MaterialsResult materials;
    }
}
