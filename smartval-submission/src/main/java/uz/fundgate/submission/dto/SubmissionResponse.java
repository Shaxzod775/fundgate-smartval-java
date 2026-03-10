package uz.fundgate.submission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponse {

    private UUID id;

    private String startupName;

    private String status;

    private String message;

    private String analysisId;

    private Double score;

    private String verdict;

    private Instant createdAt;
}
