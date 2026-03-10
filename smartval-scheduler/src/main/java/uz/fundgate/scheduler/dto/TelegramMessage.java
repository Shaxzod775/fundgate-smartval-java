package uz.fundgate.scheduler.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Telegram Bot API sendMessage request body.
 * Used by TelegramService to deliver reports to configured Telegram chat.
 *
 * Ported from Python: send_telegram_message() in tasks.py
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TelegramMessage {

    /**
     * Telegram chat ID (can be negative for group chats).
     */
    @JsonProperty("chat_id")
    private String chatId;

    /**
     * Message text content.
     */
    private String text;

    /**
     * Parse mode for formatting (e.g., "Markdown", "HTML").
     */
    @JsonProperty("parse_mode")
    @Builder.Default
    private String parseMode = "Markdown";
}
