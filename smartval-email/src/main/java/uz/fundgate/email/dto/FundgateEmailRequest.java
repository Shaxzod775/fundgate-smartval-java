package uz.fundgate.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Email request for FundGate analysis results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundgateEmailRequest {

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid email format")
    private String to;

    @NotBlank(message = "Startup name is required")
    private String startupName;

    @NotNull(message = "Score is required")
    private Integer score;

    @NotBlank(message = "Verdict is required")
    private String verdict;

    /**
     * Category scores (e.g., "Completeness: 18/20", "Team: 15/20").
     */
    private List<String> categories;

    /**
     * List of blocking issues found during analysis.
     */
    private List<String> blockers;

    /**
     * AI-generated summary of the analysis.
     */
    private String summary;

    /**
     * URL to view the full analysis results.
     */
    private String analysisUrl;
}
