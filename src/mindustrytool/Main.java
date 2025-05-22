package mindustrytool;

import org.pf4j.Plugin;

import mindustrytool.utils.HudUtils;

public class Main extends Plugin {
    public Main() {
    }

    @Override
    public void delete() {
        Config.BACKGROUND_TASK_EXECUTOR.shutdown();
        Config.BACKGROUND_SCHEDULER.shutdown();
        ServerController.eventHandler.unload();
        HudUtils.unload();
    }

}
