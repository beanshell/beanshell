package bsh;

import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import static bsh.TestUtil.eval;
import static bsh.TestUtil.script;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.arrayContaining;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;


@RunWith(FilteredTestRunner.class)
public class EnumTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void enum_with_no_constants() throws Exception {
        Class<?> clas = (Class<?>) eval(
            "enum E0 { }",
            "E0.class;"
        );
        assertTrue("VAL instance of Enum", Enum.class.isAssignableFrom(clas));
    }

    @Test
    public void enum_with_single_constant() throws Exception {
        Object obj = eval(
            "enum E1 {",
                "VAL",
            "}",
            "E1.VAL;"
        );
        assertThat("VAL instance of Enum", obj, instanceOf(Enum.class));
        assertThat("VAL string value", obj.toString(), equalTo("VAL"));
    }

    @Test
    public void enum_with_two_constant() throws Exception {
        Object obj = eval(
            "enum E2 {",
                "VAL1, VAL2",
            "}",
            "E2.VAL1;"
        );
        assertThat("VAL1 instance of Enum", obj, instanceOf(Enum.class));
        assertThat("VAL1 string value", obj.toString(), equalTo("VAL1"));
    }

    @Test
    public void enum_with_multiple_constant() throws Exception {
        Object obj = eval(
            "enum E3 {",
                "VAL1, VAL2, VAL3, VAL4",
            "}",
            "E3.VAL3;"
        );
        assertThat("VAL3 instance of Enum", obj, instanceOf(Enum.class));
        assertThat("VAL3 string value", obj.toString(), equalTo("VAL3"));
    }

    @Test
    public void enum_with_more_than_6_constant() throws Exception {
        Object obj = eval(
            "enum E8 {",
                "VAL1, VAL2, VAL3, VAL4, VAL5, VAL6, VAL7, VAL8",
            "}",
            "E8.VAL7;"
        );
        assertThat("VAL3 instance of Enum", obj, instanceOf(Enum.class));
        assertThat("VAL3 string value", obj.toString(), equalTo("VAL7"));
    }

    @Test
    public void enum_values_zero() throws Exception {
        Object[] obj = (Object[]) eval(
            "enum E0 { }",
            "E0.values();"
        );
        assertThat("array with length length is 0", obj, arrayWithSize(0));
    }

    @Test
    public void enum_values() throws Exception {
        final Interpreter bsh = new Interpreter();
        Object[] obj = (Object[]) bsh.eval(script(
            "enum E4 {",
                "VAL1, VAL2, VAL3, VAL4",
            "}",
            "E4.values();"
        ));
        assertThat("array with length length is 4", obj, arrayWithSize(4));
        assertThat("array containing VAL1, VAL2, VAL3, VAL4", obj, arrayContaining(
                bsh.eval("E4.VAL1"), bsh.eval("E4.VAL2"),
                bsh.eval("E4.VAL3"), bsh.eval("E4.VAL4")));
    }

    @Test
    public void enum_values_excludes_enum_field() throws Exception {
        final Interpreter bsh = new Interpreter();
        Object[] obj = (Object[]) bsh.eval(script(
            "enum E4 {",
                "VAL1, VAL2, VAL3, VAL4;",
                "E4 enm;",
            "}",
            "E4.values();"
        ));
        assertThat("array with length length is 4", obj, arrayWithSize(4));
        assertThat("array containing VAL1, VAL2, VAL3, VAL4", obj, arrayContaining(
                bsh.eval("E4.VAL1"), bsh.eval("E4.VAL2"),
                bsh.eval("E4.VAL3"), bsh.eval("E4.VAL4")));
    }

    @Test
    public void enum_values_excludes_enum_static_field() throws Exception {
        final Interpreter bsh = new Interpreter();
        Object[] obj = (Object[]) bsh.eval(script(
            "enum E4 {",
                "VAL1, VAL2, VAL3, VAL4;",
                "static E4 enm = E4.VAL1;",
            "}",
            "E4.values();"
        ));
        assertThat("array with length length is 4", obj, arrayWithSize(4));
        assertThat("array containing VAL1, VAL2, VAL3, VAL4", obj, arrayContaining(
                bsh.eval("E4.VAL1"), bsh.eval("E4.VAL2"),
                bsh.eval("E4.VAL3"), bsh.eval("E4.VAL4")));
    }

    @Test
    public void enum_values_excludes_enum_static_private_field() throws Exception {
        final Interpreter bsh = new Interpreter();
        Object[] obj = (Object[]) bsh.eval(script(
            "enum E4 {",
                "VAL1, VAL2, VAL3, VAL4;",
                "private static final E4 enm = E4.VAL1;",
            "}",
            "E4.values();"
        ));
        assertThat("array with length length is 4", obj, arrayWithSize(4));
        assertThat("array containing VAL1, VAL2, VAL3, VAL4", obj, arrayContaining(
                bsh.eval("E4.VAL1"), bsh.eval("E4.VAL2"),
                bsh.eval("E4.VAL3"), bsh.eval("E4.VAL4")));
    }

    @Test
    public void enum_values_length() throws Exception {
        Object obj = eval(
            "enum E4 {",
                "VAL1, VAL2, VAL3, VAL4",
            "}",
            "E4.values().length;"
        );
        assertThat("values length is 4", obj, equalTo(4));
    }

    @Test
    public void enum_valueOf() throws Exception {
        Object obj = eval(
            "enum E5 {",
                "VAL1, VAL2, VAL3, VAL4",
            "}",
            "E5.valueOf('VAL3');"
        );
        assertThat("VAL3 instance of Enum", obj, instanceOf(Enum.class));
        assertThat("VAL3 string value", obj.toString(), equalTo("VAL3"));
    }

    @Test
    public void enum_assign() throws Exception {
        Object obj = eval(
            "enum E6 {",
                "VAL1, VAL2, VAL3, VAL4",
            "}",
            "val3 = E6.VAL3;",
            "val3;"
        );
        assertThat("VAL3 instance of Enum", obj, instanceOf(Enum.class));
        assertThat("VAL3 string value", obj.toString(), equalTo("VAL3"));
    }

    @Test
    public void enum_equals() throws Exception {
        Object obj = eval(
            "enum E6 {",
                "VAL1, VAL2, VAL3, VAL4",
            "}",
            "val3 = E6.VAL3;",
            "val3 == E6.VAL3;"
        );
        assertThat("VAL3 == val3", obj, equalTo(true));
    }


    @Test
    public void enum_with_static_field() throws Exception {
        Object obj = eval(
            "enum E7 {",
                "VAL1, VAL2, VAL3, VAL4;",
                "static int val = 5;",
            "}",
            "E7.val;"
        );
        assertThat("static int value", obj, equalTo(5));
    }

    @Test
    public void enum_with_intance_field() throws Exception {
        Object obj = eval(
            "enum E8 {",
                "VAL1, VAL2, VAL3, VAL4;",
                "int val = 5;",
            "}",
            "E8.VAL2.val;"
        );
        assertThat("static int value", obj, equalTo(5));
    }

    @Test
    public void enum_with_static_method() throws Exception {
        Object obj = eval(
            "enum E9 {",
                "VAL1, VAL2, VAL3, VAL4;",
                "static int val = 5;",
                "static int getVal() { val; }",
            "}",
            "E9.getVal();"
        );
        assertThat("static int value", obj, equalTo(5));
    }

    @Test
    public void enum_switch() throws Exception {
        final Interpreter bsh = new Interpreter();
        bsh.eval(script(
            "enum Name { VAL1, VAL2 }",
            "switchit(val) {",
                "switch (val) {",
                    "case VAL1:",
                        "return 'val1';",
                        "break;",
                    "case VAL2:",
                        "return 'val2';",
                        "break;",
                    "default:",
                        "return 'default';",
                "}",
            "}"
        ));
        assertThat("val2 switched", bsh.eval("switchit(Name.VAL2);"), equalTo("val2"));
        assertThat("val1 switched", bsh.eval("switchit(Name.VAL1);"), equalTo("val1"));
        assertThat("default switched null", bsh.eval("switchit(null);"), equalTo("default"));
        assertThat("default switched string", bsh.eval("switchit('VAL1');"), equalTo("default"));
    }

    @Test
    public void enum_args_constructor_required() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Can't find constructor: Name(int)"));

        eval(
            "enum Name {",
                "VAL1(1), VAL2(2);",
                "int val;",
            "}",
            "Name.VAL1.val;"
        );
    }

    @Test
    public void enum_new_enum_default_constructor() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Can't find default constructor for: class Name"));

        eval(
            "enum Name {",
                "VAL1, VAL2",
            "}",
            "new Name();"
        );
    }

    @Test
    public void enum_new_enum_default_enum_constructor() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(
                containsString("Can't find constructor: Name(String)"));

        eval(
            "enum Name {",
                "VAL1, VAL2",
            "}",
            "new Name('VAL3');",
            // with accessibility we can use the default private constructor
            "//new Name('VAL3',3);"
        );
    }

    @Test
    public void enum_args_constructor() throws Exception {
        Object obj = eval(
            "enum Name {",
                "VAL1(1), VAL2(2);",
                "int val;",
                "private Name(int a) {",
                    "val = a;",
                "}",
            "}",
            "Name.VAL1.val;"
        );
        assertThat("enum args constructor set value", obj, equalTo(1));
    }

    @Test
    public void enum_args_constructor_multi() throws Exception {
        final Interpreter bsh = new Interpreter();
        bsh.eval(script(
            "enum Name {",
                "VAL1(1, 1.0, 'v1'), VAL2(2, 2.0, 'v2');",
                "int i;",
                "double d;",
                "String s;",
                "Name(int i, double d, String s) {",
                    "this.i = i;",
                    "this.d = d;",
                    "this.s = s;",
                "}",
            "}"
        ));
        assertThat("enum args VAL1 constructor set value i", bsh.eval("Name.VAL1.i"), equalTo(1));
        assertThat("enum args VAL1 constructor set value d", bsh.eval("Name.VAL1.d"), equalTo(1.0));
        assertThat("enum args VAL1 constructor set value s", bsh.eval("Name.VAL1.s"), equalTo("v1"));
        assertThat("enum args VAL2 constructor set value i", bsh.eval("Name.VAL2.i"), equalTo(2));
        assertThat("enum args VAL2 constructor set value d", bsh.eval("Name.VAL2.d"), equalTo(2.0));
        assertThat("enum args VAL2 constructor set value s", bsh.eval("Name.VAL2.s"), equalTo("v2"));
    }

    @Test
    public void enum_implements_interface_constants() throws Exception {
        Object obj = eval(
            "interface AA {",
                "AB=99;",
            "}",
            "enum Name implements AA {",
                "VAL1, VAL2",
            "}",
            "Name.AB;"
        );
        assertThat("interface inherited constant", obj, equalTo(99));
    }

    @Test
    public void enum_implements_interface_default_method() throws Exception {
        Object obj = eval(
            "interface AA {",
                "default int def(a) { a; }",
            "}",
            "enum Name implements AA {",
                "VAL1, VAL2;",
            "}",
            "Name.VAL2.def(4);"
        );
        assertThat("interface inherited default method", obj, equalTo(4));
    }

    @Test
    public void enum_implements_interface_static_method() throws Exception {
        Object obj = eval(
            "interface AA {",
                "static int def(a) { a; }",
            "}",
            "enum Name implements AA {",
                "VAL1, VAL2;",
            "}",
            "Name.def(44);"
        );
        assertThat("interface inherited static method", obj, equalTo(44));
    }

    @Test
    public void enum_block_variables() throws Exception {
        final Interpreter bsh = new Interpreter();
        bsh.eval(script(
            "enum Name {",
                "VAL1 {",
                    "val = 'val1';",
                "},",
                "VAL2 {",
                    "val = 'val2';",
                "}",
            "}"
        ));
        assertThat("enum block variable VAL2", bsh.eval("Name.VAL2.val"), equalTo("val2"));
        assertThat("enum block variable VAL1", bsh.eval("Name.VAL1.val"), equalTo("val1"));
    }

    @Test
    public void enum_block_method() throws Exception {
        final Interpreter bsh = new Interpreter();
        bsh.eval(script(
            "enum Name {",
                "VAL1 {",
                    "get() { 'val1'; }",
                "},",
                "VAL2 {",
                    "get() { 'val2'; }",
                "}",
            "}"
        ));
        assertThat("enum block variable VAL2", bsh.eval("Name.VAL2.get()"), equalTo("val2"));
        assertThat("enum block variable VAL1", bsh.eval("Name.VAL1.get()"), equalTo("val1"));
    }

    @Test
    public void enum_block_method_override() throws Exception {
        final Interpreter bsh = new Interpreter();
        bsh.eval(script(
            "enum Name {",
                "VAL1 {",
                    "get() { 'val1'; }",
                "},",
                "VAL2 {",
                    "get() { 'val2'; }",
                "};",
                "abstract String get();",
            "}"
        ));
        assertThat("enum block variable VAL2", bsh.eval("Name.VAL2.get()"), equalTo("val2"));
        assertThat("enum block variable VAL1", bsh.eval("Name.VAL1.get()"), equalTo("val1"));
    }

    @Test
    public void enum_block_method_and_constructor() throws Exception {
        final Interpreter bsh = new Interpreter();
        bsh.eval(script(
            "enum Name {",
                "VAL1('1val') {",
                    "get() { str + '1'; }",
                "},",
                "VAL2('2val') {",
                    "get() { str + '2'; }",
                "};",
                "String str = '';",
                "Name(String s) {",
                    "str = s;",
                "}",
            "}"
        ));
        assertThat("enum block variable VAL2", bsh.eval("Name.VAL2.get()"), equalTo("2val2"));
        assertThat("enum block variable VAL1", bsh.eval("Name.VAL1.get()"), equalTo("1val1"));
    }

}

