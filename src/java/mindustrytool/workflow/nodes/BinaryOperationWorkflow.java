package mindustrytool.workflow.nodes;

import mindustrytool.workflow.WorkflowColor;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;
import mindustrytool.workflow.WorkflowNode;
import mindustrytool.workflow.expressions.BinaryOperator;

public class BinaryOperationWorkflow extends WorkflowNode {
    private final WorkflowField<Double, Void> aField = new WorkflowField<Double, Void>("a")
            .setConsumer(new FieldConsumer<>(Double.class).defaultValue(0d));

    private final WorkflowField<Double, Void> bField = new WorkflowField<Double, Void>("b")
            .setConsumer(new FieldConsumer<>(Double.class).defaultValue(0d));

    private final WorkflowField<Void, Double> resultField = new WorkflowField<Void, Double>("result")
            .produce(new FieldProducer<Double>("result", Double.class));

    private final BinaryOperator operator;

    public BinaryOperationWorkflow(BinaryOperator operator) {
        super(operator.getName(), WorkflowGroup.OPERATION, WorkflowColor.PURPLE, 2);
        this.operator = operator;

        defaultOneOutput();
    }

    @Override
    public void execute(WorkflowEmitEvent event) {
        Double a = aField.getConsumer().asDouble(event);
        Double b = bField.getConsumer().asDouble(event);

        event.putValue(resultField.getProducer().getVariableName(), operator.getFunction().apply(a, b));

        event.next();
    }
}
