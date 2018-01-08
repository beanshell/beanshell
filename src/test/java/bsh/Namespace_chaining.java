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

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.StringReader;

import static org.junit.Assert.assertEquals;

/**
 * <a href="http://code.google.com/p/beanshell2/issues/detail?id=74">Namespace chaining issue</a>
 */
@RunWith(FilteredTestRunner.class)
public class Namespace_chaining {

    @Test
    public void namespace_nesting() throws UtilEvalError {
        final NameSpace root = new NameSpace( (NameSpace)null, "root");
        final NameSpace child = new NameSpace(root, "child");

        root.setLocalVariable("bar", 42, false);
        assertEquals(42, child.getVariable("bar"));

        child.setLocalVariable("bar", 4711, false);
        assertEquals(4711, child.getVariable("bar"));
        assertEquals(42, root.getVariable("bar"));
    }


    @Test
    public void jdownloader_test_case() throws Exception {
        Interpreter root = new Interpreter();
        Interpreter child = new Interpreter(new StringReader(""), System.out, System.err, false, new NameSpace(root.getNameSpace(), "child"));

        // root.eval("int bar=42;");
        child.eval("int bar=4711;");

        root.eval("bar;");  // Correctly prints 42 from root's namepace
        child.eval("bar;"); // Correctly prints 4711 from child's local namespace

        // Let's declare a method that should refer to the "foo" variable in the parent namespace
        root.eval("void foo() { System.out.println(\"bar is \" + bar + \". Namespace is \" + this.namespace + \". Parent namespace is \" + this.namespace.getParent()); }");

        root.eval("foo();"); // Correctly prints 42 from root's namespace as bar is visible inside method
        System.out.println("child.get(\"bar\") -> " + child.get("bar"));
        child.eval("foo();"); // Oops. Should print 4711 as the parent namespace of the method's namespace should be child's namespace, but prints 42 instead.
    }
}
