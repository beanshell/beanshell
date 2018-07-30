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

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VarargsTest {

    @Test
    public void calling_java_varargs_method_should_be_possible() throws Exception {
        final Interpreter interpreter = new Interpreter();
        interpreter.set("helper", new ClassWithVarargMethods());
        @SuppressWarnings({"unchecked"})
        final List<Object> list = (List<Object>) interpreter.eval("helper.list(1,2,3)");
        // An array initializer may be specified in a declaration, or as part of an array creation expression.
        Assert.assertEquals(Arrays.<Object>asList(1,2,3), list);
        interpreter.close();
    }

    @Test
    public void calling_java_varargs_with_old_syntax_should_be_possible() throws Exception {
        final Interpreter interpreter = new Interpreter();
        interpreter.set("helper", new ClassWithVarargMethods());
        @SuppressWarnings({"unchecked"})
        final List<Object> list = (List<Object>) interpreter.eval("helper.list(new Object[] {1,2,3})");
        Assert.assertEquals(Arrays.<Object>asList(1,2,3), list);
        interpreter.close();
    }

    @Test
    public void calling_java_varargs_with_wrapper_type_array() throws Exception {
        final Interpreter interpreter = new Interpreter();
        interpreter.set("helper", new ClassWithVarargMethods());
        @SuppressWarnings({"unchecked"})
        final List<Object> list = (List<Object>) interpreter.eval("helper.list((Integer) {1,2,3})");
        Assert.assertEquals(Arrays.<Object>asList(1,2,3), list);
        interpreter.close();
    }


    public static class ClassWithVarargMethods {

        public List<Object> list(final Object... args) {
            return new ArrayList<Object>(Arrays.asList(args));
        }


        public List<Object> list(final List<Object> list, final Object... args) {
            list.addAll(Arrays.asList(args));
            return list;
        }
    }
}
