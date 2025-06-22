package java;

import org.junit.jupiter.api.Test;

import mindustrytool.workflow.ExpressionParser;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class ExpressionParserTest {
    ExpressionParser parser = new ExpressionParser();
    private final Map<String, Double> vars = Map.of(
            "a", 10.0,
            "b", 20.0,
            "c", 3.0,
            "d", 2.0);

    @Test
    void testArithmetic() {
        assertEquals(30.0, parser.evaluate("a + b", vars));
        assertEquals(10.0, parser.evaluate("b - a", vars));
        assertEquals(20.0, parser.evaluate("a * d", vars));
        assertEquals(10.0, parser.evaluate("b / d", vars));
        assertEquals(10.0, parser.evaluate("b idiv d", vars));
        assertEquals(0.0, parser.evaluate("b % a", vars));
    }

    @Test
    void testComparison() {
        assertEquals(1.0, parser.evaluate("a == 10", vars));
        assertEquals(1.0, parser.evaluate("a != b", vars));
        assertEquals(1.0, parser.evaluate("a < b", vars));
        assertEquals(1.0, parser.evaluate("b > a", vars));
        assertEquals(1.0, parser.evaluate("a <= 10", vars));
        assertEquals(1.0, parser.evaluate("b >= 20", vars));
    }

    @Test
    void testBitwise() {
        assertEquals(2.0, parser.evaluate("a and d", vars));
        assertEquals(10.0, parser.evaluate("a or d", vars));
        assertEquals(8.0, parser.evaluate("a xor d", vars));
        assertEquals(20.0, parser.evaluate("a << 1", vars));
        assertEquals(5.0, parser.evaluate("b >> 2", vars));
        assertEquals(-11.0, parser.evaluate("flip(a)", vars));
    }

    @Test
    void testUnaryFunctions() {
        assertEquals(10.0, parser.evaluate("abs(-a)", vars));
        assertEquals(Math.log(10), (Double) parser.evaluate("log(10)", vars), 0.0001);
        assertEquals(3.0, parser.evaluate("log10(1000)", vars));
        assertEquals(1.0, parser.evaluate("floor(1.9)", vars));
        assertEquals(2.0, parser.evaluate("ceil(1.1)", vars));
        assertEquals(3.0, parser.evaluate("round(2.6)", vars));
        assertEquals(5.0, parser.evaluate("sqrt(25)", vars));
        assertEquals(0.0, parser.evaluate("sin(0)", vars));
        assertEquals(1.0, parser.evaluate("cos(0)", vars));
        assertEquals(0.0, parser.evaluate("tan(0)", vars));
        assertEquals(0.0, parser.evaluate("asin(0)", vars));
        assertEquals(0.0, parser.evaluate("atan(0)", vars));
        assertEquals(1.0, parser.evaluate("acos(1)", vars));
        assertEquals(16.0, parser.evaluate("square(4)", vars));
    }

    @Test
    void testStructures() {
        assertEquals(List.of(1.0, 2.0, 3.0, 4.0, 5.0), parser.evaluate("array[1,2,3,4,5]", vars));
        assertEquals(5.0, parser.evaluate("vec2[3,4]", vars));
        assertEquals("This is a string", parser.evaluate("string[This is a string]", vars));
        Map<String, Double> expectedMap = Map.of("key1", 1.0, "key2", 2.0);
        assertEquals(expectedMap, parser.evaluate("map[key1, 1, key2, 2]", vars));
    }
}
