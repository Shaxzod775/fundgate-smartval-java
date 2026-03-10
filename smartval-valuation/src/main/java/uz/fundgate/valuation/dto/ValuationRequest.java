package uz.fundgate.valuation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Unified request DTO for startup valuation.
 * Maps all fields from the Python request model used by Berkus, Scorecard, and Risk Factor methods.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValuationRequest {

    // =================== Core startup info ===================
    @JsonProperty("startup_id")
    private String startupId;

    @JsonProperty("owner_id")
    private String ownerId;

    @NotBlank(message = "Startup name is required")
    @JsonProperty("startup_name")
    private String startupName;

    @NotBlank(message = "Startup description is required")
    @JsonProperty("startup_description")
    private String startupDescription;

    @JsonProperty("startup_industry")
    private String startupIndustry;

    private String stage;
    private String sector;

    @JsonProperty("team_size")
    private Integer teamSize;

    private Double revenue;
    private Double funding;

    @JsonProperty("pitch_deck_url")
    private String pitchDeckUrl;

    private List<String> links;

    // =================== Berkus: Idea (Sound Idea) ===================
    @JsonProperty("idea_problem_description")
    private String ideaProblemDescription;

    @JsonProperty("idea_problem_urgency")
    private String ideaProblemUrgency;

    @JsonProperty("idea_money_time_estimation")
    private String ideaMoneyTimeEstimation;

    @JsonProperty("idea_solution_advantage")
    private String ideaSolutionAdvantage;

    // =================== Berkus: Prototype ===================
    @JsonProperty("prototype_has_prototype_mvp")
    private Boolean prototypeHasPrototypeMvp;

    @JsonProperty("prototype_demo_link")
    private String prototypeDemoLink;

    @JsonProperty("prototype_has_users_tests")
    private Boolean prototypeHasUsersTests;

    @JsonProperty("prototype_num_users_tests")
    private Integer prototypeNumUsersTests;

    @JsonProperty("prototype_dev_time")
    private String prototypeDevTime;

    // =================== Team ===================
    @JsonProperty("team_num_cofounders")
    private Integer teamNumCofounders;

    @JsonProperty("team_cofounders")
    private List<CofounderInfo> teamCofounders;

    @JsonProperty("team_has_cto")
    private Boolean teamHasCto;

    @JsonProperty("team_experience_description")
    private String teamExperienceDescription;

    @JsonProperty("team_founder_experience")
    private String teamFounderExperience;

    @JsonProperty("team_has_advisors_mentors")
    private Boolean teamHasAdvisorsMentors;

    @JsonProperty("team_advisors_mentors")
    private List<AdvisorInfo> teamAdvisorsMentors;

    // =================== GTM / Strategic Relationships ===================
    @JsonProperty("gtm_customer_acquisition_strategy")
    private String gtmCustomerAcquisitionStrategy;

    @JsonProperty("gtm_has_loi")
    private Boolean gtmHasLoi;

    @JsonProperty("gtm_num_loi")
    private Integer gtmNumLoi;

    @JsonProperty("gtm_has_pilots_projects")
    private Boolean gtmHasPilotsProjects;

    @JsonProperty("gtm_pilots_projects_description")
    private String gtmPilotsProjectsDescription;

    @JsonProperty("gtm_has_marketing_budget_plan")
    private Boolean gtmHasMarketingBudgetPlan;

    @JsonProperty("gtm_marketing_budget_plan_description")
    private String gtmMarketingBudgetPlanDescription;

    // =================== Market Risks / Product Rollout ===================
    @JsonProperty("market_risks_top_3_risks")
    private String marketRisksTop3Risks;

    @JsonProperty("market_risks_has_legal_regulatory_barriers")
    private Boolean marketRisksHasLegalRegulatoryBarriers;

    @JsonProperty("market_risks_legal_regulatory_barriers_description")
    private String marketRisksLegalRegulatoryBarriersDescription;

    @JsonProperty("market_risks_has_ip_protection")
    private Boolean marketRisksHasIpProtection;

    @JsonProperty("market_risks_ip_protection_description")
    private String marketRisksIpProtectionDescription;

    @JsonProperty("market_risks_risk_mitigation_actions")
    private String marketRisksRiskMitigationActions;

    // =================== Scorecard-specific fields ===================
    @JsonProperty("product_differentiation")
    private String productDifferentiation;

    @JsonProperty("product_unique_data")
    private String productUniqueData;

    @JsonProperty("product_barriers_description")
    private String productBarriersDescription;

    @JsonProperty("tam_amount")
    private Long tamAmount;

    @JsonProperty("tam_comment")
    private String tamComment;

    @JsonProperty("sam_amount")
    private Long samAmount;

    @JsonProperty("sam_comment")
    private String samComment;

    @JsonProperty("som_amount")
    private Long somAmount;

    @JsonProperty("som_comment")
    private String somComment;

    @JsonProperty("market_growth_rate")
    private Double marketGrowthRate;

    @JsonProperty("market_growth_comment")
    private String marketGrowthComment;

    @JsonProperty("competitors")
    private List<CompetitorInfo> competitors;

    @JsonProperty("differentiation")
    private String differentiation;

    @JsonProperty("gtm_channels")
    private List<String> gtmChannels;

    @JsonProperty("gtm_channels_comment")
    private String gtmChannelsComment;

    @JsonProperty("gtm_has_tested_channels")
    private Boolean gtmHasTestedChannels;

    @JsonProperty("gtm_has_first_leads")
    private Boolean gtmHasFirstLeads;

    // =================== Risk Factor fields ===================
    @JsonProperty("has_tech_barriers")
    private Boolean hasTechBarriers;

    @JsonProperty("tech_barriers_comment")
    private String techBarriersComment;

    @JsonProperty("has_product_confirmation")
    private Boolean hasProductConfirmation;

    @JsonProperty("product_confirmation_comment")
    private String productConfirmationComment;

    @JsonProperty("has_demand_confirmation")
    private Boolean hasDemandConfirmation;

    @JsonProperty("demand_confirmation_comment")
    private String demandConfirmationComment;

    @JsonProperty("has_loi")
    private Boolean hasLoi;

    @JsonProperty("burn_rate")
    private Long burnRate;

    private Integer runway;
    private Long cac;
    private Long ltv;

    @JsonProperty("has_licenses")
    private Boolean hasLicenses;

    @JsonProperty("licenses_comment")
    private String licensesComment;

    @JsonProperty("has_disputes")
    private Boolean hasDisputes;

    @JsonProperty("disputes_comment")
    private String disputesComment;

    @JsonProperty("license_file_url")
    private String licenseFileUrl;

    @JsonProperty("has_ip")
    private Boolean hasIp;

    @JsonProperty("ip_comment")
    private String ipComment;

    @JsonProperty("ip_file_url")
    private String ipFileUrl;

    @JsonProperty("data_storage_description")
    private String dataStorageDescription;

    @JsonProperty("gdpr_compliance")
    private Boolean gdprCompliance;

    @JsonProperty("has_positive_impact")
    private Boolean hasPositiveImpact;

    @JsonProperty("positive_impact_comment")
    private String positiveImpactComment;

    @JsonProperty("has_negative_reputation")
    private Boolean hasNegativeReputation;

    @JsonProperty("negative_reputation_comment")
    private String negativeReputationComment;

    @JsonProperty("gtm_strategy")
    private String gtmStrategy;

    // =================== Nested DTOs ===================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CofounderInfo {
        private String name;
        private String role;
        private String workType;
        private String experience;
        private String comment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdvisorInfo {
        private String name;
        private String expertise;
        private String comment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompetitorInfo {
        private String name;
        private String description;
        private String strengths;
        private String weaknesses;
    }
}
