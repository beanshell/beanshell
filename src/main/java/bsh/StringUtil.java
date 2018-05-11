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

public class StringUtil
{
    public static String maxCommonPrefix( String one, String two ) {
        int i=0;
        while( one.regionMatches( 0, two, 0, i ) )
            i++;
        return one.substring(0, i-1);
    }

    public static String methodString(String name, Class[] types)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append('(');
        for( int i=0; i<types.length; i++ )
        {
            Class c = types[i];
            if (i != 0) {
                sb.append(", ");
            }
            sb.append((c == null) ? "null" : c.getName());
        }
        sb.append(')');
        return sb.toString();
    }

    /**
        Hack - The real method is in Reflect.java which is not public.
    */
    public static String normalizeClassName( Class type )
    {
        return Reflect.normalizeClassName( type );
    }
}
