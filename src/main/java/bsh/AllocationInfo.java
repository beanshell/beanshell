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
 *                                                                           *
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/

package bsh;

/**
 * Records how an anonymous class instance, "new Type(args) { ... }", was allocated.
 * Set on the instance's namespace so the instance can be re-created or inspected;
 * see {@link NameSpace#getAllocation()} and {@link ScriptedSource#of(AllocationInfo)}.
 */
public final class AllocationInfo {

    private final BSHAllocationExpression node;   // the "new Type(args) { ... }" expression
    private final NameSpace scope;                // namespace the expression was evaluated in
    private final Class<?> type;                  // the interface or superclass
    private final CallArguments arguments;        // evaluated constructor arguments

    AllocationInfo(BSHAllocationExpression node, NameSpace scope, Class<?> type,
            CallArguments arguments) {
        this.node = node;
        this.scope = scope;
        this.type = type;
        this.arguments = arguments;
    }

    BSHAllocationExpression getNode() { return node; }

    CallArguments getCallArguments()  { return arguments; }

    /** The namespace the allocation expression was evaluated in. */
    public NameSpace getScope()       { return scope; }

    /** The interface or superclass named in the allocation. */
    public Class<?> getType()         { return type; }

    /**
     * The constructor argument values as originally evaluated, in the form the
     * interpreter passes them on. Primitive values, and the <code>null</code>
     * literal, are wrapped in {@link Primitive}; use {@link Primitive#unwrap(Object)}
     * to get the Java value. Other objects are the very same instances that were
     * passed, not copies.
     * @return a new array each time, so changing it does not change the record
     */
    public Object[] getArgs()         { return arguments.values.clone(); }

    /** Same scope, type and arguments, with a different (edited) expression. */
    AllocationInfo withNode(BSHAllocationExpression edited) {
        return new AllocationInfo(edited, scope, type, arguments);
    }

    /**
     * Same scope, type and arguments, for an edited expression.
     * @throws IllegalArgumentException if the source is not an anonymous allocation
     */
    public AllocationInfo withSource(ScriptedSource edited) {
        return withNode(edited.allocationNode());
    }
}
