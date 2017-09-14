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
*/
class BSHClassDeclaration extends SimpleNode
{
    /**
        The class instance initializer method name.
        A BshMethod by this name is installed by the class delcaration into
        the static class body namespace.
        It is called once to initialize the static members of the class space
        and each time an instances is created to initialize the instance
        members.
    */
    static final String CLASSINITNAME = "_bshClassInit";

    String name;
    Modifiers modifiers;
    int numInterfaces;
    boolean extend;
    boolean isInterface;

    BSHClassDeclaration(int id) { super(id); }

    /**
    */
    public Object eval( CallStack callstack, Interpreter interpreter )
        throws EvalError
    {
        int child = 0;

        // resolve superclass if any
        Class superClass = null;
        if ( extend )
        {
            BSHAmbiguousName superNode = (BSHAmbiguousName)jjtGetChild(child++);
            superClass = superNode.toClass( callstack, interpreter );
        }

        // Get interfaces
        Class [] interfaces = new Class[numInterfaces];
        for( int i=0; i<numInterfaces; i++) {
            BSHAmbiguousName node = (BSHAmbiguousName)jjtGetChild(child++);
            interfaces[i] = node.toClass(callstack, interpreter);
            if ( !interfaces[i].isInterface() )
                throw new EvalError(
                    "Type: "+node.text+" is not an interface!",
                    this, callstack );
        }

        BSHBlock block;
        // Get the class body BSHBlock
        if ( child < jjtGetNumChildren() )
            block = (BSHBlock)jjtGetChild(child);
        else
            block = new BSHBlock( ParserTreeConstants.JJTBLOCK );

        try {
            return ClassGenerator.getClassGenerator().generateClass(
                name, modifiers, interfaces, superClass, block, isInterface,
                callstack, interpreter );
        } catch ( UtilEvalError e ) {
            throw e.toEvalError( this, callstack );
        }
    }

    public String toString() {
        return "ClassDeclaration: "+name;
    }
}
