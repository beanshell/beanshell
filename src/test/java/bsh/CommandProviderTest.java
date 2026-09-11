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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import javax.tools.ToolProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CommandProviderTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void coreWorksWithoutProviders() throws Exception {
        Interpreter interpreter = new Interpreter();
        assertEquals(42, interpreter.eval("6 * 7"));
        assertNull(interpreter.getNameSpace().getCommand("missingCommand", new Class<?>[0], interpreter));
    }

    @Test
    public void discoversCommandsAndTheirImplementationClasses() throws Exception {
        try (URLClassLoader loader = provider("one", "answer", "return new one.Value().answer();", 42)) {
            Interpreter interpreter = new Interpreter();
            assertNull(interpreter.getClassManager().classForName("one.Value"));
            interpreter.setClassLoader(loader);
            assertEquals(42, interpreter.eval("answer()"));
            assertEquals(42, interpreter.eval("answer()"));
        }
    }

    @Test
    public void contextLoaderDiscoveryAndUserMethodsWork() throws Exception {
        ClassLoader before = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = provider("context", "answer", "return 42;", 42)) {
            Thread.currentThread().setContextClassLoader(loader);
            Interpreter interpreter = new Interpreter();
            assertEquals(42, interpreter.eval("answer()"));
            assertEquals(7, interpreter.eval("answer() { return 7; } answer()"));
        } finally {
            Thread.currentThread().setContextClassLoader(before);
        }
    }

    @Test
    public void explicitCommandImportsTakePrecedence() throws Exception {
        try (URLClassLoader loader = provider("one", "answer", "return 42;", 42)) {
            Path dir = new File(loader.getURLs()[0].toURI()).toPath();
            write(dir.resolve("custom/answer.bsh"), "answer() { return 9; }");
            Interpreter interpreter = new Interpreter();
            interpreter.setClassLoader(loader);
            assertEquals(9, interpreter.eval("importCommands(\"/custom\"); answer()"));
        }
    }

    @Test
    public void duplicateCommandsAreRejected() throws Exception {
        try (URLClassLoader one = provider("one", "answer", "return 1;", 1);
                URLClassLoader two = provider("two", "answer", "return 2;", 2);
                URLClassLoader both = new URLClassLoader(new URL[] {one.getURLs()[0], two.getURLs()[0]}, Interpreter.class.getClassLoader())) {
            Interpreter interpreter = new Interpreter();
            interpreter.setClassLoader(both);
            expectError(interpreter, "answer()", "Ambiguous optional command");
        }
    }

    @Test
    public void malformedProviderDoesNotBreakCoreEvaluation() throws Exception {
        Path dir = temporary.newFolder().toPath();
        write(dir.resolve("META-INF/services/bsh.spi.CommandProvider"), "missing.Provider");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {dir.toUri().toURL()}, Interpreter.class.getClassLoader())) {
            Interpreter interpreter = new Interpreter();
            interpreter.setClassLoader(loader);
            assertEquals(42, interpreter.eval("6 * 7"));
            interpreter.eval("printBanner()");
            expectError(interpreter, "missingCommand()", "Cannot discover command providers");
        }
    }

    @Test
    public void changingLoaderRefreshesDiscovery() throws Exception {
        Interpreter interpreter = new Interpreter();
        assertNull(interpreter.getNameSpace().getCommand("answer", new Class<?>[0], interpreter));
        try (URLClassLoader loader = provider("one", "answer", "return 42;", 42)) {
            interpreter.setClassLoader(loader);
            assertEquals(42, interpreter.eval("answer()"));
        }
    }

    private URLClassLoader provider(String pkg, String command, String body, int answer) throws Exception {
        Path dir = temporary.newFolder().toPath();
        Path provider = dir.resolve(pkg + "/Provider.java");
        write(provider, "package " + pkg + "; public class Provider implements bsh.spi.CommandProvider {"
                + "public java.util.List<String> getCommandPaths() { return java.util.Collections.singletonList(\"/" + pkg + "/commands\"); }}");
        Path value = dir.resolve(pkg + "/Value.java");
        write(value, "package " + pkg + "; public class Value { public int answer() { return " + answer + "; }}");
        String core = new File(Interpreter.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-classpath", core, "-d", dir.toString(), provider.toString(), value.toString()));
        write(dir.resolve("META-INF/services/bsh.spi.CommandProvider"), pkg + ".Provider");
        write(dir.resolve(pkg + "/commands/" + command + ".bsh"), command + "() { " + body + " }");
        return new URLClassLoader(new URL[] {dir.toUri().toURL()}, Interpreter.class.getClassLoader());
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, Arrays.asList(content), StandardCharsets.UTF_8);
    }

    private static void expectError(Interpreter interpreter, String script, String message) throws Exception {
        try {
            interpreter.eval(script);
            fail("Expected " + message);
        } catch (EvalError e) {
            assertTrue(e.toString(), e.toString().contains(message));
        }
    }
}
