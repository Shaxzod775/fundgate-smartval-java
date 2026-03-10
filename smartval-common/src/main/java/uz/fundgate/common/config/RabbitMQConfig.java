package uz.fundgate.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean(ConnectionFactory.class)
public class RabbitMQConfig {

    public static final String FUNDGATE_EXCHANGE = "fundgate.exchange";
    public static final String ANALYSIS_QUEUE = "fundgate.analysis.queue";
    public static final String EMAIL_QUEUE = "fundgate.email.queue";
    public static final String SPAM_CHECK_QUEUE = "fundgate.spam.queue";
    public static final String NOTIFICATION_QUEUE = "fundgate.notification.queue";

    @Bean
    public TopicExchange fundgateExchange() {
        return new TopicExchange(FUNDGATE_EXCHANGE);
    }

    @Bean
    public Queue analysisQueue() {
        return new Queue(ANALYSIS_QUEUE);
    }

    @Bean
    public Queue emailQueue() {
        return new Queue(EMAIL_QUEUE);
    }

    @Bean
    public Queue spamCheckQueue() {
        return new Queue(SPAM_CHECK_QUEUE);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue(NOTIFICATION_QUEUE);
    }

    @Bean
    public Binding analysisBinding() {
        return BindingBuilder.bind(analysisQueue()).to(fundgateExchange()).with("analysis.*");
    }

    @Bean
    public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue()).to(fundgateExchange()).with("email.*");
    }

    @Bean
    public Binding spamCheckBinding() {
        return BindingBuilder.bind(spamCheckQueue()).to(fundgateExchange()).with("spam.*");
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue()).to(fundgateExchange()).with("notification.*");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        return template;
    }
}
