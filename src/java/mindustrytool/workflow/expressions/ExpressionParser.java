package mindustrytool.workflow.expressions;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Function;

import arc.util.Log;
import mindustry.Vars;
import mindustry.gen.Groups;
import mindustrytool.workflow.errors.WorkflowError;
import mindustrytool.workflow.nodes.WorkflowNode;

public class ExpressionParser {
    private final Map<String, Integer> PRECEDENCE = new HashMap<>();

    public final Map<String, BinaryOperator> BINARY_OPERATORS = new HashMap<>();
    public final Map<String, UnaryOperator> UNARY_OPERATORS = new HashMap<>();
    public final Map<String, Class<?>> CLASSES = new HashMap<>();

    public ExpressionParser() {
        PRECEDENCE.put("(", 0);
        PRECEDENCE.put(")", 0);
        PRECEDENCE.put("or", 1);
        PRECEDENCE.put("xor", 2);
        PRECEDENCE.put("and", 3);
        PRECEDENCE.put("==", 4);
        PRECEDENCE.put("!=", 4);
        PRECEDENCE.put("<", 5);
        PRECEDENCE.put("><", 5);
        PRECEDENCE.put("<=", 5);
        PRECEDENCE.put(">=", 5);
        PRECEDENCE.put("<<", 6);
        PRECEDENCE.put(">>", 6);
        PRECEDENCE.put("+", 7);
        PRECEDENCE.put("-", 7);
        PRECEDENCE.put("*", 8);
        PRECEDENCE.put("/", 8);
        PRECEDENCE.put("%", 8);
        PRECEDENCE.put("idiv", 8);

        register("Multiplication", "*", (a, b) -> a * b);
        register("Division", "/", (a, b) -> a / b);
        register("Modulo", "%", (a, b) -> a % b);
        register("Integer Division", "idiv", (a, b) -> (double) ((int) (a / b)));
        register("Equals", "==", (a, b) -> a.equals(b));
        register("Not Equals", "!=", (a, b) -> !a.equals(b));
        register("Less Than", "<", (a, b) -> a < b);
        register("Greater Than", ">", (a, b) -> a > b);
        register("Less Than or Equal", "<=", (a, b) -> a <= b);
        register("Greater Than or Equal", ">=", (a, b) -> a >= b);
        register("Bitwise AND", "and", (a, b) -> (double) (a.intValue() & b.intValue()));
        register("Bitwise OR", "or", (a, b) -> (double) (a.intValue() | b.intValue()));
        register("Bitwise XOR", "xor", (a, b) -> (double) (a.intValue() ^ b.intValue()));
        register("Left Shift", "<<", (a, b) -> (double) (a.intValue() << b.intValue()));
        register("Right Shift", ">>", (a, b) -> (double) (a.intValue() >> b.intValue()));

        register("Absolute Value", "abs", Math::abs);
        register("Natural Logarithm", "log", Math::log);
        register("Base-10 Logarithm", "log10", Math::log10);
        register("Floor", "floor", Math::floor);
        register("Ceiling", "ceil", Math::ceil);
        register("Round", "round", a -> (double) Math.round(a));
        register("Square Root", "sqrt", Math::sqrt);
        register("Sine", "sin", Math::sin);
        register("Cosine", "cos", Math::cos);
        register("Tangent", "tan", Math::tan);
        register("Arcsine", "asin", Math::asin);
        register("Arccosine", "acos", Math::acos);
        register("Arctangent", "atan", Math::atan);
        register("Bitwise NOT", "flip", a -> (double) ~(a.intValue()));
        register("Square", "square", a -> a * a);
        register("Length (abs)", "length", a -> Math.abs(a));

        List<Class<?>> lists = List.of(Vars.class, Groups.class);

        for (var clazz : lists) {
            CLASSES.put(clazz.getSimpleName(), clazz);
        }

        Log.debug("Registered " + BINARY_OPERATORS.size() + " binary operators");
        Log.debug("Registered " + UNARY_OPERATORS.size() + " unary operators");
        Log.debug("Registered " + CLASSES.keySet() + " classes");
    }

    public void register(String name, String sign, BiFunction<Double, Double, Object> function) {
        BINARY_OPERATORS.put(name, new BinaryOperator(name, sign, function));
    }

    public void register(String name, String sign, Function<Double, Object> function) {
        UNARY_OPERATORS.put(name, new UnaryOperator(name, sign, function));
    }

    private Queue<String> toExpressionQueue(String expr, Map<String, Object> variables) {
        var matcher = WorkflowNode.VARIABLE_PATTERN.matcher(expr);
        var builder = new StringBuilder();
        int lastEnd = 0;

        for (var match : matcher.results().toList()) {
            String path = match.group(1);

            Log.debug("Resolving variable: " + path);

            builder.append(expr, lastEnd, match.start());

            var firstDot = path.indexOf('.');

            if (firstDot != -1) {
                var key = path.substring(0, firstDot);
                var obj = variables.get(key);

                if (obj == null) {
                    Log.debug("Variable not found: " + key);
                }

                var variable = access(obj, path.substring(firstDot + 1));

                if (variable == null) {
                    Log.debug("Variable not found: " + path);
                }

                builder.append(variable);

            } else {
                var variable = variables.get(path);

                if (variable == null) {
                    Log.debug("Variable not found: " + path);
                }

                builder.append(variable);
            }

            lastEnd = match.end();
        }

        builder.append(expr.substring(lastEnd));

        var parameter = builder.toString();

        Stack<String> ops = new Stack<>();
        Queue<String> output = new LinkedList<>();

        try (Scanner scanner = new Scanner(parameter)) {
            while (scanner.hasNext()) {
                if (scanner.hasNextDouble()) {
                    output.add(scanner.next());
                } else {
                    String token = scanner.next();

                    if (UNARY_OPERATORS.containsKey(token)) {
                        ops.push(token);
                    } else if (BINARY_OPERATORS.containsKey(token)) {
                        while (!ops.isEmpty() && PRECEDENCE.getOrDefault(ops.peek(), 0) >= PRECEDENCE.get(token)) {
                            output.add(ops.pop());
                        }
                        ops.push(token);
                    } else if ("(".equals(token)) {
                        ops.push(token);
                    } else if (")".equals(token)) {
                        while (!"(".equals(ops.peek())) {
                            output.add(ops.pop());
                        }
                        ops.pop();
                        if (!ops.isEmpty() && UNARY_OPERATORS.containsKey(ops.peek())) {
                            output.add(ops.pop());
                        }
                    } else {
                        output.add(token);
                    }
                }
            }
        }

        while (!ops.isEmpty())
            output.add(ops.pop());

        return output;
    }

    public Boolean evaluateAsBoolean(String expr, Map<String, Object> variables) {
        return evaluate(Boolean.class, expr, variables);
    }

    public Double evaluateAsDouble(String expr, Map<String, Object> variables) {
        return evaluate(Double.class, expr, variables);
    }

    private <T> T evaluate(Class<T> type, String expr, Map<String, Object> variables) {
        Map<String, Object> vars = new HashMap<>();
        Queue<String> rpn = toExpressionQueue(expr, variables);
        Stack<Object> stack = new Stack<>();

        for (String token : rpn) {
            if (BINARY_OPERATORS.containsKey(token)) {
                double b = (double) stack.pop();
                double a = (double) stack.pop();
                stack.push(BINARY_OPERATORS.get(token).getFunction().apply(a, b));
            } else if (UNARY_OPERATORS.containsKey(token)) {
                double a = (double) stack.pop();
                stack.push(UNARY_OPERATORS.get(token).getFunction().apply(a));
            } else if (vars.containsKey(token)) {
                stack.push(vars.get(token));
            } else {
                try {
                    stack.push(Double.parseDouble(token));
                } catch (Exception e) {
                    throw new WorkflowError("Invalid token: " + token, e);
                }
            }
        }
        var result = stack.pop();

        if (result == null) {
            throw new WorkflowError("Null result of expression: " + expr);
        }

        try {
            return type.cast(result);
        } catch (ClassCastException e) {
            throw new WorkflowError("Invalid result type of expression: " + expr + ", result type: "
                    + result.getClass().getSimpleName() + ", expected type: " + type.getSimpleName(), e);
        }
    }

    public static <T> T access(Object value, String path) {
        if (value == null) {
            Log.debug("Trying to access null value: " + path);
            return null;
        }

        var fields = path.trim().split("\\.");

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

    public <T> T consume(String path, Map<String, Object> variables) {
        if (path == null) {
            Log.debug("Trying to access null path");
            return null;
        }

        if (path.isBlank()) {
            Log.debug("Trying to access empty path");
            return null;
        }

        var fields = path.trim().split("\\.");

        Object result = null;
        var startIndex = 1;

        if (Character.isUpperCase(fields[0].charAt(0))) {
            var clazz = CLASSES.get(fields[0].trim());

            Log.debug("Trying to access class: " + fields[0]);

            if (clazz == null) {
                throw new WorkflowError("Class not registered: " + fields[0]);
            }

            if (fields.length == 1) {
                return (T) clazz;
            }

            try {
                Log.debug("Trying to access field: " + fields[1] + " of class " + clazz.getName());
                result = clazz.getField(fields[1]).get(null);
                startIndex = 2;
            } catch (IllegalArgumentException e) {
                throw new WorkflowError("Invalid class: " + path + " " + e.getMessage(), e);
            } catch (IllegalAccessException e) {
                throw new WorkflowError("Can not access field: " + fields[0] + " of " + path + " on value "
                        + result, e);
            } catch (NoSuchFieldException e) {
                throw new WorkflowError("Field not found: " + fields[0] + " of " + path + " on value " + result, e);
            } catch (SecurityException e) {
                throw new WorkflowError("Can not access field: " + fields[0] + " of " + path + " on value " + result,
                        e);
            }

        } else {
            Log.debug("Trying to access variable: " + fields[0]);
            result = variables.get(fields[0]);
        }

        if (fields.length == 1) {
            return (T) result;
        }

        for (int index = startIndex; index < fields.length; index++) {
            try {
                Log.debug("Trying to access field: " + fields[index] + " of " + path + " on value " + result);
                result = result.getClass().getDeclaredField(fields[index]).get(result);
            } catch (IllegalArgumentException //
                    | IllegalAccessException //
                    | NoSuchFieldException
                    | SecurityException e//
            ) {
                throw new IllegalStateException(
                        "Field not found: " + fields[index] + " of " + path + " on value " + result, e);
            }
        }

        return (T) result;
    }
}
