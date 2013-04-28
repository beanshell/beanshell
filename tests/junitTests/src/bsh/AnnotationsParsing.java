package bsh;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;


/**
 * See <a href="http://code.google.com/p/beanshell2/issues/detail?id=24">issue 24</a>.
 */
@RunWith(FilteredTestRunner.class)
public class AnnotationsParsing {

    @Test
    @Category(KnownIssue.class)
    public void annotation_on_method_declaration() throws Exception {
        TestUtil.eval("public int myMethod(final int i) {", "	return i * 7;", "}", "return myMethod(6);");
    }
}
