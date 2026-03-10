package uz.fundgate.fundgate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A blocking issue that prevents full evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Blocker {

    /** Blocker code (e.g., "no_pitch_deck"). */
    private String code;

    /** Human-readable message. */
    private String message;
}
