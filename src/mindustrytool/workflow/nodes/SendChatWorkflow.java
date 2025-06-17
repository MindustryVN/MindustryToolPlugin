package mindustrytool.workflow.nodes;

import mindustry.gen.Player;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowNode;

public class SendChatWorkflow extends WorkflowNode {
    private WorkflowConsumer<Player> playerConsumer = new WorkflowConsumer<>("player", Player.class, null);
    private WorkflowParameter<String> messageParameter = new WorkflowParameter<>("message", String.class, true,
            null);

    public SendChatWorkflow() {
        super("SendChat", "action", "#33ff44");
    }

    @Override
    public int execute(WorkflowEmitEvent event) {
        Player player = playerConsumer.consume(event);
        String message = messageParameter.getValue();

        player.sendMessage(message);

        return -1;
    }
}
