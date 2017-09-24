/**
 * The Class InterfaceConsumer.
 */
public class InterfaceConsumer {

    /**
     * Instantiates a new interface consumer.
     */
    public InterfaceConsumer() {}

    /**
     * Consume interface.
     *
     * @param interf
     *            the interf
     * @return true, if successful
     */
    public boolean consumeInterface(final Interface interf) {
        interf.getString();
        interf.getInteger();
        interf.getPrimitiveInt();
        interf.getPrimitiveBool();
        assertTrue(interf.getNull() == null);
        return true;
    }

    /**
     * Assert true.
     *
     * @param cond
     *            the cond
     */
    public static void assertTrue(final boolean cond) {
        if (!cond)
            throw new RuntimeException("assert failed..");
    }
}
