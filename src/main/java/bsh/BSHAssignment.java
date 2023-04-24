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

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

class BSHAssignment extends SimpleNode implements ParserConstants {
    private static final long serialVersionUID = 1L;
    public Integer operator;

    BSHAssignment(int id) { super(id); }

    public Object eval(CallStack callstack, Interpreter interpreter)
            throws EvalError {
        if ( null == operator ) try {
            Object retrn = jjtGetChild(0).eval(callstack, interpreter);
            // intercept LambdaMethodReference returned from BSHPrimarySuffix
            // if (retrn instanceof LambdaMethodReference)
            //     return assignMethodReference(((BSHPrimaryExpression)
            //         jjtGetChild(0)).toLHS(callstack, interpreter),
            //         (LambdaMethodReference)retrn, callstack);
            return retrn;
        } catch (SafeNavigate aborted) {
            return Primitive.NULL;
        }

        BSHPrimaryExpression lhsNode =
            (BSHPrimaryExpression) jjtGetChild(0);

        boolean strictJava = interpreter.getStrictJava();
        LHS lhs = lhsNode.toLHS( callstack, interpreter);

        // For operator-assign operations save the lhs value before evaluating
        // the rhs.  This is correct Java behavior for postfix operations
        // e.g. i=1; i+=i++; // should be 2 not 3
        Object lhsValue = null;
        if ( operator != ASSIGN ) try { // assign doesn't need the lhs value
            lhsValue = lhs.getValue();
        } catch ( UtilEvalError e ) {
            throw e.toEvalError( this, callstack );
        }

        if ( operator == NULLCOALESCEASSIGN &&  Primitive.NULL != lhsValue )
            return lhsValue; // return non null lhs before evaluating rhs

        // evaluate the right hand side
        Object rhs = jjtGetChild(1).eval(callstack, interpreter);

        if ( rhs == Primitive.VOID )
            throw new EvalError("illegal void assignment", this, callstack);

        try {
            switch( operator ) {
                case ASSIGN:
                    if (lhs.isFinal()) {
                        lhs.getVariable().setValue(rhs, Variable.ASSIGNMENT);
                        return rhs;
                    }
                    return lhs.assign(rhs, strictJava);

                case NULLCOALESCEASSIGN:
                    // we already know lhs is null
                    return lhs.assign(rhs, strictJava);

                case PLUSASSIGN:
                    if ( Primitive.NULL == lhsValue && lhs.getType() == String.class )
                        lhsValue = "null";
                    return lhs.assign(
                        operation(lhsValue, rhs, PLUS), strictJava);

                case MINUSASSIGN:
                    return lhs.assign(
                        operation(lhsValue, rhs, MINUS), strictJava);

                case STARASSIGN:
                    return lhs.assign(
                        operation(lhsValue, rhs, STAR), strictJava);

                case SLASHASSIGN:
                    return lhs.assign(
                        operation(lhsValue, rhs, SLASH), strictJava);

                case ANDASSIGN:
                case ANDASSIGNX:
                    return lhs.assign(
                        operation(lhsValue, rhs, BIT_AND), strictJava);

                case ORASSIGN:
                case ORASSIGNX:
                    return lhs.assign(
                        operation(lhsValue, rhs, BIT_OR), strictJava);

                case XORASSIGN:
                case XORASSIGNX:
                    return lhs.assign(
                        operation(lhsValue, rhs, XOR), strictJava);

                case MODASSIGN:
                case MODASSIGNX:
                    return lhs.assign(
                        operation(lhsValue, rhs, MOD), strictJava );

                case POWERASSIGN:
                case POWERASSIGNX:
                    return lhs.assign(
                            operation(lhsValue, rhs, POWER), strictJava);

                case LSHIFTASSIGN:
                case LSHIFTASSIGNX:
                    return lhs.assign(
                        operation(lhsValue, rhs, LSHIFT), strictJava);

                case RSIGNEDSHIFTASSIGN:
                case RSIGNEDSHIFTASSIGNX:
                    return lhs.assign(
                    operation(lhsValue, rhs, RSIGNEDSHIFT ), strictJava);

                case RUNSIGNEDSHIFTASSIGN:
                case RUNSIGNEDSHIFTASSIGNX:
                    return lhs.assign(
                        operation(lhsValue, rhs, RUNSIGNEDSHIFT),
                        strictJava);

                default:
                    throw new InterpreterError(
                        "unimplemented operator in assignment BSH");
            }
        } catch ( UtilEvalError e ) {
            throw e.toEvalError( this, callstack );
        }
    }

    Object assignMethodReference(LHS lhs, LambdaMethodReference rhs, CallStack callstack) throws EvalError {
        System.out.println(lhs + " \n" +rhs);
        // DEFUNCT: was hoping to get Interface class from lhs.getType but for formal parameters this is still unknown
        try {
            MethodHandles.Lookup caller = MethodHandles.lookup();
            if (!rhs.hasInstance()) { // static method
                MethodType methodType = MethodType.methodType(String.class, Object.class);
                // methodType = MethodType.methodType(Double.TYPE, Double.TYPE);
                MethodHandle inv = caller.findStatic(rhs.getType(), rhs.getName(), methodType);
                methodType = MethodType.methodType(Object.class, Object.class);
                MethodType invokeType = MethodType.methodType(lhs.getType());
                CallSite site = LambdaMetafactory.metafactory(caller, "apply", invokeType, methodType, inv, methodType);
                MethodHandle factory = site.getTarget();
                return lhs.assign(factory.invoke());
            } else { // virtual instance method - invokeType changes and factory invoke takes instance
                MethodType methodType = MethodType.methodType(Void.TYPE, Object.class);
                MethodHandle inv = caller.findVirtual(rhs.getType(), rhs.getName(), methodType);
                MethodType invokeType = MethodType.methodType(lhs.getType(), rhs.getType());
                CallSite site = LambdaMetafactory.metafactory(caller, "accept", invokeType, methodType, inv, methodType);
                MethodHandle factory = site.getTarget();
                return lhs.assign(factory.invoke(rhs.getInstance()));
            }
            //System.out.println(obj+" "+obj.getClass()+" "+field+" "+clz.newInstance());


        } catch (UtilEvalError e) {
            throw e.toEvalError(getSourceFile(), this, callstack);
        } catch (Throwable e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return lhs;
    }

    /** Convenience method to shuffle the different types off to
     * the correct helper classes for application.
     * @param lhs the assignee
     * @param rhs the assigner
     * @param kind the operation kind
     * @return the operated result
     * @throws UtilEvalError of invalidation */
    private Object operation( Object lhs, Object rhs, int kind )
            throws UtilEvalError {
        if ( lhs instanceof String || lhs.getClass().isArray() )
            return Operators.arbitraryObjectsBinaryOperation(lhs, rhs, kind);

        if ( rhs == Primitive.NULL )
            throw new UtilEvalError(
                "Illegal use of null object or 'null' literal" );

        if ( (lhs instanceof Boolean || lhs instanceof Character
               || lhs instanceof Number || lhs instanceof Primitive)
               && (rhs instanceof Boolean || rhs instanceof Character
               || rhs instanceof Number || rhs instanceof Primitive) ) {
            return Operators.binaryOperation(lhs, rhs, kind);
        }

        throw new UtilEvalError("Non primitive value in operator: " +
            lhs.getClass() + " " + tokenImage[kind] + " " + rhs.getClass());
    }

    @Override
    public String toString() {
        return super.toString() + (null == operator ? "" : ": " + tokenImage[operator]);
    }
}
