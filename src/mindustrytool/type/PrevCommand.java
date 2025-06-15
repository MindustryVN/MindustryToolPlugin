package mindustrytool.type;

import java.util.function.Consumer;

import arc.util.CommandHandler.CommandResponse;

public record PrevCommand(String command, Consumer<CommandResponse> callback) {
    
}
