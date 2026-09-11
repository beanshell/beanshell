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

/** Values and lookup types belonging to one invocation, never to a cached node. */
final class CallArguments {
    final Object[] values;
    final Class<?>[] types;

    CallArguments(Object[] values) {
        this(values == null ? Reflect.ZERO_ARGS : values,
                Types.getTypes(values == null ? Reflect.ZERO_ARGS : values));
    }

    CallArguments(Object[] values, Class<?>[] types) {
        this.values = values;
        this.types = types;
    }

    /** Optional output slot for a declared type discovered during evaluation. */
    static final class Result {
        Class<?> type;

        void variable(NameSpace namespace, String name) throws UtilEvalError {
            Variable variable = namespace.getVariableImpl(name, true);
            type = variable == null ? null : variable.getType();
        }

        void field(Object object, String name) throws UtilEvalError, ReflectError {
            if (object instanceof This) {
                variable(((This) object).getNameSpace(), name);
                return;
            }
            Class<?> owner = object instanceof Class ? (Class<?>) object : object.getClass();
            Invocable field = BshClassManager.memberCache.get(owner).findField(name);
            type = field == null ? null : field.getReturnType();
        }
    }

    /** Evaluate once, preserving declared types only when the resulting value is null. */
    static Object eval(Node node, CallStack stack, Interpreter interpreter, Result result)
            throws EvalError {
        if (node instanceof BSHAssignment) {
            BSHAssignment assignment = (BSHAssignment) node;
            if (assignment.operator == null) try {
                return eval(node.jjtGetChild(0), stack, interpreter, result);
            } catch (SafeNavigate aborted) {
                result.type = null;
                return Primitive.NULL;
            }
            return assignment.eval(stack, interpreter, result);
        }
        if (node instanceof BSHTernaryExpression)
            return ((BSHTernaryExpression) node).eval(stack, interpreter, result);
        if (node instanceof BSHPrimaryExpression)
            return ((BSHPrimaryExpression) node).eval(stack, interpreter, result);
        if (node instanceof BSHMethodInvocation)
            return ((BSHMethodInvocation) node).eval(stack, interpreter, result);
        if (node instanceof BSHAmbiguousName) try {
            return ((BSHAmbiguousName) node).getName(stack.top())
                    .toObject(stack, interpreter, false, result);
        } catch (UtilEvalError e) {
            throw e.toEvalError(node, stack);
        }
        Object value = node.eval(stack, interpreter);
        if (node instanceof BSHCastExpression)
            result.type = ((BSHType) node.jjtGetChild(0)).getType(stack, interpreter);
        return value;
    }
}
