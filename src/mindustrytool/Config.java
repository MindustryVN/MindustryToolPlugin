package mindustrytool;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Config {

    public static boolean isHub() {
        return mindustry.net.Administration.Config.all.find(conf -> conf.name.equalsIgnoreCase("port")).num() == 6567;
    }

    public static Boolean isLoaded = false;

    public static final Executor BACKGROUND_TASK_EXECUTOR = Executors.newSingleThreadExecutor();

    public static final String SERVER_IP = "15.235.147.219";
    public static final String DISCORD_INVITE_URL = "https://discord.com/invite/DCX5yrRUyp";
    public static final String MINDUSTRY_TOOL_URL = "https://mindustry-tool.app";
    public static final String RULE_URL = MINDUSTRY_TOOL_URL + "/rules";

    public static final int MAX_IDENTICAL_IPS = 3;
}
