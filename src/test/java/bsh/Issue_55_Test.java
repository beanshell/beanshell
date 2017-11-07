package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import javax.script.ScriptException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(FilteredTestRunner.class)
public class Issue_55_Test {

    @Test
    public void check_BshScriptEngineFactory() throws Exception {
        final String script = "a = null; return \"a=\" + a;\n";
        final Object interpreterResult = new Interpreter().eval(script);
        final Object scriptEngineResult = new BshScriptEngineFactory().getScriptEngine().eval(script);
        assertEquals(interpreterResult, scriptEngineResult);
    }


    @Test
    public void check_ExternalNameSpace() throws Exception {
        final ExternalNameSpace externalNameSpace = new ExternalNameSpace();
        externalNameSpace.setVariable("a", Primitive.NULL, false);
        assertTrue("map should contain variable 'a'", externalNameSpace.getMap().containsKey("a"));
        assertNull("variable 'a' should have value <NULL>", externalNameSpace.getMap().get("a"));
    }

    @Test
    public void check_ParseExceptionLineNumber() throws Exception {
        final String script = "print(\"test\";";
        try {
            new BshScriptEngineFactory().getScriptEngine().eval(script);
            fail("expected script exception");
        } catch (ScriptException e) {
            assertEquals(1, e.getLineNumber());
        }
    }

}
