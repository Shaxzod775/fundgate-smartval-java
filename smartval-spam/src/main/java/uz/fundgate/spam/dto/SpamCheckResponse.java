package uz.fundgate.spam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for spam check result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpamCheckResponse {

    /**
     * Whether the submission is classified as spam.
     */
    private boolean isSpam;

    /**
     * Confidence score from 0.0 to 1.0.
     */
    private double confidence;

    /**
     * List of reasons why the content was flagged as spam.
     */
    @Builder.Default
    private List<String> reasons = new ArrayList<>();

    public static SpamCheckResponse clean() {
        return SpamCheckResponse.builder()
                .isSpam(false)
                .confidence(0.0)
                .reasons(new ArrayList<>())
                .build();
    }

    public static SpamCheckResponse spam(double confidence, List<String> reasons) {
        return SpamCheckResponse.builder()
                .isSpam(true)
                .confidence(confidence)
                .reasons(reasons != null ? reasons : new ArrayList<>())
                .build();
    }
}
