package mindustrytool;

import org.pf4j.Plugin;

import mindustrytool.handlers.ClientCommandHandler;
import mindustrytool.handlers.ServerCommandHandler;
import mindustrytool.utils.Session;

public class Main extends Plugin {
    public Main() {
    }

    @Override
    public void stop() {
        Config.BACKGROUND_TASK_EXECUTOR.shutdownNow();
        Config.BACKGROUND_SCHEDULER.shutdownNow();

        Session.clear();

        ServerController.eventHandler.unload();
        ServerController.httpServer.unload();

        ClientCommandHandler.unload();
        ServerCommandHandler.unload();
    }
}
