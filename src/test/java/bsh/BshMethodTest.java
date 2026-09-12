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

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Assert;
import org.junit.Test;

public class BshMethodTest {

    /** The names are installed as namespace variables and printed in method
     * signatures, so every one has to be a usable identifier. */
    @Test
    public void syntheticParameterNames_are_distinct_identifiers() {
        final String[] names = BshMethod.syntheticParameterNames(30);

        Assert.assertEquals("first is a", "a", names[0]);
        Assert.assertEquals("26th is z", "z", names[25]);
        Assert.assertEquals("27th keeps to the identifier space", "a26", names[26]);
        Assert.assertEquals("all 30 are distinct",
                30, new HashSet<>(Arrays.asList(names)).size());

        for (final String name : names) {
            Assert.assertTrue(name + " starts an identifier",
                    Character.isJavaIdentifierStart(name.charAt(0)));
            for (final char ch : name.toCharArray())
                Assert.assertTrue(name + " is all identifier characters",
                        Character.isJavaIdentifierPart(ch));
        }
    }

    /**
     * Verifies that subclasses are not considered equal to superclass interfaces
     * with a (potential) subset of the subclasses fields.
     */
    @SuppressWarnings("serial")
    @Test
    public void testEqualsObject_subclassEquality() {
       // define a simple subclass of BshMethod:
       class SubMethod extends BshMethod {
          public SubMethod(String name, Class<?> returnType, String[] paramNames,
                Class<?>[] paramTypes, Modifiers[] paramModifiers, BSHBlock methodBody,
                NameSpace declaringNameSpace, Modifiers modifiers) {
             super(name, returnType, paramNames, paramTypes, paramModifiers,
                     methodBody, declaringNameSpace, modifiers,
                     false /*isVarArgs*/);
          }
       }
       final String name = "testMethod";

       final BshMethod subInst =
            new SubMethod(name, Integer.class, new String[] {}, new Class[] {},
                    new Modifiers[] {}, null, null, null);
       final BshMethod supInst =
            new BshMethod(name, Integer.class, new String[] {}, new Class[] {},
                    new Modifiers[] {}, null, null, null, false);

       Assert.assertFalse("Subclasses should not be equal to super classes",
            supInst.equals(subInst));
    }

    /**
     * Very simple test to verify hashcode contract.
     */
    @Test
    public void testHashCode_contract() {
       final String name = "testMethod";
       final BshMethod method1 = new BshMethod(name,
             Integer.class, new String[0], new Class[0], new Modifiers[0], null, null, null, false);
       final BshMethod method2 = new BshMethod(name,
             Integer.class, new String[0], new Class[0], new Modifiers[0], null, null, null, false);

       Assert.assertTrue("precondition check for test failed.",
             method2.equals(method1));
       Assert.assertEquals("Equal classes should have equal hashcodes",
             method2.hashCode(), method1.hashCode());
    }
}
