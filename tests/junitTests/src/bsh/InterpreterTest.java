package bsh;

import org.junit.Test;

import java.lang.ref.WeakReference;

public class InterpreterTest {


    /**
     * <a href="http://code.google.com/p/beanshell2/issues/detail?id=50">Issue #50</a>
     */
    @Test(timeout = 10000)
    public void check_for_memory_leak() throws Exception {
        final WeakReference<Object> reference = new WeakReference<Object>(new Interpreter().eval("x = new byte[1024 * 2024]; return x;"));
        while (reference.get() != null) {
            System.gc();
            Thread.sleep(100);
        }
    }

    /*
     @Test
     public void check_system_object() throws Exception {
         TestUtil.eval("bsh.system.foo = \"test\";");
         final Object result = TestUtil.eval("return bsh.system.foo;");
         assertEquals("test", result);
         assertNull(TestUtil.eval("return bsh.system.shutdownOnExit;"));
         Interpreter.setShutdownOnExit(false);
         assertEquals(Boolean.FALSE, TestUtil.eval("return bsh.system.shutdownOnExit;"));
     }
     */

}
