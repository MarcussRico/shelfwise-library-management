package shelfwise.model;

public class Student extends Member {
    public Student(int id, String name) { super(id, name); }
    @Override public int borrowLimit() { return 3; }
    @Override public int baseLoanDays() { return 14; }
    @Override public String type() { return "STUDENT"; }
}
