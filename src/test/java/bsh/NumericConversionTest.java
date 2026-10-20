package bsh;

import java.math.BigDecimal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class NumericConversionTest {

    @Test
    public void nan_and_infinity_are_not_equal_to_a_big_decimal() {
        Primitive big = new Primitive(BigDecimal.ONE);
        assertFalse(big.equals(new Primitive(Double.NaN)));
        assertFalse(new Primitive(Double.POSITIVE_INFINITY).equals(big));
        assertEquals(big, new Primitive(1.0));
    }

    @Test
    public void compound_assignment_leaves_a_non_numeric_result_alone() throws Exception {
        assertSame("x", Primitive.narrowLikeCast("x", Integer.TYPE));
        assertEquals(new Primitive((byte) 44),
            Primitive.narrowLikeCast(new Primitive(300), Byte.TYPE));
    }

    @Test
    public void an_undeclared_variable_has_no_declared_type() throws Exception {
        LHS lhs = new LHS(new NameSpace("test"), "undeclared", false);
        assertNull(lhs.getDeclaredType());
    }

    @Test
    public void compound_assignment_narrows_only_to_a_numeric_value_type() throws Exception {
        Primitive result = new Primitive(7.5);
        assertSame(result, Primitive.narrowLikeCast(result, null));
        assertSame(result, Primitive.narrowLikeCast(result, Object.class));
        assertSame(result, Primitive.narrowLikeCast(result, Number.class));
        assertEquals(new Primitive(7), Primitive.narrowLikeCast(result, Integer.TYPE));
        assertEquals(7, Primitive.narrowLikeCast(7.5, Integer.class));
        assertEquals(java.math.BigInteger.valueOf(7),
            Primitive.narrowLikeCast(7.5, java.math.BigInteger.class));
        assertEquals(new BigDecimal("7.5"),
            Primitive.narrowLikeCast(7.5, BigDecimal.class));
    }
}
