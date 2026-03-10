package uz.fundgate.fundgate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent D output: Team composition evaluation.
 * Max Score: 15 points.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TeamResult extends AgentResult {

    /** Role coverage score (max 6). */
    private int coverageScore;

    /** Team experience score (max 5). */
    private int experienceScore;

    /** Full-time commitment score (max 2). */
    private int commitmentScore;

    /** Advisors/mentors score (max 2). */
    private int advisorsScore;

    /** Detected team size. */
    private int teamSize;

    /** Key roles identified. */
    @lombok.Builder.Default
    private List<String> keyRolesPresent = new ArrayList<>();

    /** Missing critical roles. */
    @lombok.Builder.Default
    private List<String> missingRoles = new ArrayList<>();

    /** Has CTO/technical lead. */
    private boolean hasTechnicalCofounder;

    /** Has advisors/mentors. */
    private boolean hasAdvisors;
}
