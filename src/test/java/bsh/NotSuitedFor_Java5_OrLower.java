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

/**
 * The Class NotSuitedFor_Java5_OrLower.
 */
public class NotSuitedFor_Java5_OrLower implements TestFilter {

    /** The CURREN T V M I S BELO W v 6. */
    public static boolean CURRENT_VM_IS_BELOW_v6 = "1.6"
            .compareTo(System.getProperty("java.version").substring(0, 3)) > 0;

    /** {@inheritDoc} */
    public boolean skip() {
        return CURRENT_VM_IS_BELOW_v6;
    }
}
