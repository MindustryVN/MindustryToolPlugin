package mindustrytool;

import java.util.UUID;

import org.pf4j.Extension;

import arc.util.*;
import mindustry.Vars;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.game.EventType.MenuOptionChooseEvent;
import mindustry.game.EventType.PlayerChatEvent;
import mindustry.game.EventType.PlayerConnect;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.EventType.ServerLoadEvent;
import mindustry.game.EventType.TapEvent;
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

    public static RtvVoteHandler voteHandler = new RtvVoteHandler();
    public static EventHandler eventHandler = new EventHandler();
    public static ClientCommandHandler clientCommandHandler = new ClientCommandHandler();
    public static ServerCommandHandler serverCommandHandler = new ServerCommandHandler();
    public static ApiGateway apiGateway = new ApiGateway();
    public static HttpServer httpServer = new HttpServer();

    public static final UUID SERVER_ID = UUID.fromString(System.getenv("SERVER_ID"));

    @Override
    public void init() {

        httpServer.init();
        eventHandler.init();
        apiGateway.init();

        Timer.schedule(() -> {
            if (!Vars.state.isGame()) {
                try {
                    apiGateway.host(SERVER_ID.toString());
                } catch (Exception e) {
                    Log.err(e);
                }
            }
        }, 10);

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
    }
}
