package mindustrytool.workflow;

import java.util.Map;
import java.util.UUID;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@ToString
@RequiredArgsConstructor
@Data
public class WorkflowEvent<T extends Map> {
    private final String id = UUID.randomUUID().toString();
    private final String nodeId;
    private final String name;
    private final T value;

    private final Long createdAt = System.currentTimeMillis();
}
