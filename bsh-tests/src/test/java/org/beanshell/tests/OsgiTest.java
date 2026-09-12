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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.script.ScriptEngineFactory;

import org.apache.felix.framework.FrameworkFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;

public class OsgiTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();
    private Framework framework;
    private BundleContext context;

    @Before
    public void startFramework() throws Exception {
        Map<String, String> config = new HashMap<>();
        config.put(Constants.FRAMEWORK_STORAGE, temporary.newFolder().toString());
        config.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        config.put("felix.log.level", "1");
        framework = new FrameworkFactory().newFramework(config);
        framework.start();
        context = framework.getBundleContext();
    }

    @After
    public void stopFramework() throws Exception {
        if (framework != null) {
            framework.stop();
            framework.waitForStop(10000);
        }
    }

    @Test
    public void bundlesWorkWithoutMediator() throws Exception {
        Bundle core = install(DistributionTest.artifact("bsh"));
        Bundle gui = install(DistributionTest.artifact("bsh-gui"));
        core.start(); gui.start();
        assertEquals(42, eval(core, "6 * 7"));
        assertNotNull(eval(core, "object()"));
        assertTrue(core.adapt(BundleWiring.class).getRequiredWires("osgi.extender").isEmpty());
        assertNull(context.getServiceReferences(ScriptEngineFactory.class.getName(), null));
        for (Bundle bundle : Arrays.asList(core, gui))
            bundle.adapt(BundleWiring.class).getRequirements("osgi.extender").forEach(requirement ->
                assertEquals("optional", requirement.getDirectives().get("resolution")));
        assertEquals("optional", core.adapt(BundleWiring.class).getRequirements("osgi.serviceloader").get(0).getDirectives().get("resolution"));
    }

    @Test
    public void mediatorWiresProvidersAndRegistersJsr223() throws Exception {
        startMediator();
        Bundle core = install(DistributionTest.artifact("bsh"));
        Bundle gui = install(DistributionTest.artifact("bsh-gui"));
        Bundle fixture = install(fixture());
        assertTrue(framework.adapt(FrameworkWiring.class).resolveBundles(Arrays.asList(core, gui, fixture)));
        gui.start(); fixture.start(); core.start();
        List<BundleWire> wires = core.adapt(BundleWiring.class).getRequiredWires("osgi.extender");
        assertEquals(2, wires.size());
        assertEquals(2, core.adapt(BundleWiring.class).getRequiredWires("osgi.serviceloader").size());
        assertEquals(42, eval(core, "providerValue()"));
        assertEquals(Boolean.TRUE, eval(core,
                "try { desktop(); return false; } catch (java.awt.HeadlessException e) { return true; }"));
        assertEquals(0, eval(core, "new bsh.gui.NameCompletionTable().size()"));
        assertEquals(2, services("bsh.spi.CommandProvider"));
        ServiceReference<?>[] factories = context.getServiceReferences(ScriptEngineFactory.class.getName(), null);
        assertNotNull(factories);
        ScriptEngineFactory factory = (ScriptEngineFactory) context.getService(factories[0]);
        assertEquals(42, factory.getScriptEngine().eval("6 * 7"));
        context.ungetService(factories[0]);
        fixture.stop();
        assertEquals(1, services("bsh.spi.CommandProvider"));
        fixture.start();
        assertEquals(2, services("bsh.spi.CommandProvider"));
        assertEquals(42, eval(core, "providerValue()"));
        core.stop();
        assertEquals(0, services(ScriptEngineFactory.class.getName()));
    }

    @Test
    public void lateMediatorNeedsRefreshAndNewInterpreter() throws Exception {
        Bundle core = install(DistributionTest.artifact("bsh"));
        Bundle fixture = install(fixture());
        core.start(); fixture.start();
        assertEquals(42, eval(core, "6 * 7"));
        startMediator();
        assertTrue(core.adapt(BundleWiring.class).getRequiredWires("osgi.extender").isEmpty());
        CountDownLatch refreshed = new CountDownLatch(1);
        framework.adapt(FrameworkWiring.class).refreshBundles(Arrays.asList(core, fixture), event -> refreshed.countDown());
        assertTrue("Bundle refresh timed out", refreshed.await(10, TimeUnit.SECONDS));
        core.start(); fixture.start();
        assertFalse(core.adapt(BundleWiring.class).getRequiredWires("osgi.extender").isEmpty());
        assertEquals(42, eval(core, "providerValue()"));
    }

    private int services(String name) throws Exception {
        ServiceReference<?>[] refs = context.getServiceReferences(name, null);
        return refs == null ? 0 : refs.length;
    }

    private Object eval(Bundle core, String script) throws Exception {
        // Hide provider bundles from the ambient test class path. Discovery must be mediated.
        ClassLoader before = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(core.adapt(BundleWiring.class).getClassLoader());
            Class<?> interpreter = core.loadClass("bsh.Interpreter");
            Object instance = interpreter.getConstructor().newInstance();
            // Trigger optional discovery before an expression using provider implementation types.
            if (script.startsWith("new bsh.gui"))
                interpreter.getMethod("eval", String.class).invoke(instance, "providerValue()");
            return interpreter.getMethod("eval", String.class).invoke(instance, script);
        } catch (InvocationTargetException e) {
            throw new AssertionError(e.getCause());
        } finally {
            Thread.currentThread().setContextClassLoader(before);
        }
    }

    private Bundle install(File jar) throws Exception {
        return context.installBundle(jar.toURI().toString());
    }

    private void startMediator() throws Exception {
        for (String type : Arrays.asList("org.objectweb.asm.ClassReader", "org.objectweb.asm.tree.ClassNode",
                "org.objectweb.asm.tree.analysis.Analyzer", "org.objectweb.asm.commons.AdviceAdapter",
                "org.objectweb.asm.util.CheckClassAdapter")) {
            File jar = new File(Class.forName(type).getProtectionDomain().getCodeSource().getLocation().toURI());
            install(jar).start();
        }
        File jar = new File(Class.forName("org.apache.aries.spifly.dynamic.DynamicWeavingActivator")
                .getProtectionDomain().getCodeSource().getLocation().toURI());
        install(jar).start();
    }

    private File fixture() throws Exception {
        File file = temporary.newFile("fixture.jar");
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.putValue("Manifest-Version", "1.0");
        attrs.putValue("Bundle-ManifestVersion", "2");
        attrs.putValue("Bundle-SymbolicName", "beanshell.test.provider");
        attrs.putValue("Import-Package", "bsh.spi;version=\"[3.0,4)\"");
        attrs.putValue("Require-Capability", "osgi.extender;filter:=\"(&(osgi.extender=osgi.serviceloader.registrar)(version>=1.0.0)(!(version>=2.0.0)))\";resolution:=optional");
        attrs.putValue("Provide-Capability", "osgi.serviceloader;osgi.serviceloader=bsh.spi.CommandProvider;register:=\"fixture.FixtureProvider\";uses:=\"bsh.spi\"");
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(file), manifest)) {
            for (String name : Arrays.asList("fixture/FixtureProvider.class", "fixture/FixtureValue.class")) {
                out.putNextEntry(new JarEntry(name));
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
                    byte[] buf = new byte[4096];
                    int count;
                    while ((count = in.read(buf)) != -1)
                        out.write(buf, 0, count);
                }
                out.closeEntry();
            }
            for (Map.Entry<String, String> entry : Collections.singletonMap(
                    "META-INF/services/bsh.spi.CommandProvider", "fixture.FixtureProvider\n").entrySet())
                entry(out, entry.getKey(), entry.getValue());
            entry(out, "fixture/commands/providerValue.bsh", "providerValue() { return new fixture.FixtureValue().value(); }");
        }
        return file;
    }

    private static void entry(JarOutputStream out, String name, String text) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
