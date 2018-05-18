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
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static bsh.TestUtil.eval;


/**
 * See <a href="http://code.google.com/p/beanshell2/issues/detail?id=24">issue 24</a>.
 */
@RunWith(FilteredTestRunner.class)
public class AnnotationsParsingTest {

    @Test
    public void annotation_on_method_declaration() throws Exception {
        assertEquals(42, eval("public int myMethod(final int i) {",
                              "   return i * 7;",
                              "}",
                              "return myMethod(6);"));
    }
}
