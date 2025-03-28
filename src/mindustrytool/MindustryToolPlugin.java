package mindustrytool;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import arc.*;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.*;
import arc.util.CommandHandler.Command;
import arc.util.CommandHandler.CommandResponse;
import arc.util.CommandHandler.ResponseType;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.core.Version;
import mindustry.game.EventType.Trigger;
import mindustry.io.SaveIO;
import mindustry.maps.Maps.ShuffleMode;
import mindustry.mod.*;
import mindustry.net.Administration.Config;
import mindustrytool.handlers.ClientCommandHandler;
import mindustrytool.handlers.EventHandler;
import mindustrytool.handlers.HttpServer;
import mindustrytool.handlers.ServerCommandHandler;
import mindustrytool.handlers.ApiGateway;
import mindustrytool.handlers.RtvVoteHandler;
import mindustrytool.utils.Effects;
import mindustrytool.utils.HudUtils;
import mindustrytool.utils.VPNUtils;

public class MindustryToolPlugin extends Plugin {
    public static final RtvVoteHandler voteHandler = new RtvVoteHandler();
    public static final EventHandler eventHandler = new EventHandler();
    public static final CommandHandler handler = new CommandHandler("");
    public static final ClientCommandHandler clientCommandHandler = new ClientCommandHandler();
    public static final ServerCommandHandler serverCommandHandler = new ServerCommandHandler();
    public static final ApiGateway apiGateway = new ApiGateway();
    private static final HttpServer httpServer = new HttpServer();

    public static final UUID SERVER_ID = UUID.fromString(System.getenv("SERVER_ID"));

    public static final PrintStream standardOutputStream = System.out;

    private final Interval autosaveCount = new Interval();
    private static DateTimeFormatter autosaveDate = DateTimeFormatter.ofPattern("MM-dd-yyyy_HH-mm-ss");

    @Override
    public void init() {

        initOutputStream();

        Core.settings.defaults("bans", "", "admins", "", "shufflemode", "custom", "globalrules",
                "{reactorExplosions: false, logicUnitBuild: false}");

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

        httpServer.init();
        eventHandler.init();

        HudUtils.init();
        VPNUtils.init();
        Effects.init();

        if (Config.autoUpdate.bool()) {
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

        Vars.mods.eachClass(p -> p.registerServerCommands(handler));

        if (Version.build == -1) {
            Log.warn("&lyYour server is running a custom build, which means that client checking is disabled.");
            Log.warn(
                    "&lyIt is highly advised to specify which version you're using by building with gradle args &lb&fb-Pbuildversion=&lr<build>");
        }

        Events.run(Trigger.update, () -> {
            if (Vars.state.isPlaying() && Config.autosave.bool()) {
                if (autosaveCount.get(Config.autosaveSpacing.num() * 60)) {
                    int max = Config.autosaveAmount.num();

                    // use map file name to make sure it can be saved
                    String mapName = (Vars.state.map.file == null ? "unknown"
                            : Vars.state.map.file.nameWithoutExtension())
                            .replace(" ", "_");
                    String date = autosaveDate.format(LocalDateTime.now());

                    Seq<Fi> autosaves = Vars.saveDirectory.findAll(f -> f.name().startsWith("auto_"));
                    autosaves.sort(f -> -f.lastModified());

                    // delete older saves
                    if (autosaves.size >= max) {
                        for (int i = max - 1; i < autosaves.size; i++) {
                            autosaves.get(i).delete();
                        }
                    }

                    String fileName = "auto_" + mapName + "_" + date + "." + Vars.saveExtension;
                    Fi file = Vars.saveDirectory.child(fileName);
                    Log.info("Autosaving...");

                    try {
                        SaveIO.save(file);
                        Log.info("Autosave completed.");
                    } catch (Throwable e) {
                        Log.err("Autosave failed.", e);
                    }
                }
            }
        });

        Log.info("MindustryToolPlugin initialized.");
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

    private void sendToConsole(String message) {
        mindustrytool.Config.BACKGROUND_TASK_EXECUTOR.execute(() -> {
            try {
                apiGateway.sendConsoleMessage(message);
            } catch (Exception e) {
                try {
                    standardOutputStream.write(message.getBytes());
                } catch (IOException e1) {
                }
            }
        });
    }

    private void initOutputStream() {
        var custom = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                standardOutputStream.write(b);
                sendToConsole(String.valueOf((char) b));
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                standardOutputStream.write(b, off, len);
                String message = new String(b, off, len);
                sendToConsole(message);
            }

            @Override
            public void flush() throws IOException {
                standardOutputStream.flush();
            }

            @Override
            public void close() throws IOException {
                standardOutputStream.close();
            }
        };

        System.setOut(new PrintStream(custom));
    }
}
