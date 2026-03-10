package uz.fundgate.crm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for startup analysis.
 * Ported from Python: POST /api/analyze request body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeRequest {

    /**
     * Analysis query or startup data JSON for AI assessment.
     */
    @NotBlank(message = "Query is required")
    private String query;

    /**
     * Optional startup ID to fetch data from CRM before analysis.
     */
    private String startupId;
}
