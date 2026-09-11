/**
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */

package bsh;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ArrayParameterTest {
    @Test
    public void scalarAndArrayOverloadsIgnoreDeclarationOrder() throws Exception {
        for (boolean reverse : new boolean[] {false, true}) {
            Interpreter interpreter = new Interpreter();
            String scalar = "pick(value) { return value; }";
            String array = "pick(String[] values) { return pick(values[1]); }";
            interpreter.eval(reverse ? array + scalar : scalar + array);
            assertEquals("a", interpreter.eval("pick(\"a\");"));
            assertEquals("b", interpreter.eval("pick(new String[] {\"a\", \"b\"});"));
        }
    }

    @Test
    public void trailingParameterDimensionsAreResolved() throws Exception {
        assertEquals("a", new Interpreter().eval(
            "pick(String values[]) { return values[0]; }"
            + "pick(new String[] {\"a\"});"));
    }

    @Test
    public void splitParameterDimensionsAreCombined() throws Exception {
        assertEquals("a", new Interpreter().eval(
            "pick(String[] values[]) { return values[0][0]; }"
            + "pick(new String[][] {{\"a\"}});"));
    }

    @Test
    public void generatedMethodDescriptorsIncludeTrailingDimensions() throws Exception {
        Class<?> type = (Class<?>) new Interpreter().eval(
            "class ArrayMethods {"
            + "public String pick(String values[]) { return values[0]; }"
            + "} return ArrayMethods.class;");
        assertEquals("a", type.getMethod("pick", String[].class)
            .invoke(type.newInstance(), (Object) new String[] {"a"}));
    }

    @Test
    public void generatedConstructorsAndMethodsCombineDimensions() throws Exception {
        Class<?> type = (Class<?>) new Interpreter().eval(
            "class ArrayMethods { String value;"
            + "public ArrayMethods(String[] values[]) { value = values[0][0]; }"
            + "public String pick(String[] values[]) { return value + values[0][0]; }"
            + "} return ArrayMethods.class;");
        Object instance = type.getConstructor(String[][].class)
            .newInstance((Object) new String[][] {{"a"}});
        assertEquals("ab", type.getMethod("pick", String[][].class)
            .invoke(instance, (Object) new String[][] {{"b"}}));
    }

    @Test
    public void overloadsDistinguishArrayRanks() throws Exception {
        Interpreter interpreter = new Interpreter();
        interpreter.eval("pick(String[] values) { return 1; }"
            + "pick(String[][] values) { return 2; }");
        assertEquals(1, interpreter.eval("pick(new String[] {\"a\"});"));
        assertEquals(2, interpreter.eval("pick(new String[][] {{\"a\"}});"));
    }

    @Test
    public void primitiveDimensionsAndArrayVarargsHaveMatchingDescriptors() throws Exception {
        Class<?> type = (Class<?>) new Interpreter().eval(
            "class ArrayMethods {"
            + "public int pick(int[] values[][]) { return values[0][0][0]; }"
            + "public String first(String[]... values) { return values[0][0]; }"
            + "} return ArrayMethods.class;");
        Object instance = type.newInstance();
        assertEquals(7, type.getMethod("pick", int[][][].class)
            .invoke(instance, (Object) new int[][][] {{{7}}}));
        assertEquals("a", type.getMethod("first", String[][].class)
            .invoke(instance, (Object) new String[][] {{"a"}}));
    }

}
