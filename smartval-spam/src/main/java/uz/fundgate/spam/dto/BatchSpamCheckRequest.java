package uz.fundgate.spam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for batch spam checking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchSpamCheckRequest {

    /**
     * List of entries to check for spam.
     */
    @NotEmpty(message = "Entries list must not be empty")
    @Valid
    private List<SpamCheckRequest> entries;
}
