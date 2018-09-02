package bsh.matchers;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.core.IsEqual;
import bsh.StringUtil;

/** Custom matcher comparing to the result of StringUtil valueString.
 * @see StringUtil.valueString */
public class StringUtilValue extends IsEqual<String> {
    private final String expectedValue;

    /** Initialize super with expected value.
     * @param expectedValue the value */
    public StringUtilValue(String expectedValue) {
        super(expectedValue);
        this.expectedValue = expectedValue;
    }

    /** Retrieve the value string for comparison.
     * {@inheritDoc} */
    @Override
    public boolean matches(Object actualValue) {
        return super.matches(StringUtil.valueString(actualValue));
    }

    /** Modify expected value string for error display.
     * {@inheritDoc} */
    @Override
    public void describeTo(Description description) {
        description.appendText(expectedValue);
    }

    /** Modify found value string for error display.
     * {@inheritDoc} */
    @Override
    public void describeMismatch(Object item, Description description) {
        description.appendText("was "+StringUtil.valueString(item));
    }

    /** Static function to retrieve this Matcher instance.
     * @param value the expected value
     * @return new Matcher instance for expected value */
    public static Matcher<String> valueString(String value) {
        return new StringUtilValue(value);
    }
}
