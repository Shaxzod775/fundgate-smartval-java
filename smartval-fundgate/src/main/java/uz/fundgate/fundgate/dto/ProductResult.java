package uz.fundgate.fundgate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Agent E output: Product and technology evaluation.
 * Max Score: 15 points.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProductResult extends AgentResult {

    /** Development stage score (max 9). */
    private int stageScore;

    /** IP/defensibility score (max 6). */
    private int defensibilityScore;

    /** Detected development stage. */
    private String currentStage;

    /** Stage validated against metrics. */
    private boolean stageValidated;

    /** Has intellectual property. */
    private boolean hasIp;

    /** Has technology moat. */
    private boolean hasTechMoat;

    /** Technical complexity: low/medium/high. */
    @lombok.Builder.Default
    private String techComplexity = "medium";
}
