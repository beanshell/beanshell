package bsh;

import static org.junit.Assert.assertTrue;

import java.util.Vector;

import org.apache.bsf.BSFEngine;
import org.apache.bsf.BSFException;
import org.apache.bsf.BSFManager;
import org.junit.Test;

/** The Class TestBshBSF. */
public class TestBshBSF {

    /** The main method.
     * @param args the arguments
     * @throws BSFException the BSF exception */
    @Test
    public void test_bsf_engine() throws BSFException {
        final BSFManager mgr = new BSFManager();
        // register beanshell with the BSF framework
        final String[] extensions = {"bsh"};
        BSFManager.registerScriptingEngine("beanshell",
                "bsh.util.BeanShellBSFEngine", extensions);
        mgr.declareBean("foo", "fooString", String.class);
        mgr.declareBean("bar", "barString", String.class);
        mgr.registerBean("gee", "geeString");
        final BSFEngine beanshellEngine = mgr.loadScriptingEngine("beanshell");
        String script = "foo + bar + bsf.lookupBean(\"gee\")";
        Object result = beanshellEngine.eval("Test eval...", -1, -1, script);
        assertTrue(result.equals("fooStringbarStringgeeString"));
        // test apply()
        final Vector<String> names = new Vector<>();
        names.addElement("name");
        final Vector<String> vals = new Vector<>();
        vals.addElement("Pat");
        script = "name + name";
        result = beanshellEngine.apply("source string...", -1, -1, script,
                names, vals);
        assertTrue(result.equals("PatPat"));
        result = beanshellEngine.eval("Test eval...", -1, -1, "name");
        // name should not be set
        assertTrue(result == null);
        // Verify the primitives are unwrapped
        result = beanshellEngine.eval("Test eval...", -1, -1, "1+1");
        assertTrue(result instanceof Integer
                && ((Integer) result).intValue() == 2);
    }
}
