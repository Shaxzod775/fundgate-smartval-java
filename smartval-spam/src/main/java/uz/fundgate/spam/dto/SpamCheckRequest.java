package uz.fundgate.spam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for a single spam check entry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpamCheckRequest {

    /**
     * Text content to check for spam.
     */
    @NotBlank(message = "Content is required")
    private String content;

    /**
     * Email address of the submitter.
     */
    private String email;

    /**
     * Name of the startup being submitted.
     */
    private String startupName;

    /**
     * Startup description text.
     */
    private String description;
}
