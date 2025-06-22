package mindustrytool.workflow;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import arc.util.Log;
import lombok.Getter;
import lombok.ToString;
import mindustry.gen.Groups;

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

        Workflow.sendWorkflowEvent(new WorkflowEvent(current.getId(), "SET", Map.of(name, value)));

        return this;
    }

    private WorkflowEmitEvent(int step, WorkflowNode currentNode, Workflow context, Map<String, Object> values) {
        this.step = step;
        this.current = currentNode;
        this.context = context;
        this.values = values;

        putDefaultValues(values);

        Log.debug("step: " + step + " current: " + current.getName() + ":" + currentNode.getId() + " values: "
                + values);
    }

    public static void putDefaultValues(Map<String, Object> values) {

        values.put("@time", System.currentTimeMillis());
        values.put("@step", 0);

        Calendar calendar = Calendar.getInstance();

        values.put("@seconds", calendar.get(Calendar.SECOND));
        values.put("@minutes", calendar.get(Calendar.MINUTE));
        values.put("@hours", calendar.get(Calendar.HOUR));
        values.put("@day", calendar.get(Calendar.DAY_OF_MONTH));
        values.put("@month", calendar.get(Calendar.MONTH));
        values.put("@year", calendar.get(Calendar.YEAR));

        values.put("@datetime", new Date().toString());
        values.put("@players", Groups.player);

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
        Workflow.sendWorkflowEvent(new WorkflowEvent(nextNode.getId(), "EMIT", null));
    }

    public static WorkflowEmitEvent create(WorkflowNode current, Workflow context) {
        Workflow.sendWorkflowEvent(new WorkflowEvent(current.getId(), "EMIT", null));

        return new WorkflowEmitEvent(0, current, context, new HashMap<>());
    }
}
