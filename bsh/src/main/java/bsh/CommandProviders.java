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

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import bsh.spi.CommandProvider;

/** An interpreter-local snapshot of optional command libraries. */
final class CommandProviders {
    private final List<Library> libraries = new ArrayList<>();
    final ClassLoader contextLoader;

    CommandProviders(ClassLoader externalLoader, ClassLoader contextLoader)
            throws UtilEvalError {
        this.contextLoader = contextLoader;
        Map<Class<?>, CommandProvider> providers = new LinkedHashMap<>();
        try {
            // Keep these calls in the consuming bundle for OSGi mediation.
            if (externalLoader == null)
                collect(ServiceLoader.load(CommandProvider.class), providers);
            else
                collect(ServiceLoader.load(CommandProvider.class, externalLoader), providers);
            if (providers.isEmpty())
                collect(ServiceLoader.load(CommandProvider.class,
                        Interpreter.class.getClassLoader()), providers);
            for (CommandProvider provider : providers.values()) {
                List<String> paths = provider.getCommandPaths();
                if (paths == null)
                    throw new IllegalArgumentException("null command paths: " + provider.getClass().getName());
                for (String path : paths) {
                    if (path == null || !path.matches("/[A-Za-z_$][A-Za-z0-9_$]*(/[A-Za-z_$][A-Za-z0-9_$]*)*"))
                        throw new IllegalArgumentException("Invalid command path: " + path);
                    libraries.add(new Library(provider.getClass(), path));
                }
            }
        } catch (ServiceConfigurationError | RuntimeException e) {
            throw new UtilEvalError("Cannot discover command providers: " + e.getMessage(), e);
        }
    }

    private static void collect(ServiceLoader<CommandProvider> loader,
            Map<Class<?>, CommandProvider> providers) {
        for (CommandProvider provider : loader)
            providers.putIfAbsent(provider.getClass(), provider);
    }

    Object find(String name) throws UtilEvalError {
        Object result = null;
        String owner = null;
        for (Library library : libraries) {
            Object candidate = library.type.getResource(library.path + "/" + name + ".bsh");
            if (candidate == null)
                candidate = library.load(library.path.substring(1).replace('/', '.') + "." + name);
            if (candidate != null) {
                String identity = library.type.getName() + ":" + library.path;
                if (result != null && !owner.equals(identity))
                    throw new UtilEvalError("Ambiguous optional command " + name + ": " + owner + " and " + identity);
                result = candidate;
                owner = identity;
            }
        }
        return result;
    }

    URL getResource(String path) {
        for (Library library : libraries) {
            URL resource = library.type.getResource(path);
            if (resource != null)
                return resource;
        }
        return null;
    }

    Class<?> loadClass(String name) {
        for (Library library : libraries) {
            Class<?> type = library.load(name);
            if (type != null)
                return type;
        }
        return null;
    }

    private static final class Library {
        private final Class<?> type;
        private final String path;

        Library(Class<?> type, String path) {
            this.type = type;
            this.path = path;
        }

        Class<?> load(String name) {
            try {
                return Class.forName(name, false, type.getClassLoader());
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    }
}
