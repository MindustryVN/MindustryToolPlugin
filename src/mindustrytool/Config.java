package mindustrytool;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Config {

    public static Boolean isLoaded = false;

    public static final String HUB = System.getenv("IS_HUB");
    public static final boolean IS_HUB = HUB != null && HUB.equals("true");

    public static final String ENV = System.getenv("ENV");

    public static final boolean IS_DEVELOPMENT = ENV != null && ENV.equals("DEV");

    public static final Executor BACKGROUND_TASK_EXECUTOR = Executors.newSingleThreadExecutor();

    public static final String SERVER_IP = "103.20.96.24";
    public static final String DISCORD_INVITE_URL = "https://discord.com/invite/DCX5yrRUyp";
    public static final String MINDUSTRY_TOOL_URL = "https://mindustry-tool.com";
    public static final String RULE_URL = MINDUSTRY_TOOL_URL + "/rules";

    public static final int MAX_IDENTICAL_IPS = 3;
    public static final String HUB_MESSAGE = """
            Command
            [yellow]/servers[white] to show server list
            [yellow]/rtv[white] to vote for changing map
            [yellow]/maps[white] to see map list
            [yellow]/hub[white] show this
            [green]Log in to get more feature
            """;

    public static final String CHOOSE_SERVER_MESSAGE = """
            [accent]Click[] [orange]any server data[] to [lime]play[]
            [accent]Click[] to [scarlet]offline server[] to [lime]starting & play[] this.
            """;
}
