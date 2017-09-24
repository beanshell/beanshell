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

import java.util.StringTokenizer;
import java.util.Vector;

/**
 * The Class StringUtil.
 */
public class StringUtil {

    /**
     * Split.
     *
     * @param s
     *            the s
     * @param delim
     *            the delim
     * @return the string[]
     */
    public static String[] split(final String s, final String delim) {
        final Vector v = new Vector();
        final StringTokenizer st = new StringTokenizer(s, delim);
        while (st.hasMoreTokens())
            v.addElement(st.nextToken());
        final String[] sa = new String[v.size()];
        v.copyInto(sa);
        return sa;
    }

    /**
     * Bubble sort.
     *
     * @param in
     *            the in
     * @return the string[]
     */
    public static String[] bubbleSort(final String[] in) {
        final Vector v = new Vector();
        for (final String element : in)
            v.addElement(element);
        final int n = v.size();
        boolean swap = true;
        while (swap) {
            swap = false;
            for (int i = 0; i < n - 1; i++)
                if (((String) v.elementAt(i))
                        .compareTo((String) v.elementAt(i + 1)) > 0) {
                    final String tmp = (String) v.elementAt(i + 1);
                    v.removeElementAt(i + 1);
                    v.insertElementAt(tmp, i);
                    swap = true;
                }
        }
        final String[] out = new String[n];
        v.copyInto(out);
        return out;
    }

    /**
     * Max common prefix.
     *
     * @param one
     *            the one
     * @param two
     *            the two
     * @return the string
     */
    public static String maxCommonPrefix(final String one, final String two) {
        int i = 0;
        while (one.regionMatches(0, two, 0, i))
            i++;
        return one.substring(0, i - 1);
    }

    /**
     * Method string.
     *
     * @param name
     *            the name
     * @param types
     *            the types
     * @return the string
     */
    public static String methodString(final String name, final Class[] types) {
        final StringBuffer sb = new StringBuffer(name + "(");
        if (types.length > 0)
            sb.append(" ");
        for (int i = 0; i < types.length; i++) {
            final Class c = types[i];
            sb.append((c == null ? "null" : c.getName())
                    + (i < types.length - 1 ? ", " : " "));
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Split a filename into dirName, baseName.
     *
     * @param type
     *            the type
     * @return String [] { dirName, baseName }
     *         public String [] splitFileName(String fileName)
     *         {
     *         String dirName, baseName;
     *         int i = fileName.lastIndexOf(File.separator);
     *         if (i != -1) {
     *         dirName = fileName.substring(0, i);
     *         baseName = fileName.substring(i+1);
     *         } else
     *         baseName = fileName;
     *
     *         return new String[] { dirName, baseName };
     *         }
     **
     * Hack - The real method is in Reflect.java which is not public.
     */
    public static String normalizeClassName(final Class type) {
        return Reflect.normalizeClassName(type);
    }
}
