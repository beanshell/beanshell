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

public class Variable implements java.io.Serializable
{
    static final int DECLARATION=0, ASSIGNMENT=1;
    /** A null type means an untyped variable */
    String name;
    Class type = null;
    String typeDescriptor;
    Object value;
    Modifiers modifiers;
    LHS lhs;

    Variable( String name, Class type, LHS lhs )
    {
        this.name = name;
        this.lhs = lhs;
        this.type = type;
    }

    Variable( String name, Object value, Modifiers modifiers )
        throws UtilEvalError
    {
        this( name, (Class)null/*type*/, value, modifiers );
    }

    /**
        This constructor is used in class generation.
    */
    Variable(
        String name, String typeDescriptor, Object value, Modifiers modifiers
    )
        throws UtilEvalError
    {
        this( name, (Class)null/*type*/, value, modifiers );
        this.typeDescriptor = typeDescriptor;
    }

    /**
        @param value may be null if this
    */
    Variable( String name, Class type, Object value, Modifiers modifiers )
        throws UtilEvalError
    {

        this.name=name;
        this.type = type;
        this.modifiers = modifiers;
        setValue( value, DECLARATION );
    }

    /**
        Set the value of the typed variable.
        @param value should be an object or wrapped bsh Primitive type.
        if value is null the appropriate default value will be set for the
        type: e.g. false for boolean, zero for integer types.
    */
    public void setValue( Object value, int context )
        throws UtilEvalError
    {

        // check this.value
        if (hasModifier("final")) {
            if (this.value != null) {
                throw new UtilEvalError("Final variable '" + getName() + "', can't re-assign.");
            } else if (value == null && context == DECLARATION) {
                return;
            }
        }

        this.value = value;

        if ( this.value == null )
            this.value = Primitive.getDefaultValue( type );

        if ( lhs != null )
        {
            this.value = lhs.assign( Primitive.unwrap(value), false/*strictjava*/ );
            return;
        }

        // TODO: should add isJavaCastable() test for strictJava
        // (as opposed to isJavaAssignable())
        if ( type != null )
            this.value = Types.castObject( value, type,
                context == DECLARATION ? Types.CAST : Types.ASSIGNMENT
            );

    }

    /*
        Note: UtilEvalError here comes from lhs.getValue().
        A Variable can represent an LHS for the case of an imported class or
        object field.
    */
    Object getValue()
        throws UtilEvalError
    {
        if ( lhs != null )
            return type == null ?
                lhs.getValue() : Primitive.wrap( lhs.getValue(), type );

        return value;
    }

    /** A type of null means loosely typed variable */
    public Class getType() { return type;   }

    public String getTypeDescriptor() { return typeDescriptor; }

    public Modifiers getModifiers() { return modifiers; }

    public String getName() { return name; }

    public boolean hasModifier( String name ) {
        return modifiers != null && modifiers.hasModifier(name);
    }

    public String toString() {
        return "Variable: "+super.toString()+" "+name+", type:"+type
            +", value:"+value +", lhs = "+lhs;
    }
}
