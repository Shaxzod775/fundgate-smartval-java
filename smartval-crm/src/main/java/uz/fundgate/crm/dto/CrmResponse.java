package uz.fundgate.crm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for CRM AI operations.
 * Contains the AI response text, data sources used, and tools invoked.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrmResponse {

    /**
     * AI-generated response text.
     */
    private String response;

    /**
     * Data sources used for the response (e.g., "CRM Database", "Web Search").
     */
    private List<String> sources;

    /**
     * List of Claude tools that were invoked during response generation.
     */
    private List<String> toolsUsed;

    /**
     * Structured analysis data (for analyze endpoints).
     */
    private Map<String, Object> analysis;

    /**
     * File metadata when analyzing files.
     */
    private Map<String, Object> fileInfo;

    /**
     * Whether the operation was successful.
     */
    @Builder.Default
    private boolean success = true;
}
