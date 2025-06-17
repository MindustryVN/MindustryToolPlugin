package mindustrytool.workflow.nodes;

import mindustry.game.EventType;
import mindustrytool.workflow.Workflow;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowNode;

public class EventListenerWorkflow extends WorkflowNode {
    private WorkflowParameter<Boolean> beforeParameter = new WorkflowParameter<>("before", Boolean.class, true,
            true);
    private WorkflowParameter<String> classParameter = new WorkflowParameter<>("class", String.class, true, "");

    private WorkflowProducer eventProducer = new WorkflowProducer("event",
            () -> Class.forName(classParameter.getValue()));

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
                    next(new WorkflowEmitEvent(0, this.getId()).putValue(eventProducer.getName(), event));
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
