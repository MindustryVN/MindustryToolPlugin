package mindustrytool.workflow;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import arc.struct.ObjectMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import mindustry.game.EventType;
import mindustry.gen.Player;

@Data
@Accessors(chain = true)
public abstract class WorkflowNode {
    private static final int MAX_STEP = 500;

    private int id;
    private int x;
    private int y;

    private final String name;
    private final String group;
    private final String color;

    private List<WorkflowConsumer<?>> consumers = new ArrayList<>();
    private List<WorkflowProducer> producers = new ArrayList<>();
    private List<WorkflowOutput> outputs = List.of(new WorkflowOutput("Next", "None", 0));

    private final List<WorkflowParameter<?>> parameters = new ArrayList<>();

    public static final ObjectMap<Integer, WorkflowNode> nodes = new ObjectMap<>();
    public static final ObjectMap<String, WorkflowNode> nodeTypes = new ObjectMap<>();

    public WorkflowNode(String name, String group, String color) {
        this.name = name;
        this.group = group;
        this.color = color;

        nodeTypes.put(name, this);
    }

    public void init() {
        nodes.put(id, this);
    }

    public void unload() {
        nodes.remove(id);
    }

    public static void clear() {
        nodes.clear();
    }

    @SuppressWarnings("unchecked")
    public <T> T parameter(Class<T> clazz, String name) {
        return parameters.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst().map(p -> (T) p.getValue())
                .orElseThrow(() -> new IllegalArgumentException("Parameter not found: " + name));
    }

    public abstract int execute(WorkflowEmitEvent event);

    public void next(WorkflowEmitEvent event) {
        if (event.getStep() > MAX_STEP) {
            throw new StackOverflowError("Max stack exceeded");
        }

        var nextId = execute(event);

        if (nextId == -1) {
            return;
        }

        var nextNode = nodes.get(nextId);

        if (nextNode == null) {
            throw new IllegalStateException("Node not found, id: " + nextId);
        }

        nextNode.next(event.next(id));
    }

    @Data
    @AllArgsConstructor
    public class WorkflowParameterOption {
        private final String label;
        private final String value;
    }

    @Data
    @AllArgsConstructor
    public class WorkflowParameter<T> {
        private final String name;
        private final Class<T> type;
        private final boolean required;
        private final List<WorkflowParameterOption> options = new ArrayList<>();
        private T value;

        {
            parameters.add(this);
        }

        public WorkflowParameter<T> option(String name, String value) {
            options.add(new WorkflowParameterOption(name, value));
            return this;
        }
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
    public class WorkflowConsumer<T> {
        private final String name;
        private final Class<T> type;
        private String value;

        {
            consumers.add(this);
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

    public class EventListenerWorkflow extends WorkflowNode {
        private WorkflowParameter<Boolean> beforeParameter = new WorkflowParameter<>("before", Boolean.class, true,
                true);
        private WorkflowParameter<String> classParameter = new WorkflowParameter<>("class", String.class, true, "");

        private WorkflowProducer eventProducer = new WorkflowProducer("event",
                () -> Class.forName(classParameter.value));

        {
            preInit();
        }

        private void preInit() {
            var eventType = new EventType();
            for (var clazz : eventType.getClass().getDeclaredClasses()) {
                classParameter.option(clazz.getSimpleName(), clazz.getCanonicalName());
            }
        }

        public EventListenerWorkflow() {
            super("EventListener", "emitter", "#ff33bb");
        }

        @Override
        public void init() {
            Class<?> eventClass;
            try {
                eventClass = Class.forName(classParameter.getValue());

                Workflow.on(eventClass, (event, before) -> {
                    if (before == this.beforeParameter.getValue()) {
                        next(new WorkflowEmitEvent(0, this.getId()).putValue(eventProducer.name, event));
                    }
                });

            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Invalid class: " + classParameter.getValue());
            }
        }

        @Override
        public int execute(WorkflowEmitEvent event) {
            return outputs.get(0).getNextId();
        }
    }

    public class SendChatWorkflow extends WorkflowNode {
        private WorkflowConsumer<Player> playerConsumer = new WorkflowConsumer<>("player", Player.class, null);
        private WorkflowParameter<String> messageParameter = new WorkflowParameter<>("message", String.class, true,
                null);

        public SendChatWorkflow() {
            super("SendChat", "action", "#33ff44");
        }

        @Override
        public int execute(WorkflowEmitEvent event) {
            Player player = playerConsumer.consume(event);
            String message = messageParameter.getValue();

            player.sendMessage(message);

            return -1;
        }
    }

    public static void load(List<LoadNodeData> load) {
        nodes.values().forEach(node -> node.unload());
        nodes.clear();
        
        for (var data : load) {
            var node = WorkflowNode.nodeTypes.get(data.getName());

            if (node == null) {
                throw new IllegalArgumentException("Node type not found: " + data.getName());
            }

            var constructors = node.getClass().getConstructors();

            if (constructors == null || constructors.length == 0) {
                throw new IllegalStateException("No constructor for node: " + node.getClass().getSimpleName());
            }

            try {
                var newNode = (WorkflowNode) constructors[0].newInstance();

                newNode.setId(data.getId())
                        .setX(data.getX())
                        .setY(data.getY());

                data.getOutputs().forEach(output -> {
                    var newOutput = newNode.getOutputs()
                            .stream()
                            .filter(nn -> nn.getName().equals(output.getName()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "Node output not found: " + output.getName() + " on node: " + node.getName()));

                    newOutput.setNextId(output.getNextId());
                });

                data.getParameters().forEach(parameter -> {
                    var newOutput = newNode.getParameters()
                            .stream()
                            .filter(nn -> nn.getName().equals(parameter.getName()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "Node parameter not found: " + parameter.getName() + " on node: "
                                            + node.getName()));

                    ((WorkflowParameter<Object>) newOutput).setValue((parameter.getValue()));
                });

                data.getConsumers().forEach(consumer -> {
                    var newOutput = newNode.getConsumers()
                            .stream()
                            .filter(nn -> nn.getName().equals(consumer.getName()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "Node consumer not found: " + consumer.getName() + " on node: "
                                            + node.getName()));

                    newOutput.setValue(consumer.getValue());
                });

                newNode.init();

            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                throw new IllegalStateException("Can not create new node: " + node.getClass().getSimpleName(), e);
            }
        }
    }
}
