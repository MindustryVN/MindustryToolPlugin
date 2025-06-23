package mindustrytool.workflow;

import java.util.Map;
import java.util.UUID;

import lombok.Data;

@Data
public class WorkflowEvent<T extends Map> {
    private String id = UUID.randomUUID().toString();
    private String nodeId;
    private String name;
    private T value;
    private Long createdAt = System.currentTimeMillis();

    public WorkflowEvent(String nodeId, String name, T value) {
        this.nodeId = nodeId;
        this.name = name;
        this.value = value;
    }
}
