package bsh;

import java.io.File;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import bsh.security.SecurityGuard;

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
            if (thisArg instanceof List && methodName.equals("add")) return false;
            return true;
        }
        public boolean canInvokeSuperMethod(Class<?> superClass, Object thisArg, String methodName, Object[] args) {
            if (methodName.equals("method")) return false;
            return true;
        }
        public boolean canGetField(Object thisArg, String fieldName) {
            if (fieldName.equals("length")) return false;
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
    public void canInvokeStaticMethod1() {
        try {
            TestUtil.eval("System.getProperty(\"java.version\");");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cantInvokeStaticMethod1() {
        try {
            TestUtil.eval("System.exit(1);");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this static method: java.lang.System.exit(int)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void canInvokeStaticMethod2() {
        try {
            TestUtil.eval("void func() { System.getProperty(\"java.version\"); }; func();");
            Assert.assertTrue(true);
        } catch (Exception ex) {
            Assert.fail("The code mustn't throw any Exception!");
        }
    }

    @Test
    public void cantInvokeStaticMethod2() {
        try {
            TestUtil.eval("void func() { System.exit(1); }; func()");
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this static method: java.lang.System.exit(int)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void canInvokeStaticMethod3() {
        // Can invoke static method with Reflection using Object[]
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
    public void cantInvokeStaticMethod3() {
        // Cant invoke static method with Reflection using Object[]
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
    public void canInvokeStaticMethod4() {
        // Can invoke static method with Reflection using varargs
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
    public void cantInvokeStaticMethod4() {
        // Cant invoke static method with Reflection using varargs
        try {
            TestUtil.eval(
                "import java.lang.reflect.Method;",
                "Method method = System.class.getMethod(\"exit\", int.class);",
                "method.invoke(null, 1);"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this static method using reflection: java.lang.System.exit(int)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void canInvokeStaticMethod5() {
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
    public void cantInvokeStaticMethod5() {
        try {
            TestUtil.eval(
                "class Test { void exec() { System.exit(1); } }; ",
                "new Test().exec();"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this static method: java.lang.System.exit(int)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void canInvokeStaticMethod6() {
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
    public void cantInvokeStaticMethod6() {
        try {
            TestUtil.eval(
                "enum Test { AB; static void exec() { System.exit(1); } };",
                "Test.exec();"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this static method: java.lang.System.exit(int)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

    @Test
    public void canInvokeStaticMethod7() {
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
    public void cantInvokeStaticMethod7() {
        try {
            TestUtil.eval(
                "enum Test { AB; void exec() { System.exit(1); } };",
                "Test.AB.exec()"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this static method: java.lang.System.exit(int)";
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
            final String expectedMsg = "SecurityError: Can't invoke this method: java.util.ArrayList.add(int)";
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
                "class Cls2 { Cls1 cls1 = new Cls1(); };",
                "var myObj = new Cls2(); myObj.cls1.nums;"
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
                "class Cls2 { Cls1 cls1 = new Cls1(); };",
                "var myObj = new Cls2(); myObj.cls1.nums.length;"
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't get this field: int[].length";
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
                "import bsh.security.MainSecurityGuard;",
                "new MainSecurityGuard().add(new java.util.HashMap());" // It's used the 'java.util.HashMap' cuz we can't create 'SecurityGuard' instances :P, and it's an extra security
            );
            Assert.fail("The code must throw an Exception!");
        } catch (Exception ex) {
            final String expectedMsg = "SecurityError: Can't invoke this method: bsh.security.MainSecurityGuard.add(java.util.HashMap)";
            Assert.assertTrue("Unexpected Exception Message: " + ex, ex.toString().contains(expectedMsg));
        }
    }

}
