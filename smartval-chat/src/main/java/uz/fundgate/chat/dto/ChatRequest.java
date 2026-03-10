package uz.fundgate.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for AI chat endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Message is required")
    private String message;

    /**
     * AI model to use: openai, claude, gemini.
     * Defaults to claude (via Bedrock).
     */
    @Builder.Default
    private String model = "claude";

    /**
     * Conversation ID for maintaining context across messages.
     * If null, a new conversation is created.
     */
    private String conversationId;

    /**
     * Optional system prompt to customize AI behavior.
     */
    private String systemPrompt;
}
