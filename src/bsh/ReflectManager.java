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

import bsh.Capabilities.Unavailable;

/**
    ReflectManager is a dynamically loaded extension that supports extended
    reflection features supported by JDK1.2 and greater.

    In particular it currently supports accessible method and field access 
    supported by JDK1.2 and greater.
*/
public abstract class ReflectManager
{
    private static ReflectManager rfm;

    /**
        Return the singleton bsh ReflectManager.
        @throws Unavailable
    */
    public static ReflectManager getReflectManager() 
        throws Unavailable
    {
        if ( rfm == null ) 
        {
            Class clas;
            try {
                clas = Class.forName( "bsh.reflect.ReflectManagerImpl" );
                rfm = (ReflectManager)clas.newInstance();
            } catch ( Exception e ) {
                throw new Unavailable("Reflect Manager unavailable: "+e);
            }
        }
    
        return rfm;
    }

    /**
        Reflect Manager Set Accessible.
        Convenience method to invoke the reflect manager.
        @throws Unavailable
    */
    public static boolean RMSetAccessible( Object obj ) 
        throws Unavailable
    {
        return getReflectManager().setAccessible( obj );
    }

    /**
        Set a java.lang.reflect Field, Method, Constructor, or Array of
        accessible objects to accessible mode.
        @return true if the object was accessible or false if it was not.
    */
    public abstract boolean setAccessible( Object o );
}

