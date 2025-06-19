package mindustrytool.workflow.nodes;

import mindustry.game.EventType;
import mindustrytool.workflow.Workflow;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowNode;

public class EventListenerWorkflow extends WorkflowNode {
    private WorkflowConsumer<Boolean> beforeConsumer = new WorkflowConsumer<>("before", Boolean.class)
            .defaultValue(true);

    private WorkflowConsumer<String> classConsumer = new WorkflowConsumer<>("class", String.class);

    private WorkflowProducer eventProducer = new WorkflowProducer("event",
            () -> Class.forName(classConsumer.getValue()));

    {
        preInit();
    }

    private void preInit() {
        var eventType = new EventType();
        for (var clazz : eventType.getClass().getDeclaredClasses()) {
            classConsumer.option(clazz.getSimpleName(), clazz.getCanonicalName());
        }
    }

    public EventListenerWorkflow() {
        super("EventListener", "emitter", "#ff33bb", 0);

        defaultOneOutput();
    }

    @Override
    public void init(Workflow context) {
        Class<?> eventClass;
        try {
            eventClass = Class.forName(classConsumer.getValue());

            context.on(eventClass, (event, before) -> {
                if (before == this.beforeConsumer.asBoolean()) {
                    next(new WorkflowEmitEvent(0, this.getId(), context)
                            .putValue(eventProducer.getName(), event));
                }
            });

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Invalid class: " + classConsumer.getValue());
        }
    }

    @Override
    public String execute(WorkflowEmitEvent event) {
        return outputs.get(0).getNextId();
    }
}
