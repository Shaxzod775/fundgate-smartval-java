package uz.fundgate.fundgate.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import uz.fundgate.fundgate.dto.AgentResult;
import uz.fundgate.fundgate.dto.SubmissionData;
import uz.fundgate.fundgate.service.BedrockService;

import java.util.Map;

/**
 * Base class for all FundGate scoring agents.
 *
 * All agents share:
 * - Expert VC analyst perspective
 * - Trilingual output requirement (ru/en/uz)
 * - Structured output via Bedrock tool use
 * - Consistent scoring methodology
 * - Both AI and heuristic evaluation paths
 */
@Slf4j
public abstract class BaseAgent {

    protected static final String SYSTEM_PREAMBLE = """
            You are an expert venture capital analyst evaluating early-stage startups for investment potential.

            ## Your Perspective
            - You represent a professional fund reviewing hundreds of applications
            - You apply consistent, objective criteria across all startups
            - You focus on evidence and data, not just claims
            - You provide constructive, actionable feedback

            ## Evaluation Standards
            - Be thorough but fair
            - Score based on provided evidence only
            - Don't assume information not given
            - Consider industry context and stage
            - Flag inconsistencies or red flags

            ## CRITICAL: Trilingual Output
            You MUST provide ALL comments and recommendations in THREE languages:

            1. **Russian (ru)**: Professional business Russian, formal tone
            2. **English (en)**: Professional investment English, clear and concise
            3. **Uzbek (uz)**: Latin script (not Cyrillic), professional business style

            Each language version should convey the same meaning but be naturally written for that language, not a literal translation.

            ## Response Format
            Always use the provided tool to return structured output. Do not include explanations outside the tool response.
            """;

    protected final String name;
    protected final String category;
    protected final int maxScore;
    protected final BedrockService bedrockService;

    protected BaseAgent(String name, String category, int maxScore, BedrockService bedrockService) {
        this.name = name;
        this.category = category;
        this.maxScore = maxScore;
        this.bedrockService = bedrockService;
        log.info("Initialized {} (Category {}, max {} pts)", name, category, maxScore);
    }

    /**
     * Get category-specific evaluation instructions.
     */
    protected abstract String getInstructions();

    /**
     * Prepare input data for the agent.
     */
    protected abstract String prepareInput(SubmissionData submission, Map<String, Object> extras);

    /**
     * Get the tool schema for structured output.
     */
    protected abstract Map<String, Object> getToolSchema();

    /**
     * Parse the tool response into a typed result.
     */
    protected abstract AgentResult parseToolResponse(JsonNode toolOutput);

    /**
     * Create error output when evaluation fails.
     */
    public abstract AgentResult createErrorOutput(String errorMessage);

    /**
     * Full system prompt combining preamble with category-specific instructions.
     */
    protected String getFullSystemPrompt() {
        return SYSTEM_PREAMBLE + "\n\n" + getInstructions();
    }

    /**
     * Run AI-based evaluation via Bedrock Claude.
     */
    public AgentResult analyze(SubmissionData submission) {
        return analyze(submission, Map.of());
    }

    /**
     * Run AI-based evaluation with extra context.
     */
    public AgentResult analyze(SubmissionData submission, Map<String, Object> extras) {
        String input = prepareInput(submission, extras);
        log.info("{}: Starting AI evaluation", name);
        log.debug("{}: Input length = {} chars", name, input.length());

        try {
            String toolName = name.toLowerCase().replace(" ", "_") + "_output";
            JsonNode result = bedrockService.callClaudeWithTool(
                    getFullSystemPrompt(),
                    input,
                    toolName,
                    "Return structured " + category + " category evaluation",
                    getToolSchema()
            );

            AgentResult output = parseToolResponse(result);
            log.info("{}: Evaluation complete, score = {}/{}", name, output.getTotalScore(), maxScore);
            return output;

        } catch (Exception e) {
            log.error("{}: Evaluation error - {}", name, e.getMessage(), e);
            return createErrorOutput(e.getMessage());
        }
    }

    /**
     * Call Claude with system and user prompts, returning raw text.
     */
    protected String callClaude(String systemPrompt, String userPrompt) {
        return bedrockService.callClaude(systemPrompt, userPrompt);
    }
}
