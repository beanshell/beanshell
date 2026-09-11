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
/****************************************************************************/

package bsh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class InterpreterVersionTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void startsWithUnrelatedVersionResource() throws Exception {
        assertVersionAndEvaluation("component.version=1.0\n");
    }

    @Test
    public void ignoresUnrelatedReleaseAndBuildProperties() throws Exception {
        assertVersionAndEvaluation("release=unrelated\nbuild=123\n");
    }

    private void assertVersionAndEvaluation(String properties) throws Exception {
        File unrelatedJar = temporaryFolder.newFile("unrelated.jar");
        try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(unrelatedJar))) {
            jar.putNextEntry(new JarEntry("version.properties"));
            jar.write(properties.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }

        URL beanShell = Interpreter.class.getProtectionDomain().getCodeSource().getLocation();
        // Put the unrelated resource first, and prevent delegation to an
        // Interpreter already initialized by the test runner's classloader.
        try (URLClassLoader loader = new URLClassLoader(
                new URL[] { unrelatedJar.toURI().toURL(), beanShell },
                Interpreter.class.getClassLoader().getParent())) {
            try {
                Class<?> interpreter = Class.forName("bsh.Interpreter", true, loader);
                assertSame(loader, interpreter.getClassLoader());
                assertEquals(Interpreter.VERSION, interpreter.getField("VERSION").get(null));
                Object instance = interpreter.getDeclaredConstructor().newInstance();
                assertEquals(42, interpreter.getMethod("eval", String.class)
                        .invoke(instance, "6 * 7;"));
            } finally {
                ResourceBundle.clearCache(loader);
            }
        }
    }
}
