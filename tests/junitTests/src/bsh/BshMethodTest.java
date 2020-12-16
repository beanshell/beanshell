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
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/

package bsh;

import junit.framework.Assert;

import org.junit.Test;

public class BshMethodTest {

   /**
    * Verifies that subclasses are not considered equal to superclass interfaces
    * with a (potential) subset of the subclasses fields.
    */
   @SuppressWarnings("serial")
   @Test
   public void testEqualsObject_subclassEquality() {
      // define a simple subclass of BshMethod:
      class SubMethod extends BshMethod {
         public SubMethod(String name, Class returnType, String[] paramNames,
               Class[] paramTypes, BSHBlock methodBody,
               NameSpace declaringNameSpace, Modifiers modifiers) {
            super(name, returnType, paramNames, paramTypes, methodBody,
                  declaringNameSpace, modifiers);
         }
      }

      final String name = "testMethod";
        final BshMethod subInst = new SubMethod(name, Integer.class, new String[]{}, new Class[]{}, null, null, null);
        final BshMethod supInst = new BshMethod(name, Integer.class, new String[]{}, new Class[]{}, null, null, null);

        Assert.assertFalse("Subclasses should not be equal to super classes", supInst.equals(subInst));
   }
   
   /**
    * Very simple test to verify hashcode contract.
    */
   @Test
   public void testHashCode_contract() {
      final String name = "testMethod";
        final BshMethod method1 = new BshMethod(name, Integer.class, new String[]{}, new Class[]{}, null, null, null);
        final BshMethod method2 = new BshMethod(name, Integer.class, new String[]{}, new Class[]{}, null, null, null);

        Assert.assertTrue("precondition check for test failed.", method2.equals(method1));
        Assert.assertEquals("Equal classes should have equal hashcodes", method2.hashCode(), method1.hashCode());
   }
}
