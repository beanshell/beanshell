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

import java.util.Iterator;
import java.util.List;

import org.junit.experimental.categories.Category;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

/**
 * The Class FilteredTestRunner.
 */
public class FilteredTestRunner extends BlockJUnit4ClassRunner {

    /**
     * Instantiates a new filtered test runner.
     *
     * @param klass
     *            the klass
     * @throws InitializationError
     *             the initialization error
     */
    public FilteredTestRunner(final Class<?> klass) throws InitializationError {
        super(klass);
    }

    /** {@inheritDoc} */
    @Override
    protected List<FrameworkMethod> getChildren() {
        final List<FrameworkMethod> children = super.getChildren();
        final Iterator<FrameworkMethod> iterator = children.iterator();
        while (iterator.hasNext()) {
            final FrameworkMethod child = iterator.next();
            final Category category = child.getAnnotation(Category.class);
            if (category != null) {
                final Class<?>[] value = category.value();
                for (final Class<?> categoryClass : value)
                    if (TestFilter.class.isAssignableFrom(categoryClass))
                        try {
                            final TestFilter testFilter = (TestFilter) categoryClass
                                    .newInstance();
                            if (testFilter.skip()) {
                                System.out.println("skipping test "
                                        + child.getMethod() + " due filter "
                                        + categoryClass.getSimpleName());
                                iterator.remove();
                                break;
                            }
                        } catch (final InstantiationException e) {
                            throw new AssertionError(e);
                        } catch (final IllegalAccessException e) {
                            throw new AssertionError(e);
                        }
            }
        }
        return children;
    }
}
