package uz.fundgate.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Generic email request DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequest {

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid email format")
    private String to;

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Body is required")
    private String body;

    /**
     * Optional Thymeleaf template name.
     * If provided, the body is used as fallback plain text.
     */
    private String templateName;

    /**
     * Template variables for Thymeleaf rendering.
     */
    private Map<String, Object> variables;
}
