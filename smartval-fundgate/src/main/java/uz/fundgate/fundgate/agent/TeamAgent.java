package uz.fundgate.fundgate.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uz.fundgate.fundgate.dto.AgentResult;
import uz.fundgate.fundgate.dto.SubmissionData;
import uz.fundgate.fundgate.dto.TeamMember;
import uz.fundgate.fundgate.dto.TeamResult;
import uz.fundgate.fundgate.service.BedrockService;

import java.util.*;

import static uz.fundgate.fundgate.agent.CompletenessAgent.*;

/**
 * Agent D: Evaluates team composition and capability.
 *
 * Max Score: 15 points
 * - Coverage (6 pts): Key roles filled
 * - Experience (5 pts): Team experience level
 * - Commitment (2 pts): Full-time dedication
 * - Advisors (2 pts): Advisory board/mentors
 */
@Slf4j
@Component
public class TeamAgent extends BaseAgent {

    public TeamAgent(BedrockService bedrockService) {
        super("TeamAgent", "D", 15, bedrockService);
    }

    @Override
    protected String getInstructions() {
        return """
                ## Category D: Team Analysis (15 points max)

                You are evaluating the team composition and capability of a startup.

                ### D1: Role Coverage (0-6 points)
                Essential roles for an early-stage startup:
                - CEO/Founder: Leadership and vision
                - CTO/Technical Lead: Product development
                - Business/Sales Lead: Go-to-market

                Scoring based on team size and role coverage:
                - Solo founder: 1.5 pts
                - 2 people: 3 pts
                - 3 people: 4.5 pts
                - 4+ people: 6 pts (max)

                ### D2: Experience Score (0-5 points)
                Evaluate based on:
                - Industry relevance: Do they have domain expertise?
                - Technical depth: For tech startups, technical capability matters
                - Startup experience: Have they built companies before?
                - Track record: Any notable achievements?

                ### D3: Commitment Score (0-2 points)
                - Full-time team (2+ people): 2 pts
                - Solo founder full-time: 1.5 pts
                - Part-time team: 1 pt
                - Unclear commitment: 0.5 pts

                ### D4: Advisors Score (0-2 points)
                - Has named advisors/mentors: 2 pts
                - Has social proof (LinkedIn, etc.): 1 pt
                - No advisor information: 0 pts

                ### Red Flags
                - Very small team for complex product
                - No technical cofounder for tech startup
                - Vague role descriptions
                - Missing founder information

                ### Output
                Provide assessment of team strengths and gaps with recommendations for strengthening the team.""";
    }

    @Override
    protected String prepareInput(SubmissionData submission, Map<String, Object> extras) {
        StringBuilder sb = new StringBuilder("## Team Information\n\n");
        sb.append("- **Team Size**: ").append(nvl(submission.getTeamSize())).append(" people\n");

        String firstName = nvl(submission.getFirstName());
        String lastName = nvl(submission.getLastName());
        String role = nvl(submission.getRole());
        if (!firstName.isEmpty() || !lastName.isEmpty()) {
            sb.append("- **Founder**: ").append(firstName).append(" ").append(lastName).append("\n");
            if (!role.isEmpty()) {
                sb.append("- **Founder Role**: ").append(role).append("\n");
            }
        }

        String social = nvl(submission.getSocialLinks());
        sb.append("- **Social/LinkedIn**: ").append(social.isEmpty() ? "Not provided" : social).append("\n");

        // Additional team members
        List<TeamMember> members = submission.getTeamMembers();
        if (members != null && !members.isEmpty()) {
            sb.append("\n## Additional Team Members\n\n");
            for (int i = 0; i < members.size(); i++) {
                TeamMember m = members.get(i);
                sb.append(String.format("- **Member %d**: %s %s - %s",
                        i + 1, nvl(m.getFirstName()), nvl(m.getLastName()), nvl(m.getRole())));
                if (m.getLinkedin() != null && !m.getLinkedin().isBlank()) {
                    sb.append(" (LinkedIn: ").append(m.getLinkedin()).append(")");
                }
                sb.append("\n");
            }
        }

        sb.append("\n## Business Context\n\n");
        sb.append("- **Industry**: ").append(nvl(submission.getIndustry())).append("\n");
        sb.append("- **Stage**: ").append(nvl(submission.getStage())).append("\n");
        sb.append("- **Has Technology/IP**: ").append(submission.isHasTechnology()).append("\n");

        String techDesc = nvl(submission.getTechnologyDescription());
        if (!techDesc.isEmpty()) {
            sb.append("- **Technology**: ").append(techDesc.length() > 200 ? techDesc.substring(0, 200) : techDesc).append("\n");
        }

        String desc = nvl(submission.getDescription());
        if (!desc.isEmpty()) {
            sb.append("\n## Startup Description\n").append(desc.length() > 400 ? desc.substring(0, 400) : desc).append("\n");
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> getToolSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                        Map.entry("coverage_score", Map.of("type", "integer", "minimum", 0, "maximum", 6)),
                        Map.entry("experience_score", Map.of("type", "integer", "minimum", 0, "maximum", 5)),
                        Map.entry("commitment_score", Map.of("type", "integer", "minimum", 0, "maximum", 2)),
                        Map.entry("advisors_score", Map.of("type", "integer", "minimum", 0, "maximum", 2)),
                        Map.entry("total_score", Map.of("type", "integer", "minimum", 0, "maximum", 15)),
                        Map.entry("team_size", Map.of("type", "integer")),
                        Map.entry("key_roles_present", Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry("missing_roles", Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry("has_technical_cofounder", Map.of("type", "boolean")),
                        Map.entry("has_advisors", Map.of("type", "boolean")),
                        Map.entry("comment_ru", Map.of("type", "string")),
                        Map.entry("comment_en", Map.of("type", "string")),
                        Map.entry("comment_uz", Map.of("type", "string"))
                ),
                "required", List.of("coverage_score", "experience_score", "commitment_score",
                        "advisors_score", "total_score", "comment_ru", "comment_en", "comment_uz")
        );
    }

    @Override
    protected AgentResult parseToolResponse(JsonNode toolOutput) {
        return TeamResult.builder()
                .coverageScore(toolOutput.path("coverage_score").asInt(0))
                .experienceScore(toolOutput.path("experience_score").asInt(0))
                .commitmentScore(toolOutput.path("commitment_score").asInt(0))
                .advisorsScore(toolOutput.path("advisors_score").asInt(0))
                .totalScore(toolOutput.path("total_score").asInt(0))
                .teamSize(toolOutput.path("team_size").asInt(0))
                .keyRolesPresent(jsonArrayToList(toolOutput.path("key_roles_present")))
                .missingRoles(jsonArrayToList(toolOutput.path("missing_roles")))
                .hasTechnicalCofounder(toolOutput.path("has_technical_cofounder").asBoolean(false))
                .hasAdvisors(toolOutput.path("has_advisors").asBoolean(false))
                .commentRu(toolOutput.path("comment_ru").asText(""))
                .commentEn(toolOutput.path("comment_en").asText(""))
                .commentUz(toolOutput.path("comment_uz").asText(""))
                .build();
    }

    @Override
    public AgentResult createErrorOutput(String errorMessage) {
        return TeamResult.builder()
                .coverageScore(0).experienceScore(0).commitmentScore(0).advisorsScore(0).totalScore(0)
                .teamSize(0)
                .keyRolesPresent(new ArrayList<>())
                .missingRoles(List.of("CEO", "CTO", "Business Lead"))
                .hasTechnicalCofounder(false).hasAdvisors(false)
                .commentRu("Ошибка оценки команды: " + errorMessage)
                .commentEn("Team evaluation error: " + errorMessage)
                .commentUz("Jamoa baholash xatosi: " + errorMessage)
                .build();
    }

    /**
     * Fast heuristic evaluation without AI.
     */
    public TeamResult evaluateHeuristic(SubmissionData submission) {
        int baseTeamSize = parseInt(submission.getTeamSize());
        if (baseTeamSize < 1) baseTeamSize = 1;

        List<TeamMember> members = submission.getTeamMembers();
        int additionalCount = (members != null) ? members.size() : 0;
        int teamSize = Math.max(baseTeamSize, 1 + additionalCount);

        // D1: Coverage score
        int coverageScore = Math.min((int) (teamSize * 1.5), 6);

        // Analyze roles
        Set<String> roleCategories = new HashSet<>();
        String founderRole = nvl(submission.getRole()).toLowerCase();
        classifyRole(founderRole, roleCategories);
        if (members != null) {
            for (TeamMember m : members) {
                classifyRole(nvl(m.getRole()).toLowerCase(), roleCategories);
            }
        }

        // Diversity bonus
        int diversityBonus = roleCategories.size() > 1 ? Math.min(roleCategories.size() - 1, 2) : 0;
        coverageScore = Math.min(coverageScore + diversityBonus, 6);

        // D2: Experience score
        int experienceScore = 2;
        String industry = nvl(submission.getIndustry()).toLowerCase();
        String stage = nvl(submission.getStage()).toLowerCase();
        if (industry.contains("fintech") || industry.contains("ai") || industry.contains("ml")) {
            experienceScore = 3;
        }
        if (stage.contains("revenue") || stage.contains("выручка") || stage.contains("growth") || stage.contains("рост")) {
            experienceScore = Math.min(experienceScore + 1, 5);
        }

        // D3: Commitment score
        int commitmentScore = teamSize >= 2 ? 2 : 1;

        // D4: Advisors score (LinkedIn presence)
        int advisorsScore = 0;
        int linkedinCount = 0;
        String founderLinkedin = nvl(submission.getSocialLinks());
        if (founderLinkedin.toLowerCase().contains("linkedin.com")) linkedinCount++;
        if (members != null) {
            for (TeamMember m : members) {
                if (m.getLinkedin() != null && m.getLinkedin().toLowerCase().contains("linkedin.com")) {
                    linkedinCount++;
                }
            }
        }
        if (linkedinCount >= 3) advisorsScore = 2;
        else if (linkedinCount >= 1) advisorsScore = 1;

        int total = Math.min(coverageScore + experienceScore + commitmentScore + advisorsScore, 15);

        // Determine roles present
        boolean hasCto = roleCategories.contains("technical") || submission.isHasTechnology();
        List<String> rolesPresent = new ArrayList<>();
        rolesPresent.add("CEO/Founder");
        if (hasCto) rolesPresent.add("CTO/Technical");
        if (roleCategories.contains("business") || teamSize >= 3) rolesPresent.add("Business Lead");
        if (roleCategories.contains("marketing")) rolesPresent.add("Marketing/Sales");
        if (roleCategories.contains("product")) rolesPresent.add("Product/Design");

        List<String> missingRoles = new ArrayList<>();
        if (!hasCto && (industry.contains("tech") || industry.contains("ai") || industry.contains("software"))) {
            missingRoles.add("CTO/Technical Lead");
        }

        String structure = teamSize >= 2 ? "sufficient" : "needs expansion";
        String structureRu = teamSize >= 2 ? "достаточна" : "требует расширения";
        String structureUz = teamSize >= 2 ? "yetarli" : "kengaytirishni talab qiladi";

        return TeamResult.builder()
                .coverageScore(coverageScore)
                .experienceScore(experienceScore)
                .commitmentScore(commitmentScore)
                .advisorsScore(advisorsScore)
                .totalScore(total)
                .teamSize(teamSize)
                .keyRolesPresent(rolesPresent)
                .missingRoles(missingRoles)
                .hasTechnicalCofounder(hasCto)
                .hasAdvisors(linkedinCount >= 1)
                .commentRu(String.format("Команда из %d человек. Структура %s.", teamSize, structureRu))
                .commentEn(String.format("Team of %d people. Structure %s.", teamSize, structure))
                .commentUz(String.format("%d kishilik jamoa. Tuzilma %s.", teamSize, structureUz))
                .build();
    }

    private void classifyRole(String role, Set<String> categories) {
        if (role.isEmpty()) return;
        if (containsAny(role, "cto", "tech", "engineer", "developer")) categories.add("technical");
        if (containsAny(role, "ceo", "coo", "business", "operations")) categories.add("business");
        if (containsAny(role, "cmo", "marketing", "sales", "growth")) categories.add("marketing");
        if (containsAny(role, "cfo", "finance", "financial")) categories.add("finance");
        if (containsAny(role, "cpo", "product", "design")) categories.add("product");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
