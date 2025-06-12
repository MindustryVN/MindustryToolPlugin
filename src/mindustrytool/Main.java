package mindustrytool;

import org.pf4j.Plugin;

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

        ServerController.apiGateway = null;
        ServerController.serverCommandHandler = null;
        ServerController.clientCommandHandler = null;
        ServerController.apiGateway = null;
        ServerController.httpServer = null;
        ServerController.eventHandler = null;
    }
}
