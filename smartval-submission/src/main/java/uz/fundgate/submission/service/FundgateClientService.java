package uz.fundgate.submission.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import uz.fundgate.submission.entity.SubmissionStatus;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class FundgateClientService {

    private final WebClient webClient;
    private final SubmissionService submissionService;

    public FundgateClientService(
            @Value("${services.fundgate.url:http://localhost:8081}") String fundgateUrl,
            @Value("${fundgate.internal-api-key:fundgate-internal-secret}") String apiKey,
            SubmissionService submissionService
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(fundgateUrl)
                .defaultHeader("X-Internal-API-Key", apiKey)
                .build();
        this.submissionService = submissionService;
    }

    /**
     * Asynchronously calls the FundGate analysis service and updates the submission status upon completion.
     *
     * @param submissionId the submission UUID
     * @param requestData  the analysis request payload
     */
    @Async
    public void analyzeAsync(UUID submissionId, Map<String, Object> requestData) {
        log.info("Starting async FundGate analysis for submission: {}", submissionId);

        try {
            submissionService.updateStatus(submissionId, SubmissionStatus.PROCESSING, null, null, null);

            Map<String, Object> result = webClient.post()
                    .uri("/fundgate/analyze")
                    .bodyValue(requestData)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(120))
                    .cast(Map.class)
                    .block();

            if (result != null) {
                Double score = result.get("total") != null
                        ? ((Number) result.get("total")).doubleValue()
                        : null;
                String status = result.get("status") != null
                        ? result.get("status").toString()
                        : null;
                String analysisId = result.get("analysis_id") != null
                        ? result.get("analysis_id").toString()
                        : null;

                submissionService.updateStatus(submissionId, SubmissionStatus.ANALYZED, score, status, analysisId);
                log.info("FundGate analysis completed for submission: {} (score={})", submissionId, score);
            } else {
                submissionService.updateStatus(submissionId, SubmissionStatus.FAILED, null, "Analysis returned empty result", null);
                log.error("FundGate analysis returned null for submission: {}", submissionId);
            }
        } catch (Exception e) {
            log.error("FundGate analysis failed for submission: {}. Error: {}", submissionId, e.getMessage());
            submissionService.updateStatus(submissionId, SubmissionStatus.FAILED, null, "Analysis failed: " + e.getMessage(), null);
        }
    }
}
