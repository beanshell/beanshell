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

package org.beanshell.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DistributionTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    static File artifact(String name) {
        return new File("../" + name + "/target/" + name + "-" + System.getProperty("projectVersion") + ".jar");
    }

    @Test
    public void coreJarRunsWithoutDesktopOrOptionalModules() throws Exception {
        File output = temporary.newFile();
        List<String> command = new ArrayList<>();
        command.add(new File(System.getProperty("java.home"), "bin/java").toString());
        if (!System.getProperty("java.specification.version").equals("1.8"))
            command.addAll(Arrays.asList("--limit-modules", "java.base,java.scripting"));
        command.addAll(Arrays.asList("-cp", artifact("bsh").getAbsolutePath() + File.pathSeparator
                + new File("target/test-classes").getAbsolutePath(), CoreSmoke.class.getName()));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output).start();
        try {
            assertTrue("Core subprocess timed out", process.waitFor(30, TimeUnit.SECONDS));
            String result = new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
            assertEquals(result, 0, process.exitValue());
            assertTrue(result, result.contains("CORE_OK"));
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    public void testHelpersAreNotACopyOfTheCoreBundle() throws Exception {
        File core = artifact("bsh");
        try (JarFile helpers = new JarFile(new File(core.getParentFile(),
                core.getName().replace(".jar", "-tests.jar")))) {
            assertNull(helpers.getManifest().getMainAttributes().getValue("Bundle-SymbolicName"));
        }
    }

    @Test
    public void productionPackagesHaveOneOwnerAndCoreIsHeadless() throws Exception {
        Set<String> packages = new HashSet<>();
        for (String module : Arrays.asList("bsh", "bsh-gui", "bsh-bsf-engine")) {
            Set<String> owned = new HashSet<>();
            try (JarFile jar = new JarFile(artifact(module))) {
                assertNotNull(jar.getEntry("META-INF/LICENSE"));
                assertNotNull(jar.getEntry("META-INF/NOTICE"));
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.endsWith(".class"))
                        owned.add(name.substring(0, name.lastIndexOf('/')));
                    if (module.equals("bsh")) {
                        assertFalse(name, name.startsWith("bsh/gui/") || name.startsWith("bsh/remote/") || name.startsWith("bsh/servlet/"));
                        assertFalse(name, name.equals("bsh/Console.class") || name.equals("bsh/commands/server.bsh") || name.equals("bsh/commands/desktop.bsh"));
                    }
                }
                if (module.equals("bsh")) {
                    String imports = jar.getManifest().getMainAttributes().getValue("Import-Package");
                    assertFalse(imports, imports.contains("javax.swing") || imports.contains("java.awt") || imports.contains("javax.servlet"));
                    assertEquals("bsh.Interpreter", jar.getManifest().getMainAttributes().getValue("Main-Class"));
                }
            }
            try (JarFile sources = new JarFile(new File(artifact(module).getParentFile(),
                    artifact(module).getName().replace(".jar", "-sources.jar")))) {
                assertNotNull(module + " source license", sources.getEntry("META-INF/LICENSE"));
                assertNotNull(module + " source notice", sources.getEntry("META-INF/NOTICE"));
            }
            for (String pkg : owned)
                assertTrue("Split package " + pkg, packages.add(pkg));
        }
    }
}
