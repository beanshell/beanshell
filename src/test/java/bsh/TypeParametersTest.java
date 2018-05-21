package bsh;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static bsh.TestUtil.eval;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(FilteredTestRunner.class)
public class TypeParametersTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void generics_diamond_operator() throws Exception {
        final Object list = eval(
            "List<String> list = new ArrayList<>();",
            "return list;"
        );
        assertTrue(list.getClass().getName(), list instanceof ArrayList);
        assertEquals("List size is 0", 0, ((List<?>)list).size());
    }

    @Test
    public void generics_wildcard() throws Exception {
        final Object clas = eval(
            "Class<?> clas = ArrayList.class;",
            "clas;"
        );
        assertTrue(clas.getClass().getName(), clas instanceof Class);
    }

    @Test
    public void generics_paramater_type() throws Exception {
        final Object clas = eval(
            "checkList(List<Integer> list) {",
                "list.get(0);",
            "}",
            "List<Integer> l = new ArrayList<>();",
            "l.add(1);",
            "checkList(l);"
        );
        assertTrue(clas.getClass().getName(), clas instanceof Integer);
    }

    @Test
    public void generics_wildcard_lower_bound() throws Exception {
        final Object clas = eval(
            "checkList(List<? super Integer> list) {",
                "list.size() < 2;",
            "}",
            "List<Integer> l = new ArrayList<>();",
            "l.add(1);",
            "checkList(l);"
        );
        assertTrue(clas.getClass().getName(), (Boolean)clas);
    }

    @Test
    public void generics_wildcard_upper_bound() throws Exception {
        final Object clas = eval(
            "checkList(List<? extends Number> list) {",
                "list.get(0);",
            "}",
            "List<Integer> l = new ArrayList<>();",
            "l.add(1);",
            "checkList(l);"
        );
        assertTrue(clas.getClass().getName(), clas instanceof Integer);
    }

    @Test
    public void generics_diamond() throws Exception {
        final Object list = eval(
            "List<List<String>> list = new ArrayList<>();",
            "return list;"
        );
        assertTrue(list.getClass().getName(), list instanceof ArrayList);
    }

    @Test
    public void generics_list() throws Exception {
        final Object anagrams = eval(
                "Map<String, Object> anagrams = new HashMap<>();",
                "return anagrams;");
        assertNotNull(anagrams);
        assertTrue(anagrams.getClass().getName(), anagrams instanceof HashMap);
    }

    @Test
    public void generics_list_diamond() throws Exception {
        final Object anagrams = eval(
                "Map<String, List<String>> anagrams = new HashMap<>();",
                "return anagrams;");
        assertNotNull(anagrams);
        assertTrue(anagrams.getClass().getName(), anagrams instanceof HashMap);
    }

    @Test
    public void generics_raw_types() throws Exception {
        final Object ret = eval(
                "class Box<T> {",
                    "set(T t) { t; }",
                "}",
                "new Box<Integer>().set(1);");
        assertNotNull(ret);
        assertEquals("Type T returned 1", 1, ret);
        assertTrue(ret.getClass().getName(), ret instanceof Integer);
    }

    @Test
    public void generics_multi_bound() throws Exception {
        final Object ret = eval(
                "class D <T extends A & B & C> { }",
                "D.class;");
        assertNotNull(ret);
        assertEquals("Class getName is D", "D", ((Class<?>)ret).getName());
    }

    @Test
    public void generics_bounded_type() throws Exception {
        final Object ret = eval(
            "public class Box<T> {",
                "private T t;",
                "public void set(T t) {",
                    "this.t = t;",
                "}",
                "public T get() {",
                    "return t;",
                "}",
            "}",
            "Box<Integer> integerBox = new Box<Integer>();",
            "integerBox.set(new Integer(10));",
            "integerBox.get();"
        );
        assertNotNull(ret);
        assertEquals("Type T returned 10", 10, ret);
        assertTrue(ret.getClass().getName(), ret instanceof Integer);
    }

    @Test
    public void generics_bounded_type_params() throws Exception {
        final Object ret = eval(
            "public static <T extends Comparable<T>> int countGreaterThan(T[] anArray, T elem) {",
                "int count = 0;",
                "for (T e : anArray)",
                    "if (e.compareTo(elem) > 0)",
                        "++count;",
                "return count;",
            "}",
            "countGreaterThan(new Integer[]{1,-1,2,-2}, 0);"
        );
        assertNotNull(ret);
        assertEquals("Type T returned 2", 2, ret);
        assertTrue(ret.getClass().getName(), ret instanceof Integer);
    }

    @Test
    public void generics_bounded_type_params_no_primitive() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Command not found: countGreaterThan(int[], int)"));

        eval(
            "public static <T extends Comparable<T>> int countGreaterThan(T[] anArray, T elem) {",
            "}",
            "countGreaterThan(new int[]{1,-1,2,-2}, 0);"
        );
    }

    @Test
    public void generics_type_inference() throws Exception {
        new OldScriptsTest.TestBshScript(
                new File("src/test/resources/test-scripts/generictypeinference.bsh")
                ).runTest();
    }

    @Test
    public void generics_methods() throws Exception {
        new OldScriptsTest.TestBshScript(
                new File("src/test/resources/test-scripts/genericmethods.bsh")
                ).runTest();
    }

    @Test
    public void generics_wildcard_error_works() throws Exception {
        final Object ret = eval(
            "public class WildcardError {",
                "void foo(List<?> i) {",
                    "i.set(0, i.get(0));",
                "}",
            "}",
            "List<Bar> lst = new ArrayList<Baz>();",
            "lst.add(0);",
            "new WildcardError().foo(lst);",
            "lst"
        );
        assertNotNull(ret);
        assertEquals("List size is 1", 1, ((List)ret).size());
        assertTrue(ret.getClass().getName(), ret instanceof List);
    }

    @Test
    @Category(KnownIssue.class)
    public void generics_arargs() throws Exception {
        final Object ret = eval(
            "public static <T> void addToList (List<T> listArg, T... elements) {",
                "for (T x : elements)",
                    "listArg.add(x);",
            "}",
            "List<Bar> lst = new ArrayList<Baz>();",
            "addToList(lst, 1, 2, 3, 4);",
            "lst.size();"
        );
        assertNotNull(ret);
        assertEquals("List size is 4", 4, ret);
        assertTrue(ret.getClass().getName(), ret instanceof Integer);
    }

}
