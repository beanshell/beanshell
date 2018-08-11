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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StringUtil {

    /** Type from value to string.
     * @param value for type
     * @return string type  */
    public static String typeString(Object value) {
        return null == value || Primitive.NULL == value
            ? "null"
            : value instanceof Primitive
                ? ((Primitive) value).getType().getSimpleName()
                : typeString(value.getClass());
    }

    /** Type from class to string.
     * @param clas for type
     * @return string type */
    public static String typeString(Class<?> clas) {
        if (Map.class.isAssignableFrom(clas))
            clas = Map.class;
        else if (List.class.isAssignableFrom(clas))
            if (Queue.class.isAssignableFrom(clas))
                clas = Queue.class;
            else
                clas = List.class;
        else if (Deque.class.isAssignableFrom(clas))
            clas = Deque.class;
        else if (Set.class.isAssignableFrom(clas))
            clas = Set.class;
        else if (Entry.class.isAssignableFrom(clas))
            clas = Entry.class;
        return clas.isArray() ? typeString(clas.getComponentType())+"[]"
                : clas.getName().startsWith("java")
                ? clas.getSimpleName() : clas.getName();
    }

    /** Generate string of value and type.
     * @param value to inspect
     * @return string of value :type */
    public static String typeValueString(final Object value) {
        StringBuilder sb = new StringBuilder(valueString(value));
        return sb.append(" :").append(typeString(value)).toString();
    }

    /** Format value string.
     * @param value to format
     * @return string formatted value */
    public static String valueString(final Object value) {
        StringBuilder val = new StringBuilder(""+value);
        if (null != value && value.getClass().isArray()) {
            val = new StringBuilder("{");
            for (int i = 0; i < Array.getLength(value); i++)
                val.append(valueString(Array.get(value, i))).append(", ");
            if (val.reverse().charAt(0) == ' ')
                val.delete(0, 2);
            return val.reverse().append('}').toString();
        }
        if (value instanceof Collection) {
            val = new StringBuilder("[");
            for (Object v : ((Collection<?>) value))
                val.append(valueString(v)).append(", ");
            if (val.reverse().charAt(0) == ' ')
                val.delete(0, 2);
            return val.reverse().append(']').toString();
        }
        if (value instanceof Map) {
            val = new StringBuilder("{");
            for (Entry<?, ?> e : ((Map<?, ?>) value).entrySet())
                val.append(valueString(e.getKey())).append('=')
                .append(valueString(e.getValue())).append(", ");
            if (val.reverse().charAt(0) == ' ')
                val.delete(0, 2);
            return val.reverse().append('}').toString();
        }
        if (value instanceof Entry) {
            return new StringBuilder(
                    valueString(((Entry<?, ?>) value).getKey()))
                .append('=').append(
                    valueString(((Entry<?, ?>) value).getValue()))
                .toString();
        }
        if (value instanceof String)
            return val.insert(0, '"').append('"').toString();
        if (Primitive.unwrap(value) instanceof Character)
            return val.insert(0, '\'').append('\'').toString();
        if (Primitive.unwrap(value) instanceof Number) {
            if (Primitive.unwrap(value) instanceof Byte)
                return val.append('o').toString();
            if (Primitive.unwrap(value) instanceof Short)
                return val.append('s').toString();
            if (Primitive.unwrap(value) instanceof Integer)
                return val.append('I').toString();
            if (Primitive.unwrap(value) instanceof Long)
                return val.append('L').toString();
            if (Primitive.unwrap(value) instanceof BigInteger)
                return val.append('W').toString();
            if (Primitive.unwrap(value) instanceof Float)
                return val.append('f').toString();
            if (Primitive.unwrap(value) instanceof Double)
                return val.append('d').toString();
            if (Primitive.unwrap(value) instanceof BigDecimal)
                return val.append('w').toString();
        }
        return val.toString();
    }

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

    /** Produce a method string from supplied value args.
     * @param name method name.
     * @param args the value to process.
     * @return still just a method string. */
    public static String methodString(String name, Object[] args) {
        return methodString(name, Types.getTypes(args));
    }

    /** Produce a simple string representation of a method name with args.
     * @param name the method name
     * @param types the parameter type names
     * @return string representation of a method */
    public static String methodString(String name, String[] types) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('(');
        for( int i=0; i<types.length; i++ ) {
            if (i != 0)
                sb.append(", ");
            sb.append(types[i]);
        }
        sb.append(')');
        return sb.toString();
    }

    /** Produce a simple string representation of a method name with args.
     * @param name the method name
     * @param types the parameter type args
     * @return string representation of a method */
    public static String methodString(String name, Class<?>[] types) {
        return methodString(name, getTypeNames(types));
    }

    /** Produce a simple string representation of a method name with args.
     * @param name the method name
     * @param types the parameter type args
     * @param names the parameter names
     * @return string representation of a method */
    public static String methodString(String name, Class<?>[] types, String[] names) {
        return methodString(name, getTypeNames(types, names));
    }

    /** Get type names as stream.
     * @param types class[] of classes
     * @return Stream of type names */
    private static Stream<String> getTypeNamesStream(Class<?>[] types) {
        return Stream.of(types).map(StringUtil::getTypeName);
    }

    /** Get type names as string list.
     * @param types class[] of classes
     * @return List of type names */
    private static List<String> getTypeNamesList(Class<?>[] types) {
        return getTypeNamesStream(types).collect(Collectors.toList());
    }

    /** Get type names as string[].
     * @param types class[] of classes
     * @return String[] of type names */
    private static String[] getTypeNames(Class<?>[] types) {
        return getTypeNamesStream(types).toArray(String[]::new);
    }

    /** Get type names as string[].
     * @param types class[] of parameter classes
     * @param names string[] of parameter names
     * @return String[] of type names */
    private static String[] getTypeNames(Class<?>[] types, String[] names) {
        Iterator<String> namesIt = Stream.of(names).iterator();
        return getTypeNamesStream(types)
                .map(type -> type + ' ' + namesIt.next())
                .toArray(String[]::new);
    }

    /** Get type name as string.
     * @param type class
     * @return simple name or Object if null */
    private static String getTypeName(Class<?> type) {
        return ( null == type ) ? "Object"
                : type.getSimpleName();
    }

    /** Get extends string for class.
     * @param type the class to interrogate.
     * @return if type isInterface return empty string
     *         else return extends superClass */
    private static String getTypeExtends(Class<?> type) {
        return type.isInterface() ? "" : " extends " + getTypeName(type.getSuperclass());
    }

    /** Get the implements/extends string for type.
     * @param type the class to interrogate.
     * @return string implements (for classes)/extends (for interfaces)
     *         and comma separated list of interfaces. */
    private static String getTypeImplements(Class<?> type) {
        StringBuilder sb = new StringBuilder();
        if ( type.getInterfaces().length > 0 )
            sb.append(type.isInterface() ? " extends " : " implements ")
             .append(String.join(", ", getTypeNamesList(type.getInterfaces())));
        return sb.toString();
    }

    /** Produce a complete string representation of a reflect method. Shows
     * modifiers, return type, name and parameter types.
     * @param method a java reflect method
     * @return string representation of a method */
    public static String methodString(Method method) {
        String mods = Modifier.toString(method.getModifiers());
        StringBuilder sb = new StringBuilder();
        return sb.append(mods).append(' ')
            .append(getTypeName(method.getReturnType())).append(' ')
            .append(methodString(method.getName(), method.getParameterTypes()))
            .append(mods.contains("abstract") ? ";" : " {}").toString();
    }

    /** Produce a complete string representation of a bsh method. Shows
     * modifiers, return type, name and parameter types.
     * @param method a bsh method
     * @return string representation of a method */
    public static String methodString(BshMethod method) {
        String mods = method.getModifiers().toString().substring(11);
        StringBuilder sb = new StringBuilder();
        return sb.append(mods).append(' ')
            .append(getTypeName(method.getReturnType())).append(' ')
            .append(methodString(method.getName(),
                    method.getParameterTypes(), method.getParameterNames()))
            .append(mods.contains("abstract") ? ";" : " {}").toString();
    }

    /** Produce a string representation of a bsh generated class declaration.
     * Shows modifiers, name, extends and implements.
     * @param type the class to reflect
     * @return string representation of a class declaration */
    private static String generatedClassString(Class<?> type) {
        StringBuilder sb = new StringBuilder();
        sb.append(Reflect.getClassModifiers(type).toString().substring(11))
          .append(type.isInterface() ? " interface" : " class")
          .append(' ').append(getTypeName(type))
          .append(getTypeExtends(type))
          .append(getTypeImplements(type));
        return sb.append(" {").toString().trim();
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
            .append(' ').append(getTypeName(type))
            .append(getTypeExtends(type))
            .append(getTypeImplements(type));
        return sb.append(" {").toString().trim();
    }

    /** Produce a string representation of a bsh variable declaration.
     * Shows modifiers, type and name.
     * @param var the variable to reflect
     * @return string representation of variable declaration */
    public static String variableString(Variable var) {
        StringBuilder sb = new StringBuilder();
        sb.append(var.getModifiers().toString().substring(11))
            .append(' ').append(getTypeName(var.getType()))
            .append(' ').append(var.getName());
        return sb.append(';').toString();
    }

    /** Produce a string representation of a java field declaration.
     * Shows modifiers, type and name.
     * @param var the field to reflect
     * @return string representation of field declaration */
    public static String variableString(Field field) {
        StringBuilder sb = new StringBuilder();
        sb.append(Modifier.toString(field.getModifiers()))
            .append(' ').append(getTypeName(field.getType()))
            .append(' ').append(field.getName());
        return sb.append(';').toString();
    }
}
