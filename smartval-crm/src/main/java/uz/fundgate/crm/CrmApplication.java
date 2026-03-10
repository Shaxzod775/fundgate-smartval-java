package uz.fundgate.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SmartVal CRM AI Service - Spring Boot Application.
 *
 * AI-powered assistant for FundGate CRM with Claude on AWS Bedrock.
 * Features:
 * - Startup data analysis with Claude tool use (web_search, get_all_startups, get_startup_details)
 * - File/document analysis (images, PDF, DOCX) via Claude vision
 * - SSE streaming chat with tool execution pipeline
 * - Integration with external CRM API for startup operations
 *
 * Ported from Python: crm-funds/backend/ai-service/main.py
 */
@SpringBootApplication(scanBasePackages = {"uz.fundgate.crm", "uz.fundgate.common"})
@EnableAsync
public class CrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}
