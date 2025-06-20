package mindustrytool.workflow.nodes;

import mindustry.game.EventType;
import mindustrytool.workflow.Workflow;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowNode;

public class EventListenerWorkflow extends WorkflowNode {
    private WorkflowConsumer<Boolean> beforeConsumer = new WorkflowConsumer<>("before", Boolean.class)
            .defaultValue(true);

    private WorkflowConsumer<String> classConsumer = new WorkflowConsumer<>("class", String.class);

    {
        preInit();
    }

    private void preInit() {
        for (var clazz : EventType.class.getDeclaredClasses()) {
            classConsumer.option(clazz.getSimpleName(), clazz.getName(), clazz);
        }
    }

    public EventListenerWorkflow() {
        super("EventListener", "emitter", WorkflowColors.CYAN, 0);

        defaultOneOutput();
    }

    @Override
    public void init(Workflow context) {
        Class<?> eventClass = classConsumer.asClass();

        context.on(eventClass, (event, before) -> {
            if (before == this.beforeConsumer.asBoolean()) {
                WorkflowEmitEvent.create(this, context)
                        .putValue(classConsumer.getProduce().getVariableName(), event)
                        .next();
            }
        });
    }
}
