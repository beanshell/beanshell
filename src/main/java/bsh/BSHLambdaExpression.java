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

import java.util.function.Predicate;

/**
    A lambda expression: either a single untyped identifier (x -> ...) or a
    parenthesized formal parameter list ((a, b) -> ..., (String s) -> ...),
    followed by an expression or block body.
    For the single-identifier form, paramName is set and there is no
    BSHFormalParameters child; otherwise child 0 is the BSHFormalParameters.
*/
class BSHLambdaExpression extends SimpleNode
{
    public static final String UNTYPED_SINGLE_PARAM = null;
    public String paramName = UNTYPED_SINGLE_PARAM;

    BSHLambdaExpression(int id) { super(id); }

    @Override
    public Object eval(CallStack callstack, Interpreter interpreter) throws EvalError {
        String[] paramNames;
        Class<?>[] paramTypes;
        Modifiers[] paramModifiers;
        Node bodyNode;

        if (paramName != null) {
            paramNames = new String[] { paramName };
            paramTypes = new Class<?>[] { null };
            paramModifiers = new Modifiers[] { new Modifiers(Modifiers.PARAMETER) };
            bodyNode = (Node) jjtGetChild(0);
        } else {
            BSHFormalParameters params = (BSHFormalParameters) jjtGetChild(0);
            paramNames = params.getParamNames();
            paramTypes = (Class<?>[]) params.eval(callstack, interpreter);
            paramModifiers = params.getParamModifiers();
            bodyNode = (Node) jjtGetChild(1);
        }

        return new BshLambda(this, callstack.top(), interpreter,
            paramNames, paramTypes, paramModifiers, bodyNode, bodyShape(bodyNode));
    }

    /** Which methods the body fits (JLS 15.27.2): an expression fits a value
        method, and a statement expression a void one too; a block with a valued
        return fits a value method, one with a bare return or that can complete
        normally a void method, and any other block either. */
    static int bodyShape(Node body) {
        if (!(body instanceof BSHBlock))
            return isStatementExpression(body) ? BshLambda.EITHER : BshLambda.VALUE;
        if (contains(body, BSHLambdaExpression::isValueReturn))
            return BshLambda.VALUE;
        if (contains(body, BSHLambdaExpression::isBareReturn) || canCompleteNormally(body))
            return BshLambda.VOID;
        return BshLambda.EITHER;
    }

    // JLS 14.8; Expression() always builds a BSHAssignment, operator or not.
    private static boolean isStatementExpression(Node body) {
        if (((BSHAssignment) body).operator != null || isMethodInvocation(body))
            return true;
        Node expression = body.jjtGetChild(0);
        if (expression instanceof BSHUnaryExpression) {
            int kind = ((BSHUnaryExpression) expression).kind;
            return kind == ParserConstants.INCR || kind == ParserConstants.DECR;
        }
        if (!(expression instanceof BSHPrimaryExpression))
            return false;
        Node last = expression.jjtGetChild(expression.jjtGetNumChildren() - 1);
        if (last instanceof BSHPrimarySuffix)
            return ((BSHPrimarySuffix) last).operation == BSHPrimarySuffix.NEW;
        return last instanceof BSHAllocationExpression && last.jjtGetNumChildren() > 1
            && last.jjtGetChild(1) instanceof BSHArguments;
    }

    /** Whether an expression body is a method call, whose void result bsh takes
        as real, unlike that of an undefined name or field. */
    static boolean isMethodInvocation(Node body) {
        if (!(body instanceof BSHAssignment) || ((BSHAssignment) body).operator != null
                || !(body.jjtGetChild(0) instanceof BSHPrimaryExpression))
            return false;
        Node primary = body.jjtGetChild(0);
        Node last = primary.jjtGetChild(primary.jjtGetNumChildren() - 1);
        return last instanceof BSHMethodInvocation
            || last instanceof BSHPrimarySuffix && ((BSHPrimarySuffix) last).operation == BSHPrimarySuffix.NAME
                && last.jjtGetNumChildren() > 0;
    }

    private static boolean isValueReturn(BSHReturnStatement jump) {
        return jump.kind == ParserConstants.RETURN && jump.jjtGetNumChildren() > 0;
    }

    private static boolean isBareReturn(BSHReturnStatement jump) {
        return jump.kind == ParserConstants.RETURN && jump.jjtGetNumChildren() == 0;
    }

    private static boolean isBreak(BSHReturnStatement jump) {
        return jump.kind == ParserConstants.BREAK;
    }

    // Nested lambdas and methods (including those of local and anonymous
    // classes) have returns of their own.
    private static boolean contains(Node node, Predicate<BSHReturnStatement> jump) {
        if (node instanceof BSHReturnStatement)
            return jump.test((BSHReturnStatement) node);
        if (node instanceof BSHLambdaExpression || node instanceof BSHMethodDeclaration)
            return false;
        for (int i = 0; i < node.jjtGetNumChildren(); i++)
            if (contains(node.jjtGetChild(i), jump))
                return true;
        return false;
    }

    // JLS 14.22, answering true wherever it is unsure; any break at all is
    // taken to end its loop, switch or label.
    private static boolean canCompleteNormally(Node node) {
        int n = node.jjtGetNumChildren();
        if (node instanceof BSHThrowStatement)
            return false;
        if (node instanceof BSHBlock)
            return n == 0 || canCompleteNormally(node.jjtGetChild(n - 1));
        if (node instanceof BSHIfStatement)
            return n < 3 || canCompleteNormally(node.jjtGetChild(1))
                || canCompleteNormally(node.jjtGetChild(2));
        if (node instanceof BSHWhileStatement)
            return !isTrue(node.jjtGetChild(((BSHWhileStatement) node).isDoStatement ? n - 1 : 0))
                || contains(node, BSHLambdaExpression::isBreak);
        if (node instanceof BSHForStatement) {
            BSHForStatement loop = (BSHForStatement) node;
            return loop.hasExpression && !isTrue(node.jjtGetChild(loop.hasForInit ? 1 : 0))
                || contains(node, BSHLambdaExpression::isBreak);
        }
        if (node instanceof BSHLabeledStatement)
            return n == 0 || canCompleteNormally(node.jjtGetChild(0)) || contains(node, BSHLambdaExpression::isBreak);
        if (node instanceof BSHSwitchStatement) {
            boolean hasDefault = false;
            for (int i = 1; i < n; i++)
                hasDefault |= node.jjtGetChild(i) instanceof BSHSwitchLabel
                    && ((BSHSwitchLabel) node.jjtGetChild(i)).isDefault;
            Node last = node.jjtGetChild(n - 1);
            return !hasDefault || canCompleteNormally(last)
                || contains(node, BSHLambdaExpression::isBreak);
        }
        if (node instanceof BSHTryStatement) {
            int i = node.jjtGetChild(0) instanceof BSHTryWithResources ? 1 : 0;
            boolean completes = canCompleteNormally(node.jjtGetChild(i++));
            for (; i < n; i++) {
                if (!(node.jjtGetChild(i) instanceof BSHMultiCatch))
                    return completes && canCompleteNormally(node.jjtGetChild(i));
                completes |= canCompleteNormally(node.jjtGetChild(++i));
            }
            return completes;
        }
        return true;
    }

    private static boolean isTrue(Node condition) {
        while ((condition instanceof BSHAssignment || condition instanceof BSHPrimaryExpression)
                && condition.jjtGetNumChildren() == 1)
            condition = condition.jjtGetChild(0);
        return condition instanceof BSHLiteral && ((BSHLiteral) condition).value == Primitive.TRUE;
    }

    @Override
    public String toString() {
        return super.toString() + ": " + (paramName != null ? paramName : "(...)");
    }
}
