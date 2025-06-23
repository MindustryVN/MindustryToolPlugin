package mindustrytool.workflow.nodes;

import mindustrytool.workflow.WorkflowColor;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;
import mindustrytool.workflow.WorkflowNode;
import mindustrytool.workflow.expressions.UnaryOperator;

public class UnaryOperationWorkflow extends WorkflowNode {
    private final WorkflowField<Void, Double> aField = new WorkflowField<Void, Double>("a");

    private final WorkflowField<Void, Double> resultField = new WorkflowField<Void, Double>("b")
            .produce(new FieldProducer<Double>("result", Double.class));

    private final UnaryOperator operator;

    public UnaryOperationWorkflow(UnaryOperator operator) {
        super(operator.getName(), WorkflowGroup.OPERATION, WorkflowColor.PURPLE, 2);
        this.operator = operator;

        defaultOneOutput();
    }

    @Override
    public void execute(WorkflowEmitEvent event) {
        Double a = aField.getConsumer().asDouble(event);

        event.putValue(resultField.getProducer().getVariableName(), operator.getFunction().apply(a));

        event.next();
    }
}
