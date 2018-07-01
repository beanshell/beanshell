package bsh;

import org.junit.Test;
import org.junit.runner.RunWith;

import static bsh.TestUtil.eval;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@RunWith(FilteredTestRunner.class)
public class StatementsTest {

    @Test
    public void switch_on_strings() throws Exception {
        final Object result = eval(
            "switch('hurz') {",
            "   case 'bla': return 1;",
            "   case 'foo': return 2;",
            "   case 'hurz': return 3;",
            "   case 'igss': return 4;",
            "   default: return 5;",
            "}");
        assertEquals("hurz matches hurz", 3, result);
    }

    @Test
    public void switch_to_default() throws Throwable {

        final Object result = eval(
            "switch('hurzzzz') {",
            "   case 'bla': return 1;",
            "   case 'foo': return 2;",
            "   case 'hurz': return 3;",
            "   case 'igss': return 4;",
            "   default: return 5;",
            "}");
        assertEquals("hurzzzzz doesn't match any case", 5, result);
    }

    @Test
    public void switch_on_enum() throws Exception {
        Object ret = eval(
            "enum Test { KEY1, KEY2 }",
            "val = Test.KEY1;",
            "switch (val) {",
                "case KEY1:",
                "case KEY2:",
                    "return 'not default';",
                    "break;",
                "default:",
                    "return 'default';",
            "}"
        );
        assertThat("not default branch", ret, equalTo("not default"));
    }

}
