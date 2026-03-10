package uz.fundgate.valuation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of the Risk Factor Summation method.
 *
 * Risk Factor Summation evaluates 12 risk categories, each rated -2 to +2:
 *  1. Product Stage
 *  2. Team Completeness
 *  3. Technology Risk
 *  4. Market Demand
 *  5. Competition
 *  6. Sales Channels
 *  7. Finances
 *  8. Legal
 *  9. Intellectual Property
 * 10. Data Security
 * 11. ESG (Environmental, Social, Governance)
 * 12. AI / Automation Risk
 *
 * Formula: Adjusted Valuation = Base Pre-Money + (Sum of ratings * Adjustment Step)
 * Where Adjustment Step is typically $250,000.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskFactorResult {

    /** Base pre-money valuation */
    private long preMoneyValuation;

    /** Final adjusted valuation */
    private long adjustedValuation;

    /** List of all risk factor evaluations */
    private List<RiskFactor> riskFactors;

    /** Sum of all risk factor ratings */
    private int totalAdjustment;

    /** Adjustment step in dollars (typically $250K) */
    private long adjustmentStep;

    /** Detailed analysis text */
    private String details;

    /** Overall recommendations */
    private String recommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactor {
        /** Risk factor name */
        private String name;

        /** Rating from -2 to +2 */
        private int rating;

        /** Dollar adjustment = rating * adjustmentStep */
        private long adjustment;

        /** Comment in Russian */
        private String commentRu;
        /** Comment in English */
        private String commentEn;
        /** Comment in Uzbek */
        private String commentUz;
    }
}
