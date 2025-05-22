package mindustrytool;

import java.util.UUID;

import org.pf4j.Extension;

import arc.*;
import arc.files.Fi;
import arc.util.*;
import arc.util.CommandHandler.Command;
import arc.util.CommandHandler.CommandResponse;
import arc.util.CommandHandler.ResponseType;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.core.Version;
import mindustry.io.SaveIO;
import mindustry.maps.Maps.ShuffleMode;
import mindustry.net.Administration.Config;
import mindustrytool.handlers.ClientCommandHandler;
import mindustrytool.handlers.EventHandler;
import mindustrytool.handlers.HttpServer;
import mindustrytool.handlers.ServerCommandHandler;
import mindustrytool.handlers.ApiGateway;
import mindustrytool.handlers.RtvVoteHandler;
import mindustrytool.utils.HudUtils;
import mindustrytoolpluginloader.MindustryToolPlugin;

@Extension
public class ServerController implements MindustryToolPlugin {

    public static final RtvVoteHandler voteHandler = new RtvVoteHandler();
    public static final EventHandler eventHandler = new EventHandler();
    public static final CommandHandler handler = new CommandHandler("");
    public static final ClientCommandHandler clientCommandHandler = new ClientCommandHandler();
    public static final ServerCommandHandler serverCommandHandler = new ServerCommandHandler();
    public static final ApiGateway apiGateway = new ApiGateway();
    public static final HttpServer httpServer = new HttpServer();

    public static final UUID SERVER_ID = UUID.fromString(System.getenv("SERVER_ID"));

    @Override
    public void init() {
        Core.settings.defaults("bans", "", "admins", "", "shufflemode", "custom", "globalrules",
                "{reactorExplosions: false, logicUnitBuild: false}");

        // update log level
        Config.debug.set(false);
        Time.setDeltaProvider(() -> Math.min(Core.graphics.getDeltaTime() * 60f, 60));
        Vars.customMapDirectory.mkdirs();

        // set up default shuffle mode
        try {
            Vars.maps.setShuffleMode(ShuffleMode.valueOf(Core.settings.getString("shufflemode")));
        } catch (Exception e) {
            Vars.maps.setShuffleMode(ShuffleMode.all);
        }

        eventHandler.init();
        apiGateway.init();
        httpServer.init();

        HudUtils.init();

        if (Config.autosave.bool()) {
            System.out.println("Auto save is on");
            Core.app.post(() -> {
                // try to load auto-update save if possible
                Fi fi = Vars.saveDirectory.child("autosavebe." + Vars.saveExtension);

                if (fi.exists()) {
                    try {
                        SaveIO.load(fi);
                        Log.info("Auto-save loaded.");
                        Vars.state.set(State.playing);
                        Vars.netServer.openServer();
                    } catch (Throwable e) {
                        Log.err(e);
                    }
                }
            });
        }

        System.out.println("Register server commands");

        Vars.mods.eachClass(p -> p.registerServerCommands(handler));

        System.out.println("Register server commands done");

        if (Version.build == -1) {
            Log.warn("&lyYour server is running a custom build, which means that client checking is disabled.");
            Log.warn(
                    "&lyIt is highly advised to specify which version you're using by building with gradle args &lb&fb-Pbuildversion=&lr<build>");
        }

        System.out.println("Setup auto save");

        Log.info("MindustryToolPlugin initialized.");

        if (!Vars.state.isGame()) {
            try {
                apiGateway.host(SERVER_ID.toString());
            } catch (Exception e) {
                Log.err(e);
            }
        }
    }

    public void handleCommandString(String line) {
        CommandResponse response = handler.handleMessage(line);

        if (response.type == ResponseType.unknownCommand) {

            int minDst = 0;
            Command closest = null;

            for (Command command : handler.getCommandList()) {
                int dst = Strings.levenshtein(command.text, response.runCommand);
                if (dst < 3 && (closest == null || dst < minDst)) {
                    minDst = dst;
                    closest = command;
                }
            }

            if (closest != null && !closest.text.equals("yes")) {
                Log.err("<" + line + ">Command not found. Did you mean \"" + closest.text + "\"?");
            } else {
                Log.err("<" + line + ">Invalid command. Type 'help' for help.");
            }
        } else if (response.type == ResponseType.fewArguments) {
            Log.err("Too few command arguments. Usage: " + response.command.text + " " + response.command.paramText);
        } else if (response.type == ResponseType.manyArguments) {
            Log.err("Too many command arguments. Usage: " + response.command.text + " " + response.command.paramText);
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        serverCommandHandler.registerCommands(handler);
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        clientCommandHandler.registerCommands(handler);
    }
}
