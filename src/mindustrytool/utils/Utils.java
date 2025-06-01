package mindustrytool.utils;

import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.maps.Map;
import mindustry.maps.MapException;

public class Utils {

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

            Vars.logic.reset();

            Vars.state.rules = result.applyRules(preset);
            Vars.logic.play();

            Log.info("Starting server...");
            Vars.netServer.openServer();
            Log.info("Server started.");
        } catch (MapException event) {
            Log.err("@: @", event.map.plainName(), event.getMessage());
        }
    }
}
