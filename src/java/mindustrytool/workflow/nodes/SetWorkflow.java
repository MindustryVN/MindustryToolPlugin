package mindustrytool.workflow.nodes;

import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;

public class SetWorkflow extends WorkflowNode {

    private WorkflowField nameField = new WorkflowField("name")
            .consume(new FieldConsumer<>(String.class)
                    .defaultValue("a"));

    private WorkflowField valueField = new WorkflowField("value")
            .consume(new FieldConsumer<>(Object.class)
                    .defaultValue("0"));

    public SetWorkflow() {
        super("Set", WorkflowGroup.BASE, 1);

        defaultOneOutput();
    }

    @Override
    public void execute(WorkflowEmitEvent event) {
        event.putValue(nameField.getConsumer().asString(event), valueField.getConsumer().consume(event));

        event.next();
    }
}
