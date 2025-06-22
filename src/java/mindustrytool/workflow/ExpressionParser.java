package mindustrytool.workflow;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Function;

import arc.util.Log;
import mindustrytool.workflow.errors.WorkflowError;

public class ExpressionParser {
    private final Map<String, Integer> PRECEDENCE = new HashMap<>();
    private final Map<String, BiFunction<Double, Double, Object>> BINARY_OPERATORS = new HashMap<>();
    private final Map<String, Function<Double, Double>> UNARY_OPERATORS = new HashMap<>();

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

        // Binary operations
        BINARY_OPERATORS.put("+", (a, b) -> a + b);
        BINARY_OPERATORS.put("-", (a, b) -> a - b);
        BINARY_OPERATORS.put("*", (a, b) -> a * b);
        BINARY_OPERATORS.put("/", (a, b) -> a / b);
        BINARY_OPERATORS.put("%", (a, b) -> a % b);
        BINARY_OPERATORS.put("idiv", (a, b) -> (double) ((int) (a / b)));
        BINARY_OPERATORS.put("==", (a, b) -> a.equals(b));
        BINARY_OPERATORS.put("!=", (a, b) -> !a.equals(b));
        BINARY_OPERATORS.put("<", (a, b) -> a < b);
        BINARY_OPERATORS.put("><", (a, b) -> a > b);
        BINARY_OPERATORS.put("<=", (a, b) -> a <= b);
        BINARY_OPERATORS.put(">=", (a, b) -> a >= b);
        BINARY_OPERATORS.put("and", (a, b) -> (double) (a.intValue() & b.intValue()));
        BINARY_OPERATORS.put("or", (a, b) -> (double) (a.intValue() | b.intValue()));
        BINARY_OPERATORS.put("xor", (a, b) -> (double) (a.intValue() ^ b.intValue()));
        BINARY_OPERATORS.put("<<", (a, b) -> (double) (a.intValue() << b.intValue()));
        BINARY_OPERATORS.put(">>", (a, b) -> (double) (a.intValue() >> b.intValue()));

        // Unary operations
        UNARY_OPERATORS.put("abs", Math::abs);
        UNARY_OPERATORS.put("log", Math::log);
        UNARY_OPERATORS.put("log10", Math::log10);
        UNARY_OPERATORS.put("floor", Math::floor);
        UNARY_OPERATORS.put("ceil", Math::ceil);
        UNARY_OPERATORS.put("round", a -> (double) Math.round(a));
        UNARY_OPERATORS.put("sqrt", Math::sqrt);
        UNARY_OPERATORS.put("sin", Math::sin);
        UNARY_OPERATORS.put("cos", Math::cos);
        UNARY_OPERATORS.put("tan", Math::tan);
        UNARY_OPERATORS.put("asin", Math::asin);
        UNARY_OPERATORS.put("acos", Math::acos);
        UNARY_OPERATORS.put("atan", Math::atan);
        UNARY_OPERATORS.put("flip", a -> (double) ~(a.intValue()));
        UNARY_OPERATORS.put("square", a -> a * a);
        UNARY_OPERATORS.put("length", a -> Math.abs(a));
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

    public Object evaluate(String expr, Map<String, Object> variables) {
        return evaluate(Object.class, expr, variables);
    }

    public <T> T evaluate(Class<T> type, String expr, Map<String, Object> variables) {
        Map<String, Object> vars = new HashMap<>();
        Queue<String> rpn = toExpressionQueue(expr, variables);
        Stack<Object> stack = new Stack<>();

        for (String token : rpn) {
            if (BINARY_OPERATORS.containsKey(token)) {
                double b = (double) stack.pop();
                double a = (double) stack.pop();
                stack.push(BINARY_OPERATORS.get(token).apply(a, b));
            } else if (UNARY_OPERATORS.containsKey(token)) {
                double a = (double) stack.pop();
                stack.push(UNARY_OPERATORS.get(token).apply(a));
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
}
