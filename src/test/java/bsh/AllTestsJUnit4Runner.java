package bsh;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.internal.runners.SuiteMethod;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class AllTestsJUnit4Runner extends BlockJUnit4ClassRunner {
    private final Method method;
    private final TestSuite suite;

    public AllTestsJUnit4Runner(Class<?> klass) throws InitializationError {
        super(klass);
        try {
            suite = (TestSuite) SuiteMethod.testFromSuiteMethod(klass);
            method = TestCase.class.getDeclaredMethod("runBare");
        } catch (Throwable t) { throw new InitializationError(t); }
        
    }

    @Override
    protected List<FrameworkMethod> getChildren() {
        return Stream.concat(
                   super.getChildren().stream(), 
                   Collections.list(suite.tests()).stream()
                       .map(TestSuiteMethod::new))
               .collect(Collectors.toList());
    }

    class TestSuiteMethod extends FrameworkMethod {
        TestCase test;

        public TestSuiteMethod(Test test) {
            super(method);
            this.test = (TestCase) test;
        }

        @Override
        public Object invokeExplosively(final Object target, final Object... params)
                throws Throwable {
            test.runBare();
            return void.class;
        }

        @Override
        public String getName() {
            return test.getName();
        }
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof TestSuiteMethod))
                return false;
            return test.equals(((TestSuiteMethod) obj).test); 
        }

        @Override
        public int hashCode() {
            return test.hashCode();
        }

    };
    
}
