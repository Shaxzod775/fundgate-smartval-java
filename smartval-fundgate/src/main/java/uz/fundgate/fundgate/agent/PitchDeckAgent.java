package uz.fundgate.fundgate.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uz.fundgate.fundgate.dto.AgentResult;
import uz.fundgate.fundgate.dto.PitchDeckResult;
import uz.fundgate.fundgate.dto.SlideAnalysis;
import uz.fundgate.fundgate.dto.SubmissionData;
import uz.fundgate.fundgate.service.BedrockService;
import uz.fundgate.fundgate.service.PdfVisionService;

import java.util.*;

import static uz.fundgate.fundgate.agent.CompletenessAgent.jsonArrayToList;

/**
 * Agent B: Vision-based pitch deck analysis.
 *
 * Max Score: 20 points
 * - Structure (8 pts): Required slides present
 * - Content (8 pts): Quality of information
 * - Design (4 pts): Visual presentation
 *
 * Uses Claude Vision to analyze PDF slides via PdfVisionService.
 */
@Slf4j
@Component
public class PitchDeckAgent extends BaseAgent {

    private static final List<String> REQUIRED_SLIDES = Arrays.asList(
            "Problem", "Solution", "Market", "Business Model",
            "Traction", "Team", "Financials", "Ask"
    );

    private final PdfVisionService pdfVisionService;

    public PitchDeckAgent(BedrockService bedrockService, PdfVisionService pdfVisionService) {
        super("PitchDeckAgent", "B", 20, bedrockService);
        this.pdfVisionService = pdfVisionService;
    }

    @Override
    protected String getInstructions() {
        return """
                ## Category B: Pitch Deck Analysis (20 points max)

                You are analyzing a pitch deck using visual analysis of each slide.

                ### Required Slides
                A complete pitch deck should contain:
                1. **Problem** - Clear articulation of the pain point being solved
                2. **Solution** - Unique value proposition and how it solves the problem
                3. **Market** - TAM/SAM/SOM with credible data sources
                4. **Business Model** - Revenue streams, pricing strategy
                5. **Traction** - Metrics, milestones, customer validation
                6. **Team** - Key team members, relevant experience
                7. **Financials** - Projections, unit economics, funding history
                8. **Ask** - Funding amount, use of funds, timeline

                ### B1: Structure Score (0-8 points)
                - 8 pts: All 8 required slides present, logical flow
                - 6-7 pts: 6-7 slides present
                - 4-5 pts: 4-5 slides present
                - 2-3 pts: 2-3 slides present
                - 0-1 pts: Less than 2 slides or unreadable

                ### B2: Content Score (0-8 points)
                Evaluate each slide's content quality (1-5 scale):
                - 5: Excellent - Specific data, compelling narrative, strong evidence
                - 4: Good - Clear information, some supporting data
                - 3: Average - Basic information, standard presentation
                - 2: Below average - Vague claims, missing key info
                - 1: Poor - Unclear, missing critical information

                Calculate: (Average quality across slides) * 1.6, rounded to integer

                ### B3: Design Score (0-4 points)
                - 4 pts: Professional design, consistent branding, excellent readability
                - 3 pts: Good design, minor issues
                - 2 pts: Adequate design, some readability problems
                - 1 pt: Poor design, hard to read
                - 0 pts: Unprofessional or unusable

                ### Output
                Provide detailed analysis with actionable recommendations for improvement.""";
    }

    @Override
    protected String prepareInput(SubmissionData submission, Map<String, Object> extras) {
        StringBuilder sb = new StringBuilder("## Startup Context for Pitch Deck Evaluation\n\n");
        sb.append("- **Name**: ").append(CompletenessAgent.nvl(submission.getName())).append("\n");
        sb.append("- **Industry**: ").append(CompletenessAgent.nvl(submission.getIndustry())).append("\n");
        sb.append("- **Stage**: ").append(CompletenessAgent.nvl(submission.getStage())).append("\n");
        String desc = CompletenessAgent.nvl(submission.getDescription());
        sb.append("- **Description**: ").append(desc.length() > 500 ? desc.substring(0, 500) : desc).append("\n");
        return sb.toString();
    }

    @Override
    protected Map<String, Object> getToolSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                        Map.entry("structure_score", Map.of("type", "integer", "minimum", 0, "maximum", 8)),
                        Map.entry("content_score", Map.of("type", "integer", "minimum", 0, "maximum", 8)),
                        Map.entry("design_score", Map.of("type", "integer", "minimum", 0, "maximum", 4)),
                        Map.entry("slides_found", Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry("missing_slides", Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry("comment_en", Map.of("type", "string")),
                        Map.entry("recommendations_en", Map.of("type", "string"))
                ),
                "required", List.of("structure_score", "content_score", "design_score",
                        "slides_found", "missing_slides", "comment_en", "recommendations_en")
        );
    }

    @Override
    protected AgentResult parseToolResponse(JsonNode toolOutput) {
        int structure = Math.min(toolOutput.path("structure_score").asInt(0), 8);
        int content = Math.min(toolOutput.path("content_score").asInt(0), 8);
        int design = Math.min(toolOutput.path("design_score").asInt(0), 4);
        int total = structure + content + design;

        return PitchDeckResult.builder()
                .structureScore(structure)
                .contentScore(content)
                .designScore(design)
                .totalScore(total)
                .slidesFound(jsonArrayToList(toolOutput.path("slides_found")))
                .missingSlides(jsonArrayToList(toolOutput.path("missing_slides")))
                .commentEn(toolOutput.path("comment_en").asText(""))
                .recommendationsEn(toolOutput.path("recommendations_en").asText(""))
                .build();
    }

    @Override
    public AgentResult createErrorOutput(String errorMessage) {
        return PitchDeckResult.builder()
                .structureScore(0).contentScore(0).designScore(0).totalScore(0)
                .slidesAnalyzed(0)
                .slidesFound(new ArrayList<>())
                .missingSlides(new ArrayList<>(REQUIRED_SLIDES))
                .slideDetails(new ArrayList<>())
                .commentRu("Ошибка анализа pitch deck: " + errorMessage)
                .commentEn("Pitch deck analysis error: " + errorMessage)
                .commentUz("Pitch deck tahlil xatosi: " + errorMessage)
                .recommendationsRu("Проверьте формат файла и повторите попытку")
                .recommendationsEn("Check file format and try again")
                .recommendationsUz("Fayl formatini tekshiring va qayta urinib ko'ring")
                .build();
    }

    /**
     * Create result when no pitch deck is provided.
     */
    public PitchDeckResult createNoDeckResult() {
        return PitchDeckResult.builder()
                .structureScore(0).contentScore(0).designScore(0).totalScore(0)
                .slidesAnalyzed(0)
                .slidesFound(new ArrayList<>())
                .missingSlides(new ArrayList<>(REQUIRED_SLIDES))
                .slideDetails(new ArrayList<>())
                .commentRu("Pitch deck не загружен. Это обязательный документ для оценки.")
                .commentEn("Pitch deck not uploaded. This is a required document for evaluation.")
                .commentUz("Pitch deck yuklanmagan. Bu baholash uchun majburiy hujjat.")
                .recommendationsRu("Загрузите pitch deck в формате PDF с описанием проблемы, решения, рынка, команды и финансов.")
                .recommendationsEn("Upload a pitch deck in PDF format covering problem, solution, market, team, and financials.")
                .recommendationsUz("Muammo, yechim, bozor, jamoa va moliyani qamrab olgan PDF formatdagi pitch deck yuklang.")
                .build();
    }

    /**
     * Analyze pitch deck using Claude Vision.
     * Downloads the PDF, converts pages to images, and sends them to Claude.
     */
    public PitchDeckResult evaluateWithVision(String pdfUrl) {
        log.info("PitchDeckAgent: Starting vision analysis for URL: {}...", pdfUrl.substring(0, Math.min(80, pdfUrl.length())));
        try {
            List<byte[]> images = pdfVisionService.downloadAndConvertPdf(pdfUrl);
            if (images.isEmpty()) {
                log.warn("PitchDeckAgent: No images extracted from PDF");
                return (PitchDeckResult) createErrorOutput("Could not extract images from PDF");
            }

            String visionPrompt = buildVisionPrompt();
            Map<String, Object> toolSchema = buildVisionToolSchema();

            // Use a vision-capable model (Sonnet) for image analysis
            String visionModel = "us.anthropic.claude-sonnet-4-20250514-v1:0";
            JsonNode result = bedrockService.callClaudeVision(
                    "You are an expert pitch deck analyst for a venture capital fund. Analyze slides thoroughly and provide actionable feedback.",
                    images,
                    visionPrompt,
                    "pitch_deck_analysis",
                    "Return structured pitch deck analysis with scores",
                    toolSchema,
                    visionModel
            );

            return parseVisionResult(result, images.size());

        } catch (Exception e) {
            log.error("PitchDeckAgent: Vision analysis failed - {}", e.getMessage(), e);
            return (PitchDeckResult) createErrorOutput(e.getMessage());
        }
    }

    private String buildVisionPrompt() {
        return """
                Analyze these pitch deck slides as an expert venture capital analyst.

                ## Your Task
                Identify each slide type and evaluate the pitch deck content quality. Focus primarily on INFORMATION CONTENT, not visual design.

                ## IMPORTANT: Text-Based Slides Are Valid
                Many startups use simple text-based pitch decks without fancy graphics. This is perfectly acceptable.
                Focus on whether the slide CONTAINS the right information, not whether it looks visually impressive.

                ## Required Slides to Find
                Problem, Solution, Market, Business Model, Traction, Team, Financials, Ask

                ## Slide Type Detection Rules
                Look for these keywords/content to identify slide types:
                - **Problem**: pain points, challenges, issues, current situation
                - **Solution**: how we solve, our approach, product/service description, value proposition
                - **Market**: TAM, SAM, SOM, market size, target customers, opportunity
                - **Business Model**: revenue model, pricing, how we make money, monetization
                - **Traction**: metrics, users, customers, revenue, MRR, growth, partnerships
                - **Team**: founders, CEO, CTO, team members, experience, advisors
                - **Financials**: projections, revenue forecast, costs, runway, burn rate
                - **Ask**: funding request, raising amount, use of funds, investment ask

                ## Scoring Rules

                ### Structure Score (0-8 points)
                - 8 pts: All 8 required slides present
                - 6 pts: 6-7 required slides present
                - 4 pts: 4-5 required slides present
                - 2 pts: 2-3 required slides present
                - 0 pts: Less than 2 required slides

                ### Content Score (0-8 points)
                Calculate: (Average quality score across identified slides) * 1.6, rounded

                ### Design Score (0-4 points)
                For text-based presentations:
                - 3-4 pts: Clean, readable text, good structure
                - 2 pts: Readable but could be better organized
                - 1 pt: Hard to read or poorly structured
                - 0 pts: Unreadable

                ## Output Language
                Provide ALL comments and recommendations in ENGLISH ONLY.
                Be specific, constructive, and actionable in your feedback.""";
    }

    private Map<String, Object> buildVisionToolSchema() {
        Map<String, Object> slideAnalysisSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "slide_number", Map.of("type", "integer"),
                        "slide_type", Map.of("type", "string"),
                        "quality_score", Map.of("type", "integer", "minimum", 1, "maximum", 5),
                        "content_summary", Map.of("type", "string"),
                        "design_quality", Map.of("type", "string")
                )
        );

        return Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                        Map.entry("structure_score", Map.of("type", "integer", "minimum", 0, "maximum", 8)),
                        Map.entry("content_score", Map.of("type", "integer", "minimum", 0, "maximum", 8)),
                        Map.entry("design_score", Map.of("type", "integer", "minimum", 0, "maximum", 4)),
                        Map.entry("slides_found", Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry("missing_slides", Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry("slide_analyses", Map.of("type", "array", "items", slideAnalysisSchema)),
                        Map.entry("comment", Map.of("type", "string")),
                        Map.entry("recommendations", Map.of("type", "string"))
                ),
                "required", List.of("structure_score", "content_score", "design_score",
                        "slides_found", "missing_slides", "comment", "recommendations")
        );
    }

    private PitchDeckResult parseVisionResult(JsonNode data, int slidesCount) {
        int structureScore = Math.min(data.path("structure_score").asInt(0), 8);
        int contentScore = Math.min(data.path("content_score").asInt(0), 8);
        int designScore = Math.min(data.path("design_score").asInt(0), 4);
        int totalScore = structureScore + contentScore + designScore;

        // Parse slide analyses
        List<SlideAnalysis> slideDetails = new ArrayList<>();
        JsonNode analyses = data.path("slide_analyses");
        if (analyses.isArray()) {
            for (JsonNode sa : analyses) {
                slideDetails.add(SlideAnalysis.builder()
                        .slideNumber(sa.path("slide_number").asInt(0))
                        .slideType(sa.path("slide_type").asText("Unknown"))
                        .qualityScore(Math.min(Math.max(sa.path("quality_score").asInt(3), 1), 5))
                        .contentSummary(sa.path("content_summary").asText(""))
                        .designQuality(sa.path("design_quality").asText("average"))
                        .build());
            }
        }

        String commentEn = data.path("comment").asText("");
        String recommendationsEn = data.path("recommendations").asText("");

        return PitchDeckResult.builder()
                .structureScore(structureScore)
                .contentScore(contentScore)
                .designScore(designScore)
                .totalScore(totalScore)
                .slidesAnalyzed(slidesCount)
                .slidesFound(jsonArrayToList(data.path("slides_found")))
                .missingSlides(jsonArrayToList(data.path("missing_slides")))
                .slideDetails(slideDetails)
                .commentRu("")
                .commentEn(commentEn)
                .commentUz("")
                .recommendationsRu("")
                .recommendationsEn(recommendationsEn)
                .recommendationsUz("")
                .build();
    }
}
