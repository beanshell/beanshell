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

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static bsh.TestUtil.eval;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(FilteredTestRunner.class)
@Category(ProjectCoinFeature.class)
public class Project_Coin_Test {


    @Test
    @Category(ProjectCoinFeature.class)
    public void integer_literal_enhancements() throws Exception {
        final Interpreter interpreter = new Interpreter();
        assertEquals("0x99", 153, interpreter.eval("return 0x99;"));
        assertEquals("0231", 153, interpreter.eval("return 0231;"));
        assertEquals("0b10011001", 153, interpreter.eval("return 0b10011001;"));
        assertEquals("0b_1001_1001", 153, interpreter.eval("return 0b_1001_1001;"));
        assertEquals("0x_9_9", 153, interpreter.eval("return 0x_9_9;"));
        assertEquals("15_500_000_000L", 15500000000L, interpreter.eval("return 15_500_000_000L;"));
    }


    @Test
    @Category(ProjectCoinFeature.class)
    public void diamond_operator() throws Exception {
        eval("List<String> list = new ArrayList<>()");
        final Object anagrams = eval("Map<String, List<String>> anagrams = new HashMap<>();" + "return anagrams;");
        assertNotNull(anagrams);
        assertTrue(anagrams.getClass().getName(), anagrams instanceof HashMap);
    }


    @SuppressWarnings({"JUnitTestMethodWithNoAssertions"})
    @Test
    @Category(Project_Coin_Test.class)
    public void try_with_resource_parsing() throws Exception {
        eval("try (ByteArrayOutputStream x = new ByteArrayOutputStream()) {", "} catch (Exception e) {", "}\n");
        eval("try (ByteArrayOutputStream x = new ByteArrayOutputStream(); ByteArrayOutputStream y = new ByteArrayOutputStream()) {", "} catch (Exception e) {", "}\n");
        eval("try (x = new ByteArrayOutputStream(); y = new ByteArrayOutputStream()) {", "} catch (Exception e) {", "}\n");
    }


    @Test
    @Category(Project_Coin_Test.class)
    public void try_with_resource() throws Exception {
        final Interpreter interpreter = new Interpreter();
        final AtomicBoolean closed = new AtomicBoolean(false);
        final IOException fromWrite = new IOException("exception from write");
        final IOException fromClose = new IOException("exception from close");
        final OutputStream autoclosable = new OutputStream() {

            @Override
            public void write(final int b) throws IOException {
                throw fromWrite;
            }


            @Override
            public void close() throws IOException {
                closed.set(true);
                throw fromClose;
            }
        };
        try {
            interpreter.set("autoclosable", autoclosable);
            interpreter.eval("try (x = new BufferedOutputStream(autoclosable)) {\n" +
                    "	x.write(42);\n" +
                    "} catch (Exception e) {\n" +
                    "	thrownException = e;\n" +
                    "}\n");
            fail("expected exception");
        } catch (final EvalError evalError) {
            if (evalError instanceof ParseException) {
                throw evalError;
            }
            final Throwable e = evalError.getCause();
            assertSame(fromWrite, e);
            interpreter.set("exception", e);
            final Object suppressed = interpreter.eval("return exception.getSuppressed();"); // avoid java 7 syntax in java code ;)
            assertSame(fromClose, suppressed);
        }
        assertTrue("stream should be closed", closed.get());
    }


    @Test
    @Category(Project_Coin_Test.class)
    public void switch_on_strings() throws Exception {
        final Object result = eval("switch(\"hurz\") {\n", "	case \"bla\": return 1;", "	case \"foo\": return 2;", "	case \"hurz\": return 3;", "	case \"igss\": return 4;", "	default: return 5;", "}\n");
        assertEquals(result, 3);
    }

}
