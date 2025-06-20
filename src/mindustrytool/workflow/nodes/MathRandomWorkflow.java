package mindustrytool.workflow.nodes;

import mindustrytool.workflow.WorkflowColor;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;
import mindustrytool.workflow.WorkflowNode;

public class MathRandomWorkflow extends WorkflowNode {
    private final WorkflowProducer<Double> numberProducer = new WorkflowProducer<>("number",
            (context) -> Math.random());

    public MathRandomWorkflow() {
        super("MathRandom", WorkflowGroup.MATH, WorkflowColor.PURPLE, 1);

        defaultOneOutput();
    }

    @Override
    public void execute(WorkflowEmitEvent event) {

        event.getValues().put(numberProducer.getVariableName(), numberProducer.getProduce().apply(event));

        event.next();
    }

}
