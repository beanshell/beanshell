package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.lang.invoke.MethodType;


@RunWith(FilteredTestRunner.class)
public class InvocableTest {

    static class Tester {
        public String test = "foo";
        public String tester = "foo";
        Tester(String t) {
            test = t;
        }
        public static String test(String... t) {
            return new Tester(t[0]).test;
        }
        public static String test(String t) {
            return new Tester(t).test;
        }
    }

    @Test
    public void field_access() throws Exception {
        Invocable t = Invocable.get(Tester.class.getField("test"));

        assertThat(t.getDeclaringClass(), equalTo(Tester.class));
        assertThat(t.getLastParameterIndex(), equalTo(0));
        assertThat(t.methodType(), equalTo(MethodType.methodType(String.class, new Class[] {String.class})));
        assertThat(t.getMethodDescriptor(), equalTo("(Ljava/lang/String;)Ljava/lang/String;"));
        assertThat(t.getModifiers(), equalTo(1));
        assertThat(t.getName(), equalTo("test"));
        assertThat(t.getParameterCount(), equalTo(1));
        assertThat(t.getParameterTypes(), equalTo(new Class[] {String.class}));
        assertThat(t.getParamTypeDescriptors(), equalTo(new String[] {"Ljava/lang/String;"}));
        assertThat(t.getReturnType(), equalTo(String.class));
        assertThat(t.getReturnTypeDescriptor(), equalTo("Ljava/lang/String;"));
        assertThat(t.getVarArgsComponentType(), equalTo(Void.TYPE));
        assertThat(t.getVarArgsType(), equalTo(Void.TYPE));
        assertThat(t.hashCode(), equalTo(Invocable.get(Tester.class.getField("test")).hashCode()));

        assertFalse(t.isGetter());
        assertFalse(t.isInnerClass());
        assertFalse(t.isSetter());
        assertFalse(t.isStatic());
        assertFalse(t.isSynthetic());
        assertFalse(t.isVarArgs());
        assertTrue(t.equals(t));
        assertTrue(t.equals(Invocable.get(Tester.class.getField("test"))));
        assertFalse(t.equals(Invocable.get(Tester.class.getField("tester"))));
        assertFalse(t.equals(null));
        assertFalse(t.equals(Invocable.get(Tester.class.getDeclaredConstructors()[0])));
    }

    @Test
    public void constructor_invocable() throws Exception {
        Invocable t = Invocable.get(Tester.class.getDeclaredConstructors()[0]);

        assertThat(t.getDeclaringClass(), equalTo(Tester.class));
        assertThat(t.getLastParameterIndex(), equalTo(0));
        assertThat(t.methodType(), equalTo(MethodType.methodType(Tester.class, new Class[] {String.class})));
        assertThat(t.getMethodDescriptor(), equalTo("(Ljava/lang/String;)Lbsh/InvocableTest$Tester;"));
        assertThat(t.getModifiers(), equalTo(0));
        assertThat(t.getName(), equalTo(Tester.class.getName()));
        assertThat(t.getParameterCount(), equalTo(1));
        assertThat(t.getParameterTypes(), equalTo(new Class[] {String.class}));
        assertThat(t.getParamTypeDescriptors(), equalTo(new String[] {"Ljava/lang/String;"}));
        assertThat(t.getReturnType(), equalTo(Tester.class));
        assertThat(t.getReturnTypeDescriptor(), equalTo("Lbsh/InvocableTest$Tester;"));
        assertThat(t.getVarArgsComponentType(), equalTo(Void.TYPE));
        assertThat(t.getVarArgsType(), equalTo(Void.TYPE));
        assertThat(t.hashCode(), equalTo(Invocable.get(Tester.class.getDeclaredConstructors()[0]).hashCode()));

        assertFalse(t.isGetter());
        assertTrue(t.isInnerClass());
        assertFalse(t.isSetter());
        assertTrue(t.isStatic());
        assertFalse(t.isSynthetic());
        assertFalse(t.isVarArgs());
        assertTrue(t.equals(Invocable.get(Tester.class.getDeclaredConstructors()[0])));
    }

    @Test
    public void method_invocable() throws Exception {
        Invocable t = Invocable.get(Tester.class.getDeclaredMethod("test", new Class[] {String[].class}));

        assertThat(t.getDeclaringClass(), equalTo(Tester.class));
        assertThat(t.getLastParameterIndex(), equalTo(0));
        assertThat(t.methodType(), equalTo(MethodType.methodType(String.class, new Class[] {String[].class})));
        assertThat(t.getMethodDescriptor(), equalTo("([Ljava/lang/String;)Ljava/lang/String;"));
        assertThat(t.getModifiers(), equalTo(137));
        assertThat(t.getName(), equalTo("test"));
        assertThat(t.getParameterCount(), equalTo(1));
        assertThat(t.getParameterTypes(), equalTo(new Class[] {String[].class}));
        assertThat(t.getParamTypeDescriptors(), equalTo(new String[] {"[Ljava/lang/String;"}));
        assertThat(t.getReturnType(), equalTo(String.class));
        assertThat(t.getReturnTypeDescriptor(), equalTo("Ljava/lang/String;"));
        assertThat(t.getVarArgsComponentType(), equalTo(String.class));
        assertThat(t.getVarArgsType(), equalTo(String[].class));
        assertThat(t.hashCode(), equalTo(Invocable.get(Tester.class.getDeclaredMethod("test", new Class[] {String[].class})).hashCode()));

        assertFalse(t.isGetter());
        assertFalse(t.isInnerClass());
        assertFalse(t.isSetter());
        assertTrue(t.isStatic());
        assertFalse(t.isSynthetic());
        assertTrue(t.isVarArgs());
        assertTrue(t.equals(Invocable.get(Tester.class.getDeclaredMethod("test", new Class[] {String[].class}))));
        assertFalse(t.equals(Invocable.get(Tester.class.getDeclaredMethod("test", new Class[] {String.class}))));
        assertFalse(t.equals(Invocable.get(Tester.class.getField("test"))));
    }

}

