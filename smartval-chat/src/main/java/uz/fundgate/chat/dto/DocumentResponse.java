package uz.fundgate.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for document generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {

    /**
     * URL to download the generated document.
     */
    private String fileUrl;

    /**
     * Generated file name.
     */
    private String fileName;

    /**
     * Document format: docx, xlsx, pptx, pdf.
     */
    private String format;

    /**
     * File size in bytes.
     */
    private long fileSize;
}
