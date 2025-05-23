package mindustrytool.utils;

import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustrytool.Config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mindustry.game.EventType.MenuOptionChooseEvent;
import mindustry.game.EventType.PlayerLeave;

public class HudUtils {

    public static final int HUB_UI = 1;
    public static final int SERVERS_UI = 2;
    public static final int LOGIN_UI = 3;
    public static final int SERVER_REDIRECT = 4;

    public static final Cache<String, LinkedList<MenuData>> menus = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(10000)
            .build();

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

    public static void onPlayerLeave(PlayerLeave event) {
        var menu = menus.getIfPresent(event.player.uuid());
        if (menu != null) {
            for (var data : menu) {
                Call.hideFollowUpMenu(event.player.con, data.id);
            }
        }
        menus.invalidate(event.player.uuid());
    }

    public static Option option(PlayerPressCallback callback, String text) {
        return new Option(callback, text);
    }

    public static void showFollowDisplay(Player player, int id, String title, String description, Object state,
            List<Option> options) {
        showFollowDisplays(player, id, title, description, state,
                options.stream().map(option -> List.of(option)).toList());
    }

    public static synchronized void showFollowDisplays(Player player, int id, String title, String description,
            Object state,
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

        var userMenu = menus.get(player.uuid(), k -> new LinkedList<>());

        userMenu.removeIf(m -> m.id == id);

        if (userMenu.isEmpty()) {
            Call.menu(player.con, id, title, description, optionTexts);
        }

        userMenu.addLast(new MenuData(id, title, description, optionTexts, callbacks, state));
    }

    public static void onMenuOptionChoose(MenuOptionChooseEvent event) {
        var menu = menus.getIfPresent(event.player.uuid());

        if (menu == null || menu.isEmpty()) {
            return;
        }

        var data = menu.getFirst();

        var callbacks = data.getCallbacks();

        if (callbacks == null || event.option <= -1 || event.option >= callbacks.size()) {
            return;
        }

        var callback = callbacks.get(event.option);

        if (callback == null) {
            return;
        }

        Config.BACKGROUND_TASK_EXECUTOR.execute(() -> {
            callback.accept(event.player, data.state);
        });
    }

    public static synchronized void closeFollowDisplay(Player player, int id) {
        Call.hideFollowUpMenu(player.con, id);

        var menu = menus.getIfPresent(player.uuid());

        if (menu == null) {
            return;
        }

        menu.removeIf(data -> data.id == id);

        if (menu.isEmpty()) {
            return;
        }

        var first = menu.getFirst();

        Call.menu(player.con, id, first.title, first.description, first.optionTexts);
    }
}
