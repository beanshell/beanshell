/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                *
 * or more contributor license agreements.  See the NOTICE file              *
 * distributed with this work for additional information                     *
 * regarding copyright ownership.  The ASF licenses this file                *
 * to you under the Apache License, Version 2.0 (the                         *
 * "License"); you may not use this file except in compliance                *
 * with the License.  You may obtain a copy of the License at                *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing,                *
 * software distributed under the License is distributed on an               *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                    *
 * KIND, either express or implied.  See the License for the                 *
 * specific language governing permissions and limitations                   *
 * under the License.                                                        *
 *                                                                           *
/****************************************************************************/

package bsh;

import static bsh.TestUtil.eval;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.Callable;
import java.util.function.IntSupplier;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(FilteredTestRunner.class)
public class ClassGeneratorTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void create_class_with_default_constructor() throws Exception {
        eval("class X1 {}");
    }

    @Test
    public void creating_class_should_not_set_accessibility() throws Exception {
        boolean current = Capabilities.haveAccessibility();
        Capabilities.setAccessibility(false);
        assertFalse("pre: no accessibility should be set", Capabilities.haveAccessibility());
        TestUtil.eval("class X1 {}");
        assertFalse("post: no accessibility should be set", Capabilities.haveAccessibility());
        Capabilities.setAccessibility(current);
    }

    @Test
    public void create_instance() throws Exception {
        assertNotNull(
            eval(
                "class X2 {}",
                "return new X2();"
        ));
    }


    @Test
    public void constructor_args() throws Exception {
        final Object[] oa = (Object[]) eval(
            "class X3 implements IntSupplier {",
                "final Object _instanceVar;",
                "public X3(Object arg) { _instanceVar = arg; }",
                "public int getAsInt() { return _instanceVar; }",
            "}",
            "return new Object[] { new X3(0), new X3(1) } ");
        assertEquals(0, ( (IntSupplier) oa[0] ).getAsInt());
        assertEquals(1, ( (IntSupplier) oa[1] ).getAsInt());
    }


    @Test
    public void call_protected_constructor_from_script() throws Exception {
        final Object[] oa = (Object[]) TestUtil.eval(
            "class X4 implements java.util.concurrent.Callable {",
                "final Object _instanceVar;",
                "X4(Object arg) { _instanceVar = arg; }",
                "public Object call() { return _instanceVar; }",
            "}",
            "return new Object[] { new X4(0), new X4(1) } ");
        assertEquals(0, ( (Callable) oa[0] ).call());
        assertEquals(1, ( (Callable) oa[1] ).call());
    }


    @Test
    public void assignment_to_final_field_init_should_not_be_allowed() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final field _initVar."));

        TestUtil.eval(
            "class X3 {",
                "final Object _initVar = null;",
                "public X3() { _initVar = 0; }",
            "}",
            "new X3();");
    }

    @Test
    public void assignment_to_final_field_should_not_be_allowed() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final field _assVar."));

        TestUtil.eval(
            "class X3 {",
                "final Object _assVar = null;",
            "}",
            "x3 = new X3();",
            "x3._assVar = 0;");
    }

    @Test
    public void non_assignment_to_static_final_field_should_not_be_allowed() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Static final field _staticVar is not set."));

        TestUtil.eval(
            "class X7 {",
                "static final Object _staticVar;",
            "}");
    }

    @Test
    public void assignment_to_static_final_field_init_should_not_be_allowed() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final field _initStaticVar."));

        TestUtil.eval(
            "class X7 {",
                "static final Object _initStaticVar = null;",
                "public X7() { _initStaticVar = 0; }",
            "}",
            "new X7();");
    }

    @Test
    public void assignment_to_static_final_field_should_not_be_allowed() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot re-assign final field _assStaticVar."));

        TestUtil.eval(
            "class X7 {",
                "static final Object _assStaticVar = null;",
            "}",
            "X7._assStaticVar = 0;");
    }

    @Test
    public void assignment_to_unassigned_final_field_is_allowed() throws Exception {
        Object unAssVar = TestUtil.eval(
            "class X3 {",
                "final Object _unAssVar;",
            "}",
            "x3 = new X3();",
            "x3._unAssVar = 0;",
            "return x3._unAssVar;");
        assertEquals("Un-assigned field _unAssVal equals 0.", 0, unAssVar);
    }

    @Test
    public void verify_public_accesible_modifiers() throws Exception {
        TestUtil.cleanUp();
        boolean current = Capabilities.haveAccessibility();
        Capabilities.setAccessibility(false);

        Class<?> cls = (Class<?>) TestUtil.eval(
            "class X6 {",
                "public Object public_var;",
                "private Object private_var = null;",
                "protected Object protected_var = null;",
                "public final Object public_final_var;",
                "final Object final_var;",
                "static Object static_var;",
                "static final Object static_final_var = null;",
                "volatile Object volatile_var;",
                "transient Object transient_var;",
                "no_type_var = 0;",
                "Object no_modifier_var;",
                "X6() {}",
                "just_method() {}",
                "void void_method() {}",
                "Object type_method() {}",
                "synchronized sync_method() {}",
                "final final_method() {}",
                "static static_method() {}",
                "abstract abstract_method() {}",
                "public public_method() {}",
                "private private_method() {}",
                "protected protected_method() {}",
            "}",
            "return X6.class ");

        Object inst = cls.newInstance();
        This ths = (This)cls.getField("_bshThisX6").get(inst);
        NameSpace ns = ths.getNameSpace();
        assertTrue("public_var has public modifier", varHasModifier(ns, "public_var", "public"));
        assertFalse("private_var does not have public modifier", varHasModifier(ns, "private_var", "public"));
        assertTrue("private_var has private modifier", varHasModifier(ns, "private_var", "private"));
        assertFalse("protected_var does not have public modifier", varHasModifier(ns, "protected_var", "public"));
        assertTrue("protected_var has protected modifier", varHasModifier(ns, "protected_var", "protected"));
        assertTrue("public_final_var has public modifier", varHasModifier(ns, "public_final_var", "public"));
        assertTrue("public_final_var has final modifier", varHasModifier(ns, "public_final_var", "final"));
        assertTrue("final_var has public modifier", varHasModifier(ns, "final_var", "public"));
        assertTrue("final_var has final modifier", varHasModifier(ns, "final_var", "final"));
        assertTrue("transient_var has public modifier", varHasModifier(ns, "transient_var", "public"));
        assertTrue("transient_var has transient modifier", varHasModifier(ns, "transient_var", "transient"));
        assertTrue("volatile_var has public modifier", varHasModifier(ns, "volatile_var", "public"));
        assertTrue("volatile_var has volatile modifier", varHasModifier(ns, "volatile_var", "volatile"));
        assertTrue("no_modifier_var has public modifier", varHasModifier(ns, "no_modifier_var", "public"));
        assertTrue("no_type_var has public modifier", varHasModifier(ns, "no_type_var", "public"));
        assertTrue("constructor has public modifier", methHasModifier(ns, "X6", "public"));
        assertTrue("just_method has public modifier", methHasModifier(ns, "just_method", "public"));
        assertTrue("void_method has public modifier", methHasModifier(ns, "void_method", "public"));
        assertTrue("type_method has public modifier", methHasModifier(ns, "type_method", "public"));
        assertTrue("sync_method has public modifier", methHasModifier(ns, "sync_method", "public"));
        assertTrue("sync_method has synchronized modifier", methHasModifier(ns, "sync_method", "synchronized"));
        assertTrue("final_method has public modifier", methHasModifier(ns, "final_method", "public"));
        assertTrue("final_method has final modifier", methHasModifier(ns, "final_method", "final"));
        assertTrue("static_method has public modifier", methHasModifier(ns, "static_method", "public"));
        assertTrue("abstract_method has public modifier", methHasModifier(ns, "abstract_method", "public"));
        assertTrue("abstract_method has abstract modifier", methHasModifier(ns, "abstract_method", "abstract"));
        assertTrue("public_method has public modifier", methHasModifier(ns, "public_method", "public"));
        assertFalse("private_method does not have public modifier", methHasModifier(ns, "private_method", "public"));
        assertTrue("private_method has private modifier", methHasModifier(ns, "private_method", "private"));
        assertFalse("protected_method does not have public modifier", methHasModifier(ns, "protected_method", "public"));
        assertTrue("protected_method has protected modifier", methHasModifier(ns, "protected_method", "protected"));

        ths = (This)cls.getField("_bshStaticX6").get(null);
        ns = ths.getNameSpace();
        assertTrue("static_var has public modifier", varHasModifier(ns, "static_var", "public"));
        assertTrue("static_var has static modifier", varHasModifier(ns, "static_var", "static"));
        assertTrue("static_final_var has public modifier", varHasModifier(ns, "static_final_var", "public"));
        assertTrue("static_final_var has static modifier", varHasModifier(ns, "static_final_var", "static"));
        assertTrue("static_final_var has final modifier", varHasModifier(ns, "static_final_var", "final"));
        assertTrue("static_method has static modifier", methHasModifier(ns, "static_method", "static"));

        Capabilities.setAccessibility(current);
    }

    private boolean varHasModifier(NameSpace ns, String var, String mod) throws UtilEvalError {
        return ns.getVariableImpl(var, false).hasModifier(mod);
    }

    private boolean methHasModifier(NameSpace ns, String meth, String mod) throws UtilEvalError {
        return ns.getMethod(meth, new Class<?>[0]).hasModifier(mod);
    }

    @Test
    public void outer_namespace_visibility() throws Exception {
        final IntSupplier supplier = (IntSupplier) eval(
            "class X4 implements IntSupplier {",
                "public int getAsInt() { return var; }",
            "}",
            "var = 0;",
            "a = new X4();",
            "var = 1;",
            "return a;");
        assertEquals(1, supplier.getAsInt());
    }


    @Test
    public void static_fields_should_be_frozen() throws Exception {
        final IntSupplier supplier =  (IntSupplier)eval(
                "var = 0;",
                "class X5 implements IntSupplier {",
                    "static final Object VAR = var;",
                    "public int getAsInt() { return VAR; }",
                "}",
                "var = 1;",
                "a = new X5();",
                "var = 2;",
                "return a;"
        );
        assertEquals(0, supplier.getAsInt());
    }


    /**
     * See also failing test script "classinterf1.bsh" and
     * <a href="http://code.google.com/p/beanshell2/issues/detail?id=46">issue #46</a>.
     */
    @Test
    @Category(KnownIssue.class)
    public void define_interface_with_constants() throws Exception {
        // these three are treated equal in java
        eval("interface Test { public static final int x = 1; }");
        eval("interface Test { static final int x = 1; }");
        eval("interface Test { final int x = 1; }");
        // these three are treated equal in java
        eval("interface Test { public static int x = 1; }");
        eval("interface Test { static int x = 1; }");
        eval("interface Test { int x = 1; }");
    }
}
