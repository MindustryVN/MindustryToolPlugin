package mindustrytool.workflow.nodes;

import mindustrytool.workflow.Workflow;
import mindustrytool.workflow.WorkflowUnit;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;

public class IntervalWorkflow extends WorkflowNode {
    private enum IntervalType {
        FIXED_RATE, DELAY
    }

    private WorkflowField<Long, Void> delayField = new WorkflowField<Long, Void>("delay")
            .consume(new FieldConsumer<>(Long.class)
                    .unit(WorkflowUnit.SECOND)
                    .defaultValue(0L));

    private WorkflowField<Long, Void> intervalField = new WorkflowField<Long, Void>("interval")
            .consume(new FieldConsumer<>(Long.class)
                    .unit(WorkflowUnit.SECOND)
                    .defaultValue(5L));

    private WorkflowField<IntervalType, Void> typeField = new WorkflowField<IntervalType, Void>("type")
            .consume(new FieldConsumer<>(IntervalType.class)
                    .options(IntervalType.class)
                    .defaultValue(IntervalType.DELAY));

    public IntervalWorkflow() {
        super("Interval", WorkflowGroup.EMITTER, 0);

        defaultOneOutput();
    }

    @Override
    public void init(Workflow context) {
        if (typeField.getConsumer().asEnum() == IntervalType.FIXED_RATE) {
            context.scheduleAtFixedRate(() -> {
                WorkflowEmitEvent.create(this, context).next();
            }, delayField.getConsumer().asLong(), intervalField.getConsumer().asLong());
        } else {
            context.scheduleWithFixedDelay(() -> {
                WorkflowEmitEvent.create(this, context).next();
            }, delayField.getConsumer().asLong(), intervalField.getConsumer().asLong());
        }
    }
}
