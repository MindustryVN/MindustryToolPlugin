package test.java;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class ExpressionParserTest {
    private final Map<String, Double> vars = Map.of(
            "a", 10.0,
            "b", 20.0,
            "c", 3.0,
            "d", 2.0);

    @Test
    void testArithmetic() {
        assertEquals(30.0, ExpressionParser.evaluate("a + b", vars));
        assertEquals(10.0, ExpressionParser.evaluate("b - a", vars));
        assertEquals(20.0, ExpressionParser.evaluate("a * d", vars));
        assertEquals(10.0, ExpressionParser.evaluate("b / d", vars));
        assertEquals(10.0, ExpressionParser.evaluate("b idiv d", vars));
        assertEquals(0.0, ExpressionParser.evaluate("b % a", vars));
    }

    @Test
    void testComparison() {
        assertEquals(1.0, ExpressionParser.evaluate("a == 10", vars));
        assertEquals(1.0, ExpressionParser.evaluate("a != b", vars));
        assertEquals(1.0, ExpressionParser.evaluate("a < b", vars));
        assertEquals(1.0, ExpressionParser.evaluate("b > a", vars));
        assertEquals(1.0, ExpressionParser.evaluate("a <= 10", vars));
        assertEquals(1.0, ExpressionParser.evaluate("b >= 20", vars));
    }

    @Test
    void testBitwise() {
        assertEquals(2.0, ExpressionParser.evaluate("a and d", vars));
        assertEquals(10.0, ExpressionParser.evaluate("a or d", vars));
        assertEquals(8.0, ExpressionParser.evaluate("a xor d", vars));
        assertEquals(20.0, ExpressionParser.evaluate("a << 1", vars));
        assertEquals(5.0, ExpressionParser.evaluate("b >> 2", vars));
        assertEquals(-11.0, ExpressionParser.evaluate("flip(a)", vars));
    }

    @Test
    void testUnaryFunctions() {
        assertEquals(10.0, ExpressionParser.evaluate("abs(-a)", vars));
        assertEquals(Math.log(10), (Double) ExpressionParser.evaluate("log(10)", vars), 0.0001);
        assertEquals(3.0, ExpressionParser.evaluate("log10(1000)", vars));
        assertEquals(1.0, ExpressionParser.evaluate("floor(1.9)", vars));
        assertEquals(2.0, ExpressionParser.evaluate("ceil(1.1)", vars));
        assertEquals(3.0, ExpressionParser.evaluate("round(2.6)", vars));
        assertEquals(5.0, ExpressionParser.evaluate("sqrt(25)", vars));
        assertEquals(0.0, ExpressionParser.evaluate("sin(0)", vars));
        assertEquals(1.0, ExpressionParser.evaluate("cos(0)", vars));
        assertEquals(0.0, ExpressionParser.evaluate("tan(0)", vars));
        assertEquals(0.0, ExpressionParser.evaluate("asin(0)", vars));
        assertEquals(0.0, ExpressionParser.evaluate("atan(0)", vars));
        assertEquals(1.0, ExpressionParser.evaluate("acos(1)", vars));
        assertEquals(16.0, ExpressionParser.evaluate("square(4)", vars));
    }

    @Test
    void testStructures() {
        assertEquals(List.of(1.0, 2.0, 3.0, 4.0, 5.0), ExpressionParser.evaluate("array[1,2,3,4,5]", vars));
        assertEquals(5.0, ExpressionParser.evaluate("vec2[3,4]", vars));
        assertEquals("This is a string", ExpressionParser.evaluate("string[This is a string]", vars));
        Map<String, Double> expectedMap = Map.of("key1", 1.0, "key2", 2.0);
        assertEquals(expectedMap, ExpressionParser.evaluate("map[key1, 1, key2, 2]", vars));
    }
}
