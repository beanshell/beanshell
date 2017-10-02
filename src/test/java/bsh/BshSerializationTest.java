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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Assert;
import org.junit.Test;

/**
 * This tests serialization of the beanshell interpreter
 *
 * @author Jessen Yu
 *
 */
public class BshSerializationTest
{
    /**
     * Serializes and then deserializes the given Interpreter.
     *
     * @param origInterpreter
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private Interpreter serDeser(Interpreter origInterpreter) throws IOException, ClassNotFoundException
    {
        ByteArrayOutputStream byteOS = new ByteArrayOutputStream();
        new ObjectOutputStream(byteOS).writeObject(origInterpreter);
        return (Interpreter) new ObjectInputStream(
                new ByteArrayInputStream(byteOS.toByteArray())).readObject();
    }

    /**
     * Tests that Special.NULL_VALUE is correctly serialized/deserialized
     */
    @Test
    public void testNullValueSerialization() throws Throwable
    {
        Interpreter origInterpreter = new Interpreter();
        origInterpreter.eval("myNull = null;");
        Assert.assertEquals(null, origInterpreter.eval("myNull"));
        Interpreter deserInterpreter = serDeser(origInterpreter);
        Assert.assertEquals(null, deserInterpreter.eval("myNull"));
    }

    /**
     * Tests that Primitive.NULL is correctly serialized/deserialized
     */
    @Test
    public void testSpecialNullSerialization() throws Throwable
    {
        Interpreter originalInterpreter = new Interpreter();
        originalInterpreter.eval("myNull = null;");
        Assert.assertTrue(((Boolean)originalInterpreter.eval("myNull == null")).booleanValue());
        Interpreter deserInterpreter = serDeser(originalInterpreter);
        Assert.assertTrue(((Boolean)deserInterpreter.eval("myNull == null")).booleanValue());
    }
}
