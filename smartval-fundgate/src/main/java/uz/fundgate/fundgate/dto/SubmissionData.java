package uz.fundgate.fundgate.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Startup submission data from the frontend form.
 * All fields match the frontend StartupDraft type for compatibility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubmissionData {

    // Basic information
    @NotBlank(message = "Startup name is required")
    @Size(min = 1, max = 200)
    private String name;

    @Size(max = 10000)
    @Builder.Default
    private String description = "";

    @Builder.Default
    private String industry = "";

    @Builder.Default
    private String stage = "";

    // Team
    @Builder.Default
    private String teamSize = "1";

    // Contact info
    private String website;

    @Builder.Default
    private String email = "";

    @Builder.Default
    private String phone = "";

    @Builder.Default
    private String country = "";

    // Dates
    private String foundingDate;

    // Traction metrics
    @Builder.Default
    private String revenue = "0";

    @Builder.Default
    private String userCount = "0";

    @Builder.Default
    private boolean hasUsers = false;

    // Technology
    @Builder.Default
    private boolean hasIP = false;

    @Builder.Default
    private boolean hasTechnology = false;

    @Builder.Default
    private String technologyDescription = "";

    // Media
    private String socialLinks;
    private String videoDemo;

    // Business
    @Builder.Default
    private String businessModel = "";

    // Founder info
    @Builder.Default
    private String firstName = "";

    @Builder.Default
    private String lastName = "";

    @Builder.Default
    private String role = "";

    // Investments
    @Builder.Default
    private boolean hasInvestments = false;

    @Builder.Default
    private List<Investment> investments = new ArrayList<>();

    // Additional team members (up to 5)
    @Builder.Default
    private List<TeamMember> teamMembers = new ArrayList<>();
}
