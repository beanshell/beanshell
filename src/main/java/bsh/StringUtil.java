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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class StringUtil {

    /** Count region matches.
     * @param one string to compare
     * @param two string to compare with
     * @return return max common string */
    public static String maxCommonPrefix(String one, String two) {
        int i=0;
        while( one.regionMatches(0, two, 0, i ))
            i++;
        return one.substring(0, i-1);
    }

    /** Produce a simple string representation of a method name with args.
     * @param name the method name
     * @param types the parameter type args
     * @return string representation of a method */
    public static String methodString(String name, Class<?>[] types) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('(');
        for( int i=0; i<types.length; i++ ) {
            if (i != 0)
                sb.append(", ");
            sb.append((types[i] == null) ? "Object" : types[i].getSimpleName());
        }
        sb.append(')');
        return sb.toString();
    }

    /** Produce a complete string representation of a reflect method. Shows
     * modifiers, return type, name and parameter types.
     * @param method a java reflect method
     * @return string representation of a method */
    public static String methodString(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(Modifier.toString(method.getModifiers()))
            .append(" ").append(method.getReturnType().getSimpleName())
            .append(" ")
            .append(methodString(method.getName(), method.getParameterTypes()));
        return sb.toString();
    }

    /** Produce a complete string representation of a bsh method. Shows
     * modifiers, return type, name and parameter types.
     * @param method a bsh method
     * @return string representation of a method */
    public static String methodString(BshMethod method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.modifiers.toString().substring(11))
            .append(" ").append(method.getReturnType().getSimpleName())
            .append(" ")
            .append(methodString(method.getName(), method.getParameterTypes()));
        return sb.toString();
    }

    /** Produce a string representation of a bsh generated class declaration.
     * Shows modifiers, name, extends and implements.
     * @param type the class to reflect
     * @return string representation of a class declaration */
    private static String generatedClassString(Class<?> type) {
        StringBuilder sb = new StringBuilder();
        sb.append(Reflect.getClassModifiers(type).toString().substring(11))
          .append(type.isInterface() ? " interface" : " class")
          .append(" ").append(type.getSimpleName());
        if (type.getSuperclass() != null)
            sb.append(" extends ").append(type.getSuperclass().getSimpleName());
        if (type.getInterfaces().length > 0)
            sb.append(" implements");
            for (Class<?> i : type.getInterfaces())
                sb.append(" ").append(i.getSimpleName());
        return sb.toString();
    }

    /** Produce a string representation of a class declaration.
     * Verifies if type is a generated class else builds a java
     * reflect class string definition.
     * Shows modifiers, name, extends and implements.
     * @param type the class to reflect
     * @return string representation of a class declaration */
    public static String classString(Class<?> type) {
        if (Reflect.isGeneratedClass(type))
            return generatedClassString(type);
        StringBuilder sb = new StringBuilder();
        sb.append(Modifier.toString(type.getModifiers()))
            .append(type.isInterface() ? "": " class")
            .append(" ").append(type.getSimpleName());
        if (type.getSuperclass() != null)
            sb.append(" extends ").append(type.getSuperclass().getSimpleName());
        if (type.getInterfaces().length > 0) {
            sb.append(" implements ");
            for (Class<?> i : type.getInterfaces())
                sb.append(i.getSimpleName()).append(" ");
        }
        return sb.toString();
    }

    /** Produce a string representation of a bsh variable declaration.
     * Shows modifiers, type and name.
     * @param var the variable to reflect
     * @return string representation of variable declaration */
    public static String variableString(Variable var) {
        StringBuilder sb = new StringBuilder();
        sb.append(var.modifiers.toString().substring(11))
            .append(" ").append(var.type.getSimpleName())
            .append(" ").append(var.name);
        return sb.toString();
    }

    /** Produce a string representation of a java field declaration.
     * Shows modifiers, type and name.
     * @param var the field to reflect
     * @return string representation of field declaration */
    public static String variableString(Field field) {
        StringBuilder sb = new StringBuilder();
        sb.append(Modifier.toString(field.getModifiers()))
            .append(" ").append(field.getType().getSimpleName())
            .append(" ").append(field.getName());
        return sb.toString();
    }

    /** Expose package private class Reflect normalizeClassName method
     * for commands. Delegates to the Reflect class.
     * @param type the value to delegate.
     * @return delegated response */
    public static String normalizeClassName( Class<?> type ) {
        return Reflect.normalizeClassName( type );
    }
}
