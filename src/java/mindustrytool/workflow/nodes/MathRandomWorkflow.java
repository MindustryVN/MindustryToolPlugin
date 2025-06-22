package mindustrytool.workflow.nodes;

import mindustrytool.workflow.WorkflowColor;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;
import mindustrytool.workflow.WorkflowNode;

public class MathRandomWorkflow extends WorkflowNode {
    private final WorkflowField<Void, Double> numberProducer = new WorkflowField<Void, Double>("number")
            .produce(new FieldProducer<Double>("number", Double.class));

    public MathRandomWorkflow() {
        super("MathRandom", WorkflowGroup.MATH, WorkflowColor.PURPLE, 1);

        defaultOneOutput();
    }

    @Override
    public void execute(WorkflowEmitEvent event) {
        event.getValues().put(numberProducer.getProducer().getVariableName(), Math.random());

        event.next();
    }

}
