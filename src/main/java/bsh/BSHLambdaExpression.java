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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /** Overload resolution's last marker for this expression; see BshLambda.marker. */
    transient volatile BshLambda.Marker marker;

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
            validateParameterList(params, paramNames, paramTypes, callstack);
        }

        return new BshLambda(this, callstack.top(), interpreter,
            paramNames, paramTypes, paramModifiers, bodyNode, bodyShape(bodyNode));
    }

    // JLS 15.27.1: a lambda's formal parameter list must be entirely typed or
    // entirely untyped, its names unique, and an inferred parameter unannotated
    // -- unlike a method declaration, which FormalParameters() also serves.
    private void validateParameterList(BSHFormalParameters params, String[] paramNames,
            Class<?>[] paramTypes, CallStack callstack) throws EvalException {
        boolean anyTyped = false, anyUntyped = false;
        for (Class<?> type : paramTypes)
            if (type != null) anyTyped = true; else anyUntyped = true;
        if (anyTyped && anyUntyped)
            throw new EvalException(
                "A lambda's parameters must be either all typed or all untyped", this, callstack);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < paramNames.length; i++) {
            if (!seen.add(paramNames[i]))
                throw new EvalException(
                    "Duplicate lambda parameter name: " + paramNames[i], this, callstack);
            if (paramTypes[i] == null && ((BSHFormalParameter) params.jjtGetChild(i)).annotated)
                throw new EvalException(
                    "An inferred lambda parameter cannot be annotated: " + paramNames[i], this, callstack);
        }
    }

    /** Which methods the body fits (JLS 15.27.2): an expression fits a value
        method, and a statement expression a void one too; a block with a valued
        return fits a value method, one with a bare return or that can complete
        normally a void method, and any other block either. A valued return next
        to a bare return or a reachable end is INVALID; a valued return whose
        completion bsh cannot decide is VALUE. VOID_UNSURE is a void block whose
        completing rests on an unfoldable condition or a break. */
    static int bodyShape(Node body) {
        if (!(body instanceof BSHBlock))
            return isStatementExpression(body) ? BshLambda.EITHER : BshLambda.VALUE;
        boolean hasValueReturn = contains(body, BSHLambdaExpression::isValueReturn);
        boolean hasBareReturn = contains(body, BSHLambdaExpression::isBareReturn);
        int completion = completion(body);
        if (hasValueReturn && (hasBareReturn || completion == COMPLETES))
            return BshLambda.INVALID;
        if (hasValueReturn)
            return BshLambda.VALUE;
        if (hasBareReturn)
            return BshLambda.VOID;
        return completion == COMPLETES ? BshLambda.VOID
            : completion == NEVER ? BshLambda.EITHER : BshLambda.VOID_UNSURE;
    }

    /** The statically known result of a body, or null. Deliberately narrow:
        literals, unary +, - and ~ on them, primitive casts, a declared parameter
        read back, and operators whose result type follows from their operands;
        resolving any other name could initialize classes. */
    static LambdaDescriptor.Result result(Node body) {
        return result(body, new String[0], new Class<?>[0]);
    }

    static LambdaDescriptor.Result result(Node body, String[] paramNames, Class<?>[] paramTypes) {
        Map<String, Class<?>> declared = new HashMap<>();
        for (int i = 0; i < paramNames.length; i++)
            if (paramTypes[i] != null)
                declared.put(paramNames[i], paramTypes[i]);
        if (!(body instanceof BSHBlock))
            return expressionResult(body, declared);
        removeRedeclared(body, declared);
        List<Node> returned = new ArrayList<>();
        collectValueReturns(body, returned);
        if (returned.isEmpty())
            return null;
        Class<?> type = null;
        Set<Object> constants = new HashSet<>();
        for (Node expression : returned) {
            LambdaDescriptor.Result each = expressionResult(expression, declared);
            if (each == null || type != null && each.type != type)
                return null;
            type = each.type;
            if (constants != null && each.constants != null)
                constants.addAll(each.constants);
            else
                constants = null;
        }
        return new LambdaDescriptor.Result(type, constants);
    }

    /** bsh, unlike Java (JLS 6.4), lets a block redeclare a lambda parameter's name
        -- as a local, a catch parameter, a for-each variable or a resource. That name
        then holds the local, so the parameter's declared type says nothing about it.
        One redeclaration anywhere drops the name for the whole body: a name bsh only
        might know is treated exactly like one it cannot know. Nested lambdas and
        methods bind in their own scope, so their declarations do not count. */
    private static void removeRedeclared(Node node, Map<String, Class<?>> declared) {
        if (node instanceof BSHVariableDeclarator)
            declared.remove(((BSHVariableDeclarator) node).name);
        else if (node instanceof BSHMultiCatch)
            declared.remove(((BSHMultiCatch) node).name);
        else if (node instanceof BSHEnhancedForStatement)
            declared.remove(((BSHEnhancedForStatement) node).varName);
        else if (node instanceof BSHLambdaExpression || node instanceof BSHMethodDeclaration)
            return;
        for (int i = 0; i < node.jjtGetNumChildren() && !declared.isEmpty(); i++)
            removeRedeclared(node.jjtGetChild(i), declared);
    }

    private static void collectValueReturns(Node node, List<Node> returned) {
        if (node instanceof BSHReturnStatement && isValueReturn((BSHReturnStatement) node))
            returned.add(node.jjtGetChild(0));
        else if (!(node instanceof BSHLambdaExpression || node instanceof BSHMethodDeclaration))
            for (int i = 0; i < node.jjtGetNumChildren(); i++)
                collectValueReturns(node.jjtGetChild(i), returned);
    }

    private static LambdaDescriptor.Result expressionResult(Node expression, Map<String, Class<?>> declared) {
        Node node = unwrap(expression);
        if (node instanceof BSHAmbiguousName) {
            Class<?> type = declared.get(((BSHAmbiguousName) node).text);
            return type == null ? null : new LambdaDescriptor.Result(type, null);
        }
        if (node instanceof BSHLiteral)
            return isWidenedToLong((BSHLiteral) node) ? null : constantResult(((BSHLiteral) node).value);
        if (node instanceof BSHUnaryExpression) {
            BSHUnaryExpression unary = (BSHUnaryExpression) node;
            if (!unary.postfix && unary.kind == ParserConstants.BANG)
                return new LambdaDescriptor.Result(boolean.class, null);
            if (unary.postfix || unary.kind != ParserConstants.PLUS && unary.kind != ParserConstants.MINUS
                    && unary.kind != ParserConstants.TILDE)
                return null;
            if (unary.kind == ParserConstants.MINUS && isIntMinimumMagnitude(node.jjtGetChild(0)))
                return constantResult(new Primitive(Integer.MIN_VALUE));
            LambdaDescriptor.Result operand = expressionResult(node.jjtGetChild(0), declared);
            if (operand == null)
                return null;
            if (operand.constants != null)
                return constantResult(fold(node));
            Class<?> numeric = numericOrNull(operand.type);
            if (numeric == null || unary.kind == ParserConstants.TILDE && !isIntegral(numeric))
                return null;
            return new LambdaDescriptor.Result(promoted(numeric, int.class), null);
        }
        if (node instanceof BSHBinaryExpression) {
            int kind = ((BSHBinaryExpression) node).kind;
            LambdaDescriptor.Result lhs = expressionResult(node.jjtGetChild(0), declared);
            LambdaDescriptor.Result rhs = expressionResult(node.jjtGetChild(1), declared);
            if (isBooleanValued(kind))
                return lhs != null && lhs.constants != null && rhs != null && rhs.constants != null
                    ? constantResult(fold(node)) : new LambdaDescriptor.Result(boolean.class, null);
            if (lhs == null || rhs == null)
                return null;
            if (lhs.constants != null && rhs.constants != null)
                return constantResult(fold(node));
            Class<?> promoted = operatorType(kind, lhs.type, rhs.type);
            return promoted == null ? null : new LambdaDescriptor.Result(promoted, null);
        }
        if (node instanceof BSHTernaryExpression) {
            LambdaDescriptor.Result condition = expressionResult(node.jjtGetChild(0), declared);
            if (condition == null || condition.constants == null) {
                LambdaDescriptor.Result then = expressionResult(node.jjtGetChild(1), declared);
                LambdaDescriptor.Result otherwise = expressionResult(node.jjtGetChild(2), declared);
                return then == null || otherwise == null || then.type != otherwise.type
                    ? null : new LambdaDescriptor.Result(then.type, null);
            }
            LambdaDescriptor.Result taken = expressionResult(fold(node.jjtGetChild(0)) == Primitive.TRUE
                ? node.jjtGetChild(1) : node.jjtGetChild(2), declared);
            return taken == null || taken.constants == null ? null : constantResult(fold(node));
        }
        Class<?> cast = primitiveCast(node);
        if (cast == null)
            return null;
        LambdaDescriptor.Result operand = expressionResult(node.jjtGetChild(1), declared);
        if (operand == null || operand.constants == null)
            return new LambdaDescriptor.Result(cast, null);
        return constantResult(fold(node));
    }

    // JLS 15.20-15.24, 15.20.2: relational, equality, conditional and instanceof.
    private static boolean isBooleanValued(int kind) {
        switch (kind) {
            case ParserConstants.LT: case ParserConstants.LTX: case ParserConstants.GT: case ParserConstants.GTX:
            case ParserConstants.LE: case ParserConstants.LEX: case ParserConstants.GE: case ParserConstants.GEX:
            case ParserConstants.EQ: case ParserConstants.NE:
            case ParserConstants.BOOL_AND: case ParserConstants.BOOL_ANDX:
            case ParserConstants.BOOL_OR: case ParserConstants.BOOL_ORX:
            case ParserConstants.INSTANCEOF:
                return true;
            default:
                return false;
        }
    }

    private static final List<Class<?>> PROMOTION = Arrays.asList(
        int.class, long.class, float.class, double.class);

    // JLS 5.6.2 binary numeric promotion (boxed operands unboxed), 15.18.1 string
    // concatenation, 15.19 shifts (left operand promoted alone), 15.22 bitwise
    // and logical & | ^. Null where bsh cannot know the type.
    private static Class<?> operatorType(int kind, Class<?> lhs, Class<?> rhs) {
        if (kind == ParserConstants.PLUS && (lhs == String.class || rhs == String.class))
            return String.class;
        Class<?> l = numericOrNull(lhs), r = numericOrNull(rhs);
        switch (kind) {
            case ParserConstants.PLUS: case ParserConstants.MINUS: case ParserConstants.STAR:
            case ParserConstants.SLASH: case ParserConstants.MOD:
                return l == null || r == null ? null : promoted(l, r);
            case ParserConstants.LSHIFT: case ParserConstants.LSHIFTX:
            case ParserConstants.RSIGNEDSHIFT: case ParserConstants.RSIGNEDSHIFTX:
            case ParserConstants.RUNSIGNEDSHIFT: case ParserConstants.RUNSIGNEDSHIFTX:
                return l == null || r == null || !isIntegral(l) || !isIntegral(r) ? null : promoted(l, int.class);
            case ParserConstants.BIT_AND: case ParserConstants.BIT_ANDX:
            case ParserConstants.BIT_OR: case ParserConstants.BIT_ORX:
            case ParserConstants.XOR: case ParserConstants.XORX:
                if (unboxed(lhs) == boolean.class && unboxed(rhs) == boolean.class)
                    return boolean.class;
                return l == null || r == null || !isIntegral(l) || !isIntegral(r) ? null : promoted(l, r);
            default:
                return null;
        }
    }

    private static Class<?> unboxed(Class<?> type) {
        return type != null && Primitive.isWrapperType(type) ? Primitive.unboxType(type) : type;
    }

    private static Class<?> numericOrNull(Class<?> type) {
        Class<?> u = unboxed(type);
        return u == null || !u.isPrimitive() || u == boolean.class ? null : u;
    }

    private static boolean isIntegral(Class<?> primitive) {
        return primitive != float.class && primitive != double.class;
    }

    private static Class<?> promoted(Class<?> a, Class<?> b) {
        int ia = Math.max(0, PROMOTION.indexOf(a)), ib = Math.max(0, PROMOTION.indexOf(b));
        return PROMOTION.get(Math.max(ia, ib));
    }

    // JLS 3.10.1: an unparenthesized 2147483648 under unary minus is the int minimum.
    private static boolean isIntMinimumMagnitude(Node operand) {
        return operand instanceof BSHPrimaryExpression && operand.jjtGetNumChildren() == 1
            && operand.jjtGetChild(0) instanceof BSHLiteral
            && "2147483648".equals(((SimpleNode) operand.jjtGetChild(0)).firstToken.image.replace("_", ""));
    }

    // bsh makes an unsuffixed integer literal past int's range a long, where javac
    // would keep int (-2147483648) or reject it, so its type tells resolution nothing.
    private static boolean isWidenedToLong(BSHLiteral literal) {
        String image = ((SimpleNode) literal).firstToken.image;
        return literal.value instanceof Primitive && ((Primitive) literal.value).getType() == long.class
            && !image.endsWith("l") && !image.endsWith("L");
    }

    private static LambdaDescriptor.Result constantResult(Object value) {
        if (value == NOT_CONSTANT)
            return null;
        Set<Object> constants = new HashSet<>();
        if (value == Primitive.NULL) {
            constants.add(null);
            return new LambdaDescriptor.Result(LambdaDescriptor.NullType.class, constants);
        }
        constants.add(Primitive.unwrap(value));
        return new LambdaDescriptor.Result(Types.getType(value), constants);
    }

    private static final Object NOT_CONSTANT = new Object();

    /** The value of a constant expression built from literals, operators and
        primitive casts (JLS 15.28) as bsh evaluates it, or NOT_CONSTANT. */
    private static Object fold(Node expression) {
        Node node = unwrap(expression);
        try {
            if (node instanceof BSHLiteral)
                return ((BSHLiteral) node).value;
            if (node instanceof BSHUnaryExpression) {
                BSHUnaryExpression unary = (BSHUnaryExpression) node;
                Object operand = unary.postfix ? NOT_CONSTANT : fold(node.jjtGetChild(0));
                return operand instanceof Primitive && operand != Primitive.NULL
                    && unary.kind != ParserConstants.INCR && unary.kind != ParserConstants.DECR
                    ? Operators.unaryOperation((Primitive) operand, unary.kind) : NOT_CONSTANT;
            }
            if (node instanceof BSHBinaryExpression) {
                int kind = ((BSHBinaryExpression) node).kind;
                Object lhs = fold(node.jjtGetChild(0)), rhs = fold(node.jjtGetChild(1));
                return kind != ParserConstants.INSTANCEOF && isOperand(lhs) && isOperand(rhs)
                    ? Operators.binaryOperation(lhs, rhs, kind) : NOT_CONSTANT;
            }
            if (node instanceof BSHTernaryExpression) {
                Object condition = fold(node.jjtGetChild(0));
                if (condition != Primitive.TRUE && condition != Primitive.FALSE)
                    return NOT_CONSTANT;
                Object then = fold(node.jjtGetChild(1)), otherwise = fold(node.jjtGetChild(2));
                return then == NOT_CONSTANT || otherwise == NOT_CONSTANT ? NOT_CONSTANT
                    : condition == Primitive.TRUE ? then : otherwise;
            }
            Class<?> cast = primitiveCast(node);
            Object operand = cast == null ? NOT_CONSTANT : fold(node.jjtGetChild(1));
            return operand instanceof Primitive && operand != Primitive.NULL
                ? ((Primitive) operand).castToType(cast, Types.CAST) : NOT_CONSTANT;
        } catch (UtilEvalError | RuntimeException e) {
            return NOT_CONSTANT;
        }
    }

    private static boolean isOperand(Object value) {
        return value instanceof String || value instanceof Primitive && value != Primitive.NULL;
    }

    private static Class<?> primitiveCast(Node node) {
        if (!(node instanceof BSHCastExpression))
            return null;
        BSHType type = (BSHType) node.jjtGetChild(0);
        return type.getArrayDims() == 0 && type.jjtGetChild(0) instanceof BSHPrimitiveType
            ? ((BSHPrimitiveType) type.jjtGetChild(0)).getType() : null;
    }

    // Parentheses and the statement-level Expression() wrapper.
    static Node unwrap(Node node) {
        while (node.jjtGetNumChildren() == 1 && (node instanceof BSHPrimaryExpression
                || node instanceof BSHAssignment && ((BSHAssignment) node).operator == null))
            node = node.jjtGetChild(0);
        return node;
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
        if (last instanceof BSHPrimarySuffix && ((BSHPrimarySuffix) last).operation == BSHPrimarySuffix.NEW)
            last = last.jjtGetChild(0);
        // Array creation, qualified or not, has ArrayDimensions where a class instance has Arguments.
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

    private static boolean isContinue(BSHReturnStatement jump) {
        return jump.kind == ParserConstants.CONTINUE;
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

    private static final int NEVER = 0, COMPLETES = 1, UNSURE = 2;

    // JLS 14.15. A break that targets the construct makes its completion UNSURE
    // (reachability is not analyzed); a break targeting an inner loop, switch or
    // label does not count. An unfoldable loop condition is UNSURE too.
    private static int completion(Node node) {
        int n = node.jjtGetNumChildren();
        if (node instanceof BSHThrowStatement || node instanceof BSHReturnStatement)
            return NEVER;
        if (node instanceof BSHBlock)
            return n == 0 ? COMPLETES : completion(node.jjtGetChild(n - 1));
        if (node instanceof BSHIfStatement)
            return n < 3 ? COMPLETES : either(completion(node.jjtGetChild(1)), completion(node.jjtGetChild(2)));
        if (node instanceof BSHWhileStatement && ((BSHWhileStatement) node).isDoStatement)
            return doLoop(node.jjtGetChild(0), node.jjtGetChild(n - 1), node);
        if (node instanceof BSHWhileStatement)
            return loop(node.jjtGetChild(0), node);
        if (node instanceof BSHForStatement) {
            BSHForStatement loop = (BSHForStatement) node;
            return loop(loop.hasExpression ? node.jjtGetChild(loop.hasForInit ? 1 : 0) : null, node);
        }
        if (node instanceof BSHLabeledStatement)
            return n == 0 ? COMPLETES : either(completion(node.jjtGetChild(0)), breaks(node));
        if (node instanceof BSHSwitchStatement) {
            boolean hasDefault = false;
            for (int i = 1; i < n; i++)
                hasDefault |= node.jjtGetChild(i) instanceof BSHSwitchLabel
                    && ((BSHSwitchLabel) node.jjtGetChild(i)).isDefault;
            return hasDefault ? either(completion(node.jjtGetChild(n - 1)), breaks(node)) : COMPLETES;
        }
        if (node instanceof BSHTryStatement) {
            int i = node.jjtGetChild(0) instanceof BSHTryWithResources ? 1 : 0;
            int completes = completion(node.jjtGetChild(i++));
            for (; i < n; i++) {
                if (!(node.jjtGetChild(i) instanceof BSHMultiCatch))
                    return both(completes, completion(node.jjtGetChild(i)));
                completes = either(completes, completion(node.jjtGetChild(++i)));
            }
            return completes;
        }
        return COMPLETES;
    }

    // A missing condition, as in for (;;), is true.
    private static int loop(Node condition, Node loop) {
        Object value = condition == null ? Primitive.TRUE : fold(condition);
        return value == Primitive.TRUE ? breaks(loop) : value == NOT_CONSTANT ? UNSURE : COMPLETES;
    }

    // JLS 14.13: the condition is reached only by completing the body or a continue.
    private static int doLoop(Node body, Node condition, Node loop) {
        int reachesCondition = completion(body);
        if (reachesCondition == NEVER && contains(body, BSHLambdaExpression::isContinue))
            reachesCondition = UNSURE;
        Object value = fold(condition);
        if (value == Primitive.TRUE || reachesCondition == NEVER)
            return breaks(loop);
        return either(value == NOT_CONSTANT ? UNSURE : reachesCondition, breaks(loop));
    }

    // JLS 14.15: an unlabeled break ends the innermost loop or switch, a labeled
    // one the statement with that label.
    private static int breaks(Node target) {
        String label = target instanceof BSHLabeledStatement ? ((BSHLabeledStatement) target).label : null;
        for (int i = 0; i < target.jjtGetNumChildren(); i++)
            if (breaksOut(target.jjtGetChild(i), false, label))
                return UNSURE;
        return NEVER;
    }

    private static boolean breaksOut(Node node, boolean nested, String label) {
        if (node instanceof BSHReturnStatement) {
            BSHReturnStatement jump = (BSHReturnStatement) node;
            if (jump.kind != ParserConstants.BREAK)
                return false;
            return jump.label == null ? !nested : jump.label.equals(label);
        }
        if (node instanceof BSHLambdaExpression || node instanceof BSHMethodDeclaration)
            return false;
        boolean breakable = node instanceof BSHWhileStatement || node instanceof BSHForStatement
            || node instanceof BSHEnhancedForStatement || node instanceof BSHSwitchStatement;
        for (int i = 0; i < node.jjtGetNumChildren(); i++)
            if (breaksOut(node.jjtGetChild(i), nested || breakable, label))
                return true;
        return false;
    }

    private static int either(int a, int b) {
        return a == COMPLETES || b == COMPLETES ? COMPLETES : a == NEVER && b == NEVER ? NEVER : UNSURE;
    }

    private static int both(int a, int b) {
        return a == NEVER || b == NEVER ? NEVER : a == COMPLETES && b == COMPLETES ? COMPLETES : UNSURE;
    }

    @Override
    public String toString() {
        return super.toString() + ": " + (paramName != null ? paramName : "(...)");
    }
}
