/** Copyright 2018 Nick nickl- Lombard
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

import java.util.ArrayList;
import java.util.List;

public class BSHTryWithResources extends SimpleNode {
    private static final long serialVersionUID = 1L;
    public BSHTryWithResources(int id) { super(id); }

    public Object eval( CallStack callstack, Interpreter interpreter)
            throws EvalError {
        for (int i=0; i < jjtGetNumChildren(); i++)
            jjtGetChild(i).eval(callstack, interpreter);

        return Primitive.VOID;
    }

    public List<Throwable> autoClose() {
        List<Throwable> thrown = new ArrayList<>();
        for (int i=0; i < jjtGetNumChildren(); i++) try {
            ((BSHAutoCloseable) jjtGetChild(i)).close();
        } catch (Throwable e) {
            thrown.add(e);
        }
        return thrown;
    }
}
