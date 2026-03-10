package uz.fundgate.fundgate.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uz.fundgate.fundgate.dto.AgentResult;
import uz.fundgate.fundgate.dto.SubmissionData;
import uz.fundgate.fundgate.dto.TractionResult;
import uz.fundgate.fundgate.service.BedrockService;

import java.util.List;
import java.util.Map;

import static uz.fundgate.fundgate.agent.CompletenessAgent.*;

/**
 * Agent C: Evaluates traction metrics and validation.
 *
 * Max Score: 20 points
 * - Revenue (12 pts): Revenue metrics and growth
 * - Users (5 pts): User/customer metrics
 * - Growth (3 pts): Growth indicators and validation
 */
@Slf4j
@Component
public class TractionAgent extends BaseAgent {

    public TractionAgent(BedrockService bedrockService) {
        super("TractionAgent", "C", 20, bedrockService);
    }

    @Override
    protected String getInstructions() {
        return """
                ## Category C: Traction Analysis (20 points max)

                You are evaluating the traction and market validation of a startup.

                ### C1: Revenue Score (0-12 points)
                Monthly revenue in USD:
                - > $20,000: 12 points
                - $5,000 - $20,000: 10 points
                - $1,000 - $5,000: 7 points
                - $1 - $1,000: 4 points
                - $0: 0 points

                **Growth Bonus** (if revenue > 0 AND stage indicates growth):
                - Add up to 3 points (max 12 total for C1)
                - Consistent growth claims: +1-2 pts
                - Stage "Growth": +2-3 pts

                ### C2: User Score (0-5 points)
                Only if NO revenue (revenue-focused startups score via C1):
                - >= 10,000 users: 5 points
                - 2,000 - 10,000 users: 4 points
                - 500 - 2,000 users: 3 points
                - 100 - 500 users: 2 points
                - < 100 users: 0-1 points

                ### C3: Growth Indicators (0-3 points)
                - Stage progression evidence: 0-1 pts
                - Consistent metrics vs stage: 0-1 pts
                - Customer validation signals: 0-1 pts

                ### Validation Rules
                1. Revenue must match stage claims
                2. User counts should be plausible for industry
                3. Look for inconsistencies between metrics and stage
                4. Consider industry context (B2B vs B2C user counts)

                ### Red Flags
                - Revenue claimed but stage is "Idea"
                - Very high metrics with no supporting evidence
                - Inconsistent numbers across fields

                ### Output
                Provide specific analysis of their traction with constructive feedback on how to improve metrics presentation.""";
    }

    @Override
    protected String prepareInput(SubmissionData submission, Map<String, Object> extras) {
        StringBuilder sb = new StringBuilder("## Traction Metrics\n\n");
        sb.append("- **Monthly Revenue**: $").append(nvl(submission.getRevenue())).append("\n");
        sb.append("- **User Count**: ").append(nvl(submission.getUserCount())).append("\n");
        sb.append("- **Has Active Users**: ").append(submission.isHasUsers()).append("\n");
        sb.append("- **Development Stage**: ").append(nvl(submission.getStage())).append("\n");
        sb.append("- **Industry**: ").append(nvl(submission.getIndustry())).append("\n");

        String bm = nvl(submission.getBusinessModel());
        if (!bm.isEmpty()) {
            sb.append("- **Business Model**: ").append(bm.length() > 200 ? bm.substring(0, 200) : bm).append("\n");
        }

        sb.append("\n## Additional Context\n\n");
        String desc = nvl(submission.getDescription());
        sb.append("- **Description**: ").append(desc.length() > 300 ? desc.substring(0, 300) : desc).append("\n");

        if (submission.isHasInvestments()) {
            int rounds = submission.getInvestments() != null ? submission.getInvestments().size() : 0;
            sb.append("- **Has Investments**: Yes (").append(rounds).append(" rounds)\n");
        } else {
            sb.append("- **Has Investments**: No\n");
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> getToolSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                        Map.entry("revenue_score", Map.of("type", "integer", "minimum", 0, "maximum", 12)),
                        Map.entry("users_score", Map.of("type", "integer", "minimum", 0, "maximum", 5)),
                        Map.entry("growth_score", Map.of("type", "integer", "minimum", 0, "maximum", 3)),
                        Map.entry("total_score", Map.of("type", "integer", "minimum", 0, "maximum", 20)),
                        Map.entry("revenue_validated", Map.of("type", "boolean")),
                        Map.entry("user_count_validated", Map.of("type", "boolean")),
                        Map.entry("revenue_analysis", Map.of("type", "string")),
                        Map.entry("users_analysis", Map.of("type", "string")),
                        Map.entry("comment_ru", Map.of("type", "string")),
                        Map.entry("comment_en", Map.of("type", "string")),
                        Map.entry("comment_uz", Map.of("type", "string"))
                ),
                "required", List.of("revenue_score", "users_score", "growth_score",
                        "total_score", "comment_ru", "comment_en", "comment_uz")
        );
    }

    @Override
    protected AgentResult parseToolResponse(JsonNode toolOutput) {
        return TractionResult.builder()
                .revenueScore(toolOutput.path("revenue_score").asInt(0))
                .usersScore(toolOutput.path("users_score").asInt(0))
                .growthScore(toolOutput.path("growth_score").asInt(0))
                .totalScore(toolOutput.path("total_score").asInt(0))
                .revenueValidated(toolOutput.path("revenue_validated").asBoolean(false))
                .userCountValidated(toolOutput.path("user_count_validated").asBoolean(false))
                .revenueAnalysis(toolOutput.path("revenue_analysis").asText(""))
                .usersAnalysis(toolOutput.path("users_analysis").asText(""))
                .commentRu(toolOutput.path("comment_ru").asText(""))
                .commentEn(toolOutput.path("comment_en").asText(""))
                .commentUz(toolOutput.path("comment_uz").asText(""))
                .build();
    }

    @Override
    public AgentResult createErrorOutput(String errorMessage) {
        return TractionResult.builder()
                .revenueScore(0).usersScore(0).growthScore(0).totalScore(0)
                .revenueValidated(false).userCountValidated(false)
                .revenueAnalysis("").usersAnalysis("")
                .commentRu("Ошибка оценки тракшена: " + errorMessage)
                .commentEn("Traction evaluation error: " + errorMessage)
                .commentUz("Traction baholash xatosi: " + errorMessage)
                .build();
    }

    /**
     * Fast heuristic evaluation without AI.
     */
    public TractionResult evaluateHeuristic(SubmissionData submission) {
        double revenue = parseDouble(submission.getRevenue());
        int users = parseInt(submission.getUserCount());
        String stage = nvl(submission.getStage()).toLowerCase();

        // C1: Revenue score
        int revenueScore;
        if (revenue > 20000) revenueScore = 12;
        else if (revenue >= 5000) revenueScore = 10;
        else if (revenue >= 1000) revenueScore = 7;
        else if (revenue > 0) revenueScore = 4;
        else revenueScore = 0;

        // Growth bonus
        if (revenue > 0 && (stage.contains("рост") || stage.contains("growth"))) {
            revenueScore = Math.min(revenueScore + 2, 12);
        }

        // C2: User score (only if no revenue)
        int usersScore = 0;
        if (revenue == 0) {
            if (users >= 10000) usersScore = 5;
            else if (users >= 2000) usersScore = 4;
            else if (users >= 500) usersScore = 3;
            else if (users >= 100) usersScore = 2;
            else if (users > 0) usersScore = 1;
        }

        // C3: Growth indicators
        int growthScore = 0;
        if (stage.contains("mvp") || stage.contains("мвп")) growthScore++;
        if (revenue > 0 || users > 100) growthScore++;
        if (submission.isHasInvestments()) growthScore++;
        growthScore = Math.min(growthScore, 3);

        int total = Math.min(revenueScore + usersScore + growthScore, 20);

        return TractionResult.builder()
                .revenueScore(revenueScore)
                .usersScore(usersScore)
                .growthScore(growthScore)
                .totalScore(total)
                .revenueValidated(revenue > 0)
                .userCountValidated(users > 0)
                .revenueAnalysis(String.format("Monthly revenue: $%,.0f", revenue))
                .usersAnalysis(String.format("User count: %,d", users))
                .commentRu(String.format("Выручка: $%,.0f/мес, Пользователи: %,d", revenue, users))
                .commentEn(String.format("Revenue: $%,.0f/mo, Users: %,d", revenue, users))
                .commentUz(String.format("Daromad: $%,.0f/oy, Foydalanuvchilar: %,d", revenue, users))
                .build();
    }
}
