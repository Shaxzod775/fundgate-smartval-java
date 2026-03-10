package uz.fundgate.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AnalysisRequestedEvent extends BaseEvent {

    private String submissionId;
    private String userId;
    private String email;

    @Builder.Default
    private Instant timestamp = Instant.now();

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
}
