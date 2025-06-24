package mindustrytool.workflow.expressions;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(\\{\\{([^{}]+)\\}\\})" + // match {{ var }}
                    "|(\\d+\\\\.*\\d*)" + // match numbers
                    "|([a-zA-Z0-9_-]+)" + // match identifiers
                    "|\\S" // fallback: match any non-whitespace char
    );

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
        BINARY_OPERATORS.put(sign, new BinaryOperator(name, sign, function));
    }

    public void register(String name, String sign, Function<Double, Object> function) {
        UNARY_OPERATORS.put(sign, new UnaryOperator(name, sign, function));
    }

    private Queue<String> toExpressionQueue(String expr) {
        Stack<String> ops = new Stack<>();
        Queue<String> output = new LinkedList<>();

        Matcher matcher = TOKEN_PATTERN.matcher(expr);

        while (matcher.find()) {
            String token = matcher.group().trim();

            if (UNARY_OPERATORS.containsKey(token)) {
                ops.push(token);
                Log.debug("Push unary operator: " + token);
            } else if (BINARY_OPERATORS.containsKey(token)) {
                while (!ops.isEmpty() && PRECEDENCE.getOrDefault(ops.peek(), 0) >= PRECEDENCE.get(token)) {
                    output.add(ops.pop());
                }
                ops.push(token);
                Log.debug("Push binary operator: " + token);
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
                Log.debug("Push token: " + token);
                output.add(token);
            }
        }

        while (!ops.isEmpty()) {
            var value = ops.pop();
            output.add(value);
            Log.debug("Push rest token: " + value);
        }

        return output;
    }

    public Boolean evaluateAsBoolean(String expr, Map<String, Object> variables) {
        return evaluate(Boolean.class, expr, variables);
    }

    public Double evaluateAsDouble(String expr, Map<String, Object> variables) {
        return evaluate(Double.class, expr, variables);
    }

    public <T> T evaluate(Class<T> type, String expr, Map<String, Object> variables) {
        Map<String, Object> vars = new HashMap<>();
        Queue<String> rpn = toExpressionQueue(expr);
        Stack<Object> stack = new Stack<>();

        if (rpn.isEmpty()) {
            throw new WorkflowError("Empty stack, expression: " + expr);
        }

        for (String token : rpn) {
            token = token.trim();

            if (BINARY_OPERATORS.containsKey(token)) {
                double b = (double) stack.pop();
                double a = (double) stack.pop();
                var operation = BINARY_OPERATORS.get(token);
                var result = operation.getFunction().apply(a, b);
                Log.debug(a + " " + operation.getSign() + " " + b + " = " + result);
                stack.push(result);
            } else if (UNARY_OPERATORS.containsKey(token)) {
                double a = (double) stack.pop();
                var operation = UNARY_OPERATORS.get(token);
                var result = operation.getFunction().apply(a);
                Log.debug(a + " " + operation.getSign() + " = " + result);
                stack.push(result);
            } else if (vars.containsKey(token)) {
                stack.push(vars.get(token));
                Log.debug("Push variable: " + token);
            } else if ("true".equalsIgnoreCase(token) || "false".equalsIgnoreCase(token)) {
                stack.push(Boolean.parseBoolean(token));
            } else if (token.equals("null")) {
                stack.push(null);
                Log.debug("Push null");
            } else if (WorkflowNode.VARIABLE_PATTERN.matcher(token).matches()) {
                var variableName = token.replaceAll("\\{\\{([^{}]+)\\}\\}", "$1");
                var variable = consume(token.replaceAll(variableName, "$1"), variables);
                stack.push(variable);
                Log.debug("Push variable: " + variableName);
            } else {
                try {
                    stack.push(Double.parseDouble(token));
                    Log.debug("Push number: " + token);
                } catch (Exception e) {
                    throw new WorkflowError("Invalid token: <" + token + ">", e);
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
            throw new WorkflowError("Trying to access empty path");
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
