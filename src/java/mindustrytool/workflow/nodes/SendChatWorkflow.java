package mindustrytool.workflow.nodes;

import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustrytool.workflow.WorkflowColor;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;
import mindustrytool.workflow.WorkflowNode;

public class SendChatWorkflow extends WorkflowNode {
    private WorkflowField<Player, Void> playerField = new WorkflowField<Player, Void>("player")
            .consume(new FieldConsumer<>(Player.class).notRequired());

    private WorkflowField messageField = new WorkflowField("message")
            .consume(new FieldConsumer<>(String.class)
                    .defaultValue("Hello"));

    public SendChatWorkflow() {
        super("SendChat", WorkflowGroup.DISPLAY, WorkflowColor.LIME, 1);

        defaultOneOutput();
    }

    @Override
    public void execute(WorkflowEmitEvent event) {
        Player player = playerField.getConsumer().consume(event);
        String message = messageField.getConsumer().asString(event);

        if (player == null) {
            Call.sendMessage(message);
        } else {
            player.sendMessage(message);
        }

    }
}
