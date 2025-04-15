package mindustrytool.utils;

import arc.Core;
import arc.struct.Seq;
import arc.util.Timer;
import mindustry.entities.Effect;
import mindustry.gen.Call;

public class Effects {
    private static Seq<Effects> effects = new Seq<>();

    public final Effect effect;
    public final String name;
    public final int id;
    public boolean disabled = false;
    public boolean forAdmin = false;

    private Effects(Effect effect, String name) {
        this.effect = effect;
        this.name = name;
        this.id = effects.size + 1;
    }

    public static Effects getByID(int id) {
        if (id < 0 || id >= effects.size)
            return null;
        else
            return effects.get(id);
    }

    public static Effects getByName(String name) {
        return effects.find(e -> e.name.equals(name));
    }

    public static void setToDefault() {
        Effects ef;
        String[] list = { "none", "unitSpawn", "unitControl", "unitDespawn", "unitSpirit", "itemTransfer", "pointBeam",
                "lightning", "unitWreck", "rocketSmoke", "rocketSmokeLarge", "fireSmoke", "melting", "wet", "muddy",
                "oily", "dropItem", "impactcloud", "unitShieldBreak", "coreLand" };

        effects.each(e -> e.disabled = false);
        for (String name : list) {
            ef = getByName(name);
            if (ef != null)
                ef.disabled = true;
        }
    }

    public static Seq<Effects> copy(boolean withAdmin, boolean withDisabled) {
        return withAdmin && withDisabled //
                ? effects
                : withAdmin && !withDisabled //
                        ? effects.select(e -> !e.disabled)
                        : !withAdmin && withDisabled //
                                ? effects.select(e -> !e.forAdmin)
                                : effects.select(e -> !e.forAdmin && !e.disabled);
    }

    public static void init() {
        System.out.println("Setup effects");

        try {
            for (java.lang.reflect.Field f : mindustry.content.Fx.class.getDeclaredFields()) {
                try {
                    if (f.get(null) instanceof Effect)
                        effects.add(new Effects((Effect) f.get(null), f.getName()));
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                }
            }

            if (Core.settings.has("removedEffects")) {
                try {
                    for (String line : Core.settings.getString("removedEffects").split(" \\| ")) {
                        Effects effect = getByName(line);
                        if (effect != null)
                            effect.disabled = true;
                    }

                } catch (Exception e) {
                    saveSettings();
                }
            } else
                saveSettings();

            if (Core.settings.has("adminEffects")) {
                try {
                    for (String line : Core.settings.getString("adminEffects").split(" \\| ")) {
                        Effects effect = getByName(line);
                        if (effect != null)
                            effect.forAdmin = true;
                    }

                } catch (Exception e) {
                    saveSettings();
                }
            } else
                saveSettings();

            Timer.schedule(() -> {
                Session.each(d -> d.hasEffect,
                        d -> Call.effect(d.effect.effect, d.player.x, d.player.y, 10, arc.graphics.Color.green));
            }, 0, 0.064f);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Setup effects done");

    }

    public static void saveSettings() {
        Core.settings.put("removedEffects", effects.select(e -> e.disabled).map(e -> e.name).toString(" | "));
        Core.settings.put("adminEffects", effects.select(e -> e.forAdmin).map(e -> e.name).toString(" | "));
    }
}
