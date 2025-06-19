package mindustrytool.workflow.nodes;

import mindustry.gen.Player;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowNode;

public class SendChatToPlayerWorkflow extends WorkflowNode {
    private WorkflowConsumer<Player> playerConsumer = new WorkflowConsumer<>("player", Player.class);
    private WorkflowConsumer<String> messageConsumer = new WorkflowConsumer<>("message", String.class);

    public SendChatToPlayerWorkflow() {
        super("SendChatToPlayer", "action", "#33ff44", 1);
    }

    @Override
    public String execute(WorkflowEmitEvent event) {
        Player player = playerConsumer.consume(event);
        String message = messageConsumer.getValue();

        player.sendMessage(message);

        return null;
    }
}
