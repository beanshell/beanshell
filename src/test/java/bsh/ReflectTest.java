package bsh;


import static bsh.TestUtil.eval;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(FilteredTestRunner.class)
public class ReflectTest {

    @Test
    public void is_generated_class() throws Exception {
        Class<?> type = (Class<?>) eval(
            "class T1 {}",
            "return T1.class;"
        );
        assertTrue("scripted class is generated class",
                Reflect.isGeneratedClass(type));
    }

    @Test
    public void is_not_generated_class() throws Exception {
        assertFalse("java.lang.Object class is not generated class",
                Reflect.isGeneratedClass(Object.class));
    }

    @Test
    public void get_this_static_namespace_from_class() throws Exception {
        Class<?> type = (Class<?>) eval(
            "class T2 {}",
            "return T2.class;"
        );

        assertNotNull("getThisNS(type) is not null", Reflect.getThisNS(type));
        assertTrue("is a class namespace", Reflect.getThisNS(type).isClass);
        assertEquals("class static is type", type, Reflect.getThisNS(type).classStatic);
        assertNull("class instance is null", Reflect.getThisNS(type).classInstance);
    }

    @Test
    public void get_this_static_namespace_from_instance() throws Exception {
        Object object = eval(
            "class T3 {}",
            "return new T3();"
        );

        assertNotNull("getThisNS(object) is not null", Reflect.getThisNS(object));
        assertTrue("is a class namespace", Reflect.getThisNS(object).isClass);
        assertEquals("class static is object class",
                object.getClass(), Reflect.getThisNS(object).classStatic);
        assertEquals("class instance is object",
                object, Reflect.getThisNS(object).classInstance);
    }

    @Test
    public void get_variable_names_from_static_namespace() throws Exception {
        Class<?> type = (Class<?>) eval(
            "class T4 {",
                "static int sv1;",
                "static int sv2;",
                "static int sv3;",
                "v1 = 1;",
                "v2 = 2;",
                "v3 = 3;",
            "}",
            "return T4.class;"
        );
        NameSpace ns = Reflect.getThisNS(type);
        assertThat("variables has [sv1, sv2, sv3]",
                Arrays.asList(Reflect.getVariableNames(ns)),
                hasItems("sv1", "sv2", "sv3"));
    }

    @Test
    public void get_variable_names_from_instance_namespace() throws Exception {
        Object object = eval(
            "class T5 {",
                "static int sv1 = 0;",
                "static int sv2 = 1;",
                "static int sv3 = 2;",
                "v1 = 1;",
                "v2 = 2;",
                "v3 = 3;",
            "}",
            "return new T5();"
        );
        NameSpace ns = Reflect.getThisNS(object);
        assertThat("variables has [sv1, sv2, sv3, v1, v2, v3]",
                Arrays.asList(Reflect.getVariableNames(ns)),
                hasItems("sv1", "sv2", "sv3", "v1", "v2", "v3"));
    }

    @Test
    public void get_method_names_from_instance_namespace() throws Exception {
        Object object = eval(
            "class T6 {",
                "static sm1() {}",
                "static sm2() {}",
                "static sm3() {};",
                "m1() {}",
                "m2() {}",
                "m3() {}",
            "}",
            "return new T6();"
        );
        NameSpace ns = Reflect.getThisNS(object);
        assertThat("methods has [sm1, sm2, sm3, m1, m2, m3]",
                Arrays.asList(Reflect.getMethodNames(ns)),
                hasItems("sm1", "sm2", "sm3", "m1", "m2", "m3"));
    }

    @Test
    public void get_method_names_from_static_namespace() throws Exception {
        Class<?> type = (Class<?>) eval(
            "class T7 {",
                "static sm1() {}",
                "static sm2() {}",
                "static sm3() {};",
                "m1() {}",
                "m2() {}",
                "m3() {}",
            "}",
            "return T7.class;"
        );
        NameSpace ns = Reflect.getThisNS(type);
        assertThat("methods has [sm1, sm2, sm3]",
                Arrays.asList(Reflect.getMethodNames(ns)),
                hasItems("sm1", "sm2", "sm3"));
    }

    @Test
    public void get_static_method_from_class() throws Exception {
        Class<?> type = (Class<?>) eval(
            "class T7 {",
                "static sm1() {}",
                "static sm2() {}",
                "static sm3() {};",
                "m1() {}",
                "m2() {}",
                "m3() {}",
            "}",
            "return T7.class;"
        );
        BshMethod sm2 = Reflect.getMethod(type, "sm2", new Class<?>[0]);
        assertNotNull("get method sm2 is not null", sm2);
        assertEquals("method get is sm2", "sm2", sm2.getName());
        assertNull("gen method m2 is null",
                Reflect.getMethod(type, "m2", new Class<?>[0]));
    }

    @Test
    public void get_method_from_instance() throws Exception {
        Object object = eval(
            "class T9 {",
                "static sm1() {}",
                "static sm2() {}",
                "final static sm3() {};",
                "m1() {}",
                "m2() {}",
                "m3() {}",
            "}",
            "return new T9();"
        );
        BshMethod m2 = Reflect.getMethod(object, "m2", new Class<?>[0]);
        assertNotNull("get method m2 is not null", m2);
        assertEquals("method get is m2", "m2", m2.getName());
        assertNotNull("gen method sm2 is not null",
                Reflect.getMethod(object, "sm2", new Class<?>[0]));
    }

    @Test
    public void get_method_from_namespace() throws Exception {
        Object object = eval(
            "class T9 {",
                "static sm1() {}",
                "static sm2() {}",
                "static sm3() {};",
                "m1() {}",
                "m2() {}",
                "m3() {}",
            "}",
            "return new T9();"
        );
        NameSpace ns = Reflect.getThisNS(object);
        BshMethod m2 = Reflect.getMethod(ns, "m2", new Class<?>[0]);
        assertNotNull("get method m2 is not null", m2);
        assertEquals("method get is m2", "m2", m2.getName());
        assertNotNull("gen method sm2 is not null",
                Reflect.getMethod(ns, "sm2", new Class<?>[0]));
    }

    @Test
    public void get_new_instance() throws Exception {
        Class<?> type = (Class<?>) eval(
            "class T10 {}",
            "return T10.class;"
        );
        Object inst = Reflect.getNewInstance(type);
        assertNotNull("instance is not null", inst);
        assertThat("instance is cached", inst,
                sameInstance(Reflect.getNewInstance(type)));
    }

    @Test
    public void get_declared_method_from_class() throws Exception {
        Class<?> type = (Class<?>) eval(
            "class T11 {",
                "static sm1() {}",
                "static sm2() {}",
                "static sm3() {};",
                "m1() {}",
                "m2() {}",
                "m3() {}",
            "}",
            "return T11.class;"
        );
        BshMethod m2 = Reflect.getDeclaredMethod(type, "m2", new Class<?>[0]);
        assertNotNull("get method m2 is not null", m2);
        assertEquals("method get is m2", "m2", m2.getName());
        assertNotNull("gen method sm2 is not null",
                Reflect.getDeclaredMethod(type, "sm2", new Class<?>[0]));
    }

    @Test
    public void get_methods_from_class() throws Exception {
        Class<?> type = (Class<?>) eval(
            "class T12 {",
                "static sm1() {}",
                "static sm2() {}",
                "static sm3() {};",
                "m1() {}",
                "m2() {}",
                "m3() {}",
            "}",
            "return T12.class;"
        );
        BshMethod[] meths = Reflect.getMethods(type);
        assertThat("3 Methods", meths, arrayWithSize(3));
        assertThat("methods has [sm1, sm2, sm3]",
                Stream.of(meths).map(BshMethod::getName).collect(Collectors.toList()),
                hasItems("sm1", "sm2", "sm3"));
    }

    @Test
    public void get_methods_from_instance() throws Exception {
        Object object = eval(
            "class T13 {",
                "static sm1() {}",
                "static sm2() {}",
                "static sm3() {};",
                "m1() {}",
                "m2() {}",
                "m3() {}",
            "}",
            "return new T13();"
        );
        BshMethod[] meths = Reflect.getMethods(object);
        assertThat("6 Methods", meths, arrayWithSize(6));
        assertThat("methods has [sm1, sm2, sm3, m1, m2, m3]",
                Stream.of(meths).map(BshMethod::getName).collect(Collectors.toList()),
                hasItems("sm1", "sm2", "sm3", "m1", "m2", "m3"));
    }

    @Test
    public void get_methods_from_namespace() throws Exception {
        Class<?> type = (Class<?>) eval(
                "class T14 {",
                    "static sm1() {}",
                    "static sm2() {}",
                    "static sm3() {};",
                    "m1() {}",
                    "m2() {}",
                    "m3() {}",
                "}",
                "return T14.class;"
            );
        NameSpace ns = Reflect.getThisNS(type);
        BshMethod[] meths = Reflect.getMethods(ns);
        assertThat("3 Methods", meths, arrayWithSize(3));
        assertThat("methods has [sm1, sm2, sm3]",
                Stream.of(meths).map(BshMethod::getName).collect(Collectors.toList()),
                hasItems("sm1", "sm2", "sm3"));
    }

    @Test
    public void get_declared_methods_from_class() throws Exception {
        Class<?> type = (Class<?>) eval(
                "class T15 {",
                    "static sm1() {}",
                    "static sm2() {}",
                    "static sm3() {};",
                    "m1() {}",
                    "m2() {}",
                    "m3() {}",
                "}",
                "return T15.class;"
            );
        BshMethod[] meths = Reflect.getDeclaredMethods(type);
        assertThat("6 Methods", meths, arrayWithSize(6));
        assertThat("methods has [sm1, sm2, sm3, m1, m2, m3]",
                Stream.of(meths).map(BshMethod::getName).collect(Collectors.toList()),
                hasItems("sm1", "sm2", "sm3", "m1", "m2", "m3"));
    }

    @Test
    public void get_variable_from_class() throws Exception {
        Class<?> type = (Class<?>) eval(
                "class T16 {",
                    "static int sv1 = 0;",
                    "static int sv2 = 1;",
                    "static int sv3 = 2;",
                    "v1 = 1;",
                    "v2 = 2;",
                    "v3 = 3;",
                "}",
                "return T16.class;"
            );
        Variable var = Reflect.getVariable(type, "sv3");
        assertNotNull("var sv3 is not null", var);
        assertEquals("var sv3 equals 2", new Primitive(2), var.getValue());
    }

    @Test
    public void get_variable_from_instance() throws Exception {
        Object object = eval(
                "class T17 {",
                    "static int sv1 = 0;",
                    "static int sv2 = 1;",
                    "static int sv3 = 2;",
                    "v1 = 1;",
                    "v2 = 2;",
                    "v3 = 3;",
                "}",
                "return new T17();"
            );
        Variable var = Reflect.getVariable(object, "v3");
        assertNotNull("var v3 is not null", var);
        assertEquals("var v3 equals 3", new Primitive(3), var.getValue());
    }

    @Test
    public void get_variable_from_namespace() throws Exception {
        Object object = eval(
                "class T18 {",
                    "static int sv1 = 0;",
                    "static int sv2 = 1;",
                    "static int sv3 = 2;",
                    "v1 = 1;",
                    "v2 = 2;",
                    "v3 = 3;",
                "}",
                "return new T18();"
            );
        NameSpace ns = Reflect.getThisNS(object);
        Variable var = Reflect.getVariable(ns, "v3");
        assertNotNull("var v3 is not null", var);
        assertEquals("var v3 equals 3", new Primitive(3), var.getValue());
    }

    @Test
    public void get_declared_variable_from_class() throws Exception {
        Class<?> type = (Class<?>) eval(
                "class T20 {",
                    "static int sv1 = 0;",
                    "static int sv2 = 1;",
                    "static int sv3 = 2;",
                    "v1 = 1;",
                    "v2 = 2;",
                    "v3 = 3;",
                "}",
                "return T20.class;"
            );
        Variable var = Reflect.getDeclaredVariable(type, "v3");
        assertNotNull("var v3 is not null", var);
        assertEquals("var v3 equals 3", new Primitive(3), var.getValue());
    }

    @Test
    public void get_variables_from_class() throws Exception {
        Class<?> type = (Class<?>) eval(
                "class T16 {",
                    "static int sv1 = 0;",
                    "static int sv2 = 1;",
                    "static int sv3 = 2;",
                    "v1 = 1;",
                    "v2 = 2;",
                    "v3 = 3;",
                "}",
                "return T16.class;"
            );
        Variable[] vars = Reflect.getVariables(type);
        assertThat("3 Variables", vars, arrayWithSize(3));
        assertThat("variables has [sv1, sv2, sv3]",
                Stream.of(vars).map(Variable::getName).collect(Collectors.toList()),
                hasItems("sv1", "sv2", "sv3"));
    }

    @Test
    public void get_variables_from_instance() throws Exception {
        Object object = eval(
                "class T17 {",
                    "static int sv1 = 0;",
                    "static int sv2 = 1;",
                    "static int sv3 = 2;",
                    "v1 = 1;",
                    "v2 = 2;",
                    "v3 = 3;",
                "}",
                "return new T17();"
            );
        Variable[] vars = Reflect.getVariables(object);
        assertThat("3 Variables", vars, arrayWithSize(6));
        assertThat("variables has [sv1, sv2, sv3, v1, v2, v3]",
                Stream.of(vars).map(Variable::getName).collect(Collectors.toList()),
                hasItems("sv1", "sv2", "sv3", "v1", "v2", "v3"));
    }

    @Test
    public void get_variables_from_namespace() throws Exception {
        Object object = eval(
                "class T18 {",
                    "static int sv1 = 0;",
                    "static int sv2 = 1;",
                    "static int sv3 = 2;",
                    "v1 = 1;",
                    "v2 = 2;",
                    "v3 = 3;",
                "}",
                "return new T18();"
            );
        NameSpace ns = Reflect.getThisNS(object);
        Variable[] vars = Reflect.getVariables(ns);
        assertThat("3 Variables", vars, arrayWithSize(6));
        assertThat("variables has [sv1, sv2, sv3, v1, v2, v3]",
                Stream.of(vars).map(Variable::getName).collect(Collectors.toList()),
                hasItems("sv1", "sv2", "sv3", "v1", "v2", "v3"));
    }

    @Test
    public void get_variables_for_names_from_namespace() throws Exception {
        Object object = eval(
                "class T19 {",
                    "static int sv1 = 0;",
                    "static int sv2 = 1;",
                    "static int sv3 = 2;",
                    "v1 = 1;",
                    "v2 = 2;",
                    "v3 = 3;",
                "}",
                "return new T19();"
            );
        NameSpace ns = Reflect.getThisNS(object);
        Variable[] vars = Reflect.getVariables(ns, new String[] {"sv3", "v3"});
        assertThat("3 Variables", vars, arrayWithSize(2));
        assertThat("variables has [sv3, v3]",
                Stream.of(vars).map(Variable::getName).collect(Collectors.toList()),
                hasItems("sv3", "v3"));
    }

    @Test
    public void get_declared_variables_from_class() throws Exception {
        Class<?> type = (Class<?>) eval(
                "class T20 {",
                    "static int sv1 = 0;",
                    "static int sv2 = 1;",
                    "static int sv3 = 2;",
                    "v1 = 1;",
                    "v2 = 2;",
                    "v3 = 3;",
                "}",
                "return T20.class;"
            );
        Variable[] vars = Reflect.getDeclaredVariables(type);
        assertThat("3 Variables", vars, arrayWithSize(6));
        assertThat("variables has [sv1, sv2, sv3, v1, v2, v3]",
                Stream.of(vars).map(Variable::getName).collect(Collectors.toList()),
                hasItems("sv1", "sv2", "sv3", "v1", "v2", "v3"));
    }

    @Test
    public void get_class_modifiers_from_class() throws Exception {
        Class<?> type = (Class<?>) eval(
                "private static final class T21 {}",
                "return T21.class;"
            );
        Modifiers modifiers = Reflect.getClassModifiers(type);
        assertTrue("modifiers has private", modifiers.hasModifier("private"));
        assertTrue("modifiers has static", modifiers.hasModifier("static"));
        assertTrue("modifiers has final", modifiers.hasModifier("final"));
    }

    /**
     * See {@link bsh.SourceForgeIssuesTest#sourceforge_issue_2562805()}.
     * This test checks the resolving of PrintStream.println(null).
     * This may be resolved to {@link java.io.PrintStream#println(char[])},
     * depending on the ordering of the methods when using reflection.
     * This will result in a {@code NullPointerException}.
     *
     * See JLS 15.12.2 for the resolution rules.  Most specific signature
     * should be selected.  This means that Integer.class is more
     * specific than Object.class.  Also Integer.class is more specific than
     * NUmber.class, which is also more specific than Object.class.
     *
     * However Integer.class is not more specific than Double.class.
     * In this case the Java compiler cannot decide which to choose since
     * they both match, so an error is emitted that the reference is
     * ambiguous.  The solution to this problem in Java is to add a cast
     * to the null to indicate what to match to.
     */
    @Test
    public void findMostSpecificSignature() {
        int value = Reflect.findMostSpecificSignature(new Class[]{null}, new Class[][]{
              {Double.TYPE}, {Double.class}, {Number.class}, {Object.class}
        });
        assertEquals("most specific Double class", 1, value);
        value = Reflect.findMostSpecificSignature(new Class[]{null}, new Class[][]{
              {Number.class}, {Object.class}, {Double.class}, {Double.TYPE}
        });
        assertEquals("most specific Double class", 2, value);
        value = Reflect.findMostSpecificSignature(new Class[]{null}, new Class[][]{
              {Object.class}, {Number.class}, {Double.TYPE}, {Double.class}
        });
        assertEquals("most specific Double class", 3, value);
        value = Reflect.findMostSpecificSignature(new Class[]{null}, new Class[][]{
            {Double.TYPE}, {char[].class}, {Integer.class}, {String.class}
        });
        assertEquals("most specific String class", 3, value);
        // value = Reflect.findMostSpecificSignature(new Class[]{null}, new Class[][]{
        //     {Double.TYPE}, {char[].class}, {Number.class}, {Integer.class}
        // });
        // assertEquals("most specific char[] class", 1, value);
        // value = Reflect.findMostSpecificSignature(new Class[]{null}, new Class[][]{
        //     {Double.TYPE}, {char[].class}, {Object.class}, {Boolean.TYPE}
        // });
        // assertEquals("most specific Object class", 2, value);
        // value = Reflect.findMostSpecificSignature(new Class[]{null}, new Class[][]{
        //     {Double.TYPE}, {char[].class}, {Boolean.TYPE}
        // });
        // assertEquals("most specific char[] class", 1, value);
    }
}
