package mindustrytool.workflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import arc.util.Log;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import mindustrytool.workflow.errors.WorkflowError;

@Data
@Accessors(chain = true)
public abstract class WorkflowNode {

    // Variable name should able to contain dot(.) to access nested object, a-z A-Z
    // 0-9 _ -
    public static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^{}]+)\\}\\}");

    private String id;
    private int x;
    private int y;
    private int inputs = 1;

    protected final String name;
    protected final String group;
    protected final String color;

    protected List<WorkflowConsumer<?>> consumers = new ArrayList<>();
    protected List<WorkflowProducer> producers = new ArrayList<>();
    protected List<WorkflowOutput> outputs = new ArrayList<>();

    public void init(Workflow context) {
    }

    public void unload(Workflow context) {
        // Unload logic if needed
    }

    public WorkflowNode(String name, String group, String color, int inputs) {
        this.name = name;
        this.group = group;
        this.color = color;
        this.inputs = inputs;
    }

    public void execute(WorkflowEmitEvent event) {
        event.next(outputs.get(0).getNextId());
    }

    public void defaultOneOutput() {
        new WorkflowOutput("Next", "None");
    }

    public String nextId() {
        if (outputs.isEmpty()) {
            throw new WorkflowError("No outputs defined for node: " + name);
        }

        return outputs.get(0).getNextId();
    }

    @Data
    public class WorkflowOutput {
        private final String name;
        private final String description;
        private String nextId;

        public WorkflowOutput(String name, String description) {
            this.name = name;
            this.description = description;

            outputs.add(this);
        }
    }

    @Data
    public class WorkflowProducer<T> {
        private final String name;
        private final Function<WorkflowEmitEvent, T> produce;

        private String variableName;

        public WorkflowProducer(String name, Function<WorkflowEmitEvent, T> produce) {
            this.name = name;
            this.produce = produce;
            this.variableName = name;

            producers.add(this);
        }
    }

    @Data
    public class WorkflowConsumerOption {
        @JsonSerialize
        private final String label;

        @JsonSerialize
        private final String value;

        @JsonSerialize(using = ClassSerializer.class)
        private Class<?> produceType;

        public WorkflowConsumerOption(String label, String value, Class<?> produceType) {
            this.label = label;
            this.value = value;
            this.produceType = produceType;
        }

        public WorkflowConsumerOption(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    @Data
    public class ConsumerProducer<T> {

        @JsonSerialize(using = ClassSerializer.class)
        private Class<?> produceType;
        private String variableName;
    }

    @Data
    @ToString(exclude = { "options" })
    public class WorkflowConsumer<T> {
        private final String name;
        private final Class<T> type;

        private final List<WorkflowConsumerOption> options = new ArrayList<>();

        private WorkflowConsumerUnit unit;
        private boolean required = true;
        private String value;
        private T defaultValue;
        private ConsumerProducer<?> produce;

        public WorkflowConsumer(String name, Class<T> type) {
            this.name = name;
            this.type = type;

            this.produce = new ConsumerProducer<>().setVariableName(name);

            consumers.add(this);
        }

        public WorkflowConsumer<T> notRequired() {
            this.required = false;
            return this;
        }

        public WorkflowConsumer<T> produce(ConsumerProducer<?> producer) {
            this.produce = producer;
            return this;
        }

        public WorkflowConsumer<T> unit(WorkflowConsumerUnit unit) {
            this.unit = unit;
            return this;
        }

        public Class<?> asClass() {
            try {
                return Class.forName(value);
            } catch (Exception e) {
                throw new WorkflowError("Invalid class: " + value + " " + e.getMessage(), e);
            }
        }

        public Boolean asBoolean() {
            try {

                return Boolean.parseBoolean(value);
            } catch (Exception e) {
                throw new WorkflowError("Invalid boolean value: " + value + " " + e.getMessage(), e);
            }
        }

        public Long asLong() {
            try {
                return Long.parseLong(value);
            } catch (Exception e) {
                throw new WorkflowError("Invalid long value: " + value + " " + e.getMessage(), e);
            }
        }

        @SuppressWarnings("rawtypes")
        public T asEnum() {
            if (!type.isEnum()) {
                throw new WorkflowError("Type is not an enum: " + type.getName());
            }

            try {
                return (T) Enum.valueOf((Class<? extends Enum>) type, value);
            } catch (Exception e) {
                throw new WorkflowError("Invalid enum value: " + value + " for type: " + type.getName(), e);
            }
        }

        public WorkflowConsumer<T> defaultValue(T value) {
            this.defaultValue = value;
            return this;
        }

        public WorkflowConsumer<T> option(String name, String value) {
            options.add(new WorkflowConsumerOption(name, value));
            return this;
        }

        public WorkflowConsumer<T> option(String name, String value, Class<?> produceType) {
            options.add(new WorkflowConsumerOption(name, value, produceType));
            return this;
        }

        public WorkflowConsumer<T> options(Class<? extends Enum<?>> enumClass) {
            for (var enumConstant : enumClass.getEnumConstants()) {
                options.add(new WorkflowConsumerOption(enumConstant.name(), enumConstant.name()));
            }
            return this;
        }

        public T consume(WorkflowEmitEvent event) {
            var fields = value.split(".");

            if (fields.length == 0) {
                return (T) event.getValues().get(fields[0]);
            }

            Object result = event.getValues().get(fields[0]);

            for (int index = 1; index < fields.length; index++) {
                try {
                    result = result.getClass().getDeclaredField(fields[index]).get(result);
                } catch (IllegalArgumentException //
                        | IllegalAccessException //
                        | NoSuchFieldException
                        | SecurityException e//
                ) {
                    throw new IllegalStateException(
                            "Field not found: " + fields[index] + " of " + value + " on value " + result, e);
                }
            }

            return (T) result;
        }

        public T access(Object value, String path) {
            if (value == null) {
                Log.debug("Trying to access null value: " + path);
                return null;
            }

            var fields = path.split("\\.");

            if (fields.length == 0) {
                return (T) value;
            }

            Log.debug("Fields: " + Arrays.toString(fields));

            Object result = value;

            for (int index = 0; index < fields.length; index++) {
                try {
                    Log.debug("Trying to access field: " + fields[index] + " of " + path + " on value " + result);
                    result = result.getClass().getDeclaredField(fields[index]).get(result);
                } catch (IllegalAccessException e) {
                    throw new WorkflowError("Can not access field: " + fields[index] + " of " + path + " on value "
                            + result, e);
                } catch (NoSuchFieldException e) {
                    throw new WorkflowError(
                            "Field not found: " + fields[index] + " of " + path + " on value " + result, e);
                } catch (SecurityException e) {
                    throw new WorkflowError(
                            "Can not access field: " + fields[index] + " of " + path + " on value " + result, e);
                }
            }
            return (T) result;
        }

        public String asString(WorkflowEmitEvent event) {
            var matcher = VARIABLE_PATTERN.matcher(value);

            if (!matcher.find()) {
                return value;
            }

            matcher.reset();

            var result = new StringBuilder();
            int lastEnd = 0;

            for (var match : matcher.results().toList()) {
                String path = match.group(1);

                Log.debug("Resolving variable: " + path);

                result.append(value, lastEnd, match.start());

                var firstDot = path.indexOf('.');

                if (firstDot != -1) {
                    var key = path.substring(0, firstDot);
                    path = path.substring(firstDot + 1);
                    var obj = event.getValues().get(key);

                    if (obj == null) {
                        Log.debug("Variable not found: " + key);
                    }

                    var variable = access(obj, path.substring(firstDot + 1));

                    if (variable == null) {
                        Log.debug("Variable not found: " + path);
                    }

                    result.append(variable);

                } else {
                    var variable = event.getValues().get(path);

                    if (variable == null) {
                        Log.debug("Variable not found: " + path);
                    }

                    result.append(variable);
                }

                lastEnd = match.end();
            }

            result.append(value.substring(lastEnd));
            return result.toString();
        }
    }
}
