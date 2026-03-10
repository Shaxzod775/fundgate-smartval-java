package uz.fundgate.fundgate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * FundGate Agents API - Spring Boot Application.
 *
 * AI-powered startup evaluation using Claude on AWS Bedrock.
 * Features:
 * - 6-category scoring (A-F, 100 points total)
 * - Vision-based pitch deck analysis
 * - Multilingual output (ru/en/uz)
 * - Parallel agent execution
 * - Async processing with Firebase and email notifications
 */
@SpringBootApplication
@EnableAsync
public class FundgateApplication {

    public static void main(String[] args) {
        SpringApplication.run(FundgateApplication.class, args);
    }
}
