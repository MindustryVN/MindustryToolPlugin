package mindustrytool.utils;

import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnore;

import arc.func.Boolf;
import arc.func.Cons;
import mindustry.gen.Groups;
import mindustry.gen.Player;

public class Session {
    private static HashMap<String, Session> data = new HashMap<>();

    @JsonIgnore
    public final WeakReference<Player> player;
    public final Locale locale;
    public final Long joinedAt = Instant.now().toEpochMilli();

    @JsonIgnore
    public mindustry.game.Team spectate = null;

    public String tag = "", noColorTag = "", rainbowedName = "";
    public int hue = 0;
    public boolean votedVNW = false, //
            votedRTV = false, //
            rainbowed = false, //
            hasEffect = false, //
            isMuted = false, //
            inGodmode = false, //
            isCreator;

    private Session(Player p) {
        this.player = new WeakReference<Player>(p);
        this.locale = Locale.forLanguageTag(p.locale().replace('_', '-'));
    }

    public static HashMap<String, Session> get() {
        return data;
    }

    public static void load() {
        Groups.player.each(Session::put);
    }

    public static void clear() {
        data.clear();
    }

    public boolean spectate() {
        return this.spectate != null;
    }

    public void reset() {
        if (this.player.get() == null)
            return;

        Session newData = new Session(this.player.get());

        if (spectate())
            this.player.get().team(this.spectate);
        this.spectate = newData.spectate;
        this.hue = newData.hue;
        this.votedVNW = newData.votedVNW;
        this.votedRTV = newData.votedRTV;
        this.rainbowed = newData.rainbowed;
        this.hasEffect = newData.hasEffect;
        this.isMuted = newData.isMuted;
        this.inGodmode = newData.inGodmode;
        this.isCreator = newData.isCreator;
    }

    public static Session getByName(String name) {
        return find(p -> p.player.get() != null && p.player.get().name.equals(name));
    }

    public static Session getByID(String id) {
        return find(p -> p.player.get() != null && p.player.get().uuid().equals(id));
    }

    public static Session get(Player p) {
        if (p == null)
            return null;
        return data.get(p.uuid());
    }

    public static Session put(Player p) {
        Session data_ = new Session(p);

        data.put(p.uuid(), data_);

        return data_;
    }

    public static Session remove(Player p) {
        Session data_ = get(p);

        data.remove(p.uuid());

        return data_;
    }

    public static boolean contains(Player p) {
        return data.containsKey(p.uuid());
    }

    public static void each(Cons<Session> item) {
        data.forEach((k, v) -> item.get(v));
    }

    public static void each(Boolf<Session> pred, Cons<Session> item) {
        data.forEach((k, v) -> {
            if (pred.get(v))
                item.get(v);
        });
    }

    public static int count(Boolf<Session> pred) {
        int size = 0;

        for (Session p : data.values()) {
            if (pred.get(p))
                size++;
        }

        return size;
    }

    public static Session find(Boolf<Session> pred) {
        for (Session p : data.values()) {
            if (pred.get(p))
                return p;
        }
        return null;
    }
}
