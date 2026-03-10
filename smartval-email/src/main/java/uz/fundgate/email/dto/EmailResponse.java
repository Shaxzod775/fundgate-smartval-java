package uz.fundgate.email.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for email operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailResponse {

    private boolean success;
    private String message;

    @Builder.Default
    private Instant timestamp = Instant.now();

    public static EmailResponse ok(String message) {
        return EmailResponse.builder()
                .success(true)
                .message(message)
                .build();
    }

    public static EmailResponse error(String message) {
        return EmailResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}
