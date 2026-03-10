package uz.fundgate.fundgate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Analysis of a single pitch deck slide.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlideAnalysis {

    private int slideNumber;
    private String slideType;
    private int qualityScore;
    @Builder.Default
    private String contentSummary = "";
    @Builder.Default
    private String designQuality = "average";
}
