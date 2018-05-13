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

import java.util.Hashtable;

/**

    @author Pat Niemeyer (pat@pat.net)
*/
/*
    Note: which of these things should be checked at parse time vs. run time?
*/
public class Modifiers implements java.io.Serializable
{
    public static final int CLASS=0, METHOD=1, FIELD=2;
    private final int context;
    private final Hashtable modifiers = new Hashtable();

    public Modifiers(int context) {
        this.context = context;
    }

    /**
        @param context is METHOD or FIELD
    */
    public void addModifier( String name )
    {
        Object existing = modifiers.put( name, Void.TYPE/*arbitrary flag*/ );
        if ( existing != null )
            throw new IllegalStateException("Duplicate modifier: "+ name );

        int count = 0;
        if ( hasModifier("private") ) ++count;
        if ( hasModifier("protected") ) ++count;
        if ( hasModifier("public") ) ++count;
        if ( count > 1 )
            throw new IllegalStateException(
                "public/private/protected cannot be used in combination." );

        switch( context )
        {
        case CLASS:
            validateForClass();
            break;
        case METHOD:
            validateForMethod();
            break;
        case FIELD:
            validateForField();
            break;
        }
    }

    public boolean hasModifier( String name )
    {
        if (name.equals("public") && !modifiers.containsKey(name)
                && !Capabilities.haveAccessibility()
                && !modifiers.containsKey("private")
                && !modifiers.containsKey("protected")) {
            try {
                addModifier(name);
            } catch (Throwable e) { /*ignore */ }
        }
        return modifiers.containsKey(name);
    }

    // could refactor these a bit
    private void validateForMethod()
    {
        insureNo("volatile", "Method");
        insureNo("transient", "Method");
    }
    private void validateForField()
    {
        insureNo("synchronized", "Variable");
        insureNo("native", "Variable");
        insureNo("abstract", "Variable");
    }
    private void validateForClass()
    {
        validateForMethod(); // volatile, transient
        insureNo("native", "Class");
        insureNo("synchronized", "Class");
    }

    private void insureNo( String modifier, String context )
    {
        if ( hasModifier( modifier ) )
            throw new IllegalStateException(
                context + " cannot be declared '"+modifier+"'");
    }

    public String toString()
    {
        return "Modifiers: "+modifiers.keySet();
    }

}
