package uz.fundgate.scheduler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import uz.fundgate.scheduler.dto.ReportData;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Service for generating daily and weekly statistics reports.
 * Fetches data from the platform API and formats reports for Telegram delivery.
 *
 * Ported from Python: fetch_statistics(), format_daily_report(), format_weekly_report() in tasks.py
 */
@Slf4j
@Service
public class ReportService {

    private static final ZoneId TASHKENT_TZ = ZoneId.of("Asia/Tashkent");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter SHORT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM");

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ReportService(
            @Value("${platform.api.url:https://api-kpsspj764a-uc.a.run.app}") String apiUrl,
            ObjectMapper objectMapper
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .build();
        this.objectMapper = objectMapper;
        log.info("ReportService initialized with API URL: {}", apiUrl);
    }

    /**
     * Generate daily statistics report by fetching data from API.
     * Ported from Python: fetch_statistics("day") + format_daily_report()
     */
    public ReportData generateDailyReport() {
        log.info("Generating daily report...");
        JsonNode statistics = fetchStatistics("day");
        return parseStatistics(statistics, "daily");
    }

    /**
     * Generate weekly statistics report by fetching data from API.
     * Ported from Python: fetch_statistics("week") + format_weekly_report()
     */
    public ReportData generateWeeklyReport() {
        log.info("Generating weekly report...");
        JsonNode statistics = fetchStatistics("week");
        return parseStatistics(statistics, "weekly");
    }

    /**
     * Format report data as Telegram message (Markdown).
     * Ported from Python: format_daily_report() and format_weekly_report()
     *
     * @param data report data
     * @param type "daily" or "weekly"
     * @return formatted Markdown message
     */
    public String formatReportMessage(ReportData data, String type) {
        if (data == null) {
            return type.equals("weekly")
                    ? "Не удалось получить статистику за неделю."
                    : "Не удалось получить статистику.";
        }

        if ("weekly".equals(type)) {
            return formatWeeklyMessage(data);
        }
        return formatDailyMessage(data);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Fetch statistics from platform API.
     * Ported from Python: fetch_statistics()
     */
    private JsonNode fetchStatistics(String period) {
        try {
            String responseBody = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/admin/statistics")
                            .queryParam("period", period)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .onErrorResume(e -> {
                        log.error("API error fetching statistics: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (responseBody != null) {
                return objectMapper.readTree(responseBody);
            }
        } catch (Exception e) {
            log.error("Error fetching statistics for period '{}': {}", period, e.getMessage());
        }
        return null;
    }

    /**
     * Parse API response JSON into ReportData.
     * Ported from Python: data["statistics"] parsing in format_*_report()
     */
    private ReportData parseStatistics(JsonNode rootNode, String type) {
        if (rootNode == null || !rootNode.has("statistics")) {
            log.warn("No statistics data available");
            return null;
        }

        JsonNode stats = rootNode.get("statistics");
        JsonNode startups = stats.path("startups");
        JsonNode users = stats.path("users");
        JsonNode analyses = stats.path("analyses");
        JsonNode smartval = analyses.path("smartval");
        JsonNode chatkit = stats.path("chatkit");

        LocalDate now = LocalDate.now(TASHKENT_TZ);
        String dateStr;
        if ("weekly".equals(type)) {
            LocalDate startDate = now.minusDays(6);
            dateStr = startDate.format(SHORT_DATE_FORMAT) + " - " + now.format(DATE_FORMAT);
        } else {
            dateStr = now.format(DATE_FORMAT);
        }

        return ReportData.builder()
                .date(dateStr)
                .newUsers(users.path("new").asInt(0))
                .totalUsers(users.path("total").asInt(0))
                .startupsCreated(startups.path("created").asInt(0))
                .totalStartups(startups.path("total").asInt(0))
                .fundgateAnalyses(analyses.path("fundgate").asInt(0))
                .smartvalTotal(smartval.path("total").asInt(0))
                .berkusAnalyses(smartval.path("berkus").asInt(0))
                .scorecardAnalyses(smartval.path("scorecard").asInt(0))
                .riskFactorAnalyses(smartval.path("riskfactor").asInt(0))
                .chatkitMessages(chatkit.path("messages").asInt(0))
                .feedbackCount(stats.path("feedback").asInt(0))
                .build();
    }

    /**
     * Format daily report message.
     * Exact format ported from Python: format_daily_report()
     */
    private String formatDailyMessage(ReportData data) {
        return String.format("""
                \uD83D\uDCCA Статистика за %s

                \uD83D\uDC65 Пользователи
                   \u2022 Новых: %d
                   \u2022 Всего: %d

                \uD83D\uDE80 Стартапы
                   \u2022 Создано: %d
                   \u2022 Всего: %d

                \uD83D\uDCC8 Анализы
                   \u2022 FundGate: %d
                   \u2022 SmartVal: %d
                     \u251C Berkus: %d
                     \u251C Scorecard: %d
                     \u2514 Risk Factor: %d

                \uD83D\uDCAC Chatkit: %d сообщений
                \uD83D\uDCDD Отзывы: %d""",
                data.getDate(),
                data.getNewUsers(), data.getTotalUsers(),
                data.getStartupsCreated(), data.getTotalStartups(),
                data.getFundgateAnalyses(),
                data.getSmartvalTotal(),
                data.getBerkusAnalyses(), data.getScorecardAnalyses(), data.getRiskFactorAnalyses(),
                data.getChatkitMessages(),
                data.getFeedbackCount());
    }

    /**
     * Format weekly report message.
     * Exact format ported from Python: format_weekly_report()
     */
    private String formatWeeklyMessage(ReportData data) {
        return String.format("""
                \uD83D\uDCCA Недельная статистика
                \uD83D\uDCC5 %s

                \u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501

                \uD83D\uDC65 ПОЛЬЗОВАТЕЛИ
                   Новых за неделю: %d
                   Всего на платформе: %d

                \uD83D\uDE80 СТАРТАПЫ
                   Создано за неделю: %d
                   Всего на платформе: %d

                \uD83D\uDCC8 АНАЛИЗЫ (%d за неделю)
                   FundGate оценок: %d
                   SmartVal оценок: %d
                   \u2022 Berkus: %d
                   \u2022 Scorecard: %d
                   \u2022 Risk Factor: %d

                \uD83D\uDCAC CHATKIT
                   Сообщений: %d

                \uD83D\uDCDD ОТЗЫВЫ
                   Получено: %d

                \u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501
                \uD83D\uDD17 FundGate SmartVal Platform""",
                data.getDate(),
                data.getNewUsers(), data.getTotalUsers(),
                data.getStartupsCreated(), data.getTotalStartups(),
                data.getTotalAnalyses(),
                data.getFundgateAnalyses(),
                data.getSmartvalTotal(),
                data.getBerkusAnalyses(), data.getScorecardAnalyses(), data.getRiskFactorAnalyses(),
                data.getChatkitMessages(),
                data.getFeedbackCount());
    }
}
