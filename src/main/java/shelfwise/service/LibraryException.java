package shelfwise.service;

/** Base class for every rule the library enforces; the message is shown to the librarian as-is. */
public class LibraryException extends Exception {
    public LibraryException(String message) { super(message); }

    public static class NotFound extends LibraryException {
        public NotFound(String what) { super(what + " not found."); }
    }

    public static class LimitReached extends LibraryException {
        public LimitReached(String member, int limit) {
            super(member + " already has " + limit + " books, the most allowed.");
        }
    }

    public static class AlreadyHeld extends LibraryException {
        public AlreadyHeld() { super("This member already has this title or is already waiting for it."); }
    }

    public static class NotOnLoan extends LibraryException {
        public NotOnLoan() { super("That loan is not open, so it cannot be returned."); }
    }

    public static class InvalidInput extends LibraryException {
        public InvalidInput(String message) { super(message); }
    }
}
