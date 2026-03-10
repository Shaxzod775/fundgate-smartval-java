package uz.fundgate.valuation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Unified response DTO for startup valuation results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValuationResponse {

    private String method;
    private String startupName;

    private ValuationRange valuation;
    private Map<String, Object> scores;
    private String details;
    private String recommendations;

    private BerkusResult berkusResult;
    private ScorecardResult scorecardResult;
    private RiskFactorResult riskFactorResult;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private String status;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValuationRange {
        private long min;
        private long max;
        private long best;
    }
}
