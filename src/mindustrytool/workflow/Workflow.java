package mindustrytool.workflow;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import arc.func.Cons;
import arc.func.Cons2;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import lombok.Getter;
import mindustrytool.type.WorkflowContext;
import mindustrytool.utils.JsonUtils;
import mindustrytool.workflow.errors.WorkflowError;
import mindustrytool.workflow.nodes.EventListenerWorkflow;
import mindustrytool.workflow.nodes.SendChatWorkflow;

public class Workflow {
    private final ObjectMap<Object, Seq<Cons2<?, Boolean>>> events = new ObjectMap<>();

    @Getter
    private final ObjectMap<String, WorkflowNode> nodeTypes = new ObjectMap<>();
    @Getter
    private final ObjectMap<Integer, WorkflowNode> nodes = new ObjectMap<>();

    private static final String WORKFLOW_PATH = "workflow/workflow.json";

    @Getter
    public WorkflowContext context;

    public void init() {
        add(new EventListenerWorkflow());
        add(new SendChatWorkflow());

        loadWorkflowFromFile();
    }

    private void loadWorkflowFromFile() {
        try {
            String content = Files.readString(Path.of(WORKFLOW_PATH));
            context = JsonUtils.readJsonAsClass(content, WorkflowContext.class);
        } catch (IOException e) {
            context = new WorkflowContext();
            context.setCreatedAt(Instant.now());
            Log.err("Fail to load workflow from file", e);
        }
    }

    private void writeWorkflowToFile() {
        try {
            Files.writeString(Path.of(WORKFLOW_PATH), JsonUtils.toJsonString(context));
        } catch (IOException e) {
            Log.err("Fail to write workflow to file", e);
        }
    }

    private void add(WorkflowNode node) {
        nodeTypes.put(node.getName(), node);
    }

    public void clear() {
        events.clear();
        nodeTypes.clear();
        nodes.clear();
    }

    public void load(WorkflowContext context) {
        this.context = context;
        writeWorkflowToFile();

        nodes.clear();

        for (var data : context.getNodes()) {
            var node = nodeTypes.get(data.getName());

            if (node == null) {
                throw new WorkflowError("Node type not found: " + data.getName());
            }

            var constructors = node.getClass().getConstructors();

            if (constructors == null || constructors.length == 0) {
                throw new WorkflowError("No constructor for node: " + node.getClass().getSimpleName());
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
                            .orElseThrow(() -> new WorkflowError(
                                    "Node output not found: " + output.getName() + " on node: " + node.getName()));

                    newOutput.setNextId(output.getNextId());
                });

                data.getConsumers().forEach(consumer -> {
                    var newOutput = newNode.getConsumers()
                            .stream()
                            .filter(nn -> nn.getName().equals(consumer.getName()))
                            .findFirst()
                            .orElseThrow(() -> new WorkflowError(
                                    "Node consumer not found: " + consumer.getName() + " on node: "
                                            + node.getName()));

                    newOutput.setValue(consumer.getValue());
                });

                nodes.put(newNode.getId(), newNode);

            } catch (InstantiationException | IllegalAccessException | WorkflowError
                    | InvocationTargetException e) {
                throw new WorkflowError("Can not create new node: " + node.getClass().getSimpleName(), e);
            }
        }
    }

    public <T> Cons2<T, Boolean> on(Class<T> type, Cons2<T, Boolean> listener) {
        events.get(type, () -> new Seq<>(Cons.class)).add(listener);

        return listener;
    }

    public <T> boolean remove(Class<T> type, Cons2<T, Boolean> listener) {
        return events.get(type, () -> new Seq<>(Cons.class)).remove(listener);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public <T extends Enum<T>> void fire(Enum<T> type, boolean before) {
        Seq<Cons2<?, Boolean>> listeners = events.get(type);

        if (listeners != null) {
            int len = listeners.size;
            Cons2[] items = listeners.items;
            for (int i = 0; i < len; i++) {
                items[i].get(type, before);
            }
        }
    }

    /** Fires a non-enum event by class. */
    public <T> void fire(T type, boolean before) {
        fire(type.getClass(), type, before);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public <T> void fire(Class<?> ctype, T type, boolean before) {
        Seq<Cons2<?, Boolean>> listeners = events.get(ctype);

        if (listeners != null) {
            int len = listeners.size;
            Cons2[] items = listeners.items;
            for (int i = 0; i < len; i++) {
                items[i].get(type, before);
            }
        }
    }

}
