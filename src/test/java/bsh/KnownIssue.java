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

import java.util.HashSet;
import java.util.Set;

/** The Class KnownIssue. */
public class KnownIssue implements TestFilter {

    /** The Constant KNOWN_FAILING_TESTS. */
    public static final Set<String> KNOWN_FAILING_TESTS = new HashSet<>();
    /** The Constant SKIP_KNOWN_ISSUES. */
    public static final boolean SKIP_KNOWN_ISSUES = Boolean.valueOf(System.getProperties()
            .getProperty("skip_known_issues"));

    /** {@inheritDoc} */
    @Override
    public boolean skip() {
        return SKIP_KNOWN_ISSUES;
    }
}
