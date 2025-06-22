package mindustrytool.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ExpressionParser {
    private final Map<String, Integer> PRECEDENCE = new HashMap<>();
    private final Map<String, BiFunction<Double, Double, Double>> BINARY_OPERATORS = new HashMap<>();
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
        BINARY_OPERATORS.put("==", (a, b) -> a.equals(b) ? 1.0 : 0.0);
        BINARY_OPERATORS.put("!=", (a, b) -> !a.equals(b) ? 1.0 : 0.0);
        BINARY_OPERATORS.put("<", (a, b) -> a < b ? 1.0 : 0.0);
        BINARY_OPERATORS.put("><", (a, b) -> a > b ? 1.0 : 0.0);
        BINARY_OPERATORS.put("<=", (a, b) -> a <= b ? 1.0 : 0.0);
        BINARY_OPERATORS.put(">=", (a, b) -> a >= b ? 1.0 : 0.0);
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

    private Queue<String> toExpressionQueue(String expr) {
        Stack<String> ops = new Stack<>();
        Queue<String> output = new LinkedList<>();

        try (Scanner scanner = new Scanner(expr)) {
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
                    } else if (token.contains("[")) {
                        int start = token.indexOf('[');
                        int end = token.lastIndexOf(']');
                        String type = token.substring(0, start);
                        String contents = token.substring(start + 1, end);
                        switch (type) {
                            case "array":
                                String[] nums = contents.split(",");
                                for (String n : nums)
                                    output.add(n.trim());
                                output.add("array");
                                break;
                            case "vec2":
                                String[] xy = contents.split(",");
                                output.add(xy[0].trim());
                                output.add(xy[1].trim());
                                output.add("vec2");
                                break;
                            case "string":
                                output.add("\"" + contents + "\"");
                                break;
                            case "map":
                                String[] pairs = contents.split(",");
                                for (int i = 0; i < pairs.length; i += 2) {
                                    String key = pairs[i].trim();
                                    String value = (i + 1 < pairs.length) ? pairs[i + 1].trim() : "0";
                                    output.add(key);
                                    output.add(value);
                                }
                                output.add("map");
                                break;
                            default:
                                throw new RuntimeException("Unknown type: " + type);
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

    public double evaluate(String expr, Map<String, Double> vars) {
        Queue<String> rpn = toExpressionQueue(expr);
        Stack<Double> stack = new Stack<>();

        for (String token : rpn) {
            if (BINARY_OPERATORS.containsKey(token)) {
                double b = stack.pop();
                double a = stack.pop();
                stack.push(BINARY_OPERATORS.get(token).apply(a, b));
            } else if (UNARY_OPERATORS.containsKey(token)) {
                double a = stack.pop();
                stack.push(UNARY_OPERATORS.get(token).apply(a));
            } else if (vars.containsKey(token)) {
                stack.push(vars.get(token));
            } else if (token.equals("vec2")) {
                double y = stack.pop();
                double x = stack.pop();
                stack.push(Math.sqrt(x * x + y * y));
            } else if (token.equals("array")) {
                List<Double> values = new ArrayList<>();
                while (!stack.isEmpty())
                    values.add(stack.pop());
                stack.push((double) values.size()); // Just return size for demo
            } else if (token.equals("map")) {
                Map<String, Double> map = new HashMap<>();
                while (stack.size() >= 2) {
                    double val = stack.pop();
                    String key = String.valueOf(stack.pop().intValue());
                    map.put(key, val);
                }
                stack.push((double) map.size()); // Just return size for demo
            } else if (token.startsWith("\"")) {
                System.out.println("Parsed string: " + token.substring(1, token.length() - 1));
                stack.push(0.0);
            } else {
                stack.push(Double.parseDouble(token));
            }
        }
        return stack.pop();
    }
}
