package mindustrytool.workflow.nodes;

import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowNode;

public class MathRandomWorkflow extends WorkflowNode {
    private final WorkflowProducer<Double> numberProducer = new WorkflowProducer<>("number",
            (context) -> Math.random());

    public MathRandomWorkflow() {
        super("MathRandom", "Math", "#aa33ff", 1);

        defaultOneOutput();
    }

    @Override
    public String execute(WorkflowEmitEvent event) {

        event.getValues().put(numberProducer.getVariableName(), numberProducer.getProduce().apply(event));

        return outputs.get(0).getNextId();
    }

}
