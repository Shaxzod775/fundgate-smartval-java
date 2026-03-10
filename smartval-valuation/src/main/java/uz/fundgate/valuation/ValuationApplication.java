package uz.fundgate.valuation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SmartVal Valuation API - Spring Boot Application.
 *
 * Startup valuation using three professional methods:
 * - Berkus Method: 5 factors, max $500K total
 * - Scorecard Method: weighted multipliers against base valuation
 * - Risk Factor Summation: 12 risk categories with $250K adjustments
 *
 * Uses Claude AI via AWS Bedrock for intelligent evaluation.
 */
@SpringBootApplication(scanBasePackages = {"uz.fundgate.valuation", "uz.fundgate.common"})
@EnableAsync
public class ValuationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ValuationApplication.class, args);
    }
}
