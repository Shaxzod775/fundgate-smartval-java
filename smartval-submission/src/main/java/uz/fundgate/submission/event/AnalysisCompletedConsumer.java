package uz.fundgate.submission.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import uz.fundgate.common.config.RabbitMQConfig;
import uz.fundgate.common.event.AnalysisCompletedEvent;
import uz.fundgate.submission.entity.SubmissionStatus;
import uz.fundgate.submission.service.SubmissionService;

import java.util.UUID;

@Component
@ConditionalOnBean(RabbitTemplate.class)
@RequiredArgsConstructor
@Slf4j
public class AnalysisCompletedConsumer {

    private final SubmissionService submissionService;

    @RabbitListener(queues = RabbitMQConfig.ANALYSIS_QUEUE)
    public void handleAnalysisCompleted(AnalysisCompletedEvent event) {
        log.info("Received analysis completed event for submission: {}, status: {}",
                event.getSubmissionId(), event.getStatus());

        try {
            UUID submissionId = UUID.fromString(event.getSubmissionId());
            SubmissionStatus status = resolveStatus(event.getStatus());

            submissionService.updateStatus(
                    submissionId,
                    status,
                    event.getScore(),
                    event.getStatus(),
                    null
            );

            log.info("Submission {} updated after analysis completion (status={}, score={})",
                    submissionId, status, event.getScore());
        } catch (IllegalArgumentException e) {
            log.error("Invalid submission ID in analysis completed event: {}", event.getSubmissionId(), e);
        } catch (Exception e) {
            log.error("Failed to process analysis completed event for submission: {}",
                    event.getSubmissionId(), e);
        }
    }

    private SubmissionStatus resolveStatus(String eventStatus) {
        if (eventStatus == null) {
            return SubmissionStatus.FAILED;
        }
        return switch (eventStatus.toLowerCase()) {
            case "analyzed", "completed", "success" -> SubmissionStatus.ANALYZED;
            case "failed", "error" -> SubmissionStatus.FAILED;
            case "processing" -> SubmissionStatus.PROCESSING;
            default -> SubmissionStatus.ANALYZED;
        };
    }
}
