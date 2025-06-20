package mindustrytool.workflow.nodes;

import mindustry.gen.Player;
import mindustrytool.workflow.WorkflowColor;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;
import mindustrytool.workflow.WorkflowNode;

public class SendChatToPlayerWorkflow extends WorkflowNode {
    private WorkflowConsumer<Player> playerConsumer = new WorkflowConsumer<>("player", Player.class);
    private WorkflowConsumer<String> messageConsumer = new WorkflowConsumer<>("message", String.class)
            .defaultValue("Hello");

    public SendChatToPlayerWorkflow() {
        super("SendChatToPlayer", WorkflowGroup.ACTION, WorkflowColor.LIME, 1);
    }

    @Override
    public void execute(WorkflowEmitEvent event) {
        Player player = playerConsumer.consume(event);
        String message = messageConsumer.asString(event);

        player.sendMessage(message);
    }
}
