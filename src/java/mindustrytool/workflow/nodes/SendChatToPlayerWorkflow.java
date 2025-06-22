package mindustrytool.workflow.nodes;

import mindustry.gen.Player;
import mindustrytool.workflow.WorkflowColor;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;
import mindustrytool.workflow.WorkflowNode;

public class SendChatToPlayerWorkflow extends WorkflowNode {
    private WorkflowField<Player, Object> playerField = new WorkflowField<>("player", Player.class)
            .consume(new FieldConsumer<>(Player.class));
    
    private WorkflowField messageField = new WorkflowField<>("message", String.class)
            .consume(new FieldConsumer<>(String.class).defaultValue("Hello"));

    public SendChatToPlayerWorkflow() {
        super("SendChatToPlayer", WorkflowGroup.ACTION, WorkflowColor.LIME, 1);
    }

    @Override
    public void execute(WorkflowEmitEvent event) {
        Player player = playerField.getConsumer().consume(event);
        String message = messageField.getConsumer().asString(event);

        player.sendMessage(message);
    }
}
