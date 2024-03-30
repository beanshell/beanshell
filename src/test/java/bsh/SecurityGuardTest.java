package bsh;

import java.io.File;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FilteredTestRunner.class)
public class SecurityGuardTest {

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
            if (fieldName.equals("length")) return false;
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
        Interpreter.mainSecurityGuard.add(SecurityGuardTest.mySecurityGuard);
    }

    @AfterClass
    public static void afterAll() {
        Interpreter.mainSecurityGuard.remove(SecurityGuardTest.mySecurityGuard);
    }

    @Test
    public void canConstruct() {
        try {
            TestUtil.eval("var obj = new java.util.HashMap();");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cantConstruct() {
        try {
            TestUtil.eval("var obj = new java.io.File(\"\");");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't call this construct: new java.io.File(java.lang.String)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void canConstructLegacyReflection() {
        try {
            TestUtil.eval("var obj = HashMap.class.newInstance()");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cantConstructLegacyReflection() {
        try {
            TestUtil.eval("var obj = File.class.newInstance();");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't call this construct using reflection: new java.io.File()";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void canConstructReflection() {
        try {
            TestUtil.eval("var obj = HashMap.class.getConstructor(new Class[] {}).newInstance(new Object[] {});");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cantConstructReflection() {
        try {
            TestUtil.eval("var obj = File.class.getConstructor(new Class[] { String.class }).newInstance(new Object[] { \"\" });");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't call this construct using reflection: new java.io.File(java.lang.String)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void canConstructReflectionVarArgs() {
        try {
            TestUtil.eval("var obj = HashMap.class.getConstructor().newInstance();");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cantConstructReflectionVarArgs() {
        try {
            TestUtil.eval("var obj = File.class.getConstructor(String.class).newInstance(\"\");");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't call this construct using reflection: new java.io.File(java.lang.String)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void canInvokeStaticMethod() {
        try {
            TestUtil.eval("System.getProperty(\"java.version\");");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cantInvokeStaticMethod() {
        try {
            TestUtil.eval("System.exit(1);");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this static method: java.lang.System.exit(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void canInvokeStaticMethodWithinLocalMethod() {
        try {
            TestUtil.eval("void func() { System.getProperty(\"java.version\"); }; func();");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cantInvokeStaticMethodWithinLocalMethod() {
        try {
            TestUtil.eval("void func() { System.exit(1); }; func()");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this static method: java.lang.System.exit(java.lang.Integer)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void canInvokeStaticMethodWithReflection() {
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
    public void cantInvokeStaticMethodWithReflection() {
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
    public void canInvokeStaticMethodWithReflectionVarArgs() {
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
    public void cantInvokeStaticMethodWithReflectionVarArgs() {
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
    public void canInvokeStaticMethodWithinClassMethod() {
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
    public void cantInvokeStaticMethodWithinClassMethod() {
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
    public void canInvokeStaticMethodWithinClassStaticMethod() {
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
    public void cantInvokeStaticMethodWithinClassStaticMethod() {
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
    public void canInvokeStaticMethodWithinEnumStaticMethod() {
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
    public void cantInvokeStaticMethodWithinEnumStaticMethod() {
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
    public void canInvokeStaticMethodWithinEnumMethod() {
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
    public void cantInvokeStaticMethodWithinEnumMethod() {
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
    public void canInvokeMethod() {
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
    public void cantInvokeMethod() {
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
    public void canInvokeMethodWithinLocalMethod() {
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
    public void cantInvokeMethodWithinLocalMethod() {
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
    public void canInvokeMethodWithReflection() {
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
    public void cantInvokeMethodWithReflection() {
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
    public void canInvokeMethodWithReflectionVarArgs() {
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
    public void cantInvokeMethodWithReflectionVarArgs() {
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
    public void canInvokeMethodWithinClassMethod() {
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
    public void cantInvokeMethodWithinClassMethod() {
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
    public void canInvokeMethodWithinClassStaticMethod() {
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
    public void cantInvokeMethodWithinClassStaticMethod() {
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
    public void canInvokeMethodWithinEnumStaticMethod() {
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
    public void cantInvokeMethodWithinEnumStaticMethod() {
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
    public void canInvokeMethodWithinEnumMethod() {
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
    public void cantInvokeMethodWithinEnumMethod() {
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
    public void canInvokeLocalMethod() {
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
    public void cantInvokeLocalMethod() {
        try {
            TestUtil.eval("eval(\"return 123;\")");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this local method: eval(java.lang.String)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void canInvokeSuperMethod() {
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
    public void cantInvokeSuperMethod() {
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
    public void canGetField() {
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
    public void cantGetField() {
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
    }

    @Test
    public void canGetFieldWithReflection() {
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
    public void cantGetFieldWithReflection() {
        try {
            TestUtil.eval(
                "class Cls1 { int[] nums2 = {1, 2, 3}; };",
                "var myObj = new Cls1();",
                "var field = Cls1.class.getField(\"nums2\");",
                "field.get(myObj);"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't get this field: Cls1.nums2";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void cantGetArrayLengthWithReflection() {
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
    }

    @Test
    public void canGetStaticField() {
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
    public void cantGetStaticField() {
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
    public void canGetStaticFieldWithReflection() {
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
    public void cantGetStaticFieldWithReflection() {
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
    public void canExtends() {
        try {
            TestUtil.eval("class MyClass extends java.util.HashMap { }");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cantExtends() {
        try {
            TestUtil.eval("class MyClass extends java.io.File { }");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: This class can't be extended: java.io.File";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void canImplements() {
        try {
            TestUtil.eval("interface EmptyInterface {}; class MyClass implements EmptyInterface { }");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cantImplements() {
        try {
            TestUtil.eval("class MyClass implements java.util.List { }");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: This interface can't be implemented: java.util.List";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void cantUseSecurityGuardAPI1() {
        try {
            TestUtil.eval(
                "import bsh.SecurityGuard;",
                "class MySecuryGuard implements SecurityGuard {}"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: This interface can't be implemented: bsh.SecurityGuard";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void cantUseSecurityGuardAPI2() {
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
    public void cantUseSecurityGuardAPI3() {
        try {
            TestUtil.eval(
                "import bsh.MainSecurityGuard;",
                "new MainSecurityGuard().add(new java.util.HashMap());"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this method: bsh.MainSecurityGuard.add(java.util.HashMap)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

}
