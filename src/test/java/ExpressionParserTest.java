import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import mindustrytool.workflow.expressions.ExpressionParser;

public class ExpressionParserTest {
    ExpressionParser parser = new ExpressionParser();

    Map<String, Object> variables = Map.of("a", 1, "b", 2);

    @Test
    void testAddition() {
        assertEquals(parser.evaluate(Object.class, "1 + 2", variables), 3d);
        assertEquals(parser.evaluate(Object.class, "{{a}} + {{ b}}", variables), 3d);
    }
}
