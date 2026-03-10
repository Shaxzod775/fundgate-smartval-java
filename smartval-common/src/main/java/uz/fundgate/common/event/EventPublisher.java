package uz.fundgate.common.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import uz.fundgate.common.config.RabbitMQConfig;

@Component
@ConditionalOnBean(RabbitTemplate.class)
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(String routingKey, Object event) {
        log.debug("Publishing event: routingKey={}, event={}", routingKey, event.getClass().getSimpleName());
        rabbitTemplate.convertAndSend(RabbitMQConfig.FUNDGATE_EXCHANGE, routingKey, event);
    }
}
