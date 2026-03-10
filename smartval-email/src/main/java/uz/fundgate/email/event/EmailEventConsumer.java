package uz.fundgate.email.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import uz.fundgate.common.config.RabbitMQConfig;
import uz.fundgate.common.event.EmailEvent;
import uz.fundgate.email.dto.EmailRequest;
import uz.fundgate.email.service.EmailService;

/**
 * RabbitMQ consumer for email events.
 * Listens on the EMAIL_QUEUE and dispatches to the appropriate EmailService method.
 * Only active when RabbitMQ is available (ConditionalOnBean).
 */
@Component
@ConditionalOnBean(RabbitTemplate.class)
@RequiredArgsConstructor
@Slf4j
public class EmailEventConsumer {

    private final EmailService emailService;

    @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE)
    public void handleEmailEvent(EmailEvent event) {
        log.info("Received email event [{}] for: {}, template: {}",
                event.getEventId(), event.getTo(), event.getTemplateName());

        try {
            EmailRequest request = EmailRequest.builder()
                    .to(event.getTo())
                    .subject(event.getSubject())
                    .body(event.getSubject()) // fallback plain text
                    .templateName(event.getTemplateName())
                    .variables(event.getVariables())
                    .build();

            emailService.sendGenericEmail(request);
            log.info("Email event [{}] processed successfully", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process email event [{}]: {}", event.getEventId(), e.getMessage(), e);
        }
    }
}
