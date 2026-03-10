package uz.fundgate.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for document generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentGenerateRequest {

    /**
     * Output format: docx, xlsx, pptx, pdf.
     */
    @NotBlank(message = "Format is required")
    @Pattern(regexp = "docx|xlsx|pptx|pdf", message = "Format must be one of: docx, xlsx, pptx, pdf")
    private String format;

    @NotBlank(message = "Title is required")
    private String title;

    /**
     * Main content/task description for document generation.
     */
    @NotBlank(message = "Content is required")
    private String content;

    /**
     * Optional sections for structured document generation.
     * Each section may contain a heading and body text.
     */
    private List<DocumentSection> sections;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentSection {
        private String heading;
        private String body;
    }
}
