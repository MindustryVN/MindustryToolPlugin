package mindustrytool.utils;

import arc.Core;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustrytool.Config;
import mindustrytool.ServerController;

public class Utils {

    public static void executeExpectError(Runnable runnable) {
        try {
            Config.BACKGROUND_TASK_EXECUTOR.execute(runnable);
        } catch (Exception e) {
            Log.err(e);
        }
    }

    public synchronized static void host(String mapName, String mode) {
        if (Vars.state.isGame()) {
            Log.err("Already hosting. Type 'stop' to stop hosting first.");
            return;
        }
        try {
            Gamemode preset = Gamemode.survival;

            if (mode != null) {
                try {
                    preset = Gamemode.valueOf(mode);
                } catch (IllegalArgumentException event) {
                    Log.err("No gamemode '@' found.", mode);
                    return;
                }
            }

            Map result;
            if (mapName != null) {
                result = Vars.maps.all().find(map -> map.plainName().replace('_', ' ')
                        .equalsIgnoreCase(Strings.stripColors(mapName).replace('_', ' ')));

                if (result == null) {
                    Log.err("No map with name '@' found.", mapName);
                    return;
                }
            } else {
                result = Vars.maps.getShuffleMode().next(preset, Vars.state.map);
                Log.info("Randomized next map to be @.", result.plainName());
            }

            Log.info("Loading map...");

            Vars.logic.reset();
            ServerController.eventHandler.lastMode = preset;
            Core.settings.put("lastServerMode", ServerController.eventHandler.lastMode.name());

            Vars.world.loadMap(result, result.applyRules(ServerController.eventHandler.lastMode));
            Vars.state.rules = result.applyRules(preset);
            Vars.logic.play();

            Log.info("Map loaded.");

            Vars.netServer.openServer();
        } catch (MapException event) {
            Log.err("@: @", event.map.plainName(), event.getMessage());
        }
    }
}
