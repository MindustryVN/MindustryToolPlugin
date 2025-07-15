package mindustrytool;

import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.pf4j.Plugin;

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
import mindustrytool.handler.HudHandler;
import mindustrytool.handler.RtvVoteHandler;
import mindustrytool.handler.ServerCommandHandler;
import mindustrytool.handler.SessionHandler;
import mindustrytool.workflow.Workflow;
import mindustrytoolpluginloader.MindustryToolPlugin;

public class ServerController extends Plugin implements MindustryToolPlugin {

    public ApiGateway apiGateway;
    public RtvVoteHandler voteHandler;
    public EventHandler eventHandler;
    public ClientCommandHandler clientCommandHandler;
    public ServerCommandHandler serverCommandHandler;
    public HttpServer httpServer;
    public SessionHandler sessionHandler;
    public Workflow workflow;
    public HudHandler hudHandler;

    public WeakReference<ServerController> context = new WeakReference<>(this);

    public static final UUID SERVER_ID = UUID.fromString(System.getenv("SERVER_ID"));
    public static boolean isUnloaded = false;

    public final ExecutorService BACKGROUND_TASK_EXECUTOR = new ThreadPoolExecutor(
            0,
            20,
            5,
            TimeUnit.SECONDS,
            new SynchronousQueue<Runnable>(),
            new DefaultThreadFactory());

    public final ScheduledExecutorService BACKGROUND_SCHEDULER = Executors
            .newSingleThreadScheduledExecutor();

    public ServerController() {
        httpServer = new HttpServer(context);
        apiGateway = new ApiGateway(context);
        voteHandler = new RtvVoteHandler();
        eventHandler = new EventHandler(context);
        clientCommandHandler = new ClientCommandHandler(context);
        serverCommandHandler = new ServerCommandHandler(context);
        sessionHandler = new SessionHandler();
        workflow = new Workflow(context);
        hudHandler = new HudHandler(context);

        Log.info("Server controller created: " + this);
    }

    @Override
    protected void finalize() throws Throwable {
        System.out.println("Finalizing " + this);
    }

    @Override
    public void start() {
        Log.info("Server controller started: " + this);
    }

    @Override
    public void init() {

        httpServer.init();
        eventHandler.init();
        apiGateway.init();
        workflow.init();

        BACKGROUND_SCHEDULER.schedule(() -> {
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

        BACKGROUND_SCHEDULER.schedule(() -> {
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
        try {
            workflow.fire(event, true);

            if (event instanceof PlayerJoin playerJoin) {
                eventHandler.onPlayerJoin(playerJoin);
            } else if (event instanceof PlayerLeave playerLeave) {
                eventHandler.onPlayerLeave(playerLeave);
                hudHandler.onPlayerLeave(playerLeave);
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
                hudHandler.onMenuOptionChoose(menuOption);
            } else if (event instanceof GameOverEvent gameOverEvent) {
                eventHandler.onGameOver(gameOverEvent);
            }

            workflow.fire(event, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        isUnloaded = true;
        BACKGROUND_TASK_EXECUTOR.shutdown();
        BACKGROUND_SCHEDULER.shutdown();

        eventHandler.unload();
        httpServer.unload();
        clientCommandHandler.unload();
        serverCommandHandler.unload();
        apiGateway.unload();
        sessionHandler.clear();
        workflow.clear();

        hudHandler.unload();

        apiGateway = null;
        voteHandler = null;
        eventHandler = null;
        clientCommandHandler = null;
        serverCommandHandler = null;
        sessionHandler = null;
        httpServer = null;
        workflow = null;

        Log.info("Server controller stopped: " + this);
    }

    @Override
    public void delete() {
        Log.info("Server controller deleted: " + this);
    }

    private static class DefaultThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        @SuppressWarnings("deprecation")
        DefaultThreadFactory() {
            @SuppressWarnings("removal")
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            namePrefix = "background-pool-" + poolNumber.getAndIncrement() + "-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
