package mindustrytool;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.pf4j.Plugin;

import arc.Core;
import arc.util.*;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.MenuOptionChooseEvent;
import mindustry.game.EventType.PlayerChatEvent;
import mindustry.game.EventType.PlayerConnect;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.EventType.ServerLoadEvent;
import mindustry.game.EventType.TapEvent;
import mindustry.gen.Groups;
import mindustrytool.handler.ApiGateway;
import mindustrytool.handler.ClientCommandHandler;
import mindustrytool.handler.EventHandler;
import mindustrytool.handler.HttpServer;
import mindustrytool.handler.RtvVoteHandler;
import mindustrytool.handler.ServerCommandHandler;
import mindustrytool.handler.SessionHandler;
import mindustrytool.utils.HudUtils;
import mindustrytool.utils.JsonUtils;
import mindustrytool.workflow.Workflow;
import mindustrytoolpluginloader.MindustryToolPlugin;

public class ServerController extends Plugin implements MindustryToolPlugin {

    public ApiGateway apiGateway = new ApiGateway(this);
    public RtvVoteHandler voteHandler = new RtvVoteHandler();
    public EventHandler eventHandler = new EventHandler(this);
    public ClientCommandHandler clientCommandHandler = new ClientCommandHandler(this);
    public ServerCommandHandler serverCommandHandler = new ServerCommandHandler(this);
    public HttpServer httpServer = new HttpServer(this);
    public SessionHandler sessionHandler = new SessionHandler();
    public Workflow workflow = new Workflow();

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

        httpServer.init();
        eventHandler.init();
        apiGateway.init();
        workflow.init();

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
        Core.app.post(() -> {
            try {
                workflow.fire(event, true);

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
                    eventHandler.onTap(tapEvent);
                } else if (event instanceof MenuOptionChooseEvent menuOption) {
                    HudUtils.onMenuOptionChoose(menuOption);
                } else if (event instanceof GameOverEvent gameOverEvent) {
                    eventHandler.onGameOver(gameOverEvent);
                }

                workflow.fire(event, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void stop() {
        isUnloaded = true;
        Config.BACKGROUND_TASK_EXECUTOR.shutdown();
        Config.BACKGROUND_SCHEDULER.shutdown();

        eventHandler.unload();
        httpServer.unload();
        clientCommandHandler.unload();
        serverCommandHandler.unload();
        sessionHandler.clear();
        workflow.clear();

        HudUtils.menus.invalidateAll();
        HudUtils.menus = null;
        JsonUtils.objectMapper = null;

        Log.info("Server controller stopped");
    }
}
