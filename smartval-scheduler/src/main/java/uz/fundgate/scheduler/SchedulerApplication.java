package uz.fundgate.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SmartVal Scheduler Service - Spring Boot Application.
 *
 * Scheduled tasks and Telegram bot integration for FundGate platform.
 * Features:
 * - Daily statistics report at 23:00 Tashkent time
 * - Weekly statistics report on Sunday 11:00 Tashkent time
 * - Telegram Bot API integration for report delivery
 * - Manual report trigger endpoints
 *
 * Ported from Python: telegram-bot/tasks.py + celeryconfig.py (Celery -> Spring @Scheduled)
 */
@SpringBootApplication(scanBasePackages = {"uz.fundgate.scheduler", "uz.fundgate.common"})
@EnableScheduling
public class SchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerApplication.class, args);
    }
}
