package uz.fundgate.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response DTO for AI chat endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String message;

    private String conversationId;

    private String model;

    /**
     * Token usage statistics.
     * Keys: inputTokens, outputTokens, totalTokens
     */
    private Map<String, Integer> usage;
}
