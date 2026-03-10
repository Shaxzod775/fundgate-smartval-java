package uz.fundgate.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import uz.fundgate.chat.dto.ChatRequest;
import uz.fundgate.chat.dto.ChatResponse;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service handling AI chat using AWS Bedrock Claude.
 * Supports SSE streaming and conversation context management.
 *
 * Ported from Python chatkit-backend: chat.py + claude_document_agent.py
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

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

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are a helpful AI assistant for FundGate startup platform.
            Always respond in the same language the user asks in.
            Keep responses concise but helpful.
            You help users with startup-related questions, document creation, and platform navigation.
            """;

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    /**
     * In-memory conversation context store.
     * Maps conversationId -> list of messages (role + content).
     */
    private final ConcurrentHashMap<String, ArrayNode> conversationStore = new ConcurrentHashMap<>();

    private final ExecutorService sseExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Create an SSE emitter for streaming chat responses.
     * The response is streamed token-by-token via Server-Sent Events.
     */
    public SseEmitter streamChat(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2 minutes timeout

        sseExecutor.execute(() -> {
            try {
                String conversationId = request.getConversationId();
                if (conversationId == null || conversationId.isBlank()) {
                    conversationId = "conv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                }

                // Send conversation ID to client
                sendSseEvent(emitter, Map.of(
                        "type", "conversation_id",
                        "conversation_id", conversationId
                ));

                String systemPrompt = request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()
                        ? request.getSystemPrompt()
                        : DEFAULT_SYSTEM_PROMPT;

                // Build messages with conversation history
                ArrayNode messages = getOrCreateConversation(conversationId);

                // Add user message to history
                ObjectNode userMsg = objectMapper.createObjectNode();
                userMsg.put("role", "user");
                ArrayNode userContent = userMsg.putArray("content");
                ObjectNode textBlock = userContent.addObject();
                textBlock.put("type", "text");
                textBlock.put("text", request.getMessage());
                messages.add(userMsg);

                // Call Claude via Bedrock
                String responseText = callClaude(systemPrompt, messages);

                // Add assistant message to history
                ObjectNode assistantMsg = objectMapper.createObjectNode();
                assistantMsg.put("role", "assistant");
                ArrayNode assistantContent = assistantMsg.putArray("content");
                ObjectNode assistantTextBlock = assistantContent.addObject();
                assistantTextBlock.put("type", "text");
                assistantTextBlock.put("text", responseText);
                messages.add(assistantMsg);

                // Limit conversation history to last 20 messages
                trimConversation(conversationId, 20);

                // Stream the response in chunks to simulate streaming
                streamResponseInChunks(emitter, responseText);

                // Send completion
                sendSseEvent(emitter, Map.of("type", "done"));
                emitter.complete();

                log.info("[CHAT] Completed response for conversation {}, length: {} chars",
                        conversationId, responseText.length());

            } catch (Exception e) {
                log.error("[CHAT] Error during streaming: {}", e.getMessage(), e);
                try {
                    sendSseEvent(emitter, Map.of(
                            "type", "text_delta",
                            "delta", Map.of("text", "Error: " + e.getMessage())
                    ));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    /**
     * Non-streaming chat: returns a complete response.
     */
    public ChatResponse chat(ChatRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = "conv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }

        String systemPrompt = request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()
                ? request.getSystemPrompt()
                : DEFAULT_SYSTEM_PROMPT;

        ArrayNode messages = getOrCreateConversation(conversationId);

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        ArrayNode userContent = userMsg.putArray("content");
        ObjectNode textBlock = userContent.addObject();
        textBlock.put("type", "text");
        textBlock.put("text", request.getMessage());
        messages.add(userMsg);

        String responseText = callClaude(systemPrompt, messages);

        ObjectNode assistantMsg = objectMapper.createObjectNode();
        assistantMsg.put("role", "assistant");
        ArrayNode assistantContent = assistantMsg.putArray("content");
        ObjectNode assistantTextBlock = assistantContent.addObject();
        assistantTextBlock.put("type", "text");
        assistantTextBlock.put("text", responseText);
        messages.add(assistantMsg);

        trimConversation(conversationId, 20);

        int estimatedInputTokens = request.getMessage().length() / 4;
        int estimatedOutputTokens = responseText.length() / 4;

        return ChatResponse.builder()
                .message(responseText)
                .conversationId(conversationId)
                .model(modelId)
                .usage(Map.of(
                        "inputTokens", estimatedInputTokens,
                        "outputTokens", estimatedOutputTokens,
                        "totalTokens", estimatedInputTokens + estimatedOutputTokens
                ))
                .build();
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private String callClaude(String systemPrompt, ArrayNode messages) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("anthropic_version", "bedrock-2023-05-31");
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("temperature", temperature);
            requestBody.put("system", systemPrompt);
            requestBody.set("messages", messages);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            JsonNode responseJson = invokeWithRetryAndFallback(jsonBody);

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
            log.error("[CHAT] Error calling Claude: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Claude: " + e.getMessage(), e);
        }
    }

    private JsonNode invokeWithRetryAndFallback(String jsonBody) {
        try {
            return invokeWithRetry(modelId, jsonBody);
        } catch (Exception primaryError) {
            if (!modelId.equals(fallbackModelId)) {
                log.warn("[CHAT] Primary model {} failed, trying fallback: {}", modelId, fallbackModelId);
                try {
                    return invokeWithRetry(fallbackModelId, jsonBody);
                } catch (Exception fallbackError) {
                    log.error("[CHAT] Fallback model also failed: {}", fallbackError.getMessage());
                    throw new RuntimeException("Both primary and fallback models failed", fallbackError);
                }
            }
            throw primaryError;
        }
    }

    private JsonNode invokeWithRetry(String model, String jsonBody) {
        Exception lastError = null;
        long delay = INITIAL_RETRY_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                InvokeModelRequest request = InvokeModelRequest.builder()
                        .modelId(model)
                        .contentType("application/json")
                        .accept("application/json")
                        .body(SdkBytes.fromUtf8String(jsonBody))
                        .build();

                InvokeModelResponse response = bedrockClient.invokeModel(request);
                String responseBody = response.body().asUtf8String();
                JsonNode responseJson = objectMapper.readTree(responseBody);

                JsonNode usage = responseJson.get("usage");
                if (usage != null) {
                    log.info("[CHAT] Tokens - input: {}, output: {}",
                            usage.path("input_tokens").asInt(),
                            usage.path("output_tokens").asInt());
                }

                return responseJson;

            } catch (Exception e) {
                log.warn("[CHAT] Attempt {} failed for model {}: {}", attempt, model, e.getMessage());
                lastError = e;
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                    delay *= 2;
                }
            }
        }
        throw new RuntimeException("All retry attempts failed", lastError);
    }

    private void streamResponseInChunks(SseEmitter emitter, String text) throws IOException {
        // Split response into chunks to simulate token-by-token streaming
        int chunkSize = 20; // characters per chunk
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            String chunk = text.substring(i, end);

            sendSseEvent(emitter, Map.of(
                    "type", "text_delta",
                    "delta", Map.of("text", chunk)
            ));
        }
    }

    private void sendSseEvent(SseEmitter emitter, Object data) throws IOException {
        String json = objectMapper.writeValueAsString(data);
        emitter.send(SseEmitter.event().data(json));
    }

    private ArrayNode getOrCreateConversation(String conversationId) {
        return conversationStore.computeIfAbsent(conversationId, k -> objectMapper.createArrayNode());
    }

    private void trimConversation(String conversationId, int maxMessages) {
        ArrayNode messages = conversationStore.get(conversationId);
        if (messages != null && messages.size() > maxMessages) {
            ArrayNode trimmed = objectMapper.createArrayNode();
            for (int i = messages.size() - maxMessages; i < messages.size(); i++) {
                trimmed.add(messages.get(i));
            }
            conversationStore.put(conversationId, trimmed);
        }
    }
}
