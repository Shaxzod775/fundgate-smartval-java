package uz.fundgate.fundgate.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * AWS Bedrock client wrapper for Claude model calls.
 * Handles message construction, tool use, and response parsing using the
 * Claude Messages API via AWS Bedrock.
 */
@Slf4j
@Service
public class BedrockService {

    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.bedrock.model-id:us.anthropic.claude-3-5-haiku-20241022-v1:0}")
    private String modelId;

    @Value("${aws.bedrock.max-tokens:8192}")
    private int maxTokens;

    public BedrockService(BedrockRuntimeClient bedrockClient) {
        this.bedrockClient = bedrockClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Call Claude with a system prompt and user message, returning raw text.
     *
     * @param systemPrompt the system instructions
     * @param userMessage  the user prompt content
     * @return Claude's text response
     */
    public String callClaude(String systemPrompt, String userMessage) {
        return callClaude(systemPrompt, userMessage, modelId, maxTokens, 0.3);
    }

    /**
     * Call Claude with full parameters.
     */
    public String callClaude(String systemPrompt, String userMessage,
                             String model, int tokens, double temperature) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("anthropic_version", "bedrock-2023-05-31");
            requestBody.put("max_tokens", tokens);
            requestBody.put("temperature", temperature);
            requestBody.put("system", systemPrompt);

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            ArrayNode content = userMsg.putArray("content");
            ObjectNode textBlock = content.addObject();
            textBlock.put("type", "text");
            textBlock.put("text", userMessage);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(model)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(jsonBody))
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);
            String responseBody = response.body().asUtf8String();
            JsonNode responseJson = objectMapper.readTree(responseBody);

            // Extract text from response
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
     * Forces Claude to respond using the specified tool, returning the tool input as JSON.
     *
     * @param systemPrompt   system instructions
     * @param userMessage    user prompt content
     * @param toolName       name of the tool to force
     * @param toolDescription tool description
     * @param toolSchema     JSON schema for tool input (as Map)
     * @return parsed tool input as JsonNode
     */
    public JsonNode callClaudeWithTool(String systemPrompt, String userMessage,
                                       String toolName, String toolDescription,
                                       Map<String, Object> toolSchema) {
        return callClaudeWithTool(systemPrompt, userMessage, toolName, toolDescription,
                toolSchema, modelId, maxTokens, 0.3);
    }

    /**
     * Call Claude with tool use and full parameters.
     */
    public JsonNode callClaudeWithTool(String systemPrompt, String userMessage,
                                       String toolName, String toolDescription,
                                       Map<String, Object> toolSchema,
                                       String model, int tokens, double temperature) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("anthropic_version", "bedrock-2023-05-31");
            requestBody.put("max_tokens", tokens);
            requestBody.put("temperature", temperature);
            requestBody.put("system", systemPrompt);

            // Messages
            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            ArrayNode content = userMsg.putArray("content");
            ObjectNode textBlock = content.addObject();
            textBlock.put("type", "text");
            textBlock.put("text", userMessage);

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

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(model)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(jsonBody))
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);
            String responseBody = response.body().asUtf8String();
            JsonNode responseJson = objectMapper.readTree(responseBody);

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

    /**
     * Call Claude Vision with images and tool use for pitch deck analysis.
     *
     * @param systemPrompt   system instructions
     * @param images         list of JPEG image bytes (one per slide)
     * @param textPrompt     the analysis prompt text
     * @param toolName       tool name for structured output
     * @param toolDescription tool description
     * @param toolSchema     JSON schema for tool input
     * @param model          model ID (use Sonnet for vision)
     * @return parsed tool input as JsonNode
     */
    public JsonNode callClaudeVision(String systemPrompt, List<byte[]> images,
                                     String textPrompt, String toolName,
                                     String toolDescription, Map<String, Object> toolSchema,
                                     String model) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("anthropic_version", "bedrock-2023-05-31");
            requestBody.put("max_tokens", 4096);
            requestBody.put("temperature", 0.3);
            requestBody.put("system", systemPrompt);

            // Messages with images
            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            ArrayNode content = userMsg.putArray("content");

            // Add images
            for (int i = 0; i < images.size(); i++) {
                ObjectNode imageBlock = content.addObject();
                imageBlock.put("type", "image");
                ObjectNode source = imageBlock.putObject("source");
                source.put("type", "base64");
                source.put("media_type", "image/jpeg");
                source.put("data", Base64.getEncoder().encodeToString(images.get(i)));

                ObjectNode slideLabel = content.addObject();
                slideLabel.put("type", "text");
                slideLabel.put("text", String.format("[Slide %d of %d]", i + 1, images.size()));
            }

            // Add analysis prompt
            ObjectNode promptBlock = content.addObject();
            promptBlock.put("type", "text");
            promptBlock.put("text", textPrompt);

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

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(model)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(jsonBody))
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);
            String responseBody = response.body().asUtf8String();
            JsonNode responseJson = objectMapper.readTree(responseBody);

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

            log.warn("No tool_use block found in vision response for tool: {}", toolName);
            return objectMapper.createObjectNode();

        } catch (Exception e) {
            log.error("Error calling Claude Vision: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Claude Vision: " + e.getMessage(), e);
        }
    }

    public String getModelId() {
        return modelId;
    }
}
