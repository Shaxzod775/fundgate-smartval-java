package uz.fundgate.scheduler.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.fundgate.scheduler.dto.ReportData;
import uz.fundgate.scheduler.service.ReportService;
import uz.fundgate.scheduler.service.TelegramService;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Scheduled report tasks for automated statistics delivery via Telegram.
 * Replaces Celery beat scheduler from the Python implementation.
 *
 * Ported from Python:
 * - tasks.py: send_daily_report() Celery task -> dailyReport() @Scheduled
 * - tasks.py: send_weekly_report() Celery task -> weeklyReport() @Scheduled
 * - celeryconfig.py: beat_schedule cron definitions
 *
 * Schedule:
 * - Daily report: every day at 23:00 Tashkent time (UTC+5)
 * - Weekly report: every Sunday at 11:00 Tashkent time (UTC+5)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportScheduler {

    private static final ZoneId TASHKENT_TZ = ZoneId.of("Asia/Tashkent");

    private final ReportService reportService;
    private final TelegramService telegramService;

    /**
     * Send daily statistics report at 23:00 Tashkent time.
     *
     * Ported from Python celeryconfig.py:
     *   "send-daily-report": { "schedule": crontab(hour=23, minute=0) }
     * and tasks.py: send_daily_report()
     */
    @Scheduled(cron = "0 0 23 * * *", zone = "Asia/Tashkent")
    public void dailyReport() {
        ZonedDateTime now = ZonedDateTime.now(TASHKENT_TZ);
        log.info("=== Daily report triggered at {} ===", now);

        if (!telegramService.isNotificationsEnabled()) {
            log.info("Notifications disabled, skipping daily report at {}", now);
            return;
        }

        try {
            ReportData data = reportService.generateDailyReport();

            if (data != null) {
                telegramService.sendReport(data, "daily");
                log.info("Daily report sent successfully at {}", now);
            } else {
                log.warn("Daily report data is null, sending error message");
                telegramService.sendReport(null, "daily");
            }

        } catch (Exception e) {
            log.error("Failed to send daily report at {}: {}", now, e.getMessage(), e);
        }
    }

    /**
     * Send weekly statistics report on Sunday at 11:00 Tashkent time.
     *
     * Ported from Python celeryconfig.py:
     *   "send-weekly-report": { "schedule": crontab(day_of_week=0, hour=11, minute=0) }
     * and tasks.py: send_weekly_report()
     *
     * Note: In Spring cron, SUN is the last day. In Python celery, day_of_week=0 is Sunday.
     */
    @Scheduled(cron = "0 0 11 * * SUN", zone = "Asia/Tashkent")
    public void weeklyReport() {
        ZonedDateTime now = ZonedDateTime.now(TASHKENT_TZ);
        log.info("=== Weekly report triggered at {} ===", now);

        if (!telegramService.isNotificationsEnabled()) {
            log.info("Notifications disabled, skipping weekly report at {}", now);
            return;
        }

        try {
            ReportData data = reportService.generateWeeklyReport();

            if (data != null) {
                telegramService.sendReport(data, "weekly");
                log.info("Weekly report sent successfully at {}", now);
            } else {
                log.warn("Weekly report data is null, sending error message");
                telegramService.sendReport(null, "weekly");
            }

        } catch (Exception e) {
            log.error("Failed to send weekly report at {}: {}", now, e.getMessage(), e);
        }
    }
}
