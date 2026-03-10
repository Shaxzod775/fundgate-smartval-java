package uz.fundgate.spam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SmartVal Spam Checker Service - Spring Boot Application.
 *
 * AI-powered spam detection using Claude via AWS Bedrock.
 * Features:
 * - Single and batch spam checking
 * - Heuristic pre-filters (keyboard mashing, gibberish, repeating chars)
 * - Claude AI deep analysis for ambiguous cases
 * - RabbitMQ event-driven processing
 */
@SpringBootApplication(scanBasePackages = {"uz.fundgate.spam", "uz.fundgate.common"})
@EnableAsync
public class SpamApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpamApplication.class, args);
    }
}
