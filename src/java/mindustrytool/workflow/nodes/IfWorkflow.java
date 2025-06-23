package mindustrytool.workflow.nodes;

import mindustrytool.workflow.WorkflowColor;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;
import mindustrytool.workflow.WorkflowNode;

public class IfWorkflow extends WorkflowNode {

    private final WorkflowOutput trueOutput = new WorkflowOutput("True", "True");
    private final WorkflowOutput falseOutput = new WorkflowOutput("False", "False");

    private WorkflowField<String, Void> conditionField = new WorkflowField<String, Void>("condition")
            .consume(new FieldConsumer<>(String.class));

    public IfWorkflow() {
        super("if", WorkflowGroup.FLOW, WorkflowColor.LIME, 1);
    }

    @Override
    public void execute(WorkflowEmitEvent event) {
        Boolean condition = event.getContext()
                .getExpressionParser()
                .evaluateAsBoolean(conditionField.getConsumer().getValue(), event.getVariables());

        if (condition) {
            event.next(trueOutput.getNextId());
        } else {
            event.next(falseOutput.getNextId());
        }
    }
}
