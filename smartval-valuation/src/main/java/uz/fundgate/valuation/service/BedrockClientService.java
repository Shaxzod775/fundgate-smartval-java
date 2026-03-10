package uz.fundgate.valuation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.Map;

/**
 * AWS Bedrock client wrapper for Claude model calls in the valuation module.
 * Mirrors the Python BedrockClient with retry, fallback, and tool use support.
 *
 * Provides:
 * - callClaude() for raw text responses
 * - callClaudeWithTool() for structured output via Claude Tool Use
 * - Automatic fallback to alternative model on errors
 * - Retry with exponential backoff
 */
@Slf4j
@Service
public class BedrockClientService {

    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.bedrock.model-id:us.anthropic.claude-haiku-4-5-20251001-v1:0}")
    private String modelId;

    @Value("${aws.bedrock.fallback-model-id:us.anthropic.claude-3-5-haiku-20241022-v1:0}")
    private String fallbackModelId;

    @Value("${aws.bedrock.max-tokens:4096}")
    private int maxTokens;

    @Value("${aws.bedrock.temperature:0.7}")
    private double temperature;

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;
    private static final double RETRY_BACKOFF_FACTOR = 2.0;

    public BedrockClientService(BedrockRuntimeClient bedrockClient) {
        this.bedrockClient = bedrockClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Call Claude with a system prompt and user message, returning raw text.
     */
    public String callClaude(String systemPrompt, String userMessage) {
        return callClaude(systemPrompt, userMessage, modelId, maxTokens, temperature);
    }

    /**
     * Call Claude with full parameters.
     */
    public String callClaude(String systemPrompt, String userMessage,
                             String model, int tokens, double temp) {
        try {
            ObjectNode requestBody = buildBaseRequest(systemPrompt, userMessage, tokens, temp);
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            JsonNode responseJson = invokeWithRetryAndFallback(model, jsonBody, "text-call");

            JsonNode contentArray = responseJson.get("content");
            if (contentArray != null && contentArray.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode block : contentArray) {
                    if ("text".equals(block.path("type").asText())) {
                        sb.append(block.path("text").asText());
                    }
                }
                return sb.toString();
            }
            return "";
        } catch (Exception e) {
            log.error("Error calling Claude: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Claude model: " + e.getMessage(), e);
        }
    }

    /**
     * Call Claude with tool use (structured output).
     * Forces Claude to respond using the specified tool, returning the tool input as JsonNode.
     * This is the primary method for getting structured valuation results from Claude.
     */
    public JsonNode callClaudeWithTool(String systemPrompt, String userMessage,
                                       String toolName, String toolDescription,
                                       Map<String, Object> toolSchema) {
        return callClaudeWithTool(systemPrompt, userMessage, toolName, toolDescription,
                toolSchema, modelId, maxTokens, temperature);
    }

    /**
     * Call Claude with tool use and full parameters.
     */
    public JsonNode callClaudeWithTool(String systemPrompt, String userMessage,
                                       String toolName, String toolDescription,
                                       Map<String, Object> toolSchema,
                                       String model, int tokens, double temp) {
        try {
            ObjectNode requestBody = buildBaseRequest(systemPrompt, userMessage, tokens, temp);

            // Tools
            ArrayNode tools = requestBody.putArray("tools");
            ObjectNode tool = tools.addObject();
            tool.put("name", toolName);
            tool.put("description", toolDescription);
            tool.set("input_schema", objectMapper.valueToTree(toolSchema));

            // Force tool use
            ObjectNode toolChoice = requestBody.putObject("tool_choice");
            toolChoice.put("type", "tool");
            toolChoice.put("name", toolName);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            JsonNode responseJson = invokeWithRetryAndFallback(model, jsonBody, toolName);

            // Extract tool use input
            JsonNode contentArray = responseJson.get("content");
            if (contentArray != null && contentArray.isArray()) {
                for (JsonNode block : contentArray) {
                    if ("tool_use".equals(block.path("type").asText())
                            && toolName.equals(block.path("name").asText())) {
                        return block.get("input");
                    }
                }
            }

            log.warn("No tool_use block found in Claude response for tool: {}", toolName);
            return objectMapper.createObjectNode();

        } catch (Exception e) {
            log.error("Error calling Claude with tool {}: {}", toolName, e.getMessage(), e);
            throw new RuntimeException("Failed to call Claude with tool: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private ObjectNode buildBaseRequest(String systemPrompt, String userMessage,
                                        int tokens, double temp) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("anthropic_version", "bedrock-2023-05-31");
        requestBody.put("max_tokens", tokens);
        requestBody.put("temperature", temp);
        requestBody.put("system", systemPrompt);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        ArrayNode content = userMsg.putArray("content");
        ObjectNode textBlock = content.addObject();
        textBlock.put("type", "text");
        textBlock.put("text", userMessage);

        return requestBody;
    }

    /**
     * Invokes the model with retry and automatic fallback to the alternative model.
     * Mirrors the Python _invoke_with_retry pattern.
     */
    private JsonNode invokeWithRetryAndFallback(String primaryModel, String jsonBody, String agentName) {
        try {
            return invokeWithRetry(primaryModel, jsonBody, agentName);
        } catch (Exception primaryError) {
            if (!primaryModel.equals(fallbackModelId)) {
                log.warn("Primary model {} failed for agent {}, trying fallback: {}",
                        primaryModel, agentName, fallbackModelId);
                try {
                    return invokeWithRetry(fallbackModelId, jsonBody, agentName);
                } catch (Exception fallbackError) {
                    log.error("Fallback model {} also failed for agent {}: {}",
                            fallbackModelId, agentName, fallbackError.getMessage());
                    throw new RuntimeException("Both primary and fallback models failed", fallbackError);
                }
            }
            throw primaryError;
        }
    }

    /**
     * Invokes the model with exponential backoff retry.
     */
    private JsonNode invokeWithRetry(String model, String jsonBody, String agentName) {
        Exception lastError = null;
        long delay = INITIAL_RETRY_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.debug("Invoking model {} for agent {}, attempt {}", model, agentName, attempt);

                InvokeModelRequest request = InvokeModelRequest.builder()
                        .modelId(model)
                        .contentType("application/json")
                        .accept("application/json")
                        .body(SdkBytes.fromUtf8String(jsonBody))
                        .build();

                InvokeModelResponse response = bedrockClient.invokeModel(request);
                String responseBody = response.body().asUtf8String();
                JsonNode responseJson = objectMapper.readTree(responseBody);

                log.debug("Model {} invocation successful for agent {}, stop_reason: {}",
                        model, agentName, responseJson.path("stop_reason").asText());

                // Log token usage
                JsonNode usage = responseJson.get("usage");
                if (usage != null) {
                    log.info("Agent {} tokens - input: {}, output: {}",
                            agentName, usage.path("input_tokens").asInt(),
                            usage.path("output_tokens").asInt());
                }

                return responseJson;

            } catch (Exception e) {
                log.warn("Attempt {} failed for model {} agent {}: {}",
                        attempt, model, agentName, e.getMessage());
                lastError = e;

                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                    delay = (long) (delay * RETRY_BACKOFF_FACTOR);
                }
            }
        }

        throw new RuntimeException("All " + MAX_RETRIES + " retry attempts failed for agent " + agentName, lastError);
    }

    public String getModelId() {
        return modelId;
    }
}
