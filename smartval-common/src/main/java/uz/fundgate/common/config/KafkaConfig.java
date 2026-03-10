package uz.fundgate.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaConfig {

    public static final String ANALYSIS_EVENTS_TOPIC = "fundgate.analysis.events";
    public static final String SUBMISSION_EVENTS_TOPIC = "fundgate.submission.events";
    public static final String AUDIT_EVENTS_TOPIC = "fundgate.audit.events";

    @Bean
    public NewTopic analysisEventsTopic() {
        return TopicBuilder.name(ANALYSIS_EVENTS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic submissionEventsTopic() {
        return TopicBuilder.name(SUBMISSION_EVENTS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic auditEventsTopic() {
        return TopicBuilder.name(AUDIT_EVENTS_TOPIC).partitions(3).replicas(1).build();
    }
}
