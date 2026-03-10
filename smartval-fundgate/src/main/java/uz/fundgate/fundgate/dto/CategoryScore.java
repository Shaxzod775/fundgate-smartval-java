package uz.fundgate.fundgate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Score for a single category with details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryScore {

    private int score;
    private int maxScore;
    @Builder.Default
    private double percentage = 0.0;
    @Builder.Default
    private String comment = "";
    @Builder.Default
    private Map<String, Object> details = new HashMap<>();
}
