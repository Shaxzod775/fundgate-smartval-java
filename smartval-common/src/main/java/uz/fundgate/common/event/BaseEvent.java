package uz.fundgate.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent {

    private Instant timestamp = Instant.now();
    private String eventId = UUID.randomUUID().toString();
}
