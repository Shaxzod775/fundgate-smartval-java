package uz.fundgate.fundgate.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete response from FundGate API.
 * Matches the frontend FundGateResponse interface exactly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FundGateResponse {

    @Builder.Default
    private String module = "fundgate";

    @Builder.Default
    private String version = "1.0.0";

    /** Blocking issues, if any. */
    @Builder.Default
    private List<Blocker> blockers = new ArrayList<>();

    /** Scores by category (A=20, B=20, C=20, D=15, E=15, F=10). */
    @Builder.Default
    private Map<String, Integer> scores = new HashMap<>();

    /** Total score (0-100). */
    @Builder.Default
    private int total = 0;

    /** Status: blocked, needs_improvement, ready_to_route. */
    @Builder.Default
    private String status = "needs_improvement";

    /** Improvement suggestions. */
    @Builder.Default
    private List<String> recommendations = new ArrayList<>();

    /** AI-generated analysis. */
    private StartupComment startupComment;

    /** Detailed scores by category. */
    private Map<String, CategoryScore> categoryDetails;

    /** Analysis ID for result retrieval. */
    private String analysisId;

    /** Timestamp of processing. */
    private Instant processedAt;
}
