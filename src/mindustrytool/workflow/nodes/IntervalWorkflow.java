package mindustrytool.workflow.nodes;

import mindustrytool.workflow.Workflow;
import mindustrytool.workflow.WorkflowConsumerUnit;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowNode;

public class IntervalWorkflow extends WorkflowNode {
    private enum IntervalType {
        FIXED_RATE, DELAY
    }

    private WorkflowConsumer<Long> delayConsumer = new WorkflowConsumer<>("delay", Long.class)
            .unit(WorkflowConsumerUnit.SECOND)
            .defaultValue(1000L);

    private WorkflowConsumer<Long> intervalConsumer = new WorkflowConsumer<>("interval", Long.class)
            .unit(WorkflowConsumerUnit.SECOND)
            .defaultValue(1000L);

    private WorkflowConsumer<IntervalType> typeConsumer = new WorkflowConsumer<>("type", IntervalType.class)
            .options(IntervalType.class)
            .defaultValue(IntervalType.DELAY);

    public IntervalWorkflow() {
        super("Interval", "emitter", "#ff33bb", 0);

        defaultOneOutput();
    }

    @Override
    public void init(Workflow context) {
        if (typeConsumer.asEnum() == IntervalType.FIXED_RATE) {
            context.scheduleAtFixedRate(() -> {
                WorkflowEmitEvent.create(this, context).next();
            }, 0, intervalConsumer.asLong());
            return;
        }

        context.scheduleWithFixedDelay(() -> {
            WorkflowEmitEvent.create(this, context).next();
        }, delayConsumer.asLong(), intervalConsumer.asLong());
    }
}
