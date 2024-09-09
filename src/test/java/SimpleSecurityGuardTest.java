import java.io.File;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import bsh.FilteredTestRunner;
import bsh.Interpreter;
import bsh.TestUtil;
import bsh.security.MainSecurityGuard;
import bsh.security.SecurityGuard;

// Note: this class has almost all tests from bsh.security.SecurityGuardTest, the idea of this class is to ensure that we won't have package visibility issues again!
@RunWith(FilteredTestRunner.class)
public class SimpleSecurityGuardTest {

    public static class MySecurityGuard implements SecurityGuard {}

    /**
     * It's an empty implementation of SecurityGuard, so it basically does nothing.
     * It's just to make the default implementations of the methods be covedered by tests, in another words, it's just to increase the code coverage.
     */
    private static final SecurityGuard emptySecurityGuard = new SecurityGuard() {};

    /** It's an implementation of SecurityGuard just to execute the tests */
    private static final SecurityGuard mySecurityGuard = new SecurityGuard() {
        public boolean canConstruct(Class<?> _class, Object[] args) {
            if (_class == File.class) return false;
            return true;
        }
        public boolean canInvokeStaticMethod(Class<?> _class, String methodName, Object[] args) {
            if (_class == System.class && methodName.equals("exit"))
                return false;
            return true;
        }
        public boolean canInvokeMethod(Object thisArg, String methodName, Object[] args) {
            if (thisArg instanceof List && methodName.equals("add") && args[0] instanceof Number) return false;
            if (methodName.equals("someMethod")) return false;
            return true;
        }
        public boolean canInvokeLocalMethod(String methodName, Object[] args) {
            if (methodName.equals("eval")) return false;
            return true;
        }
        public boolean canInvokeSuperMethod(Class<?> superClass, Object thisArg, String methodName, Object[] args) {
            if (methodName.equals("method")) return false;
            return true;
        }
        public boolean canGetField(Object thisArg, String fieldName) {
            if (fieldName.equals("nums2")) return false;
            return true;
        }
        public boolean canGetStaticField(Class<?> _class, String fieldName) {
            if (fieldName.equals("num")) return false;
            return true;
        }
        public boolean canExtends(Class<?> superClass) {
            if (superClass == File.class) return false;
            return true;
        }
        public boolean canImplements(Class<?> _interface) {
            if (_interface == List.class) return false;
            return true;
        }
    };

    @BeforeClass
    public static void beforeAll() {
        Interpreter.mainSecurityGuard.add(SimpleSecurityGuardTest.emptySecurityGuard);
        Interpreter.mainSecurityGuard.add(SimpleSecurityGuardTest.mySecurityGuard);
    }

    @AfterClass
    public static void afterAll() {
        Interpreter.mainSecurityGuard.remove(SimpleSecurityGuardTest.emptySecurityGuard);
        Interpreter.mainSecurityGuard.remove(SimpleSecurityGuardTest.mySecurityGuard);
    }

    @Test
    public void can_construct() {
        try {
            TestUtil.eval("var obj = new java.util.HashMap();");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_construct() {
        try {
            TestUtil.eval("var obj = new java.io.File(\"\");");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't call this construct: new java.io.File(java.lang.String)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_construct_using_legacy_reflection() {
        try {
            TestUtil.eval("var obj = HashMap.class.newInstance()");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_construct_using_legacy_reflection() {
        try {
            TestUtil.eval("var obj = File.class.newInstance();");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't call this construct using reflection: new java.io.File()";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_construct_using_reflection() {
        try {
            TestUtil.eval("var obj = HashMap.class.getConstructor(new Class[] {}).newInstance(new Object[] {});");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_construct_using_reflection() {
        try {
            TestUtil.eval("var obj = File.class.getConstructor(new Class[] { String.class }).newInstance(new Object[] { \"\" });");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't call this construct using reflection: new java.io.File(java.lang.String)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_construct_using_reflection_with_varargs() {
        try {
            TestUtil.eval("var obj = HashMap.class.getConstructor().newInstance();");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_construct_using_reflection_with_varargs() {
        try {
            TestUtil.eval("var obj = File.class.getConstructor(String.class).newInstance(\"\");");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't call this construct using reflection: new java.io.File(java.lang.String)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_static_method() {
        try {
            TestUtil.eval("System.getProperty(\"java.version\");");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_static_method() {
        try {
            TestUtil.eval("System.exit(1);");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this static method: java.lang.System.exit(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_static_method_within_local_method() {
        try {
            TestUtil.eval("void func() { System.getProperty(\"java.version\"); }; func();");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_static_method_within_local_method() {
        try {
            TestUtil.eval("void func() { System.exit(1); }; func()");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this static method: java.lang.System.exit(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_static_method_using_reflection() {
        try {
            TestUtil.eval(
                "import java.lang.reflect.Method;",
                "Method method = System.class.getMethod(\"getProperty\", new Class[] { String.class });",
                "method.invoke(null, new Object[] { \"java.version\" });"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_static_method_using_reflection() {
        try {
            TestUtil.eval(
                "import java.lang.reflect.Method;",
                "Method method = System.class.getMethod(\"exit\", new Class[] { int.class });",
                "method.invoke(null, new Object[] { 1 });"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this static method using reflection: java.lang.System.exit(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_static_method_using_reflection_with_varargs() {
        try {
            TestUtil.eval(
                "import java.lang.reflect.Method;",
                "Method method = System.class.getMethod(\"getProperty\", String.class);",
                "method.invoke(null, \"java.version\");"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_static_method_using_reflection_with_varargs() {
        try {
            TestUtil.eval(
                "import java.lang.reflect.Method;",
                "Method method = System.class.getMethod(\"exit\", int.class);",
                "method.invoke(null, 1);"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this static method using reflection: java.lang.System.exit(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_static_method_within_class_method() {
        try {
            TestUtil.eval(
                "class Test { void exec() { System.getProperty(\"java.version\"); } }; ",
                "new Test().exec();"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_static_method_within_class_method() {
        try {
            TestUtil.eval(
                "class Test { void exec() { System.exit(1); } }; ",
                "new Test().exec();"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this static method: java.lang.System.exit(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_static_method_within_class_static_method() {
        try {
            TestUtil.eval(
                "class Test { static void exec() { System.getProperty(\"java.version\"); } };",
                "Test.exec();"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_static_method_within_class_static_method() {
        try {
            TestUtil.eval(
                "class Test { static void exec() { System.exit(1); } };",
                "Test.exec();"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this static method: java.lang.System.exit(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_static_method_within_enum_static_method() {
        try {
            TestUtil.eval(
                "enum Test { AB; static void exec() { System.getProperty(\"java.version\"); } };",
                "Test.exec();"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_static_method_within_enum_static_method() {
        try {
            TestUtil.eval(
                "enum Test { AB; static void exec() { System.exit(1); } };",
                "Test.exec();"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this static method: java.lang.System.exit(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_static_method_within_enum_method() {
        try {
            TestUtil.eval(
                "enum Test { AB; void exec() { System.getProperty(\"java.version\"); } };",
                "Test.AB.exec()"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_static_method_within_enum_method() {
        try {
            TestUtil.eval(
                "enum Test { AB; void exec() { System.exit(1); } };",
                "Test.AB.exec()"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this static method: java.lang.System.exit(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_method() {
        try {
            TestUtil.eval(
                "import java.util.ArrayList;",
                "new ArrayList().size();"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_method() {
        try {
            TestUtil.eval(
                "import java.util.ArrayList;",
                "new ArrayList().add(1);"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this method: java.util.ArrayList.add(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_method_within_local_method() {
        try {
            TestUtil.eval(
                "import java.util.ArrayList;",
                "void func() { new ArrayList().size(); }",
                "func();"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_method_within_local_method() {
        try {
            TestUtil.eval(
                "import java.util.ArrayList;",
                "void func() { new ArrayList().add(1); }",
                "func();"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this method: java.util.ArrayList.add(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_method_using_reflection() {
        try {
            TestUtil.eval(
                "import java.util.ArrayList;",
                "var method = ArrayList.class.getMethod(\"add\", Object.class);",
                "var list = new ArrayList();",
                "method.invoke(list, new Object[] { \"1\" });"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_method_using_reflection() {
        try {
            TestUtil.eval(
                "import java.util.ArrayList;",
                "var method = ArrayList.class.getMethod(\"add\", Object.class);",
                "var list = new ArrayList();",
                "method.invoke(list, new Object[] { 1 });"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this method using reflection: java.util.ArrayList.add(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_method_using_reflection_with_varargs() {
        try {
            TestUtil.eval(
                "import java.util.ArrayList;",
                "var method = ArrayList.class.getMethod(\"add\", Object.class);",
                "var list = new ArrayList();",
                "method.invoke(list, \"1\");"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_method_using_reflection_with_varargs() {
        try {
            TestUtil.eval(
                "import java.util.ArrayList;",
                "var method = ArrayList.class.getMethod(\"add\", Object.class);",
                "var list = new ArrayList();",
                "method.invoke(list, 1);"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this method using reflection: java.util.ArrayList.add(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_method_using_reflection_with_multiple_varargs() {
        try {
            TestUtil.eval(
                "class MyList extends ArrayList { public void add(String a, String b, String c) {} }",
                "var method = MyList.class.getMethod(\"add\", String.class, String.class, String.class);",
                "var list = new MyList();",
                "method.invoke(list, \"1\", \"2\", \"3\");"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_method_using_reflection_with_multiple_varargs() {
        try {
            TestUtil.eval(
                "class MyList extends ArrayList { public void add(int a, int b, int c) {} }",
                "var method = MyList.class.getMethod(\"add\", int.class, int.class, int.class);",
                "var list = new MyList();",
                "method.invoke(list, 1, 2, 3);"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this method using reflection: MyList.add(java.lang.Integer, java.lang.Integer, java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_method_within_class_method() {
        try {
            TestUtil.eval(
                "class Test { void exec() { new ArrayList().size(); } }; ",
                "new Test().exec();"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_method_within_class_method() {
        try {
            TestUtil.eval(
                "class Test { void exec() { new ArrayList().add(1); } };",
                "new Test().exec();"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this method: java.util.ArrayList.add(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_method_within_class_static_method() {
        try {
            TestUtil.eval(
                "class Test { void exec() { new ArrayList().size(); } }; ",
                "new Test().exec();"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            System.out.println("Error: " + ex);
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_method_within_class_static_method() {
        try {
            TestUtil.eval(
                "class Test { static void exec() { new ArrayList().add(1); } };",
                "Test.exec();"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this method: java.util.ArrayList.add(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_method_within_enum_static_method() {
        try {
            TestUtil.eval(
                "enum Test { AB; static void exec() { new ArrayList().size(); } };",
                "Test.exec();"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_method_within_enum_static_method() {
        try {
            TestUtil.eval(
                "enum Test { AB; static void exec() { new ArrayList().add(1); } };",
                "Test.exec();"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this method: java.util.ArrayList.add(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_method_within_enum_method() {
        try {
            TestUtil.eval(
                "enum Test { AB; void exec() { new ArrayList().size(); } };",
                "Test.AB.exec()"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_method_within_enum_method() {
        try {
            TestUtil.eval(
                "enum Test { AB; void exec() { new ArrayList().add(1); } };",
                "Test.AB.exec()"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this method: java.util.ArrayList.add(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_local_method() {
        try {
            TestUtil.eval(
                "int doubleIt(int num) { return num * 2; }",
                "doubleIt(20);"
                );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_local_method() {
        try {
            TestUtil.eval("eval(\"return 123;\")");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this local method: eval(java.lang.String)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_invoke_super_method() {
        try {
            TestUtil.eval(
                "class Cls1 { int exec() { return 1; } };",
                "class Cls2 extends Cls1 { int method2() { return super.exec(); } };",
                "new Cls2().method2();"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_invoke_super_method() {
        try {
            TestUtil.eval(
                "class Cls1 { int method() { return 1; } };",
                "class Cls2 extends Cls1 { int method2() { return super.method(); } };",
                "new Cls2().method2();"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this super method: Cls1.method()";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_get_field() {
        try {
            TestUtil.eval(
                "class Cls1 { int[] nums = {1, 2, 3}; };",
                "var myObj = new Cls1(); myObj.nums;"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_get_field() {
        try {
            TestUtil.eval(
                "class Cls1 { int[] nums2 = {1, 2, 3}; };",
                "var myObj = new Cls1();",
                "myObj.nums2;",
                "field.get(myObj);"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't get this field: Cls1.nums2";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_get_array_length_field() {
        try {
            TestUtil.eval(
                "class Cls1 { int[] nums = {1, 2, 3}; };",
                "var myObj = new Cls1(); myObj.nums.length;"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_get_array_length_field() {
        final SecurityGuard arrayLengthSecurityGuard = new SecurityGuard() {
            public boolean canGetField(Object thisArg, String fieldName) {
                return !fieldName.equals("length");
            }
        };
        Interpreter.mainSecurityGuard.add(arrayLengthSecurityGuard);

        try {
            TestUtil.eval(
                "class Cls1 { int[] nums = {1, 2, 3}; };",
                "var myObj = new Cls1(); myObj.nums.length;"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't get this field: int[].length";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }

        Interpreter.mainSecurityGuard.remove(arrayLengthSecurityGuard);
    }

    @Test
    public void can_get_field_using_reflection() {
        try {
            TestUtil.eval(
                "class Cls1 { int[] nums = {1, 2, 3}; }",
                "var myObj = new Cls1();",
                "var field = Cls1.class.getField(\"nums\");",
                "field.get(myObj);"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_get_field_using_reflection() {
        try {
            TestUtil.eval(
                "class Cls1 { int[] nums2 = {1, 2, 3}; };",
                "var myObj = new Cls1();",
                "var field = Cls1.class.getField(\"nums2\");",
                "field.get(myObj);"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't get this field using reflection: Cls1.nums2";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_get_array_length_field_using_reflection() {
        try {
            TestUtil.eval(
                "import java.lang.reflect.Array;",
                "int[] nums = {1, 2, 3};",
                "Array.getLength(nums);"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_get_array_length_field_using_reflection() {
        final SecurityGuard arrayLengthSecurityGuard = new SecurityGuard() {
            public boolean canGetField(Object thisArg, String fieldName) {
                return !fieldName.equals("length");
            }
        };
        Interpreter.mainSecurityGuard.add(arrayLengthSecurityGuard);

        try {
            TestUtil.eval(
                "import java.lang.reflect.Array;",
                "int[] nums = {1, 2, 3};",
                "Array.getLength(nums);"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't get this field using reflection: int[].length";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }

        Interpreter.mainSecurityGuard.remove(arrayLengthSecurityGuard);
    }

    @Test
    public void can_get_static_field() {
        try {
            TestUtil.eval(
                "class Cls { static int nums = 1; };",
                "Cls.nums;"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_get_static_field() {
        try {
            TestUtil.eval(
                "class Cls { static int num = 1; };",
                "Cls.num;"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't get this static field: Cls.num";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_get_static_field_using_reflection() {
        try {
            TestUtil.eval(
                "class Cls { static int nums = 1; };",
                "var field = Cls.class.getField(\"nums\");",
                "field.get(null);"
            );
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_get_static_field_using_reflection() {
        try {
            TestUtil.eval(
                "class Cls { static int num = 1; };",
                "var field = Cls.class.getField(\"num\");",
                "field.get(null);"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't get this static field using reflection: Cls.num";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_extends() {
        try {
            TestUtil.eval("class MyClass extends java.util.HashMap { }");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_extends() {
        try {
            TestUtil.eval("class MyClass extends java.io.File { }");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: This class can't be extended: java.io.File";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void can_implements() {
        try {
            TestUtil.eval("interface EmptyInterface {}; class MyClass implements EmptyInterface { }");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cant_implements() {
        try {
            TestUtil.eval("class MyClass implements java.util.List { }");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: This interface can't be implemented: java.util.List";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void cant_use_security_guard_API_1st() {
        try {
            TestUtil.eval(
                "import bsh.security.SecurityGuard;",
                "class MySecuryGuard implements SecurityGuard {}"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: This interface can't be implemented: bsh.security.SecurityGuard";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void cant_use_security_guard_API_2nd() {
        try {
            TestUtil.eval(
                "import bsh.Interpreter;",
                "Interpreter.mainSecurityGuard;"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't get this static field: bsh.Interpreter.mainSecurityGuard";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void cant_use_security_guard_API_3rd() {
        try {
            Interpreter bsh = new Interpreter();
            bsh.set("mainSG", new MainSecurityGuard());
            bsh.eval("mainSG.add(new java.util.HashMap());");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this method: bsh.security.MainSecurityGuard.add(java.util.HashMap)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void cant_instantiate_security_guard() {
        try {
            TestUtil.eval(
                "import SimpleSecurityGuardTest.MySecurityGuard;",
                "new MySecurityGuard();"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't call this construct: new SimpleSecurityGuardTest$MySecurityGuard()";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

}
