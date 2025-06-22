package mindustrytool.workflow.nodes;

import mindustrytool.workflow.WorkflowColor;
import mindustrytool.workflow.WorkflowUnit;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;
import mindustrytool.workflow.WorkflowNode;

public class WaitWorkflow extends WorkflowNode {
    private WorkflowField secondField = new WorkflowField("second")
            .consume(new FieldConsumer<>(Long.class)
                    .unit(WorkflowUnit.SECOND)
                    .defaultValue(1000L));

    public WaitWorkflow() {
        super("Wait", WorkflowGroup.TIME, WorkflowColor.CYAN, 1);

        defaultOneOutput();
    }

    @Override
    public void execute(WorkflowEmitEvent event) {
        event.getContext().schedule(() -> {
            event.next();
        }, secondField.getConsumer().asLong());
    }

}
