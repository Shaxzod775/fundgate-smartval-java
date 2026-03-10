package uz.fundgate.fundgate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent B output: Pitch deck visual analysis using Claude Vision.
 * Max Score: 20 points.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PitchDeckResult extends AgentResult {

    /** Slide structure completeness (max 8). */
    private int structureScore;

    /** Content quality (max 8). */
    private int contentScore;

    /** Visual design quality (max 4). */
    private int designScore;

    /** Number of slides analyzed. */
    private int slidesAnalyzed;

    /** Types of slides found. */
    @lombok.Builder.Default
    private List<String> slidesFound = new ArrayList<>();

    /** Missing required slides. */
    @lombok.Builder.Default
    private List<String> missingSlides = new ArrayList<>();

    /** Per-slide analysis. */
    @lombok.Builder.Default
    private List<SlideAnalysis> slideDetails = new ArrayList<>();

    /** Trilingual recommendations. */
    @lombok.Builder.Default
    private String recommendationsRu = "";
    @lombok.Builder.Default
    private String recommendationsEn = "";
    @lombok.Builder.Default
    private String recommendationsUz = "";
}
