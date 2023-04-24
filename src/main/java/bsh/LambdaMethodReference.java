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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class LambdaMethodReference implements Function<Object, Object>, Consumer<Object>, Supplier<Object>, BiConsumer<Object,Object>, Predicate<Object>, Comparator<Object> {
    Class<?> clas;
    String methodName;
    Object object;

    public LambdaMethodReference( Class<?> clas, String name ) {
        this.clas = clas;
        this.methodName = name;
    }

    public LambdaMethodReference( Object obj, String name ) {
        this(obj.getClass(), name);
        this.object = obj;
    }

    public Class<?> getType() {
        return clas;
    }

    public String getName() {
        return methodName;
    }

    public boolean hasInstance() {
        return null != this.object;
    }

    public Object getInstance() {
        return object;
    }

    public String toString() {
        return "Mehod Referencee: "+getType().getSimpleName()+"::"+getName();
    }

    private Invocable getMethod(Class<?>[] types) {
        return BshClassManager.memberCache.get(getType()).findMethod(methodName, types);
    }

    private Object invokeMethod(Object... args) {
        try {
            return getMethod(Types.getTypes(args)).invoke(getInstance(), args);
        } catch (InvocationTargetException e) {
            throw new ReflectError("Lambda method reference invocation: "+e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object castReturn(Object retrn) {
        return retrn;
        // try {
        //     return (R) Types.castObject(retrn, getReturnType(), Types.CAST);
        // } catch (UtilEvalError e) {
        //     throw new ReflectError("Lambda method reference cast return: "+e.getMessage(), e);
        // }
    }

    @SuppressWarnings("unchecked")
    private Class<?> getReturnType() {
        return (Class<Object>)((ParameterizedType) getClass().getGenericInterfaces()[0])
            .getActualTypeArguments()[1];
    }

    @Override
    public void accept(Object arg0) {
        invokeMethod(arg0);
    }

    @Override
    public Object apply(Object arg0) {
        return invokeMethod(arg0);
    }

    @Override
    public int compare(Object arg0, Object arg1) {
        return (int) invokeMethod(arg0, arg1);
    }

    @Override
    public boolean test(Object arg0) {
        return (boolean) invokeMethod(arg0);
    }

    @Override
    public void accept(Object arg0, Object arg1) {
        invokeMethod(arg0, arg1);
    }

    @Override
    public Object get() {
        return invokeMethod();
    }

}
