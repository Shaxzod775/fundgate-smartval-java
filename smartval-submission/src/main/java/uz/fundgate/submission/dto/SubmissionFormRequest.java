package uz.fundgate.submission.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionFormRequest {

    @NotBlank(message = "Startup name is required")
    private String startupName;

    @NotBlank(message = "Founder email is required")
    private String founderEmail;

    @NotBlank(message = "Description is required")
    private String description;

    private String stage;

    private String sector;

    private Integer teamSize;

    @Valid
    private List<TeamMemberDto> teamMembers;

    private String pitchDeckUrl;

    private String businessPlanUrl;

    private List<String> links;

    private String fundingStage;

    private String fundingAmount;

    private String previousFunding;

    private String revenue;

    private String users;

    private String growthRate;

    private String marketSize;

    private String competitiveAdvantage;

    private String businessModel;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMemberDto {

        private String name;

        private String role;

        private String linkedin;

        private String experience;
    }
}
