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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.concurrent.CompletionException;

import bsh.congo.parser.BaseNode;
import bsh.congo.parser.Node;

/**
    New object, new array, or inner class style allocation with body.
*/
public class BSHAllocationExpression extends BaseNode
{
    private static int innerClassCount = 0;

    public Object eval( CallStack callstack, Interpreter interpreter)
        throws EvalError
    {
        // loose typed array initializer ex. new {1, 2, 3};
        if ( getChildCount() == 1 && getChild(0)
                instanceof BSHArrayDimensions )
                return arrayAllocation( (BSHArrayDimensions) getChild(0),
                       Void.TYPE, callstack, interpreter );

        // type is either a class name or a primitive type
        Node type = getChild(0);

        // args is either constructor arguments or array dimensions
        Node args = getChild(1);

        if ( type instanceof BSHAmbiguousName )
        {
            BSHAmbiguousName name = (BSHAmbiguousName)type;

            if (args instanceof BSHArguments)
                return objectAllocation(name, (BSHArguments)args,
                    callstack, interpreter );
            else
                return objectArrayAllocation(name, (BSHArrayDimensions)args,
                    callstack, interpreter );
        }
        else
            return primitiveArrayAllocation((BSHPrimitiveType)type,
                (BSHArrayDimensions)args, callstack, interpreter );
    }

    private Object objectAllocation(
        BSHAmbiguousName nameNode, BSHArguments argumentsNode,
        CallStack callstack, Interpreter interpreter
    )
        throws EvalError
    {
        Object[] args = argumentsNode.getArguments( callstack, interpreter );
        if ( args == null)
            throw new EvalError( "Null args in new.", this, callstack );

        // Lookup class
        Object obj = nameNode.toObject(
            callstack, interpreter, true /*force class*/ );


        Class<?> type = null;
        if ( obj instanceof ClassIdentifier )
            type = ((ClassIdentifier)obj).getTargetClass();
        else
            throw new EvalError(
                "Unknown class: "+nameNode.text, this, callstack );

        // Is an inner class style object allocation
        boolean hasBody = getChildCount() > 2;

        if ( hasBody )
        {
            BSHBlock body = (BSHBlock)getChild(2);
            if ( type.isInterface() )
                return constructWithInterfaceBody(
                    type, args, body, callstack, interpreter );
            else
                return constructWithClassBody(
                    type, args, body, callstack, interpreter );
        } else
            return constructObject( type, args, callstack, interpreter );
    }

    Object constructFromEnclosingInstance(Object obj, CallStack callstack,
            Interpreter interpreter ) throws EvalError {

        String typeString = "";
        if (getChild(0) instanceof BSHAmbiguousName)
            typeString = ((BSHAmbiguousName) getChild(0)).text;

        Object[] args = null;
        if (getChild(1) instanceof BSHArguments)
            args = ((BSHArguments) getChild(1)).getArguments(
                        callstack, interpreter);

        Class<?> type = null;
        for (Class<?> t : obj.getClass().getDeclaredClasses())
            if (Types.getBaseName(t.getName()).equals(typeString)) {
                type = t;
                break;
            }

        try {
            return Reflect.constructObject( type, obj, args );
        } catch (InvocationTargetException e) {
            throw new TargetError("Object constructor", e.getCause(),
                    this, callstack, true);
        }
    }

    private Object constructObject(Class<?> type, Object[] args,
            CallStack callstack, Interpreter interpreter ) throws EvalError {
        final boolean isGeneratedClass = Reflect.isGeneratedClass(type);
        if (isGeneratedClass) {
            This.registerConstructorContext(callstack, interpreter);
        }
        Object obj;
        try {
            obj = Reflect.constructObject( type, args );
        } catch ( ReflectError e) {
            throw new EvalError(
                "Constructor error: " + e.getMessage(), this, callstack, e);
        } catch (InvocationTargetException | CompletionException e) {
            // No need to wrap this debug
            Interpreter.debug("The constructor threw an exception:\n\t"
                    + e.getCause());
            throw new TargetError("Object constructor", e.getCause(),
                    this, callstack, true);
        } finally {
            if (isGeneratedClass)
                // clean up, prevent memory leak
                This.registerConstructorContext(null, null);
        }
        String className = type.getName();
        // Is it an inner class?
        if ( className.indexOf("$") == -1 )
            return obj;

        // work through to class 'this'
        This ths = callstack.top().getThis( null );
        NameSpace instanceNameSpace = ths.getNameSpace();

        // method and class name spaces acceptable
        if ( null != Name.getClassNameSpace(instanceNameSpace)
                && !Reflect.getClassModifiers(obj.getClass()).hasModifier("static") ) {
            Reflect.getThisNS(obj).setParent(instanceNameSpace);
        } else if ( Reflect.getClassModifiers(obj.getClass()).hasModifier("static") ) {
            // add class static parent as instance parent
            Reflect.getThisNS(obj).setParent(Reflect.getThisNS(obj.getClass()).getParent());
        }

        return obj;
    }

    private Object constructWithClassBody(
        Class<?> type, Object[] args, BSHBlock block,
        CallStack callstack, Interpreter interpreter )
        throws EvalError
    {
        String anon = "anon" + (++innerClassCount);
        String name = callstack.top().getName().replace('/', '_') + "$" + anon;
        This.CONTEXT_ARGS.get().put(anon, args);
        Modifiers modifiers = new Modifiers(Modifiers.CLASS);
        Class<?> clas = ClassGenerator.getClassGenerator().generateClass(
                name, modifiers, null/*interfaces*/, type/*superClass*/,
                block, ClassGenerator.Type.CLASS, callstack, interpreter );
        try {
            return Reflect.constructObject( clas, args );
        } catch ( Exception e ) {
            Throwable cause = e;
            if ( e instanceof InvocationTargetException )
                cause = e.getCause();
            throw new EvalError("Error constructing inner class instance: "
                + e, this, callstack, cause);
        }
    }

    private Object constructWithInterfaceBody(
        Class<?> type, Object[] args, BSHBlock body,
        CallStack callstack, Interpreter interpreter )
        throws EvalError
    {
        NameSpace namespace = callstack.top();
        NameSpace local = new NameSpace(namespace, "AnonymousBlock");
        callstack.push(local);
        try {
            body.eval( callstack, interpreter, true/*overrideNamespace*/ );
        } finally {
            callstack.pop();
        }
        // statical import fields from the interface so that code inside
        // can refer to the fields directly (e.g. HEIGHT)
        local.importStatic( type );
        return local.getThis(interpreter).getInterface( type );
    }

    private Object objectArrayAllocation(
        BSHAmbiguousName nameNode, BSHArrayDimensions dimensionsNode,
        CallStack callstack, Interpreter interpreter
    )
        throws EvalError
    {
        Class<?> type = nameNode.toClass( callstack, interpreter );

        return arrayAllocation( dimensionsNode, type, callstack, interpreter );
    }

    private Object primitiveArrayAllocation(
            BSHPrimitiveType typeNode, BSHArrayDimensions dimensionsNode,
            CallStack callstack, Interpreter interpreter)
            throws EvalError {
        Class<?> type = typeNode.getType();

        return arrayAllocation( dimensionsNode, type, callstack, interpreter );
    }

    private Object arrayAllocation(
            BSHArrayDimensions dimensionsNode, Class<?> type,
            CallStack callstack, Interpreter interpreter )
            throws EvalError {
        /*
            dimensionsNode can return either a fully initialized array or VOID.
            when VOID the prescribed array dimensions (defined and undefined)
            are contained in the node.
        */
        Object result = dimensionsNode.eval( type, callstack, interpreter );
        if ( result != Primitive.VOID )
            return result;
        else
            return arrayNewInstance( type, dimensionsNode, callstack, interpreter );
    }

    /**
        Create an array of the dimensions specified in dimensionsNode.
        dimensionsNode may contain a number of "undefined" as well as "defined"
        dimensions.
        <p>

        Background: in Java arrays are implemented in arrays-of-arrays style
        where, for example, a two dimensional array is a an array of arrays of
        some base type.  Each dimension-type has a Java class type associated
        with it... so if foo = new int[5][5] then the type of foo is
        int [][] and the type of foo[0] is int[], etc.  Arrays may also be
        specified with undefined trailing dimensions - meaning that the lower
        order arrays are not allocated as objects. e.g.
        if foo = new int [5][]; then foo[0] == null //true; and can later be
        assigned with the appropriate type, e.g. foo[0] = new int[5];
        (See Learning Java, O'Reilly & Associates more background).
        <p>

        To create an array with undefined trailing dimensions using the
        reflection API we must use an array type to represent the lower order
        (undefined) dimensions as the "base" type for the array creation...
        Java will then create the correct type by adding the dimensions of the
        base type to specified allocated dimensions yielding an array of
        dimensionality base + specified with the base dimensons unallocated.
        To create the "base" array type we simply create a prototype, zero
        length in each dimension, array and use it to get its class
        (Actually, I think there is a way we could do it with Class.forName()
        but I don't trust this).   The code is simpler than the explanation...
        see below.
    */
    private Object arrayNewInstance(
            Class<?> type, BSHArrayDimensions dimensionsNode,
            CallStack callstack, Interpreter interpreter) throws EvalError {
        if ( dimensionsNode.numUndefinedDims > 0 ) {
            Object proto = Array.newInstance(
                type, new int [dimensionsNode.numUndefinedDims] ); // zeros
            type = proto.getClass();
        }

        try {
            Object arr = Array.newInstance(
                type, dimensionsNode.definedDimensions);
            if ( !interpreter.getStrictJava() )
                arrayFillDefaultValue(arr);
            return arr;
        } catch( NegativeArraySizeException e1 ) {
            throw new TargetError( e1, this, callstack );
        } catch( Exception e ) {
            throw new EvalError("Can't construct primitive array: "
                    + e.getMessage(), this, callstack, e);
        }
    }

    /** Fill boxed numeric types with default numbers instead of nulls.
     * @param arr the array to fill. */
    private void arrayFillDefaultValue(Object arr) {
        if (null == arr)
            return;
        Class<?> clas = arr.getClass();
        Class<?> comp = Types.arrayElementType(clas);
        if ( !comp.isPrimitive() )
            if ( Types.arrayDimensions(clas) > 1 )
                for ( int i = 0; i < Array.getLength(arr); i++ )
                    arrayFillDefaultValue(Array.get(arr, i));
            else
                Arrays.fill((Object[]) arr, Primitive.unwrap(
                    Primitive.getDefaultValue(comp)));
    }
}
