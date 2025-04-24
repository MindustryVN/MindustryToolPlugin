package mindustrytool.utils;

import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustrytool.Config;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import arc.Events;
import arc.util.Log;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mindustry.game.EventType.MenuOptionChooseEvent;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;

public class HudUtils {

    public static final int HUB_UI = 1;
    public static final int SERVERS_UI = 2;
    public static final int LOGIN_UI = 3;
    public static final int SERVER_REDIRECT = 4;

    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private static final List<Player> leaved = new ArrayList<Player>();
    private static final List<Player> markForRemove = new ArrayList<Player>();

    public static final ConcurrentHashMap<String, LinkedList<MenuData>> menus = new ConcurrentHashMap<>();

    @Data
    @AllArgsConstructor
    public static class MenuData {
        int id;
        String title;
        String description;
        String[][] optionTexts;
        List<PlayerPressCallback> callbacks;
        Object state;
    }

    @FunctionalInterface
    public interface PlayerPressCallback {
        void accept(Player player, Object state);
    }

    @Data
    @RequiredArgsConstructor
    public static class Option {
        private final PlayerPressCallback callback;
        private final String text;
    }

    public static void init() {

        executor.scheduleAtFixedRate(HudUtils::cleanUpCallback, 0, 10, TimeUnit.MINUTES);

        Events.on(PlayerLeave.class, HudUtils::onPlayerLeave);
        Events.on(PlayerJoin.class, HudUtils::onPlayerJoin);
        Events.on(MenuOptionChooseEvent.class, HudUtils::onMenuOptionChoose);
    }

    private static void onPlayerJoin(PlayerJoin event) {
        markForRemove.removeIf(p -> p.uuid().equals(event.player.uuid()));
    }

    private static void onPlayerLeave(PlayerLeave event) {
        leaved.add(event.player);
    }

    public static Option option(PlayerPressCallback callback, String text) {
        return new Option(callback, text);
    }

    public static void showFollowDisplay(Player player, int id, String title, String description, Object state,
            List<Option> options) {
        showFollowDisplays(player, id, title, description, state,
                options.stream().map(option -> List.of(option)).toList());
    }

    public static void showFollowDisplays(Player player, int id, String title, String description, Object state,
            List<List<Option>> options) {

        String[][] optionTexts = new String[options.size()][];
        for (int i = 0; i < options.size(); i++) {
            var op = options.get(i);
            optionTexts[i] = op.stream()//
                    .map(data -> data.text)//
                    .toArray(String[]::new);
        }

        var callbacks = options.stream()//
                .flatMap(option -> option.stream().map(l -> l.callback))//
                .toList();

        var userMenu = menus.computeIfAbsent(player.uuid(), k -> new LinkedList<>());

        if (userMenu.isEmpty()) {
            Call.menu(player.con, id, title, description, optionTexts);
        }

        userMenu.addLast(new MenuData(id, title, description, optionTexts, callbacks, state));
    }

    public static void onMenuOptionChoose(MenuOptionChooseEvent event) {
        var menu = menus.get(event.player.uuid());

        if (menu == null) {
            return;
        }

        var data = menu.stream().filter(m -> m.getId() == event.menuId).findFirst();

        if (data.isEmpty()) {
            Log.info("No menu data found for player: " + event.player.uuid());
            return;
        }

        var callbacks = data.get().getCallbacks();

        if (callbacks == null || event.option <= -1 || event.option >= callbacks.size()) {
            return;
        }

        var callback = callbacks.get(event.option);

        if (callback == null) {
            return;
        }

        Config.BACKGROUND_TASK_EXECUTOR.execute(() -> {
            callback.accept(event.player, data.get().state);
        });
    }

    public static void cleanUpCallback() {
        markForRemove.forEach(player -> {
            menus.remove(player.uuid());
        });

        markForRemove.clear();

        leaved.forEach(player -> {
            markForRemove.add(player);
        });

        leaved.clear();
    }

    public static void closeFollowDisplay(Player player, int id) {
        Call.hideFollowUpMenu(player.con, id);

        var menu = menus.get(player.uuid());

        if (menu == null) {
            return;
        }

        menu.removeFirst();

        var first = menu.getFirst();

        if (first == null) {
            return;
        }

        Call.menu(player.con, id, first.title, first.description, first.optionTexts);
    }
}
