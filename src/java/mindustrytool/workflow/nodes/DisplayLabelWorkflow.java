package mindustrytool.workflow.nodes;

import mindustry.gen.Call;
import mindustrytool.workflow.WorkflowColor;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;
import mindustrytool.workflow.WorkflowNode;
import mindustrytool.workflow.WorkflowUnit;

public class DisplayLabelWorkflow extends WorkflowNode {
    private WorkflowField<String, Void> messageField = new WorkflowField<String, Void>("message")
            .consume(new FieldConsumer<>(String.class)
                    .defaultValue("Hello"));

    private WorkflowField<Float, Void> xField = new WorkflowField<Float, Void>("x")
            .consume(new FieldConsumer<>(Float.class)
                    .defaultValue(0f));

    private WorkflowField<Float, Void> yField = new WorkflowField<Float, Void>("y")
            .consume(new FieldConsumer<>(Float.class)
                    .defaultValue(0f));

    private WorkflowField<Float, Void> durationField = new WorkflowField<Float, Void>("duration")
            .consume(new FieldConsumer<>(Float.class)
                    .unit(WorkflowUnit.MILLISECOND)
                    .defaultValue(0f));

    public DisplayLabelWorkflow() {
        super("DisplayLabel", WorkflowGroup.DISPLAY, WorkflowColor.LIME, 1);
    }

    @Override
    public void execute(WorkflowEmitEvent event) {
        String message = messageField.getConsumer().asString(event);
        Float x = xField.getConsumer().asFloat(event);
        Float y = yField.getConsumer().asFloat(event);
        Float duration = durationField.getConsumer().asFloat(event);

        Call.label(message, x, y, duration);
    }
}
