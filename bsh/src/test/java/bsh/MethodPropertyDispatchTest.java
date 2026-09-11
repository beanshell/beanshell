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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import org.junit.Test;

/** Real methods and property aliases must not compete in one overload set. */
public class MethodPropertyDispatchTest {
    public interface Shutter {
        void up();
        boolean isUp();
        void down();
        boolean isDown();
    }

    public static class Device implements Shutter {
        public String trace = "";
        public boolean isUp() { trace += "isUp;"; return true; }
        public boolean isDown() { trace += "isDown;"; return false; }
        public String getTitle() { trace += "getTitle;"; return "property"; }
        public int getLevel() { return 0; }
        public void setLevel(int value) { trace += "setLevel;"; }
        public void up() { trace += "up;"; }
        public void down() { trace += "down;"; }
        public String title() { trace += "title;"; return "method"; }
        public void level(int value) { trace += "level;"; }
        public void value(Object value) { trace += "value;"; }
        public void setValue(String value) { trace += "setValue;"; }
        public boolean isEnabled() { trace += "isEnabled;"; return true; }
        public void enabled(int value) { trace += "enabled;"; }
        public void broken() { trace += "broken;"; throw new IllegalStateException("method failure"); }
        public boolean isBroken() { trace += "isBroken;"; return false; }
    }
    public static class Inherited extends Device {}
    public static class Override extends Device {
        @java.lang.Override public void up() { trace += "override;"; }
    }
    public static class FieldDevice extends Device {
        public String title = "field";
    }
    public static class Aliases {
        public int reads, writes;
        public boolean isUp() { reads++; return true; }
        public void setLevel(int value) { writes += value; }
    }
    public static class StaticDevice {
        public static String trace = "";
        public static boolean isUp() { trace += "isUp;"; return true; }
        public static void up() { trace += "up;"; }
        public static void setLevel(int value) { trace += "setLevel;"; }
        public static void level(int value) { trace += "level;"; }
    }
    public static class StaticAlias {
        public static boolean isUp() { return true; }
    }
    public static class Empty {}

    private Interpreter interpreter(boolean strict, Object device) throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.setStrictJava(strict);
        interpreter.getNameSpace().getThis(interpreter);
        interpreter.set("device", device);
        return interpreter;
    }

    @Test public void explicit_calls_invoke_real_methods() throws Exception {
        for (boolean strict : new boolean[] {false, true})
            for (Device device : new Device[] {new Device(), new Inherited()}) {
                Interpreter interpreter = interpreter(strict, device);
                assertNull(interpreter.eval("device.up(); device.down();"));
                assertEquals("method", interpreter.eval("device.title();"));
                interpreter.eval("device.level(7);");
                assertEquals("up;down;title;level;", device.trace);
            }
    }

    @Test public void properties_still_invoke_accessors() throws Exception {
        for (boolean strict : new boolean[] {false, true}) {
            Device device = new Inherited();
            Interpreter interpreter = interpreter(strict, device);
            assertEquals(true, interpreter.eval("device.up"));
            assertEquals(false, interpreter.eval("device{\"down\"}"));
            assertEquals("property", interpreter.eval("device.title"));
            interpreter.eval("device.level = 3; device{\"level\"} = 4;");
            assertEquals("isUp;isDown;getTitle;setLevel;setLevel;", device.trace);
        }
    }

    @Test public void real_method_wins_over_more_specific_alias() throws Exception {
        for (boolean strict : new boolean[] {false, true}) {
            Device device = new Device();
            interpreter(strict, device).eval("device.value(\"text\");");
            assertEquals("value;", device.trace);
        }
    }

    @Test public void alias_fallback_requires_no_applicable_real_method() throws Exception {
        for (boolean strict : new boolean[] {false, true}) {
            Device device = new Device();
            Interpreter interpreter = interpreter(strict, device);
            assertEquals(true, interpreter.eval("device.enabled()"));
            interpreter.eval("device.enabled(1); device.enabled(); device.enabled(2);");
            assertEquals("isEnabled;enabled;isEnabled;enabled;", device.trace);
            try {
                interpreter.eval("device.level(1, 2);");
                fail("Neither method nor accessor accepts two arguments");
            } catch (EvalError expected) {
                assertTrue(expected.getMessage().contains("not found"));
            }
        }
    }

    @Test public void alias_only_calls_are_retained() throws Exception {
        for (boolean strict : new boolean[] {false, true}) {
            Aliases device = new Aliases();
            Interpreter interpreter = interpreter(strict, device);
            assertEquals(true, interpreter.eval("device.up()"));
            interpreter.eval("device.level(7);");
            assertEquals(1, device.reads);
            assertEquals(7, device.writes);
        }
    }

    @Test public void throwing_real_method_does_not_try_alias() throws Exception {
        for (boolean strict : new boolean[] {false, true}) {
            Device device = new Device();
            try {
                interpreter(strict, device).eval("device.broken();");
                fail("Expected the real method to throw");
            } catch (TargetError expected) {
                assertTrue(expected.getTarget() instanceof IllegalStateException);
                assertEquals("method failure", expected.getTarget().getMessage());
            }
            assertEquals("broken;", device.trace);
        }
    }

    @Test public void overrides_and_fields_keep_precedence() throws Exception {
        for (boolean strict : new boolean[] {false, true}) {
            Device device = new Override();
            interpreter(strict, device).eval("device.up();");
            assertEquals("override;", device.trace);
            FieldDevice field = new FieldDevice();
            Interpreter interpreter = interpreter(strict, field);
            assertEquals("field", interpreter.eval("device.title"));
            assertEquals("property", interpreter.eval("device{\"title\"}"));
            assertEquals("getTitle;", field.trace);
        }
    }

    @Test public void static_calls_and_imports_use_real_names() throws Exception {
        String owner = "bsh.MethodPropertyDispatchTest.StaticDevice";
        for (boolean strict : new boolean[] {false, true})
            for (String script : new String[] {
                    "import " + owner + "; StaticDevice.up(); StaticDevice.level(7);",
                    "import static " + owner + ".*; up(); level(7);",
                    "import " + owner + "; import static StaticDevice.up; import static StaticDevice.level; up(); level(7);"}) {
                StaticDevice.trace = "";
                interpreter(strict, new Device()).eval(script);
                assertEquals("up;level;", StaticDevice.trace);
            }
    }

    @Test public void static_alias_fallback_is_retained() throws Exception {
        String owner = "bsh.MethodPropertyDispatchTest.StaticAlias";
        for (boolean strict : new boolean[] {false, true}) {
            Interpreter interpreter = interpreter(strict, new Device());
            assertEquals(true, interpreter.eval("import " + owner + "; StaticAlias.up()"));
            assertEquals(true, interpreter.eval("import static " + owner + ".*; up()"));
            // Named imports historically bind the accessor's actual name.
            assertEquals(true, interpreter.eval("import static " + owner + ".up; isUp()"));
        }
    }

    @Test public void imported_objects_keep_methods_and_properties() throws Exception {
        for (boolean strict : new boolean[] {false, true}) {
            Device device = new Device();
            Interpreter interpreter = interpreter(strict, device);
            interpreter.getNameSpace().importObject(device);
            interpreter.eval("up(); level(7);");
            assertEquals(true, interpreter.eval("up"));
            // Strict mode requires an explicit accessor for an undeclared local property.
            interpreter.eval(strict ? "setLevel(3);" : "level = 3;");
            assertEquals("up;level;isUp;setLevel;", device.trace);
        }
    }

    @Test public void property_presence_does_not_require_a_real_method() {
        assertTrue(Reflect.hasObjectPropertyGetter(Aliases.class, "up"));
        assertTrue(Reflect.hasObjectPropertySetter(Aliases.class, "level"));
        assertFalse(Reflect.hasObjectPropertyGetter(Aliases.class, "missing"));
        assertFalse(Reflect.hasObjectPropertySetter(Aliases.class, "missing"));
        assertTrue(Reflect.hasObjectPropertyGetter(java.util.Map.class, "anything"));
    }

    @Test public void member_enumeration_contains_only_real_names() {
        BshClassManager.MemberCache cache = BshClassManager.memberCache.get(Inherited.class);
        for (String name : new String[] {"up", "down", "title", "level"})
            for (Invocable member : cache.members(name))
                assertEquals(name, member.getName());
        BshClassManager.MemberCache aliases = BshClassManager.memberCache.get(Aliases.class);
        assertFalse(aliases.hasMember("up"));
        assertNull(aliases.findMethod("up", new Class<?>[0]));
        assertNotNull(aliases.findGetter("up"));
        assertEquals(1, aliases.memberCount(Aliases.class.getName()));
        assertEquals(0, aliases.findMemberIndex(Aliases.class.getName(), new Class<?>[0]));
    }

    @Test public void insertion_order_cannot_replace_real_members() throws Exception {
        Method add = BshClassManager.MemberCache.class.getDeclaredMethod("cacheMember", Invocable.class);
        add.setAccessible(true);
        for (String[] pair : new String[][] {{"up", "isUp"}, {"down", "isDown"},
                {"title", "getTitle"}, {"level", "setLevel"}}) {
            Class<?>[] types = pair[0].equals("level") ? new Class<?>[] {int.class} : new Class<?>[0];
            Invocable method = Invocable.get(Device.class.getMethod(pair[0], types));
            Invocable accessor = Invocable.get(Device.class.getMethod(pair[1], types));
            for (boolean accessorFirst : new boolean[] {false, true}) {
                BshClassManager.MemberCache cache = new BshClassManager.MemberCache(Empty.class);
                add.invoke(cache, accessorFirst ? accessor : method);
                add.invoke(cache, accessorFirst ? method : accessor);
                assertEquals(pair[0], cache.findMethod(pair[0], types).getName());
                assertEquals(pair[1], (accessor.isGetter()
                        ? cache.findGetter(pair[0]) : cache.findSetter(pair[0])).getName());
            }
        }
    }
}
