package bsh;

import org.junit.experimental.categories.Category;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

import java.util.Iterator;
import java.util.List;

public class FilteredTestRunner extends BlockJUnit4ClassRunner {


    public FilteredTestRunner(final Class<?> klass) throws InitializationError {
        super(klass);
    }


    @Override
    protected List<FrameworkMethod> getChildren() {
        final List<FrameworkMethod> children = super.getChildren();
        final Iterator<FrameworkMethod> iterator = children.iterator();
        while (iterator.hasNext()) {
            final FrameworkMethod child = iterator.next();
            final Category category = child.getAnnotation(Category.class);
            if (category != null) {
                final Class<?>[] value = category.value();
                for (final Class<?> categoryClass : value) {
                    if (TestFilter.class.isAssignableFrom(categoryClass)) {
                        try {
                            final TestFilter testFilter = (TestFilter) categoryClass.newInstance();
                            if (testFilter.skip()) {
                                System.out.println("skipping test " + child.getMethod() + " due filter " + categoryClass.getSimpleName());
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
            }
        }
        return children;
    }
}
