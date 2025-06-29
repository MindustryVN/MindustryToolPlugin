package mindustrytool.workflow.nodes;

import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustrytool.workflow.WorkflowEmitEvent;
import mindustrytool.workflow.WorkflowGroup;

public class SendChatWorkflow extends WorkflowNode {
    private final WorkflowField<Player, Void> playerField = new WorkflowField<Player, Void>("player")
            .consume(new FieldConsumer<>(Player.class).notRequired());

    private final WorkflowField messageField = new WorkflowField("message")
            .consume(new FieldConsumer<>(String.class)
                    .defaultValue("Hello"));

    public SendChatWorkflow() {
        super("SendChat", WorkflowGroup.DISPLAY, 1);

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
