package mindustrytool.workflow;

import lombok.RequiredArgsConstructor;
import lombok.ToString;

@ToString
@RequiredArgsConstructor
public class WorkflowEvent {
    private final String nodeId;
    private final String name;
    private final Object value;
}
