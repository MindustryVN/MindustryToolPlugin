package mindustrytool;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import arc.*;
import arc.util.*;
import arc.util.CommandHandler.Command;
import arc.util.CommandHandler.CommandResponse;
import arc.util.CommandHandler.ResponseType;
import mindustry.Vars;
import mindustry.core.Version;
import mindustry.maps.Maps.ShuffleMode;
import mindustry.mod.*;
import mindustry.net.Administration.Config;
import mindustrytool.handlers.ClientCommandHandler;
import mindustrytool.handlers.EventHandler;
import mindustrytool.handlers.ServerCommandHandler;
import mindustrytool.handlers.RtvVoteHandler;
import mindustrytool.skeleton.ApiGatewayImpl;
import mindustrytool.skeleton.ServerGateway;
import mindustrytool.utils.Effects;
import mindustrytool.utils.HudUtils;
import mindustrytool.utils.VPNUtils;

public class MindustryToolPlugin extends Plugin {
    public static final RtvVoteHandler voteHandler = new RtvVoteHandler();
    public static final EventHandler eventHandler = new EventHandler();
    public static final CommandHandler handler = new CommandHandler("");
    public static final ClientCommandHandler clientCommandHandler = new ClientCommandHandler();
    public static final ServerCommandHandler serverCommandHandler = new ServerCommandHandler();
    public static final ServerGateway serverGateway = getServerGateway();

    @Override
    public void init() {

        Core.settings.defaults("bans", "", "admins", "", "shufflemode", "custom", "globalrules", "{reactorExplosions: false, logicUnitBuild: false}");

        // update log level
        Config.debug.set(Config.debug.bool());
        Time.setDeltaProvider(() -> Math.min(Core.graphics.getDeltaTime() * 60f, 60));
        Vars.customMapDirectory.mkdirs();

        // set up default shuffle mode
        try {
            Vars.maps.setShuffleMode(ShuffleMode.valueOf(Core.settings.getString("shufflemode")));
        } catch (Exception e) {
            Vars.maps.setShuffleMode(ShuffleMode.all);
        }

        Timer.schedule(() -> System.gc(), 0, 60);

        initRmiServer();

        eventHandler.init();

        HudUtils.init();
        VPNUtils.init();
        Effects.init();

        Vars.mods.eachClass(p -> p.registerServerCommands(handler));

        if (Version.build == -1) {
            Log.warn("&lyYour server is running a custom build, which means that client checking is disabled.");
            Log.warn("&lyIt is highly advised to specify which version you're using by building with gradle args &lb&fb-Pbuildversion=&lr<build>");
        }
    }

    private void initRmiServer() {
        try {
            String port = System.getenv("RMI_PORT");
            int portNumber = 78877;

            try {
                portNumber = port == null ? 78877 : Integer.parseInt(port);
            } catch (Exception e) {
                // do nothing
            }

            Registry registry = LocateRegistry.getRegistry(portNumber);
            registry.rebind("ApiGateway", new ApiGatewayImpl());

        } catch (RemoteException e) {
            Log.err("Remote exception: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.err("Unexpected exception: " + e.getMessage(), e);
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

    private static ServerGateway getServerGateway() {
        try {
            Registry registry = LocateRegistry.getRegistry("server-manager", 0);
            return (ServerGateway) registry.lookup("ServerGateway");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
