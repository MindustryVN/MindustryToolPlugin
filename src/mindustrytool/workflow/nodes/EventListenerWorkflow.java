package mindustrytool.workflow.nodes;

import mindustry.game.EventType;
import mindustrytool.workflow.Workflow;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowNode;
import mindustrytool.workflow.errors.WorkflowError;

public class EventListenerWorkflow extends WorkflowNode {
    private WorkflowConsumer<Boolean> beforeConsumer = new WorkflowConsumer<>("before", Boolean.class)
            .defaultValue(true);

    private WorkflowConsumer<String> classConsumer = new WorkflowConsumer<>("class", String.class);

    private WorkflowProducer eventProducer = new WorkflowProducer("event",
            (context) -> {
                try {
                    return Class.forName(classConsumer.getValue());
                } catch (ClassNotFoundException e) {
                    throw new WorkflowError("Invalid class: " + classConsumer.getValue() + " " + e.getMessage(), e);
                }
            });

    {
        preInit();
    }

    private void preInit() {
        for (var clazz : EventType.class.getDeclaredClasses()) {
            classConsumer.option(clazz.getSimpleName(), clazz.getName());
        }
    }

    public EventListenerWorkflow() {
        super("EventListener", "emitter", "#ff33bb", 0);

        defaultOneOutput();
    }

    @Override
    public void init(Workflow context) {
        Class<?> eventClass = classConsumer.asClass();

        context.on(eventClass, (event, before) -> {
            if (before == this.beforeConsumer.asBoolean()) {
                next(new WorkflowEmitEvent(0, this.getId(), context)
                        .putValue(eventProducer.getName(), event));
            }
        });
    }

    @Override
    public String execute(WorkflowEmitEvent event) {
        return outputs.get(0).getNextId();
    }
}
