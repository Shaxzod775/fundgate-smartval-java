package uz.fundgate.fundgate.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uz.fundgate.fundgate.dto.AgentResult;
import uz.fundgate.fundgate.dto.ProductResult;
import uz.fundgate.fundgate.dto.SubmissionData;
import uz.fundgate.fundgate.service.BedrockService;

import java.util.List;
import java.util.Map;

import static uz.fundgate.fundgate.agent.CompletenessAgent.*;

/**
 * Agent E: Evaluates product development stage and technological defensibility.
 *
 * Max Score: 15 points
 * - Stage (9 pts): Development stage
 * - Defensibility (6 pts): IP and technology moat
 */
@Slf4j
@Component
public class ProductAgent extends BaseAgent {

    /** Stage string -> score mapping. */
    private static final Map<String, Integer> STAGE_SCORES = Map.ofEntries(
            Map.entry("idea", 0),
            Map.entry("только идея", 0),
            Map.entry("pre-mvp", 2),
            Map.entry("mvp", 6),
            Map.entry("есть mvp", 6),
            Map.entry("mvp_no_revenue", 6),
            Map.entry("users", 7),
            Map.entry("есть пользователи", 7),
            Map.entry("users_no_stable_revenue", 7),
            Map.entry("revenue", 8),
            Map.entry("есть выручка", 8),
            Map.entry("early_revenue", 8),
            Map.entry("growth", 9),
            Map.entry("есть рост", 9),
            Map.entry("stable_revenue_growth", 9),
            Map.entry("financial_model_cashflow", 9)
    );

    public ProductAgent(BedrockService bedrockService) {
        super("ProductAgent", "E", 15, bedrockService);
    }

    @Override
    protected String getInstructions() {
        return """
                ## Category E: Product & Technology Analysis (15 points max)

                You are evaluating the product development stage and technological defensibility.

                ### E1: Stage Score (0-9 points)
                Based on development stage:

                | Stage | Score | Description |
                |-------|-------|-------------|
                | Idea Only | 0 | Just an idea, no development |
                | Pre-MVP | 2 | Early development, no working product |
                | MVP | 6 | Minimum viable product exists |
                | Users | 7 | MVP with active users |
                | Revenue | 8 | Generating revenue |
                | Growth | 9 | Scaling with proven model |

                ### E2: Defensibility Score (0-6 points)
                Technology moat and intellectual property:

                **IP Protection (0-3 pts)**:
                - Has patents or pending patents: 3 pts
                - Has proprietary technology: 2 pts
                - Plans for IP protection: 1 pt
                - No IP mentioned: 0 pts

                **Technology Barriers (0-3 pts)**:
                - Unique algorithm/data advantage: 3 pts
                - Significant technical complexity: 2 pts
                - Standard technology well-executed: 1 pt
                - Easily replicable: 0 pts

                Industry adjustments:
                - AI/ML: Data moat highly valued (+1 if unique data)
                - FinTech: Regulatory compliance as barrier (+1)
                - Hardware: Manufacturing expertise (+1)

                ### Red Flags
                - Stage claims don't match metrics
                - Vague technology descriptions
                - Easily replicable solution
                - No clear technical differentiation

                ### Output
                Provide assessment of product maturity and defensibility with recommendations for strengthening competitive position.""";
    }

    @Override
    protected String prepareInput(SubmissionData submission, Map<String, Object> extras) {
        StringBuilder sb = new StringBuilder("## Product Information\n\n");
        sb.append("- **Development Stage**: ").append(nvl(submission.getStage())).append("\n");
        sb.append("- **Has Proprietary Technology**: ").append(submission.isHasTechnology()).append("\n");
        sb.append("- **Has IP/Patents**: ").append(submission.isHasIP()).append("\n");

        String techDesc = nvl(submission.getTechnologyDescription());
        if (!techDesc.isEmpty()) {
            sb.append("- **Technology Description**: ").append(techDesc).append("\n");
        }

        sb.append("\n## Business Context\n\n");
        sb.append("- **Industry**: ").append(nvl(submission.getIndustry())).append("\n");
        String bm = nvl(submission.getBusinessModel());
        sb.append("- **Business Model**: ").append(bm.length() > 200 ? bm.substring(0, 200) : (bm.isEmpty() ? "Not specified" : bm)).append("\n");

        sb.append("\n## Stage Validation Metrics\n\n");
        sb.append("- **Revenue**: $").append(nvl(submission.getRevenue())).append("\n");
        sb.append("- **Users**: ").append(nvl(submission.getUserCount())).append("\n");
        sb.append("- **Has Users**: ").append(submission.isHasUsers()).append("\n");

        String desc = nvl(submission.getDescription());
        if (!desc.isEmpty()) {
            sb.append("\n## Startup Description\n").append(desc.length() > 500 ? desc.substring(0, 500) : desc).append("\n");
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> getToolSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                        Map.entry("stage_score", Map.of("type", "integer", "minimum", 0, "maximum", 9)),
                        Map.entry("defensibility_score", Map.of("type", "integer", "minimum", 0, "maximum", 6)),
                        Map.entry("total_score", Map.of("type", "integer", "minimum", 0, "maximum", 15)),
                        Map.entry("current_stage", Map.of("type", "string")),
                        Map.entry("stage_validated", Map.of("type", "boolean")),
                        Map.entry("has_ip", Map.of("type", "boolean")),
                        Map.entry("has_tech_moat", Map.of("type", "boolean")),
                        Map.entry("tech_complexity", Map.of("type", "string")),
                        Map.entry("comment_ru", Map.of("type", "string")),
                        Map.entry("comment_en", Map.of("type", "string")),
                        Map.entry("comment_uz", Map.of("type", "string"))
                ),
                "required", List.of("stage_score", "defensibility_score", "total_score",
                        "comment_ru", "comment_en", "comment_uz")
        );
    }

    @Override
    protected AgentResult parseToolResponse(JsonNode toolOutput) {
        return ProductResult.builder()
                .stageScore(toolOutput.path("stage_score").asInt(0))
                .defensibilityScore(toolOutput.path("defensibility_score").asInt(0))
                .totalScore(toolOutput.path("total_score").asInt(0))
                .currentStage(toolOutput.path("current_stage").asText("Unknown"))
                .stageValidated(toolOutput.path("stage_validated").asBoolean(false))
                .hasIp(toolOutput.path("has_ip").asBoolean(false))
                .hasTechMoat(toolOutput.path("has_tech_moat").asBoolean(false))
                .techComplexity(toolOutput.path("tech_complexity").asText("medium"))
                .commentRu(toolOutput.path("comment_ru").asText(""))
                .commentEn(toolOutput.path("comment_en").asText(""))
                .commentUz(toolOutput.path("comment_uz").asText(""))
                .build();
    }

    @Override
    public AgentResult createErrorOutput(String errorMessage) {
        return ProductResult.builder()
                .stageScore(0).defensibilityScore(0).totalScore(0)
                .currentStage("Unknown").stageValidated(false)
                .hasIp(false).hasTechMoat(false).techComplexity("unknown")
                .commentRu("Ошибка оценки продукта: " + errorMessage)
                .commentEn("Product evaluation error: " + errorMessage)
                .commentUz("Mahsulot baholash xatosi: " + errorMessage)
                .build();
    }

    /**
     * Fast heuristic evaluation without AI.
     */
    public ProductResult evaluateHeuristic(SubmissionData submission) {
        String stage = nvl(submission.getStage()).toLowerCase();

        // E1: Stage score
        int stageScore = 0;
        for (Map.Entry<String, Integer> entry : STAGE_SCORES.entrySet()) {
            if (stage.contains(entry.getKey())) {
                stageScore = entry.getValue();
                break;
            }
        }

        // Validate stage against metrics
        double revenue = parseDouble(submission.getRevenue());
        int users = parseInt(submission.getUserCount());
        boolean stageValidated = true;
        if (stageScore >= 8 && revenue == 0) stageValidated = false;
        if (stageScore >= 7 && users == 0 && revenue == 0) stageValidated = false;

        // E2: Defensibility score
        int defensibilityScore = 0;
        boolean hasIp = submission.isHasIP();
        boolean hasTech = submission.isHasTechnology();
        String industry = nvl(submission.getIndustry()).toLowerCase();

        if (hasIp) defensibilityScore += 3;
        if (hasTech) defensibilityScore += 2;
        else if (isNotEmpty(submission.getTechnologyDescription())) defensibilityScore += 1;

        // Industry bonus
        if ((industry.contains("ai") || industry.contains("ml") || industry.contains("fintech")) && hasTech) {
            defensibilityScore = Math.min(defensibilityScore + 1, 6);
        }

        int total = Math.min(stageScore + defensibilityScore, 15);

        // Tech complexity
        String techComplexity = "low";
        if (hasTech || hasIp) techComplexity = "medium";
        if (industry.contains("ai") || industry.contains("ml") || industry.contains("blockchain")) {
            techComplexity = "high";
        }

        String stageDisplay = nvl(submission.getStage()).isEmpty() ? "Unknown" : submission.getStage();

        return ProductResult.builder()
                .stageScore(stageScore)
                .defensibilityScore(defensibilityScore)
                .totalScore(total)
                .currentStage(stageDisplay)
                .stageValidated(stageValidated)
                .hasIp(hasIp)
                .hasTechMoat(hasTech)
                .techComplexity(techComplexity)
                .commentRu(String.format("Стадия: %s. %s", stageDisplay,
                        hasIp ? "IP защита имеется." : "IP защита отсутствует."))
                .commentEn(String.format("Stage: %s. %s", stageDisplay,
                        hasIp ? "IP protection present." : "No IP protection."))
                .commentUz(String.format("Bosqich: %s. %s", stageDisplay,
                        hasIp ? "IP himoyasi mavjud." : "IP himoyasi yo'q."))
                .build();
    }
}
