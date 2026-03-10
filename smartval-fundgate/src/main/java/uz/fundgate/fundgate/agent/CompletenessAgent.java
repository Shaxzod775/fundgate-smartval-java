package uz.fundgate.fundgate.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uz.fundgate.fundgate.dto.AgentResult;
import uz.fundgate.fundgate.dto.CompletenessResult;
import uz.fundgate.fundgate.dto.SubmissionData;
import uz.fundgate.fundgate.service.BedrockService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Agent A: Evaluates data completeness and validity.
 *
 * Max Score: 20 points
 * - Core Fields (12 pts): Presence of required fields
 * - Format Validation (4 pts): Email, phone, URL formats
 * - Consistency (4 pts): Logical data consistency
 */
@Slf4j
@Component
public class CompletenessAgent extends BaseAgent {

    private static final List<String> CORE_FIELDS = Arrays.asList(
            "name", "description", "industry", "stage",
            "teamSize", "website", "email", "phone"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?[0-9]{7,15}$");

    private static final Pattern URL_PATTERN = Pattern.compile(
            "^https?://[\\w\\-]+(\\.[\\w\\-]+)+[/#?]?.*$");

    public CompletenessAgent(BedrockService bedrockService) {
        super("CompletenessAgent", "A", 20, bedrockService);
    }

    @Override
    protected String getInstructions() {
        return """
                ## Category A: Data Completeness Evaluation (20 points max)

                You are evaluating the completeness and validity of startup submission data.

                ### A1: Core Fields (12 points max)
                Award points for each filled field (1.5 points each, max 12):
                | Field | Weight |
                |-------|--------|
                | name | 1.5 pts |
                | description | 1.5 pts |
                | industry | 1.5 pts |
                | stage | 1.5 pts |
                | teamSize | 1.5 pts |
                | website | 1.5 pts |
                | email | 1.5 pts |
                | phone | 1.5 pts |

                ### A2: Format Validation (4 points max)
                - Valid email format (user@domain.com): 1 point
                - Valid phone (international +XXX...): 1 point
                - Valid website URL: 1 point
                - Valid social media links: 1 point

                ### A3: Data Consistency (4 points max)
                Check for logical inconsistencies:
                - Revenue > 0 but stage = "Idea" -> Inconsistent
                - Users > 1000 but stage = "Idea" -> Inconsistent
                - hasUsers = true but userCount = 0 -> Inconsistent
                - Revenue = 0 but stage = "Revenue" -> Inconsistent

                Score:
                - 0 inconsistencies: 4 points
                - 1 inconsistency: 2 points
                - 2+ inconsistencies: 0 points

                ### Output Requirements
                1. Calculate each sub-score accurately
                2. List all missing required fields
                3. List all format issues found
                4. List all logical inconsistencies
                5. Total score = min(A1 + A2 + A3, 20)
                6. Provide comments in Russian, English, and Uzbek""";
    }

    @Override
    protected String prepareInput(SubmissionData submission, Map<String, Object> extras) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Startup Submission Data\n\n");

        appendField(sb, "name", submission.getName());
        appendField(sb, "description", submission.getDescription());
        appendField(sb, "industry", submission.getIndustry());
        appendField(sb, "stage", submission.getStage());
        appendField(sb, "teamSize", submission.getTeamSize());
        appendField(sb, "website", submission.getWebsite());
        appendField(sb, "email", submission.getEmail());
        appendField(sb, "phone", submission.getPhone());

        sb.append("\n## Additional Fields for Consistency Check\n\n");
        sb.append("- revenue: ").append(submission.getRevenue()).append("\n");
        sb.append("- userCount: ").append(submission.getUserCount()).append("\n");
        sb.append("- hasUsers: ").append(submission.isHasUsers()).append("\n");
        sb.append("- socialLinks: ").append(nvl(submission.getSocialLinks())).append("\n");

        sb.append("\n## Format Validation Results\n\n");
        sb.append("- Email '").append(nvl(submission.getEmail())).append("': ")
                .append(isValidEmail(submission.getEmail()) ? "Valid" : "Invalid").append("\n");
        sb.append("- Phone '").append(nvl(submission.getPhone())).append("': ")
                .append(isValidPhone(submission.getPhone()) ? "Valid" : "Invalid").append("\n");
        sb.append("- Website '").append(nvl(submission.getWebsite())).append("': ")
                .append(isValidUrl(submission.getWebsite()) ? "Valid" : "Invalid or missing").append("\n");
        sb.append("- Social Links '").append(nvl(submission.getSocialLinks())).append("': ")
                .append(isValidUrl(submission.getSocialLinks()) ? "Valid" : "Invalid or missing").append("\n");

        return sb.toString();
    }

    @Override
    protected Map<String, Object> getToolSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "core_fields_score", Map.of("type", "integer", "minimum", 0, "maximum", 12),
                        "format_score", Map.of("type", "integer", "minimum", 0, "maximum", 4),
                        "consistency_score", Map.of("type", "integer", "minimum", 0, "maximum", 4),
                        "total_score", Map.of("type", "integer", "minimum", 0, "maximum", 20),
                        "missing_fields", Map.of("type", "array", "items", Map.of("type", "string")),
                        "format_issues", Map.of("type", "array", "items", Map.of("type", "string")),
                        "consistency_issues", Map.of("type", "array", "items", Map.of("type", "string")),
                        "comment_ru", Map.of("type", "string"),
                        "comment_en", Map.of("type", "string"),
                        "comment_uz", Map.of("type", "string")
                ),
                "required", List.of("core_fields_score", "format_score", "consistency_score",
                        "total_score", "comment_ru", "comment_en", "comment_uz")
        );
    }

    @Override
    protected AgentResult parseToolResponse(JsonNode toolOutput) {
        return CompletenessResult.builder()
                .coreFieldsScore(toolOutput.path("core_fields_score").asInt(0))
                .formatScore(toolOutput.path("format_score").asInt(0))
                .consistencyScore(toolOutput.path("consistency_score").asInt(0))
                .totalScore(toolOutput.path("total_score").asInt(0))
                .missingFields(jsonArrayToList(toolOutput.path("missing_fields")))
                .formatIssues(jsonArrayToList(toolOutput.path("format_issues")))
                .consistencyIssues(jsonArrayToList(toolOutput.path("consistency_issues")))
                .commentRu(toolOutput.path("comment_ru").asText(""))
                .commentEn(toolOutput.path("comment_en").asText(""))
                .commentUz(toolOutput.path("comment_uz").asText(""))
                .build();
    }

    @Override
    public AgentResult createErrorOutput(String errorMessage) {
        return CompletenessResult.builder()
                .coreFieldsScore(0).formatScore(0).consistencyScore(0).totalScore(0)
                .missingFields(new ArrayList<>(CORE_FIELDS))
                .formatIssues(List.of("Evaluation failed"))
                .consistencyIssues(new ArrayList<>())
                .commentRu("Ошибка оценки: " + errorMessage)
                .commentEn("Evaluation error: " + errorMessage)
                .commentUz("Baholash xatosi: " + errorMessage)
                .build();
    }

    /**
     * Fast heuristic evaluation without AI.
     */
    public CompletenessResult evaluateHeuristic(SubmissionData submission) {
        // A1: Core fields
        int filledCount = 0;
        List<String> missing = new ArrayList<>();
        if (isNotEmpty(submission.getName())) filledCount++; else missing.add("name");
        if (isNotEmpty(submission.getDescription())) filledCount++; else missing.add("description");
        if (isNotEmpty(submission.getIndustry())) filledCount++; else missing.add("industry");
        if (isNotEmpty(submission.getStage())) filledCount++; else missing.add("stage");
        if (isNotEmpty(submission.getTeamSize())) filledCount++; else missing.add("teamSize");
        if (isNotEmpty(submission.getWebsite())) filledCount++; else missing.add("website");
        if (isNotEmpty(submission.getEmail())) filledCount++; else missing.add("email");
        if (isNotEmpty(submission.getPhone())) filledCount++; else missing.add("phone");

        int coreFieldsScore = Math.min((int) (filledCount * 1.5), 12);

        // A2: Format validation
        int formatScore = 0;
        List<String> formatIssues = new ArrayList<>();

        if (isValidEmail(submission.getEmail())) {
            formatScore++;
        } else {
            formatIssues.add("Invalid email format");
        }
        if (isValidPhone(submission.getPhone())) {
            formatScore++;
        } else {
            formatIssues.add("Invalid phone format");
        }
        if (isValidUrl(submission.getWebsite())) {
            formatScore++;
        } else {
            formatIssues.add("Missing or invalid website");
        }
        if (isValidUrl(submission.getSocialLinks())) {
            formatScore++;
        }

        // A3: Consistency check
        List<String> consistencyIssues = new ArrayList<>();
        String stage = nvl(submission.getStage()).toLowerCase();
        double revenue = parseDouble(submission.getRevenue());
        int users = parseInt(submission.getUserCount());

        if (revenue > 0 && (stage.contains("idea") || stage.contains("идея"))) {
            consistencyIssues.add("Revenue > 0 but stage is 'Idea'");
        }
        if (users > 1000 && (stage.contains("idea") || stage.contains("идея"))) {
            consistencyIssues.add("Users > 1000 but stage is 'Idea'");
        }
        if (submission.isHasUsers() && users == 0) {
            consistencyIssues.add("hasUsers = true but userCount = 0");
        }
        if (revenue == 0 && (stage.contains("revenue") || stage.contains("выручка"))) {
            consistencyIssues.add("Revenue = 0 but stage claims revenue");
        }

        int consistencyScore;
        if (consistencyIssues.isEmpty()) {
            consistencyScore = 4;
        } else if (consistencyIssues.size() == 1) {
            consistencyScore = 2;
        } else {
            consistencyScore = 0;
        }

        int total = Math.min(coreFieldsScore + formatScore + consistencyScore, 20);

        return CompletenessResult.builder()
                .coreFieldsScore(coreFieldsScore)
                .formatScore(formatScore)
                .consistencyScore(consistencyScore)
                .totalScore(total)
                .missingFields(missing)
                .formatIssues(formatIssues)
                .consistencyIssues(consistencyIssues)
                .commentRu(String.format("Заполнено %d/8 обязательных полей. Найдено %d несоответствий.",
                        filledCount, consistencyIssues.size()))
                .commentEn(String.format("Filled %d/8 required fields. Found %d inconsistencies.",
                        filledCount, consistencyIssues.size()))
                .commentUz(String.format("%d/8 majburiy maydonlar to'ldirilgan. %d ta nomuvofiqlik topildi.",
                        filledCount, consistencyIssues.size()))
                .build();
    }

    // --- Utility methods ---

    private void appendField(StringBuilder sb, String fieldName, String value) {
        String status = isNotEmpty(value) ? "Provided" : "Missing";
        String display = isNotEmpty(value) ? value : "[empty]";
        sb.append("- **").append(fieldName).append("**: ").append(display)
                .append(" (").append(status).append(")\n");
    }

    static boolean isValidEmail(String email) {
        return email != null && !email.isBlank() && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    static boolean isValidPhone(String phone) {
        return phone != null && !phone.isBlank()
                && PHONE_PATTERN.matcher(phone.trim().replaceAll("[\\s()-]", "")).matches();
    }

    static boolean isValidUrl(String url) {
        return url != null && !url.isBlank() && URL_PATTERN.matcher(url.trim()).matches();
    }

    static boolean isNotEmpty(String value) {
        return value != null && !value.isBlank();
    }

    static String nvl(String value) {
        return value != null ? value : "";
    }

    static double parseDouble(String value) {
        try {
            return value != null && !value.isBlank() ? Double.parseDouble(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static int parseInt(String value) {
        try {
            return value != null && !value.isBlank() ? (int) Double.parseDouble(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static List<String> jsonArrayToList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                list.add(item.asText());
            }
        }
        return list;
    }
}
