package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import static bsh.TestUtil.eval;
import static org.junit.Assert.assertEquals;

@RunWith(FilteredTestRunner.class)
public class GoogleReports {

    @Test
    @SuppressWarnings({"ConstantIfStatement"})
    public void while_loop() throws Exception {
        int loopCount = 0;
        do {
            loopCount++;
            if (loopCount > 100) {
                break;
            }
            if (true) {
                continue;
            }
        } while (false);
        assertEquals(1, loopCount);
        loopCount = (Integer) eval(
                "int loopCount = 0;",
                "do{", "	loopCount++;",
                "	if (loopCount > 100) break;",
                "	if (true) continue;",
                "} while (false);",
                "return loopCount");
        assertEquals(1, loopCount);
        loopCount = (Integer) eval(
                "int loopCount = 0;",
                "while (loopCount < 1) {",
                "	loopCount++;",
                "	if (loopCount > 100) return loopCount;",
                "	if (true) continue;", "}",
                "return loopCount");
        assertEquals(1, loopCount);
        assertEquals(Boolean.TRUE, eval("while(true) { break; return false; } return true;"));
        assertEquals(Boolean.TRUE, eval("do { break; return false; } while(true); return true;"));
        loopCount = (Integer) eval(
                "int loopCount = 0;",
                "while (++loopCount < 2);",
                "return loopCount");
        assertEquals(2, loopCount);
        loopCount = (Integer) eval(
                "int loopCount = 0;",
                "do { } while (++loopCount < 2);",
                "return loopCount");
        assertEquals(2, loopCount);
    }


    /**
     * <a href="http://code.google.com/p/beanshell2/issues/detail?id=60">issue#60</a>
     @Test public void issue_60() throws Exception {
     final String script =
     "String foo = null;" +
     "if (foo != null && foo.length() > 0) return \"not empty\";" +
     "return \"empty\";";
     final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
     scriptEngineManager.registerEngineName("beanshell", new BshScriptEngineFactory());
     final ScriptEngine engine = scriptEngineManager.getEngineByName("beanshell");
     assertNotNull(engine);
     Object result;
     result = engine.eval(script);
     assertEquals("empty", result);
     result = eval(script);
     assertEquals("empty", result);
     }
     */
}
