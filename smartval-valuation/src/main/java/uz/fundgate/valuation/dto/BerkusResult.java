package uz.fundgate.valuation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of the Berkus Method valuation.
 *
 * Classic Berkus Method evaluates pre-revenue startups across 5 factors,
 * each worth up to $100,000 (max total $500,000):
 *
 * 1. Sound Idea (value of the idea) - $0 to $100K
 * 2. Prototype (technology risk reduction) - $0 to $100K
 * 3. Quality Management Team - $0 to $100K
 * 4. Strategic Relationships (market risk reduction) - $0 to $100K
 * 5. Product Rollout (production risk reduction) - $0 to $100K
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BerkusResult {

    /** Sound Idea evaluation ($0-$100K) */
    private FactorEvaluation soundIdea;

    /** Prototype / Technology Risk Reduction ($0-$100K) */
    private FactorEvaluation prototype;

    /** Quality Management Team ($0-$100K) */
    private FactorEvaluation qualityTeam;

    /** Strategic Relationships / GTM ($0-$100K) */
    private FactorEvaluation strategicRelationships;

    /** Product Rollout / Market Risk Reduction ($0-$100K) */
    private FactorEvaluation productRollout;

    /** Total Berkus valuation sum ($0-$500K) */
    private long totalValuation;

    /** Detailed analysis text */
    private String details;

    /** Overall recommendations */
    private String recommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FactorEvaluation {
        private long evaluation;
        private boolean isSpam;
        private String commentRu;
        private String commentEn;
        private String commentUz;
        private String recommendationsRu;
        private String recommendationsEn;
        private String recommendationsUz;
    }
}
