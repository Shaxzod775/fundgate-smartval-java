package uz.fundgate.fundgate.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Additional team member information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamMember {

    private String id;
    private String firstName;
    private String lastName;
    private String role;
    private String customRole;
    private String linkedin;
}
