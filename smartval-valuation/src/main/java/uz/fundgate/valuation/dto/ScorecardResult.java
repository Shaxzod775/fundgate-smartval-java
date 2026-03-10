package uz.fundgate.valuation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of the Scorecard Method valuation.
 *
 * Scorecard Method uses weighted multipliers to evaluate the startup:
 * - Team Experience (30%)
 * - Product / Barriers (25%)
 * - Market Size (15%)
 * - Competition (10%)
 * - GTM / Sales (10%)
 * - Traction (10%)
 *
 * Formula: Adjusted Valuation = Base Pre-Money * Sum(factor * weight)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScorecardResult {

    /** Base pre-money valuation used for calculation */
    private long baseValuation;

    /** Final adjusted valuation */
    private long adjustedValuation;

    /** List of weighted scorecard factors */
    private List<ScorecardFactor> factors;

    /** Weighted sum multiplier */
    private double totalMultiplier;

    /** Detailed analysis text */
    private String details;

    /** Overall recommendations */
    private String recommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScorecardFactor {
        /** Factor name (e.g., "Team Experience", "Product / Barriers") */
        private String name;

        /** Weight percentage (0.0-1.0, e.g. 0.30 for 30%) */
        private double weight;

        /** Multiplier from AI evaluation (0.5-1.5 typically) */
        private double multiplier;

        /** Weighted score = weight * multiplier */
        private double score;

        /** Comment in Russian */
        private String commentRu;
        /** Comment in English */
        private String commentEn;
        /** Comment in Uzbek */
        private String commentUz;
        /** Recommendations in Russian */
        private String recommendationsRu;
        /** Recommendations in English */
        private String recommendationsEn;
        /** Recommendations in Uzbek */
        private String recommendationsUz;
    }
}
