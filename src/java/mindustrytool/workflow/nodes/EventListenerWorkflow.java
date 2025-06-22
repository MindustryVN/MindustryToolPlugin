package mindustrytool.workflow.nodes;

import mindustry.game.EventType;
import mindustrytool.workflow.Workflow;
import mindustrytool.workflow.WorkflowColor;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;
import mindustrytool.workflow.WorkflowNode;

public class EventListenerWorkflow extends WorkflowNode {
    private WorkflowField beforeField = new WorkflowField<>("before", Boolean.class)
            .consume(new FieldConsumer<>(Boolean.class)
                    .defaultValue(true));

    private WorkflowField classField = new WorkflowField<>("class", Class.class)
            .consume(new FieldConsumer<>(Class.class))
            .produce(new FieldProducer("event", Class.class));

    {
        preInit();
    }

    private void preInit() {
        for (var clazz : EventType.class.getDeclaredClasses()) {
            classField.getConsumer().option(clazz.getSimpleName(), clazz.getName(), clazz);
        }
    }

    public EventListenerWorkflow() {
        super("EventListener", WorkflowGroup.EMITTER, WorkflowColor.CYAN, 0);

        defaultOneOutput();
    }

    @Override
    public void init(Workflow context) {
        Class<?> eventClass = classField.getConsumer().asClass();

        context.on(eventClass, (event, before) -> {
            if (before == this.beforeField.getConsumer().asBoolean()) {
                WorkflowEmitEvent.create(this, context)
                        .putValue(classField.getProducer().getVariableName(), event)
                        .next();
            }
        });
    }
}
