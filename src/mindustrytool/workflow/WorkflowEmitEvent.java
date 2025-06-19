package mindustrytool.workflow;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class WorkflowEmitEvent {
    private final int step;
    private final String fromId;

    private final Workflow context;
    private final Map<String, Object> values = new HashMap<>();

    public WorkflowEmitEvent putValue(String name, Object value) {
        values.put(name, value);
        return this;
    }

    private WorkflowEmitEvent(int step, String fromId, Workflow context) {
        this.step = step;
        this.fromId = fromId;
        this.context = context;
    }

    public static WorkflowEmitEvent create(WorkflowNode node, Workflow context) {
        return new WorkflowEmitEvent(0, node.getId(), context);
    }

    public WorkflowEmitEvent next(String fromId) {
        return new WorkflowEmitEvent(step + 1, fromId, context);
    }
}
