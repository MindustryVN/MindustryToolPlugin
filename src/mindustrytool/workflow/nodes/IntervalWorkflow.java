package mindustrytool.workflow.nodes;

import mindustrytool.workflow.Workflow;
import mindustrytool.workflow.WorkflowColor;
import mindustrytool.workflow.WorkflowUnit;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;
import mindustrytool.workflow.WorkflowNode;

public class IntervalWorkflow extends WorkflowNode {
    private enum IntervalType {
        FIXED_RATE, DELAY
    }

    private WorkflowField delayField = new WorkflowField<>("delay", Long.class)
            .consume(new FieldConsumer<>(Long.class)
                    .unit(WorkflowUnit.SECOND)
                    .defaultValue(1000L));

    private WorkflowField intervalField = new WorkflowField<>("interval", Long.class)
            .consume(new FieldConsumer<>(Long.class)
                    .unit(WorkflowUnit.SECOND)
                    .defaultValue(1000L));

    private WorkflowField typeField = new WorkflowField<>("type", IntervalType.class)
            .consume(new FieldConsumer<>(IntervalType.class)
                    .options(IntervalType.class)
                    .defaultValue(IntervalType.DELAY));

    public IntervalWorkflow() {
        super("Interval", WorkflowGroup.EMITTER, WorkflowColor.CYAN, 0);

        defaultOneOutput();
    }

    @Override
    public void init(Workflow context) {
        if (typeField.getConsumer().asEnum() == IntervalType.FIXED_RATE) {
            context.scheduleAtFixedRate(() -> {
                WorkflowEmitEvent.create(this, context).next();
            }, 0, intervalField.getConsumer().asLong());
            return;
        }

        context.scheduleWithFixedDelay(() -> {
            WorkflowEmitEvent.create(this, context).next();
        }, delayField.getConsumer().asLong(), intervalField.getConsumer().asLong());
    }
}
