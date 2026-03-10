package uz.fundgate.spam.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import uz.fundgate.common.config.RabbitMQConfig;
import uz.fundgate.common.event.SpamCheckEvent;
import uz.fundgate.spam.dto.SpamCheckRequest;
import uz.fundgate.spam.dto.SpamCheckResponse;
import uz.fundgate.spam.service.SpamCheckService;

/**
 * RabbitMQ consumer for processing spam check events from other services.
 *
 * Listens on the SPAM_CHECK_QUEUE and processes incoming spam check requests
 * asynchronously. Other services (e.g., smartval-submission) publish SpamCheckEvent
 * messages when new startup submissions arrive.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpamCheckConsumer {

    private final SpamCheckService spamCheckService;

    /**
     * Process a spam check event from the queue.
     *
     * @param event the spam check event containing submission data
     */
    @RabbitListener(queues = RabbitMQConfig.SPAM_CHECK_QUEUE)
    public void handleSpamCheckEvent(SpamCheckEvent event) {
        log.info("[SPAM-EVENT] Received spam check event for submission: {}, eventId: {}",
                event.getSubmissionId(), event.getEventId());

        try {
            SpamCheckRequest request = SpamCheckRequest.builder()
                    .content(event.getContent())
                    .email(event.getEmail())
                    .build();

            SpamCheckResponse result = spamCheckService.checkSingle(request);

            if (result.isSpam()) {
                log.warn("[SPAM-EVENT] SPAM detected for submission {}: confidence={}, reasons={}",
                        event.getSubmissionId(), result.getConfidence(), result.getReasons());

                // TODO: Publish spam detected event back to the submitting service
                // This could update the submission status, notify admins, etc.
            } else {
                log.info("[SPAM-EVENT] Submission {} passed spam check",
                        event.getSubmissionId());
            }

        } catch (Exception e) {
            log.error("[SPAM-EVENT] Failed to process spam check for submission {}: {}",
                    event.getSubmissionId(), e.getMessage(), e);
            // The message will be requeued based on RabbitMQ retry configuration
        }
    }
}
