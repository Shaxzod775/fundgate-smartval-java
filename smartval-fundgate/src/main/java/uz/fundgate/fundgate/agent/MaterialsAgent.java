package uz.fundgate.fundgate.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uz.fundgate.fundgate.dto.AgentResult;
import uz.fundgate.fundgate.dto.FileUrls;
import uz.fundgate.fundgate.dto.MaterialsResult;
import uz.fundgate.fundgate.dto.SubmissionData;
import uz.fundgate.fundgate.service.BedrockService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static uz.fundgate.fundgate.agent.CompletenessAgent.*;

/**
 * Agent F: Evaluates materials quality.
 *
 * Max Score: 10 points
 * - Description (3 pts): Quality of description
 * - Links (3 pts): Working links (with validation)
 * - Documents (4 pts): Additional materials
 */
@Slf4j
@Component
public class MaterialsAgent extends BaseAgent {

    public MaterialsAgent(BedrockService bedrockService) {
        super("MaterialsAgent", "F", 10, bedrockService);
    }

    @Override
    protected String getInstructions() {
        return """
                ## Category F: Materials Quality Analysis (10 points max)

                You are evaluating the quality and completeness of submission materials.

                ### F1: Description Quality (0-3 points)
                Evaluate the startup description:
                - 3 pts: Clear, detailed (300+ chars), explains problem/solution well
                - 2 pts: Adequate (100-300 chars), covers basics
                - 1 pt: Brief (30-100 chars), minimal information
                - 0 pts: Too short (<30 chars) or unclear

                Quality indicators:
                - Clear problem statement
                - Understandable solution
                - Value proposition evident
                - Professional language

                ### F2: Links Quality (0-3 points)
                - Working website: 1 pt
                - Video demo link: 1 pt
                - Social media/LinkedIn: 1 pt

                Deduct for broken/invalid links.

                ### F3: Documents (0-4 points)
                - Pitch deck uploaded: 2 pts (essential document)
                - One-pager uploaded: 1 pt
                - Financial model uploaded: 1 pt

                ### Quality Assessment
                Consider:
                1. Professionalism of materials
                2. Consistency across documents
                3. Completeness of information
                4. Presentation quality

                ### Red Flags
                - Broken links
                - Generic/template descriptions
                - Missing essential documents
                - Inconsistent information across materials

                ### Output
                Provide assessment of materials quality with specific recommendations for improvement.""";
    }

    @Override
    protected String prepareInput(SubmissionData submission, Map<String, Object> extras) {
        StringBuilder sb = new StringBuilder("## Submission Materials\n\n");

        // Description
        String desc = nvl(submission.getDescription());
        sb.append("### Description (").append(desc.length()).append(" characters)\n");
        sb.append(desc.isEmpty() ? "[No description]" : (desc.length() > 500 ? desc.substring(0, 500) : desc)).append("\n");

        // Links
        sb.append("\n### Links\n");
        String website = nvl(submission.getWebsite());
        String video = nvl(submission.getVideoDemo());
        String social = nvl(submission.getSocialLinks());
        sb.append("- Website: ").append(website.isEmpty() ? "[Not provided]" : website)
                .append(" - ").append(isValidUrl(website) ? "Valid" : "Invalid/Missing").append("\n");
        sb.append("- Video Demo: ").append(video.isEmpty() ? "[Not provided]" : video)
                .append(" - ").append(isValidUrl(video) ? "Valid" : "Invalid/Missing").append("\n");
        sb.append("- Social Links: ").append(social.isEmpty() ? "[Not provided]" : social)
                .append(" - ").append(isValidUrl(social) ? "Valid" : "Invalid/Missing").append("\n");

        // Documents
        sb.append("\n### Documents\n");
        @SuppressWarnings("unchecked")
        FileUrls fileUrls = extras.get("fileUrls") instanceof FileUrls ? (FileUrls) extras.get("fileUrls") : new FileUrls();
        sb.append("- Pitch Deck: ").append(isNotEmpty(fileUrls.getPitchDeck()) ? "Uploaded" : "Not uploaded").append("\n");
        sb.append("- One-Pager: ").append(isNotEmpty(fileUrls.getOnePager()) ? "Uploaded" : "Not uploaded").append("\n");
        sb.append("- Financial Model: ").append(isNotEmpty(fileUrls.getFinancialModel()) ? "Uploaded" : "Not uploaded").append("\n");

        return sb.toString();
    }

    @Override
    protected Map<String, Object> getToolSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                        Map.entry("description_score", Map.of("type", "integer", "minimum", 0, "maximum", 3)),
                        Map.entry("links_score", Map.of("type", "integer", "minimum", 0, "maximum", 3)),
                        Map.entry("documents_score", Map.of("type", "integer", "minimum", 0, "maximum", 4)),
                        Map.entry("total_score", Map.of("type", "integer", "minimum", 0, "maximum", 10)),
                        Map.entry("broken_links", Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry("description_issues", Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry("comment_ru", Map.of("type", "string")),
                        Map.entry("comment_en", Map.of("type", "string")),
                        Map.entry("comment_uz", Map.of("type", "string"))
                ),
                "required", List.of("description_score", "links_score", "documents_score",
                        "total_score", "comment_ru", "comment_en", "comment_uz")
        );
    }

    @Override
    protected AgentResult parseToolResponse(JsonNode toolOutput) {
        return MaterialsResult.builder()
                .descriptionScore(toolOutput.path("description_score").asInt(0))
                .linksScore(toolOutput.path("links_score").asInt(0))
                .documentsScore(toolOutput.path("documents_score").asInt(0))
                .totalScore(toolOutput.path("total_score").asInt(0))
                .brokenLinks(jsonArrayToList(toolOutput.path("broken_links")))
                .descriptionIssues(jsonArrayToList(toolOutput.path("description_issues")))
                .commentRu(toolOutput.path("comment_ru").asText(""))
                .commentEn(toolOutput.path("comment_en").asText(""))
                .commentUz(toolOutput.path("comment_uz").asText(""))
                .build();
    }

    @Override
    public AgentResult createErrorOutput(String errorMessage) {
        return MaterialsResult.builder()
                .descriptionScore(0).linksScore(0).documentsScore(0).totalScore(0)
                .hasPitchDeck(false).hasOnePager(false).hasFinancialModel(false).hasVideoDemo(false)
                .brokenLinks(new ArrayList<>())
                .descriptionIssues(List.of("Evaluation failed"))
                .commentRu("Ошибка оценки материалов: " + errorMessage)
                .commentEn("Materials evaluation error: " + errorMessage)
                .commentUz("Materiallar baholash xatosi: " + errorMessage)
                .build();
    }

    /**
     * Fast heuristic evaluation without AI.
     */
    public MaterialsResult evaluateHeuristic(SubmissionData submission, FileUrls fileUrls) {
        if (fileUrls == null) fileUrls = new FileUrls();

        // F1: Description score
        String desc = nvl(submission.getDescription());
        int descLen = desc.length();
        int descriptionScore;
        if (descLen >= 300) descriptionScore = 3;
        else if (descLen >= 100) descriptionScore = 2;
        else if (descLen >= 30) descriptionScore = 1;
        else descriptionScore = 0;

        List<String> descriptionIssues = new ArrayList<>();
        if (descLen < 100) descriptionIssues.add("Description too short");

        // F2: Links score
        int linksScore = 0;
        List<String> brokenLinks = new ArrayList<>();
        String website = nvl(submission.getWebsite());
        String video = nvl(submission.getVideoDemo());
        String social = nvl(submission.getSocialLinks());

        if (isValidUrl(website)) linksScore++;
        else if (!website.isEmpty()) brokenLinks.add("Website: " + website);

        if (isValidUrl(video)) linksScore++;
        else if (!video.isEmpty()) brokenLinks.add("Video: " + video);

        if (isValidUrl(social)) linksScore++;
        else if (!social.isEmpty()) brokenLinks.add("Social: " + social);

        // F3: Documents score
        int documentsScore = 0;
        boolean hasPitch = isNotEmpty(fileUrls.getPitchDeck());
        boolean hasOnePager = isNotEmpty(fileUrls.getOnePager());
        boolean hasFinancial = isNotEmpty(fileUrls.getFinancialModel());

        if (hasPitch) documentsScore += 2;
        if (hasOnePager) documentsScore += 1;
        if (hasFinancial) documentsScore += 1;

        int total = Math.min(descriptionScore + linksScore + documentsScore, 10);
        int docCount = (hasPitch ? 1 : 0) + (hasOnePager ? 1 : 0) + (hasFinancial ? 1 : 0);

        return MaterialsResult.builder()
                .descriptionScore(descriptionScore)
                .linksScore(linksScore)
                .documentsScore(documentsScore)
                .totalScore(total)
                .hasPitchDeck(hasPitch)
                .hasOnePager(hasOnePager)
                .hasFinancialModel(hasFinancial)
                .hasVideoDemo(!video.isEmpty() && isValidUrl(video))
                .brokenLinks(brokenLinks)
                .descriptionIssues(descriptionIssues)
                .commentRu(String.format("Описание: %d символов. Документы: %d/3.", descLen, docCount))
                .commentEn(String.format("Description: %d chars. Documents: %d/3.", descLen, docCount))
                .commentUz(String.format("Tavsif: %d belgi. Hujjatlar: %d/3.", descLen, docCount))
                .build();
    }
}
