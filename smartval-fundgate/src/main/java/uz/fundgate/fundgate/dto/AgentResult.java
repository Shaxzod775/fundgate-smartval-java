package uz.fundgate.fundgate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Base class for all agent evaluation results.
 * Each agent extends this with category-specific fields.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class AgentResult {

    /** Total score for this category. */
    private int totalScore;

    /** Multilingual comments. */
    private String commentRu;
    private String commentEn;
    private String commentUz;
}
