package shelfwise.model;

/** A library member. Subclasses differ in how many books they may hold and for how long. */
public abstract class Member {
    private final int id;
    private final String name;

    protected Member(int id, String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Member name is required");
        this.id = id;
        this.name = name.trim();
    }

    public int getId() { return id; }
    public String getName() { return name; }

    /** Maximum books on loan at once. */
    public abstract int borrowLimit();

    /** Standard loan length in days when nobody is waiting for the book. */
    public abstract int baseLoanDays();

    public abstract String type();

    @Override public String toString() { return name + " (" + type() + " #" + id + ")"; }

    public static Member of(int id, String name, String type) {
        return "FACULTY".equalsIgnoreCase(type) ? new Faculty(id, name) : new Student(id, name);
    }
}
