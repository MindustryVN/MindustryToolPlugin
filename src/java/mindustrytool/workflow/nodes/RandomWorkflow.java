package mindustrytool.workflow.nodes;

import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;

public class RandomWorkflow extends WorkflowNode {
    private final WorkflowField<Void, Double> numberField = new WorkflowField<Void, Double>("number")
            .produce(new FieldProducer<Double>("number", Double.class));

    public RandomWorkflow() {
        super("Random", WorkflowGroup.OPERATION, 1);

        defaultOneOutput();
    }

    @Override
    public void execute(WorkflowEmitEvent event) {
        event.putValue(numberField.getProducer().getVariableName(), Math.random());

        event.next();
    }

}
