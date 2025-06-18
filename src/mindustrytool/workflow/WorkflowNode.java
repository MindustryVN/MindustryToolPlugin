package mindustrytool.workflow;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public abstract class WorkflowNode {
    private static final int MAX_STEP = 500;

    private String id;
    private int x;
    private int y;

    protected final String name;
    protected final String group;
    protected final String color;

    protected List<WorkflowConsumer<?>> consumers = new ArrayList<>();
    protected List<WorkflowProducer> producers = new ArrayList<>();
    protected List<WorkflowOutput> outputs = List.of(new WorkflowOutput("Next", "None", null));

    public void init(Workflow context) {
    }

    public void unload(Workflow context) {
        // Unload logic if needed
    }

    public WorkflowNode(String name, String group, String color) {
        this.name = name;
        this.group = group;
        this.color = color;
    }

    public abstract String execute(WorkflowEmitEvent event);

    public void next(WorkflowEmitEvent event) {
        if (event.getStep() > MAX_STEP) {
            throw new StackOverflowError("Max stack exceeded");
        }

        var nextId = execute(event);

        if (nextId == null) {
            return;
        }

        var nextNode = event.getContext().getNodes().get(nextId);

        if (nextNode == null) {
            throw new IllegalStateException("Node not found, id: " + nextId);
        }

        nextNode.next(event.next(id));
    }

    @Data
    @AllArgsConstructor
    public class WorkflowConsumerOption {
        private final String label;
        private final String value;
    }

    @Data
    @AllArgsConstructor
    public class WorkflowProducer {
        private final String name;
        private final ESupplier<Class<?>> type;

        {
            producers.add(this);
        }

    }

    @Data
    @AllArgsConstructor
    public class WorkflowOutput {
        private final String name;
        private final String description;
        private String nextId;
    }

    @Data
    @RequiredArgsConstructor
    public class WorkflowConsumer<T> {
        private final String name;
        private final Class<T> type;
        private final List<WorkflowConsumerOption> options = new ArrayList<>();

        private boolean required = true;
        private String value;
        private T defaultValue;

        public WorkflowConsumer<T> notRequired() {
            this.required = false;
            return this;
        }

        public Boolean asBoolean() {
            return Boolean.parseBoolean(value);
        }

        public WorkflowConsumer<T> defaultValue(T value) {
            this.defaultValue = value;
            return this;
        }

        {
            consumers.add(this);
        }

        public WorkflowConsumer<T> option(String name, String value) {
            options.add(new WorkflowConsumerOption(name, value));
            return this;
        }

        public T consume(WorkflowEmitEvent event) {
            var fields = value.split(".");

            if (fields.length == 0) {
                return (T) event.getValues().get(fields[0]);
            }

            Object result = event.getValues().get(fields[0]);

            for (int index = 1; index < fields.length; index++) {
                try {
                    result = result.getClass().getDeclaredField(fields[index]).get(result);
                } catch (IllegalArgumentException //
                        | IllegalAccessException //
                        | NoSuchFieldException
                        | SecurityException e//
                ) {
                    throw new IllegalStateException(
                            "Field not found: " + fields[index] + " of " + value + " on value " + result, e);
                }
            }

            return (T) result;
        }
    }
}
