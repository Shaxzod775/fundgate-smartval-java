package uz.fundgate.fundgate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent A output: Data completeness and validity evaluation.
 * Max Score: 20 points.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CompletenessResult extends AgentResult {

    /** Core fields presence score (max 12). */
    private int coreFieldsScore;

    /** Format validity score (max 4). */
    private int formatScore;

    /** Data consistency score (max 4). */
    private int consistencyScore;

    /** List of missing required fields. */
    @lombok.Builder.Default
    private List<String> missingFields = new ArrayList<>();

    /** List of format validation issues. */
    @lombok.Builder.Default
    private List<String> formatIssues = new ArrayList<>();

    /** List of logical inconsistencies. */
    @lombok.Builder.Default
    private List<String> consistencyIssues = new ArrayList<>();
}
