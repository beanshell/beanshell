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

/**
    New object, new array, or inner class style allocation with body.
*/
class BSHAllocationExpression extends SimpleNode
{
    BSHAllocationExpression(int id) { super(id); }
    private static int innerClassCount = 0;

    public Object eval( CallStack callstack, Interpreter interpreter)
        throws EvalError
    {
        // type is either a class name or a primitive type
        SimpleNode type = (SimpleNode)jjtGetChild(0);

        // args is either constructor arguments or array dimensions
        SimpleNode args = (SimpleNode)jjtGetChild(1);

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
        NameSpace namespace = callstack.top();

        Object[] args = argumentsNode.getArguments( callstack, interpreter );
        if ( args == null)
            throw new EvalError( "Null args in new.", this, callstack );

        // Look for scripted class object
        Object obj = nameNode.toObject(
            callstack, interpreter, false/* force class*/ );

        // Try regular class

        obj = nameNode.toObject(
            callstack, interpreter, true/*force class*/ );

        Class type = null;
        if ( obj instanceof ClassIdentifier )
            type = ((ClassIdentifier)obj).getTargetClass();
        else
            throw new EvalError(
                "Unknown class: "+nameNode.text, this, callstack );

        // Is an inner class style object allocation
        boolean hasBody = jjtGetNumChildren() > 2;

        if ( hasBody )
        {
            BSHBlock body = (BSHBlock)jjtGetChild(2);
            if ( type.isInterface() )
                return constructWithInterfaceBody(
                    type, args, body, callstack, interpreter );
            else
                return constructWithClassBody(
                    type, args, body, callstack, interpreter );
        } else
            return constructObject( type, args, callstack, interpreter );
    }

    private Object constructObject(Class<?> type, Object[] args, CallStack callstack, Interpreter interpreter ) throws EvalError {
        final boolean isGeneratedClass = Reflect.isGeneratedClass(type);
        if (isGeneratedClass) {
            ClassGeneratorUtil.registerConstructorContext(callstack, interpreter);
        }
        Object obj;
        try {
            obj = Reflect.constructObject( type, args );
        } catch ( ReflectError e) {
            throw new EvalError(
                "Constructor error: " + e.getMessage(), this, callstack, e);
        } catch (InvocationTargetException e) {
            // No need to wrap this debug
            Interpreter.debug("The constructor threw an exception:\n\t" + e.getTargetException());
            throw new TargetError("Object constructor", e.getTargetException(), this, callstack, true);
        } finally {
            if (isGeneratedClass) {
                ClassGeneratorUtil.registerConstructorContext(null, null); // clean up, prevent memory leak
            }
        }
        String className = type.getName();
        // Is it an inner class?
        if ( className.indexOf("$") == -1 )
            return obj;

        // Temporary hack to support inner classes
        // If the obj is a non-static inner class then import the context...
        // This is not a sufficient emulation of inner classes.
        // Replace this later...

        // work through to class 'this'
        This ths = callstack.top().getThis( null );
        NameSpace instanceNameSpace =
            Name.getClassNameSpace( ths.getNameSpace() );

        // Change the parent (which was the class static) to the class instance
        // We really need to check if we're a static inner class here first...
        // but for some reason Java won't show the static modifier on our
        // fake inner classes...  could generate a flag field.
        if ( instanceNameSpace != null
            && className.startsWith( instanceNameSpace.getName() +"$")
        )
        {
            ClassGenerator.getClassGenerator().setInstanceNameSpaceParent(
                obj, className, instanceNameSpace );
        }

        return obj;
    }

    private Object constructWithClassBody(
        Class type, Object[] args, BSHBlock block,
        CallStack callstack, Interpreter interpreter )
        throws EvalError
    {
        String name = callstack.top().getName() + "$" + (++innerClassCount);
        Modifiers modifiers = new Modifiers(Modifiers.CLASS);
        modifiers.hasModifier("public");
        Class clas = ClassGenerator.getClassGenerator().generateClass(
                name, modifiers, null/*interfaces*/, type/*superClass*/,
                block, false/*isInterface*/, callstack, interpreter );
        try {
            return Reflect.constructObject( clas, args );
        } catch ( Exception e ) {
            Throwable cause = e;
            if ( e instanceof InvocationTargetException ) {
                cause = ((InvocationTargetException) e).getTargetException();
            }
            throw new EvalError("Error constructing inner class instance: "+e, this, callstack, cause);
        }
    }

    private Object constructWithInterfaceBody(
        Class type, Object[] args, BSHBlock body,
        CallStack callstack, Interpreter interpreter )
        throws EvalError
    {
        NameSpace namespace = callstack.top();
        NameSpace local = new NameSpace(namespace, "AnonymousBlock");
        callstack.push(local);
        body.eval( callstack, interpreter, true/*overrideNamespace*/ );
        callstack.pop();
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
        NameSpace namespace = callstack.top();
        Class type = nameNode.toClass( callstack, interpreter );
        if ( type == null )
            throw new EvalError( "Class " + nameNode.getName(namespace)
                + " not found.", this, callstack );

        return arrayAllocation( dimensionsNode, type, callstack, interpreter );
    }

    private Object primitiveArrayAllocation(
        BSHPrimitiveType typeNode, BSHArrayDimensions dimensionsNode,
        CallStack callstack, Interpreter interpreter
    )
        throws EvalError
    {
        Class type = typeNode.getType();

        return arrayAllocation( dimensionsNode, type, callstack, interpreter );
    }

    private Object arrayAllocation(
        BSHArrayDimensions dimensionsNode, Class type,
        CallStack callstack, Interpreter interpreter )
        throws EvalError
    {
        /*
            dimensionsNode can return either a fully intialized array or VOID.
            when VOID the prescribed array dimensions (defined and undefined)
            are contained in the node.
        */
        Object result = dimensionsNode.eval( type, callstack, interpreter );
        if ( result != Primitive.VOID )
            return result;
        else
            return arrayNewInstance( type, dimensionsNode, callstack );
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
        Class type, BSHArrayDimensions dimensionsNode, CallStack callstack )
        throws EvalError
    {
        if ( dimensionsNode.numUndefinedDims > 0 )
        {
            Object proto = Array.newInstance(
                type, new int [dimensionsNode.numUndefinedDims] ); // zeros
            type = proto.getClass();
        }

        try {
            return Array.newInstance(
                type, dimensionsNode.definedDimensions);
        } catch( NegativeArraySizeException e1 ) {
            throw new TargetError( e1, this, callstack );
        } catch( Exception e ) {
            throw new EvalError("Can't construct primitive array: " +
                e.getMessage(), this, callstack, e);
        }
    }
}
