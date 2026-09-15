package mypackage;

// Package-private: a lambda wrapper (always defined in package bsh) cannot
// name this type, so it must not appear in the generated declaredExceptions.
class HiddenCheckedException extends Exception {
    HiddenCheckedException(String message) {
        super(message);
    }
}
