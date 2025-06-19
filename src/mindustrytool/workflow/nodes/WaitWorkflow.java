package mindustrytool.workflow.nodes;

import mindustrytool.workflow.WorkflowConsumerUnit;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowNode;

public class WaitWorkflow extends WorkflowNode {
    private WorkflowConsumer<Long> secondConsumer = new WorkflowConsumer<>("second", Long.class)
            .unit(WorkflowConsumerUnit.SECOND)
            .defaultValue(1000L);

    public WaitWorkflow() {
        super("Wait", "time", WorkflowColors.CYAN, 1);

        defaultOneOutput();
    }

    @Override
    public void execute(WorkflowEmitEvent event) {
        event.getContext().schedule(() -> {
            event.next();
        }, secondConsumer.asLong());
    }

}
