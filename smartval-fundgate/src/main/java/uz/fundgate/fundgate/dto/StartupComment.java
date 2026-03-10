package uz.fundgate.fundgate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * AI-generated comment about the startup.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartupComment {

    @Builder.Default
    private List<String> strengths = new ArrayList<>();

    @Builder.Default
    private List<String> weaknesses = new ArrayList<>();

    @Builder.Default
    private String overallComment = "";

    @Builder.Default
    private String detailedComment = "";

    @Builder.Default
    private String recommendation = "";
}
