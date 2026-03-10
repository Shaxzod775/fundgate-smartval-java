package uz.fundgate.fundgate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Agent C output: Traction metrics evaluation.
 * Max Score: 20 points.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TractionResult extends AgentResult {

    /** Revenue metrics score (max 12). */
    private int revenueScore;

    /** User metrics score (max 5). */
    private int usersScore;

    /** Growth indicators score (max 3). */
    private int growthScore;

    /** Revenue data validated. */
    private boolean revenueValidated;

    /** User count validated. */
    private boolean userCountValidated;

    /** Estimated growth rate. */
    private String growthRateEstimated;

    /** Revenue analysis text. */
    private String revenueAnalysis;

    /** Users analysis text. */
    private String usersAnalysis;
}
