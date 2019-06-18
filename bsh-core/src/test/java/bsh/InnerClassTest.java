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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import static bsh.TestUtil.eval;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;

@RunWith(FilteredTestRunner.class)
public class InnerClassTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void run_script_class13() throws Throwable {
        new BshScriptTestCase.Script("class13.bsh").runTest();
    }

    @Test
    public void run_script_innerclass() throws Throwable {
        new BshScriptTestCase.Script("innerclass.bsh").runTest();
    }

    @Test
    public void run_script_innerclass2() throws Throwable {
        new BshScriptTestCase.Script("innerclass2.bsh").runTest();
    }

    @Test
    public void run_script_classinner() throws Throwable {
        new BshScriptTestCase.Script("classinner.bsh").runTest();
    }

    @Test
    public void local_class_access_outer_class_instance_variable() throws Exception {
        Object ret = eval(
            "class Outer {",
                "method() {",
                    "class Inner {",
                        "gogo() { return go; }",
                    "}",
                    "return new Inner().gogo();",
                "}",
                "int go = 779;",
            "}",
            "new Outer().method();"
        );
        assertEquals("Outer class instance variable is 779", 779, ret);
    }

    @Test
    public void local_class_access_method_local_variable() throws Exception {
        Object ret = eval(
            "class Outer {",
                "method() {",
                    "int go = 788;",
                    "class Inner {",
                        "gogo() { return go; }",
                    "}",
                    "return new Inner().gogo();",
                "}",
            "}",
            "new Outer().method();"
        );
        assertEquals("Local variable is 788", 788, ret);
    }

    @Test
    public void local_class_access_outer_class_static_variable() throws Exception {
        Object ret = eval(
            "class Outer {",
                "method() {",
                    "class Inner {",
                        "gogo() { return go; }",
                    "}",
                    "return new Inner().gogo();",
                "}",
                "static int go = 778;",
            "}",
            "new Outer().method();"
        );
        assertEquals("Outer class static variable is 778", 778, ret);
    }

    @Test
    public void static_inner_class_access_outer_class_static_variable() throws Exception {
        Object ret = eval(
            "class Outer {",
                "static class Inner {",
                    "gogo() { return go; }",
                "}",
                "static int go = 777;",
            "}",
            "new Outer.Inner().gogo();"
        );
        assertEquals("Outer class static variable is 777", 777, ret);
    }

    @Test
    public void static_inner_class_access_outer_class_instance_variable() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Can't reach instance field: go from static context"));

        eval(
            "class Outer {",
                "static class Inner {",
                    "gogo() { return go; }",
                "}",
                "int go = 7777;",
            "}",
            "new Outer.Inner().gogo();"
        );
    }

    @Test
    public void static_inner_class_access_outer_class_static_method() throws Exception {
        Object ret = eval(
            "class Outer {",
                "static class Inner {",
                    "gogo() { return go(); }",
                "}",
                "static int go() {",
                    "return 77;",
                "}",
            "}",
            "new Outer.Inner().gogo();"
        );
        assertEquals("Outer class static method returns 77", 77, ret);
    }

    @Test
    public void static_inner_class_access_outer_class_instance_method() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Cannot reach instance method: go() from static context"));

        eval(
            "class Outer {",
                "static class Inner {",
                    "gogo() { return go(); }",
                "}",
                "go() {",
                    "return 3;",
                "}",
            "}",
            "new Outer.Inner().gogo();"
        );
    }

    @Test
    public void inner_class_access_outer_class_static_variable() throws Exception {
        Object ret = eval(
            "class Outer {",
                "class Inner {",
                    "gogo() { return go; }",
                "}",
                "static int go = 778;",
            "}",
            "new Outer().new Inner().gogo();"
        );
        assertEquals("Outer class static variable is 778", 778, ret);
    }

    @Test
    public void inner_class_access_outer_class_instance_variable() throws Exception {
        Object ret = eval(
            "class Outer {",
                "class Inner {",
                    "gogo() { return go; }",
                "}",
                "int go = 779;",
            "}",
            "new Outer().new Inner().gogo();"
        );
        assertEquals("Outer class instance variable is 779", 779, ret);
    }

    @Test
    public void inner_class_access_outer_class_static_method() throws Exception {
        Object ret = eval(
            "class Outer {",
                "class Inner {",
                    "gogo() { return go(); }",
                "}",
                "static int go() {",
                    "return 7;",
                "}",
            "}",
            "new Outer().new Inner().gogo();"
        );
        assertEquals("Outer class static method returns 7", 7, ret);
    }

    @Test
    public void inner_class_access_outer_class_instance_method() throws Exception {
        Object ret = eval(
            "class Outer {",
                "class Inner {",
                    "gogo() { return go(); }",
                "}",
                "go() {",
                    "return 3;",
                "}",
            "}",
            "new Outer().new Inner().gogo();"
        );
        assertEquals("Outer class instance method returns 3", 3, ret);
    }

    @Test
    public void inner_class_instance_from_outer_method() throws Exception {
        Object ret = eval(
            "class Outer {",
                "class Inner {",
                    "go() { return 1; }",
                "}",
                "go() {",
                    "new Inner();",
                "}",
            "}",
            "new Outer().go().go();"
        );
        assertEquals("Inner class instance returns 1", 1, ret);
    }

    @Test
    public void inner_class_instance_from_outer_constructor() throws Exception {
        Object ret = eval(
            "class Outer {",
                "Inner go;",
                "class Inner {",
                    "go() { return 1; }",
                "}",
                "Outer() {",
                    "go = new Inner();",
                "}",
            "}",
            "new Outer().go.go();"
        );
        assertEquals("Inner class instance returns 1", 1, ret);
    }

    @Test
    public void static_reference_to_inner_class() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("Static method Inner() not found in class'Outer'"));

        eval(
            "class Outer {",
                "class Inner {",
                    "go() { return 1; }",
                "}",
            "}",
            "Outer.Inner().go();"
        );
    }

    @Test
    public void static_reference_to_inner_class_after_instance() throws Exception {
        thrown.expect(EvalError.class);
        thrown.expectMessage(containsString("an enclosing instance that contains Outer.Inner is required"));

        eval(
            "class Outer {",
                "class Inner {",
                    "go() { return 1; }",
                "}",
                "static class InStatic {",
                    "go() { return 1; }",
                "}",
            "}",
            "new Outer.InStatic();",
            "new Outer.Inner();"
        );
    }

    @Test
    public void new_inner_class_instance() throws Exception {
        Object ret = eval(
            "class Outer {",
                "class Inner {",
                    "go() { return 1; }",
                "}",
            "}",
            "o = new Outer();",
            "return o.new Inner().go();"
        );
        assertEquals("New inner class from instance", 1, ret);
    }

    @Test
    public void new_inner_class_new_instance() throws Exception {
        Object ret = eval(
            "class Outer {",
                "class Inner {",
                    "go() { return 1; }",
                "}",
            "}",
            "new Outer().new Inner().go();"
        );
        assertEquals("New inner class from instance", 1, ret);
    }

    @Test
    public void new_inner_class_new_instance_with_args() throws Exception {
        Object ret = eval(
            "class Outer {",
                "class Inner {",
                    "int a;",
                    "go() { return this.a; }",
                    "Inner(a) {",
                        "this.a = a;",
                    "}",
                "}",
            "}",
            "new Outer().new Inner(1).go();"
        );
        assertEquals("New inner class from instance", 1, ret);
    }

}
