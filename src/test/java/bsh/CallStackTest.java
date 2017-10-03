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

import junit.framework.Assert;
import org.junit.Test;

public class CallStackTest {

    /**
     * <a href="http://code.google.com/p/beanshell2/issues/detail?id=35">Issue 35 - CallStack class should be
     * Serializable</a>
     * @throws Exception in case of failure
     */
    @Test
    public void callStack_should_be_serializable() throws Exception {
        final NameSpace nameSpace = new NameSpace(null, new BshClassManager(), "test");
        nameSpace.setLocalVariable("test", "test", false);
        final CallStack stack = TestUtil.serDeser(new CallStack(nameSpace));
        Assert.assertEquals("test", stack.top().get("test", null));
    }
}
