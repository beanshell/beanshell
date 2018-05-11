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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.concurrent.Callable;
import java.util.function.IntSupplier;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(FilteredTestRunner.class)
public class ClassGeneratorTest {

    @Test
    public void create_class_with_default_constructor() throws Exception {
        eval("class X1 {}");
    }

    @Test
    public void creating_class_should_not_set_accessibility() throws Exception {
        assertFalse("pre: no accessibility should be set", Capabilities.haveAccessibility());
        TestUtil.eval("class X1 {}");
        assertFalse("post: no accessibility should be set", Capabilities.haveAccessibility());
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


    @Test ( expected = TargetError.class)
    public void assignment_to_final_field_should_not_be_allowed() throws Exception {
        TestUtil.eval(
            "class X3 implements java.util.concurrent.Callable {",
                "final Object _instanceVar = null;",
                "public X3(Object arg) { _instanceVar = arg; }",
            "}",
            "return new Object[] { new X3(0), new X3(1) } ");
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
