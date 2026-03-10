package uz.fundgate.email;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SmartVal Email Service - Spring Boot Application.
 *
 * Centralized email sending service for FundGate and SmartVal platforms.
 * Features:
 * - FundGate analysis result emails with scoring and verdicts
 * - SmartVal valuation result emails
 * - Generic email support with Thymeleaf templates
 * - RabbitMQ event-driven email sending
 * - SMTP integration (Gmail, custom SMTP)
 */
@SpringBootApplication(scanBasePackages = {"uz.fundgate.email", "uz.fundgate.common"})
@EnableAsync
public class EmailApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailApplication.class, args);
    }
}
