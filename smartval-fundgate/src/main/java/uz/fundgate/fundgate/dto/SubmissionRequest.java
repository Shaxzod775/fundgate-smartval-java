package uz.fundgate.fundgate.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Complete request to FundGate API.
 * Matches the frontend FundGateRequest interface exactly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubmissionRequest {

    @Builder.Default
    private String module = "fundgate";

    private String submissionId;

    @NotNull(message = "Submission data is required")
    @Valid
    private SubmissionData submission;

    @Builder.Default
    private Map<String, Object> files = new HashMap<>();

    @Builder.Default
    private String locale = "ru";

    @Builder.Default
    private FileUrls fileUrls = new FileUrls();

    /** Firebase startup document ID for async processing. */
    private String startupId;

    /** Firebase user UID (startup owner) for async processing. */
    private String ownerId;
}
