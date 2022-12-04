/** Copyright 2022 Nick nickl- Lombard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */

package bsh;

import java.math.BigInteger;

import org.junit.Assert;
import org.junit.Test;

public class TypesTest {

    /**
     * Test cast primitive to wrapper type
     * @throws Exception in case of failure
     */
    @Test
    public void cast_object_primitive_to_wrapper_type() throws Exception {
        Object casted = Types.castObject(Primitive.wrap(1, Integer.TYPE), Integer.class, Types.CAST);

        Assert.assertEquals(Integer.valueOf(1), casted);
    }

    /**
     * Test can cast primitive to wrapper type
     * @throws Exception in case of failure
     */
    @Test
    public void can_cast_object_primitive_to_wrapper_type() throws Exception {
        Assert.assertTrue(Types.isBshAssignable(Integer.class, Integer.TYPE));
    }

    /**
     * Test cast primitive to wrapper type
     * @throws Exception in case of failure
     */
    @Test
    public void cast_object_primitive_big_integer_to_long_type() throws Exception {
        Object casted = Types.castObject(Primitive.wrap(BigInteger.valueOf(3L), BigInteger.class), Long.class, Types.CAST);

        Assert.assertEquals(Long.valueOf(3), casted);
    }

    /**
     * Test can cast primitive to wrapper type
     * @throws Exception in case of failure
     */
    @Test
    public void can_cast_object_primitive_big_integer_to_long_type() throws Exception {
        Assert.assertTrue(Types.isBshAssignable(Long.class, BigInteger.class));
    }

    /**
     * Test cast primitive to object
     * @throws Exception in case of failure
     */
    @Test
    public void cast_object_primitive_to_object() throws Exception {
        Object casted = Types.castObject(Primitive.wrap(1, Integer.TYPE), Object.class, Types.CAST);

        Assert.assertEquals((Object)Integer.valueOf(1), casted);
    }

    /**
     * Test can cast primitive to object
     * @throws Exception in case of failure
     */
    @Test
    public void can_cast_object_primitive_to_object() throws Exception {
        Assert.assertTrue(Types.isBshAssignable(Object.class, Integer.TYPE));
    }

}
