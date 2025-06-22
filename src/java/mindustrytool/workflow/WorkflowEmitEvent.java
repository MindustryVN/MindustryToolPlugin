package mindustrytool.workflow;

import java.util.HashMap;
import java.util.Map;

import arc.util.Log;
import lombok.Getter;
import lombok.ToString;

@ToString(exclude = { "context" })
@Getter
public class WorkflowEmitEvent {
    private static final int MAX_STEP = 5000;

    private final int step;
    private final WorkflowNode current;
    private final Map<String, Object> values;
    private final Workflow context;

    public WorkflowEmitEvent putValue(String name, Object value) {
        values.put(name, value);
        Log.debug("Add variable: " + name + ": " + value);
        return this;
    }

    private WorkflowEmitEvent(int step, WorkflowNode currentNode, Workflow context, Map<String, Object> values) {
        this.step = step;
        this.current = currentNode;
        this.context = context;
        this.values = values;

        Log.debug(this);
    }

    public void next() {
        next(current.nextId());
    }

    public void next(String nextId) {
        if (step > MAX_STEP) {
            throw new StackOverflowError("Max stack exceeded");
        }

        if (nextId == null) {
            Log.debug("No next node to execute, step: @, current: @", step, current.getId());
            return;
        }

        var nextNode = context.getNodes().get(nextId);

        if (nextNode == null) {
            throw new IllegalStateException("Node not found, id: " + nextId);
        }

        nextNode.execute(new WorkflowEmitEvent(step + 1, nextNode, context, values));
    }

    public static WorkflowEmitEvent create(WorkflowNode current, Workflow context) {
        return new WorkflowEmitEvent(0, current, context, new HashMap<>());
    }
}
