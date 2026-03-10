package uz.fundgate.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Email request for SmartVal valuation results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartvalEmailRequest {

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid email format")
    private String to;

    @NotBlank(message = "Startup name is required")
    private String startupName;

    @NotBlank(message = "Valuation method is required")
    private String method;

    @NotNull(message = "Valuation amount is required")
    private Long valuation;

    /**
     * Additional details about the valuation (methodology breakdown, assumptions, etc.).
     */
    private String details;
}
