package mindustrytool.workflow;

import java.util.HashMap;
import java.util.Map;

import arc.util.Log;
import lombok.Data;

@Data
public class WorkflowEmitEvent {
    private final int step;
    private final WorkflowNode current;
    private static final int MAX_STEP = 5000;

    private final Workflow context;
    private final Map<String, Object> values = new HashMap<>();

    public WorkflowEmitEvent putValue(String name, Object value) {
        values.put(name, value);
        return this;
    }

    private WorkflowEmitEvent(int step, WorkflowNode currentNode, Workflow context) {
        this.step = step;
        this.current = currentNode;
        this.context = context;
    }

    public void next() {
        next(current.nextId());
    }

    public void next(String nextId) {
        if (step > MAX_STEP) {
            throw new StackOverflowError("Max stack exceeded");
        }

        if (nextId == null) {
            Log.debug("No next node to execute, step: %s, current: %s", step, current.getId());
            return;
        }

        var nextNode = context.getNodes().get(nextId);

        if (nextNode == null) {
            throw new IllegalStateException("Node not found, id: " + nextId);
        }

        nextNode.execute(new WorkflowEmitEvent(step + 1, nextNode, context));
    }

    public static WorkflowEmitEvent create(WorkflowNode current, Workflow context) {
        return new WorkflowEmitEvent(0, current, context);
    }
}
