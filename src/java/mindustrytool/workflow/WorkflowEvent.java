package mindustrytool.workflow;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.ToString;

@ToString
@RequiredArgsConstructor
public class WorkflowEvent {
    private final String id = UUID.randomUUID().toString();
    private final String nodeId;
    private final String name;
    private final Object value;

    private final Long createdAt = System.currentTimeMillis();
}
