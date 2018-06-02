package bsh;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static bsh.TestUtil.eval;
import static bsh.TestUtil.toMap;
import static org.junit.Assert.assertEquals;

public class TryStatementTest {

    @Test
    public void try_catch_finally() throws Exception {
        final List<String> calls = new ArrayList<String>();
        final Object result = eval(
                toMap("calls", calls),
                "calls.add(\"start\");",
                "try {",
                "   calls.add(\"try\");",
                "} catch (Exception e) {",
                "   calls.add(\"catch\");",
                "} finally {",
                "   calls.add(\"finally\");",
                "}",
                "calls.add(\"after\");",
                "return \"return after try..catch..finally\";"
        );
        assertEquals("return after try..catch..finally", result);
        assertEquals("calls are :" + calls.toString(),
                Arrays.asList("start", "try", "finally", "after"),
                calls);
    }

    @Test
    public void execute_finally_when_try_block_contains_return() throws Exception {
        final List<String> calls = new ArrayList<String>();
        final Object result = eval(
                toMap("calls", calls),
                "calls.add(\"start\");",
                "try {",
                "   calls.add(\"try\");",
                "   return \"return from try\";",
                "} catch (Exception e) {",
                "   calls.add(\"catch\");",
                "} finally {",
                "   calls.add(\"finally\");",
                "}",
                "calls.add(\"after\");",
                "return \"return after try..catch..finally\";"
        );
        assertEquals("return from try", result);
        assertEquals("calls are :" + calls.toString(),
                Arrays.asList("start", "try", "finally"),
                calls);
    }


    @Test
    public void execute_finally_block_when_catch_block_throws_exception() throws Exception {
        final List<String> calls = new ArrayList<String>();
        final Object result = eval(
                toMap("calls", calls),
                "calls.add(\"start\");",
                "try {",
                "   calls.add(\"try\");",
                "   throw new Exception(\"inside try\");",
                "} catch (Exception e) {",
                "   calls.add(\"catch\");",
                "   throw new Exception(\"inside catch\");",
                "} finally {",
                "   calls.add(\"finally\");",
                "   return \"return from finally\";",
                "}",
                "calls.add(\"after\");",
                "return \"return after try..catch..finally\";"
        );
        assertEquals("return from finally", result);
        assertEquals("calls are :" + calls.toString(),
                Arrays.asList("start", "try", "catch", "finally"),
                calls);
    }


    @Test
    public void execute_finally_block_when_catch_block_contains_return_statement() throws Exception {
        final List<String> calls = new ArrayList<String>();
        final Object result = eval(
                toMap("calls", calls),
                "calls.add(\"start\");",
                "try {",
                "   calls.add(\"try\");",
                "   throw new Exception(\"inside try\");",
                "} catch (Exception e) {",
                "   calls.add(\"catch\");",
                "   return \"return from catch\";",
                "} finally {",
                "   calls.add(\"finally\");",
                "   return \"return from finally\";",
                "}",
                "calls.add(\"after\");",
                "return \"return after try..catch..finally\";"
        );
        assertEquals("return from finally", result);
        assertEquals("calls are :" + calls.toString(),
                Arrays.asList("start", "try", "catch", "finally"),
                calls);
    }


    @Test
    public void execute_finally_block_when_try_block_contains_return_statement() throws Exception {
        final Object result = eval(
                "try {",
                "   return \"return from try\";",
                "} finally {",
                "   return \"return from finally\";",
                "}",
                "return \"return after try..finally\";"
        );
        assertEquals("return from finally", result);
    }

}
