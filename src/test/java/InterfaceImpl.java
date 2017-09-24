/**
 * The Class InterfaceImpl.
 */
public class InterfaceImpl implements Interface {

    /** {@inheritDoc} */
    public String getString() {
        return "foo";
    }

    /** {@inheritDoc} */
    public Integer getInteger() {
        return new Integer(5);
    }

    /** {@inheritDoc} */
    public int getPrimitiveInt() {
        return 7;
    }

    /** {@inheritDoc} */
    public boolean getPrimitiveBool() {
        return true;
    }

    /** {@inheritDoc} */
    public Object getNull() {
        return null;
    }
}
