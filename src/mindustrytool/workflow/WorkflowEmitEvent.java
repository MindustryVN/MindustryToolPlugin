package mindustrytool.workflow;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class WorkflowEmitEvent {
    private final int step;
    private final int fromId;

    private final Workflow context;
    private final Map<String, Object> values = new HashMap<>();

    public WorkflowEmitEvent putValue(String name, Object value) {
        values.put(name, value);
        return this;
    }

    public WorkflowEmitEvent(int step, int fromId, Workflow context) {
        this.step = step;
        this.fromId = fromId;
        this.context = context;
    }

    public WorkflowEmitEvent next(int fromId) {
        return new WorkflowEmitEvent(step + 1, fromId, context);
    }
}
