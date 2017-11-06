package bsh;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(FilteredTestRunner.class)
public class Issue_55_Test {

	@Test
	@Category(KnownIssue.class)
	public void check_BshScriptEngineFactory() throws Exception {
		final String script = "a = null; return \"a=\" + a;\n";
		final Object interpreterResult = new Interpreter().eval(script);
		final Object scriptEngineResult = new BshScriptEngineFactory().getScriptEngine().eval(script);
		assertEquals(interpreterResult, scriptEngineResult);
	}


	@Test
    @Category(KnownIssue.class)
	public void check_ExternalNameSpace() throws Exception {
		final ExternalNameSpace externalNameSpace = new ExternalNameSpace();
		externalNameSpace.setVariable("a", Primitive.NULL, false);
		assertTrue("map should contain variable 'a'", externalNameSpace.getMap().containsKey("a"));
		assertNull("variable 'a' should have value <NULL>", externalNameSpace.getMap().get("a"));
	}

}
