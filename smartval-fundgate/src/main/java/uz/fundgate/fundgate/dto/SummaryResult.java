package uz.fundgate.fundgate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Summary Agent output: Overall startup assessment with trilingual support.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryResult {

    // Strengths (multilingual)
    @Builder.Default
    private List<String> strengthsRu = new ArrayList<>();
    @Builder.Default
    private List<String> strengthsEn = new ArrayList<>();
    @Builder.Default
    private List<String> strengthsUz = new ArrayList<>();

    // Weaknesses (multilingual)
    @Builder.Default
    private List<String> weaknessesRu = new ArrayList<>();
    @Builder.Default
    private List<String> weaknessesEn = new ArrayList<>();
    @Builder.Default
    private List<String> weaknessesUz = new ArrayList<>();

    // Overall comment - brief summary (multilingual)
    @Builder.Default
    private String overallCommentRu = "";
    @Builder.Default
    private String overallCommentEn = "";
    @Builder.Default
    private String overallCommentUz = "";

    // Detailed comment - in-depth analysis (multilingual)
    @Builder.Default
    private String detailedCommentRu = "";
    @Builder.Default
    private String detailedCommentEn = "";
    @Builder.Default
    private String detailedCommentUz = "";

    // Main recommendation (multilingual)
    @Builder.Default
    private String recommendationRu = "";
    @Builder.Default
    private String recommendationEn = "";
    @Builder.Default
    private String recommendationUz = "";

    /** Investment readiness: blocked/needs_improvement/ready_to_route. */
    @Builder.Default
    private String investmentReadiness = "needs_improvement";
}
