package mindustrytool;

import org.pf4j.Plugin;

import mindustrytool.utils.HudUtils;
import mindustrytool.utils.Session;

public class Main extends Plugin {
    public Main() {
    }

    @Override
    public void stop() {
        Config.BACKGROUND_TASK_EXECUTOR.shutdown();
        Config.BACKGROUND_SCHEDULER.shutdown();
        ServerController.eventHandler.unload();
        ServerController.httpServer.unload();
        HudUtils.unload();

        Session.clear();
    }
}
