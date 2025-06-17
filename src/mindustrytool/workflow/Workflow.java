package mindustrytool.workflow;

import arc.func.Cons;
import arc.func.Cons2;
import arc.struct.ObjectMap;
import arc.struct.Seq;

public class Workflow {
    private static final ObjectMap<Object, Seq<Cons2<?, Boolean>>> events = new ObjectMap<>();

    public static <T> Cons2<T, Boolean> on(Class<T> type, Cons2<T, Boolean> listener) {
        events.get(type, () -> new Seq<>(Cons.class)).add(listener);

        return listener;
    }

    public static <T> boolean remove(Class<T> type, Cons2<T, Boolean> listener) {
        return events.get(type, () -> new Seq<>(Cons.class)).remove(listener);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T extends Enum<T>> void fire(Enum<T> type, boolean before) {
        Seq<Cons2<?, Boolean>> listeners = events.get(type);

        if (listeners != null) {
            int len = listeners.size;
            Cons2[] items = listeners.items;
            for (int i = 0; i < len; i++) {
                items[i].get(type, before);
            }
        }
    }

    /** Fires a non-enum event by class. */
    public static <T> void fire(T type, boolean before) {
        fire(type.getClass(), type, before);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T> void fire(Class<?> ctype, T type, boolean before) {
        Seq<Cons2<?, Boolean>> listeners = events.get(ctype);

        if (listeners != null) {
            int len = listeners.size;
            Cons2[] items = listeners.items;
            for (int i = 0; i < len; i++) {
                items[i].get(type, before);
            }
        }
    }

    public static void clear() {
        events.clear();

        WorkflowNode.nodes.values().forEach(node -> node.unload());
        WorkflowNode.nodes.clear();
        WorkflowNode.nodeTypes.clear();
    }
}
