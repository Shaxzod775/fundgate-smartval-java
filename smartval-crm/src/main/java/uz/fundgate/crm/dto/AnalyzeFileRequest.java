package uz.fundgate.crm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for file-based analysis.
 * The file content is sent as base64-encoded string.
 * Ported from Python: POST /api/analyze-file (multipart form).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeFileRequest {

    /**
     * Base64-encoded file content.
     */
    @NotBlank(message = "File content is required")
    private String fileContent;

    /**
     * Original file name with extension (e.g., "pitch_deck.pdf").
     */
    @NotBlank(message = "File name is required")
    private String fileName;

    /**
     * Analysis query or prompt for the file.
     */
    private String query;
}
