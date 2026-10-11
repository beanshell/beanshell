/**
    Used to test that a class whose internal (bytecode) name does not match
    the name under which it was looked up (as happens on case-insensitive
    file systems, e.g. Windows, when a lowercase variable name resolves to
    an uppercase class resource) is treated as "not found" rather than
    propagating a NoClassDefFoundError.
*/
public class WrongCase
{
    public int getFive() { return 5; }
}
