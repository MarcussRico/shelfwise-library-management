package shelfwise.model;

public class Faculty extends Member {
    public Faculty(int id, String name) { super(id, name); }
    @Override public int borrowLimit() { return 6; }
    @Override public int baseLoanDays() { return 28; }
    @Override public String type() { return "FACULTY"; }
}
