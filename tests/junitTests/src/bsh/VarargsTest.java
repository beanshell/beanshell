package bsh;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VarargsTest {

    @Test
    public void calling_java_varargs_method_should_be_possible() throws Exception {
        final Interpreter interpreter = new Interpreter();
        interpreter.set("helper", new ClassWithVarargMethods());
        @SuppressWarnings({"unchecked"})
        final List<Object> list = (List<Object>) interpreter.eval("helper.list(1,2,3)");
        Assert.assertEquals(Arrays.<Object>asList(1, 2, 3), list);
    }


    @Test
    public void calling_java_varargs_wit_old_syntax_should_be_possible() throws Exception {
        final Interpreter interpreter = new Interpreter();
        interpreter.set("helper", new ClassWithVarargMethods());
        @SuppressWarnings({"unchecked"})
        final List<Object> list = (List<Object>) interpreter.eval("helper.list(new Object[] {1,2,3})");
        Assert.assertEquals(Arrays.<Object>asList(1, 2, 3), list);
    }


    public static class ClassWithVarargMethods {

        public List<Object> list(final Object... args) {
            return new ArrayList<Object>(Arrays.asList(args));
        }


        public List<Object> list(final List<Object> list, final Object... args) {
            list.addAll(Arrays.asList(args));
            return list;
        }
    }
}
