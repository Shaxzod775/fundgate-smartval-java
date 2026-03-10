package uz.fundgate.crm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for CRM chat with AI assistant.
 * Supports streaming responses via SSE with Claude tool use.
 * Ported from Python: POST /api/chat request body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmChatRequest {

    /**
     * User message text.
     */
    @NotBlank(message = "Message is required")
    private String message;

    /**
     * Conversation ID for context continuity.
     * If null, a new conversation is created.
     */
    private String conversationId;

    /**
     * Organization ID for scoping startup data access.
     */
    private String organizationId;

    /**
     * Whether the user is on a mobile device (affects response formatting).
     */
    @Builder.Default
    private boolean mobile = false;
}
