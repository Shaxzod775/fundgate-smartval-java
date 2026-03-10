package uz.fundgate.fundgate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent F output: Materials quality evaluation.
 * Max Score: 10 points.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MaterialsResult extends AgentResult {

    /** Description quality score (max 3). */
    private int descriptionScore;

    /** Working links score (max 3). */
    private int linksScore;

    /** Additional documents score (max 4). */
    private int documentsScore;

    /** Pitch deck uploaded. */
    private boolean hasPitchDeck;

    /** One-pager uploaded. */
    private boolean hasOnePager;

    /** Financial model uploaded. */
    private boolean hasFinancialModel;

    /** Demo video provided. */
    private boolean hasVideoDemo;

    /** Non-working links. */
    @lombok.Builder.Default
    private List<String> brokenLinks = new ArrayList<>();

    /** Description quality issues. */
    @lombok.Builder.Default
    private List<String> descriptionIssues = new ArrayList<>();
}
