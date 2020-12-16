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
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/

package bsh;

import java.util.*;

public class StringUtil
{

	public static String [] split( String s, String delim) {
		List<String> v = new ArrayList<String>();
		StringTokenizer st = new StringTokenizer(s, delim);
		while ( st.hasMoreTokens() )
			v.add( st.nextToken() );
		return v.toArray(new String[0]);
	}

	public static String maxCommonPrefix( String one, String two ) {
		int i=0;
		while( one.regionMatches( 0, two, 0, i ) )
			i++;
		return one.substring(0, i-1);
	}

    public static String methodString(String name, Class[] types)
    {
        StringBuilder sb = new StringBuilder(name + "(");
        if ( types.length > 0 )
			sb.append(" ");
        for( int i=0; i<types.length; i++ )
        {
            Class c = types[i];
            sb.append( ( (c == null) ? "null" : c.getName() ) 
				+ ( i < (types.length-1) ? ", " : " " ) );
        }
        sb.append(")");
        return sb.toString();
    }

	/**
		Split a filename into dirName, baseName
		@return String [] { dirName, baseName }
    public String [] splitFileName( String fileName ) 
	{ 
		String dirName, baseName;
		int i = fileName.lastIndexOf( File.separator );
		if ( i != -1 ) {
			dirName = fileName.substring(0, i);
			baseName = fileName.substring(i+1);
		} else
			baseName = fileName;

		return new String[] { dirName, baseName };
	}

	*/

	/**
		Hack - The real method is in Reflect.java which is not public.
	*/
    public static String normalizeClassName( Class type )
	{
		return Reflect.normalizeClassName( type );
	}
}
