package mindustrytool.workflow.nodes;

import mindustry.gen.Player;
import mindustrytool.workflow.WorkflowColor;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;
import mindustrytool.workflow.WorkflowNode;

public class SendChatToPlayerWorkflow extends WorkflowNode {
    private WorkflowField<Player, Void> playerField = new WorkflowField<Player, Void>("player")
            .consume(new FieldConsumer<>(Player.class));

    private WorkflowField messageField = new WorkflowField("message")
            .consume(new FieldConsumer<>(String.class).defaultValue("Hello"));

    public SendChatToPlayerWorkflow() {
        super("SendChatToPlayer", WorkflowGroup.DISPLAY, WorkflowColor.LIME, 1);

        defaultOneOutput();
    }

    @Override
    public void execute(WorkflowEmitEvent event) {
        Player player = playerField.getConsumer().consume(event);
        String message = messageField.getConsumer().asString(event);

        player.sendMessage(message);
    }
}
