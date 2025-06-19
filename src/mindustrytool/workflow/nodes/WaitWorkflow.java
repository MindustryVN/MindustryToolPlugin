package mindustrytool.workflow.nodes;

import mindustrytool.workflow.Workflow;
import mindustrytool.workflow.WorkflowConsumerUnit;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowNode;

public class WaitWorkflow extends WorkflowNode {
    private WorkflowConsumer<Long> secondConsumer = new WorkflowConsumer<>("second", Long.class)
            .unit(WorkflowConsumerUnit.SECOND)
            .defaultValue(1000L);

    public WaitWorkflow() {
        super("Wait", "time", "#aa33bb", 1);

        defaultOneOutput();
    }

    @Override
    public void init(Workflow context) {
        context.schedule(() -> {
            next(WorkflowEmitEvent.create(this, context));
        }, secondConsumer.asLong());
    }

    @Override
    public String execute(WorkflowEmitEvent event) {
        return outputs.get(0).getNextId();
    }

}
