package mindustrytool.workflow.nodes;

import mindustry.gen.Call;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowNode;

public class SendChatWorkflow extends WorkflowNode {
    private WorkflowConsumer<String> messageConsumer = new WorkflowConsumer<>("message", String.class);

    public SendChatWorkflow() {
        super("SendChat", "action", WorkflowColors.LIME, 1);
    }

    @Override
    public void execute(WorkflowEmitEvent event) {
        String message = messageConsumer.getValue();

        Call.sendMessage(message);
    }
}
