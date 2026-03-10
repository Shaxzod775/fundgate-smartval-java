package uz.fundgate.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EmailEvent extends BaseEvent {

    private String to;
    private String subject;
    private String templateName;
    private Map<String, Object> variables;

    @Builder.Default
    private Instant timestamp = Instant.now();

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
}
