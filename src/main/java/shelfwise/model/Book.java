package shelfwise.model;

/** A catalogue title; {@code copies} physical copies of which {@code available} are on the shelf. */
public record Book(int id, String isbn13, String title, String authors, int year,
                   long popularity, int copies, int available) {
    public Book {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title is required");
        if (copies < 1) throw new IllegalArgumentException("A title needs at least one copy");
        if (available < 0 || available > copies) throw new IllegalArgumentException("Available copies out of range");
    }
}
