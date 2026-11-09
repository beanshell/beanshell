package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** Assigning a name that a scripted setter matches. */
@RunWith(FilteredTestRunner.class)
public class NameSpacePropertySetterTest {

    /** Without any interpreter at hand, the setter can't run, so the
     * assignment creates a variable, as BeanShell 2.x did. */
    @Test
    public void setter_skipped_without_interpreter() throws Exception {
        final NameSpace ns = new NameSpace((NameSpace) null, "no interpreter");
        new Interpreter().eval("setFoo(v) { throw new Error(\"ran\"); }", ns);
        ns.setVariableOrProperty("foo", new Primitive(5), false);
        assertEquals(new Primitive(5), ns.getVariable("foo"));
    }

    /** With the interpreter that made the class manager, the same setter
     * runs, even though the assignment passes no interpreter. */
    @Test
    public void setter_runs_with_class_manager_interpreter() throws Exception {
        final Interpreter bsh = new Interpreter();
        bsh.eval("setFoo(v) { throw new Error(\"ran\"); }");
        UtilTargetError e = assertThrows(UtilTargetError.class, () ->
            bsh.getNameSpace().setVariableOrProperty(
                "foo", new Primitive(5), false));
        assertEquals("ran", e.getCause().getMessage());
    }
}
