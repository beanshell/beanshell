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

/**
 * This tests serialization of the beanshell interpreter
 *
 * @author Jessen Yu
 */
public class BshSerializationTest {

    /**
     * Tests that Special.NULL_VALUE is correctly serialized/deserialized
     *
     * @throws Exception in case of failure
     */
    @Test
    public void testNullValueSerialization() throws Exception {
        final Interpreter origInterpreter = new Interpreter();
        origInterpreter.eval("myNull = null;");
        Assert.assertNull(origInterpreter.eval("myNull"));
        final Interpreter deserInterpreter = TestUtil.serDeser(origInterpreter);
        Assert.assertNull(deserInterpreter.eval("myNull"));
    }


    /**
     * Tests that Primitive.NULL is correctly serialized/deserialized
     *
     * @throws Exception in case of failure
     */
    @Test
    public void testSpecialNullSerialization() throws Exception {
        final Interpreter originalInterpreter = new Interpreter();
        originalInterpreter.eval("myNull = null;");
        Assert.assertTrue((Boolean) originalInterpreter.eval("myNull == null"));
        final Interpreter deserInterpreter = TestUtil.serDeser(originalInterpreter);
        Assert.assertTrue((Boolean) deserInterpreter.eval("myNull == null"));
    }


    /**
     * Tests that a declared method can be serialized (but not exploited)
     *
     * @throws Exception in case of failure
     */
    @Test
    public void testMethodSerialization() throws Exception {
        final Interpreter origInterpreter = new Interpreter();
        origInterpreter.eval("int method() { return 1337; }");
        Assert.assertEquals(1337, origInterpreter.eval("method()"));
        final Interpreter deserInterpreter = TestUtil.serDeser(origInterpreter);
        Assert.assertEquals(1337, deserInterpreter.eval("method()"));
    }

}
