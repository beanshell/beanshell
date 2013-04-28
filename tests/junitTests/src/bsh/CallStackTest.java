package bsh;

import junit.framework.Assert;
import org.junit.Test;

public class CallStackTest {

    /**
     * <a href="http://code.google.com/p/beanshell2/issues/detail?id=35">Issue 35 - CallStack class should be
     * Serializable</a>
     *
     * @throws Exception in case of failure
     */
    @Test
    public void callStack_should_be_serializable() throws Exception {
        final NameSpace nameSpace = new NameSpace(null, new BshClassManager(), "test");
        nameSpace.setLocalVariable("test", "test", false);
        final CallStack stack = (CallStack) TestUtil.serDeser((java.io.Serializable) new CallStack(nameSpace));
        Assert.assertEquals("test", stack.top().get("test", null));
    }
}
