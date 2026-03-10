package uz.fundgate.scheduler.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.fundgate.scheduler.dto.ReportData;
import uz.fundgate.scheduler.service.ReportService;
import uz.fundgate.scheduler.service.TelegramService;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for manual report triggering and health checks.
 * Allows administrators to trigger reports on demand (bypasses schedule).
 *
 * Ported from Python: test_report() Celery task for manual triggering.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Scheduler", description = "Report scheduler and Telegram integration")
public class SchedulerController {

    private final ReportService reportService;
    private final TelegramService telegramService;

    /**
     * Manually trigger daily report generation and send via Telegram.
     * Ported from Python: test_report(report_type="daily")
     */
    @PostMapping("/api/reports/daily")
    @Operation(summary = "Trigger daily report", description = "Manually generate and send daily statistics report via Telegram")
    public ResponseEntity<Map<String, Object>> triggerDailyReport() {
        log.info("[SCHEDULER] Manual daily report trigger");

        try {
            ReportData data = reportService.generateDailyReport();
            telegramService.sendReport(data, "daily");

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("type", "daily");
            response.put("data", data);
            response.put("timestamp", Instant.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[SCHEDULER] Manual daily report failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "type", "daily",
                    "error", e.getMessage(),
                    "timestamp", Instant.now().toString()
            ));
        }
    }

    /**
     * Manually trigger weekly report generation and send via Telegram.
     * Ported from Python: test_report(report_type="weekly")
     */
    @PostMapping("/api/reports/weekly")
    @Operation(summary = "Trigger weekly report", description = "Manually generate and send weekly statistics report via Telegram")
    public ResponseEntity<Map<String, Object>> triggerWeeklyReport() {
        log.info("[SCHEDULER] Manual weekly report trigger");

        try {
            ReportData data = reportService.generateWeeklyReport();
            telegramService.sendReport(data, "weekly");

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("type", "weekly");
            response.put("data", data);
            response.put("timestamp", Instant.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[SCHEDULER] Manual weekly report failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "type", "weekly",
                    "error", e.getMessage(),
                    "timestamp", Instant.now().toString()
            ));
        }
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Service health status")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "smartval-scheduler",
                "notificationsEnabled", telegramService.isNotificationsEnabled(),
                "timestamp", Instant.now().toString()
        ));
    }
}
