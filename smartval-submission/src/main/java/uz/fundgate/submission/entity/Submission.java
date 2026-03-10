package uz.fundgate.submission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import uz.fundgate.common.entity.BaseEntity;

@Entity
@Table(name = "submissions")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Submission extends BaseEntity {

    @Column(name = "startup_name", nullable = false)
    private String startupName;

    @Column(name = "founder_email", nullable = false)
    private String founderEmail;

    @Column(name = "founder_name")
    private String founderName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "stage")
    private String stage;

    @Column(name = "sector")
    private String sector;

    @Column(name = "team_size")
    private Integer teamSize;

    @Column(name = "pitch_deck_url")
    private String pitchDeckUrl;

    @Column(name = "business_plan_url")
    private String businessPlanUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubmissionStatus status;

    @Column(name = "fundgate_score")
    private Double fundgateScore;

    @Column(name = "verdict")
    private String verdict;

    @Column(name = "analysis_id")
    private String analysisId;

    @Column(name = "raw_form_data", columnDefinition = "jsonb")
    private String rawFormData;
}
