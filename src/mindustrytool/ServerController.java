package mindustrytool;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.pf4j.Extension;

import arc.util.*;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.game.EventType.MenuOptionChooseEvent;
import mindustry.game.EventType.PlayerChatEvent;
import mindustry.game.EventType.PlayerConnect;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.EventType.ServerLoadEvent;
import mindustry.game.EventType.TapEvent;
import mindustry.gen.Groups;
import mindustrytool.handlers.ClientCommandHandler;
import mindustrytool.handlers.EventHandler;
import mindustrytool.handlers.HttpServer;
import mindustrytool.handlers.ServerCommandHandler;
import mindustrytool.handlers.ApiGateway;
import mindustrytool.handlers.RtvVoteHandler;
import mindustrytool.utils.HudUtils;
import mindustrytool.utils.JsonUtils;
import mindustrytool.utils.Session;
import mindustrytoolpluginloader.MindustryToolPlugin;

@Extension
public class ServerController implements MindustryToolPlugin {

    public ApiGateway apiGateway = new ApiGateway(this);
    public RtvVoteHandler voteHandler = new RtvVoteHandler();
    public EventHandler eventHandler = new EventHandler(this);
    public ClientCommandHandler clientCommandHandler = new ClientCommandHandler(this);
    public ServerCommandHandler serverCommandHandler = new ServerCommandHandler(this);
    public HttpServer httpServer = new HttpServer(this);

    public static final UUID SERVER_ID = UUID.fromString(System.getenv("SERVER_ID"));
    public static boolean isUnloaded = false;

    public ServerController() {
        Log.info("Server controller created");
    }

    @Override
    protected void finalize() throws Throwable {
        System.out.println("Finalizing " + this);
    }

    @Override
    public void init() {

        eventHandler.init();
        apiGateway.init();
        httpServer.init();

        Config.BACKGROUND_SCHEDULER.schedule(() -> {
            try {
                if (!Vars.state.isGame()) {
                    Log.info("Server not hosting, auto host");
                    if (apiGateway != null) {
                        apiGateway.host(SERVER_ID.toString());
                    }
                }
            } catch (Exception e) {
                Log.err(e);
            }
        }, 10, TimeUnit.SECONDS);

        Config.BACKGROUND_SCHEDULER.schedule(() -> {
            if (!Vars.state.isPaused() && Groups.player.size() == 0) {
                Vars.state.set(State.paused);
                Log.info("No player: paused");
            }
        }, 10, TimeUnit.SECONDS);

        Log.info("Server controller initialized.");
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        serverCommandHandler.registerCommands(handler);
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        clientCommandHandler.registerCommands(handler);
    }

    @Override
    public void onEvent(Object event) {
        Config.BACKGROUND_TASK_EXECUTOR.execute(() -> {

            if (event instanceof PlayerJoin playerJoin) {
                eventHandler.onPlayerJoin(playerJoin);
            } else if (event instanceof PlayerLeave playerLeave) {
                eventHandler.onPlayerLeave(playerLeave);
                HudUtils.onPlayerLeave(playerLeave);
            } else if (event instanceof PlayerChatEvent playerChat) {
                eventHandler.onPlayerChat(playerChat);
            } else if (event instanceof ServerLoadEvent serverLoad) {
                eventHandler.onServerLoad(serverLoad);
            } else if (event instanceof PlayerConnect playerConnect) {
                eventHandler.onPlayerConnect(playerConnect);
            } else if (event instanceof BlockBuildEndEvent blockBuild) {
                eventHandler.onBuildBlockEnd(blockBuild);
            } else if (event instanceof TapEvent tapEvent) {
                if (mindustrytool.Config.IS_HUB) {
                    eventHandler.onTap(tapEvent);
                }
            } else if (event instanceof MenuOptionChooseEvent menuOption) {
                HudUtils.onMenuOptionChoose(menuOption);
            } else {
                Log.warn("Unhandled event: " + event.getClass().getSimpleName() + " " + event);
            }
        });
    }

    @Override
    public void unload() {
        isUnloaded = true;
        Config.BACKGROUND_TASK_EXECUTOR.shutdownNow();
        Config.BACKGROUND_SCHEDULER.shutdownNow();

        eventHandler.unload();
        httpServer.unload();
        clientCommandHandler.unload();
        serverCommandHandler.unload();

        Session.clear();

        HudUtils.menus.invalidateAll();
        HudUtils.menus = null;
        JsonUtils.objectMapper = null;

        Log.info("Server controller stopped");
    }
}
