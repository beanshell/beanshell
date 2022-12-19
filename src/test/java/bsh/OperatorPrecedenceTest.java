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

import static org.junit.Assert.assertEquals;

/**
 * Check that the order of operator evaluation is identical to Java.
 */
public class OperatorPrecedenceTest {

   private Interpreter i = new Interpreter();

    @Test
    public void issue_568() throws Exception {

       // Issue #568
       assertEquals("true || false ? \"True\" : \"False\"", i.eval("true || false ? \"True\" : \"False\""), true || false ? "True" : "False");
    }

    @Test
    public void issue_590() throws Exception {

       // Issue #590
       assertEquals("1000+60*0", i.eval("1000+60*0"), 1000+60*0);
    }

    @Test
    public void precedence_tests() throws Exception {

       assertEquals("1+1", i.eval("1+1"), 1+1);

       assertEquals("false?true?1:2:false?3:4", i.eval("false?true?1:2:false?3:4"), false?true?1:2:false?3:4);

       assertEquals("true||false&&false||false", i.eval("true||false&&false||false"), true||false&&false||false);
       assertEquals("true||false&&false||false?1:2", i.eval("true||false&&false||false?1:2"), true||false&&false||false?1:2);
       assertEquals("1==0||false&&false||false?1:2", i.eval("1==0||false&&false||false?1:2"), 1==0||false&&false||false?1:2);
       assertEquals("1==1||false&&false||false?1:2", i.eval("1==1||false&&false||false?1:2"), 1==1||false&&false||false?1:2);
       assertEquals("false||1<0==false||false?1:2", i.eval("false||1<0==false||false?1:2"), false||1<0==false||false?1:2);
       assertEquals("false||1>=0==false||false?1:2", i.eval("false||1>=0==false||false?1:2"), false||1>=0==false||false?1:2);
       assertEquals("false||1+1==2==false||false?1:2", i.eval("false||1+1==2==false||false?1:2"), false||1+1==2==false||false?1:2);
       assertEquals("1+2*3<<2", i.eval("1+2*3<<2"), 1+2*3<<2);
       assertEquals("false||1+2*3<<2==28==false||false?1:2", i.eval("false||1+2*3<<2==28==false||false?1:2"), false||1+2*3<<2==28==false||false?1:2);
       assertEquals("31/7/2", i.eval("31/7/2"), 31/7/2);
       assertEquals("1+31/7/2*10-5", i.eval("1+31/7/2*10-5"), 1+31/7/2*10-5);
       assertEquals("1+31/7/2*10+ -5", i.eval("1+31/7/2*10+ -5"), 1+31/7/2*10+ -5);
       assertEquals("- - - - -5", i.eval("- - - - -5"), - - - - -5);
       assertEquals("- + - + -5", i.eval("- + - + -5"), - + - + -5);
       assertEquals("\"a\"+\"b\".equals(\"b\")", i.eval("\"a\"+\"b\".equals(\"b\")"), "a"+"b".equals("b"));
       // power
       assertEquals("1+2*3**4", i.eval("1+2*3**4"), (int)(1+2*Math.pow(3,4)));
       // increment
       int j=2;
       assertEquals("-2++", i.eval("j=2;-j++"), -j++);
       assertEquals("j", i.eval("j;"), j);
       j=2;
       assertEquals("+2--", i.eval("j=2;+j--"), +j--);
       assertEquals("j", i.eval("j;"), j);

       double[] k= new double[1];
       assertEquals("-k[0]++", i.eval("k=new double[1];-k[0]++"), -k[0]++);
       assertEquals("k[0]", i.eval("k[0];"), k[0]);

       // mult/mod
       assertEquals("2*5%3", i.eval("2*5%3"), (int)(2*5%3));

       // mod/power
       assertEquals("5%3**4", i.eval("5%3**4"), (int)(5%Math.pow(3,4)));
       assertEquals("5**3%4", i.eval("5**3%4"), (int)(Math.pow(5,3)%4));

       // casting
       assertEquals("(int)(5.0/2.0)", i.eval("(int)(5.0/2.0)"), (int)(5.0/2.0));
       assertEquals("(int)-(5.0/2.0)", i.eval("(int)-(5.0/2.0)"), (int)-(5.0/2.0));

       // Trinary
       assertEquals("true && false ? true : true && false ? true : false;",
                    i.eval("true && false ? true : true && false ? true : false;"),
                    true && false ? true : true && false ? true : false);
    }
}
