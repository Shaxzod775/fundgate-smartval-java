package uz.fundgate.scheduler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import uz.fundgate.scheduler.dto.ReportData;
import uz.fundgate.scheduler.dto.TelegramMessage;

import java.time.Duration;

/**
 * Service for sending messages via Telegram Bot API.
 * Uses WebClient for non-blocking HTTP calls to Telegram servers.
 *
 * Ported from Python: send_telegram_message() in tasks.py
 */
@Slf4j
@Service
public class TelegramService {

    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";

    private final WebClient webClient;
    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.chat.id}")
    private String chatId;

    @Value("${telegram.notifications.enabled:true}")
    private boolean notificationsEnabled;

    public TelegramService(ReportService reportService, ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
                .baseUrl(TELEGRAM_API_BASE)
                .build();
        this.reportService = reportService;
        this.objectMapper = objectMapper;
    }

    /**
     * Send a text message to a Telegram chat.
     * Ported from Python: send_telegram_message()
     *
     * @param targetChatId Telegram chat ID (can be negative for groups)
     * @param text         message text
     */
    public void sendMessage(String targetChatId, String text) {
        log.info("Sending Telegram message to chat {}, length: {} chars", targetChatId, text.length());

        try {
            TelegramMessage message = TelegramMessage.builder()
                    .chatId(targetChatId)
                    .text(text)
                    .parseMode("Markdown")
                    .build();

            String responseBody = webClient.post()
                    .uri(botToken + "/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(message)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (responseBody != null) {
                JsonNode response = objectMapper.readTree(responseBody);
                if (response.path("ok").asBoolean(false)) {
                    log.info("Telegram message sent successfully to chat {}", targetChatId);
                } else {
                    log.error("Telegram API error: {}", response.path("description").asText("unknown error"));
                }
            }

        } catch (Exception e) {
            log.error("Failed to send Telegram message to chat {}: {}", targetChatId, e.getMessage(), e);
            throw new RuntimeException("Failed to send Telegram message: " + e.getMessage(), e);
        }
    }

    /**
     * Send a formatted statistics report to the configured Telegram chat.
     * Checks if notifications are enabled before sending.
     *
     * Ported from Python: send_daily_report() / send_weekly_report() Celery tasks
     *
     * @param data report data to format and send
     * @param type "daily" or "weekly"
     */
    public void sendReport(ReportData data, String type) {
        if (!notificationsEnabled) {
            log.info("Notifications disabled, skipping {} report", type);
            return;
        }

        String message = reportService.formatReportMessage(data, type);
        sendMessage(chatId, message);
        log.info("{} report sent successfully", type);
    }

    /**
     * Check if notifications are currently enabled.
     */
    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }
}
