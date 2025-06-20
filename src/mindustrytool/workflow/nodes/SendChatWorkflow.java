package mindustrytool.workflow.nodes;

import mindustry.gen.Call;
import mindustrytool.workflow.WorkflowColor;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;
import mindustrytool.workflow.WorkflowNode;

public class SendChatWorkflow extends WorkflowNode {
    private WorkflowConsumer<String> messageConsumer = new WorkflowConsumer<>("message", String.class)
            .defaultValue("Hello");

    public SendChatWorkflow() {
        super("SendChat", WorkflowGroup.ACTION, WorkflowColor.LIME, 1);
    }

    @Override
    public void execute(WorkflowEmitEvent event) {
        String message = messageConsumer.asString(event);

        Call.sendMessage(message);
    }
}
