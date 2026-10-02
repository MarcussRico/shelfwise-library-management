package shelfwise.util;

/** ISBN-10 / ISBN-13 checksum validation and conversion. */
public final class Isbn {
    private Isbn() {}

    public static String clean(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\s-]", "").toUpperCase();
    }

    public static boolean isValid10(String raw) {
        String s = clean(raw);
        if (!s.matches("\\d{9}[\\dX]")) return false;
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            int d = (i == 9 && s.charAt(i) == 'X') ? 10 : s.charAt(i) - '0';
            sum += d * (10 - i);
        }
        return sum % 11 == 0;
    }

    public static boolean isValid13(String raw) {
        String s = clean(raw);
        if (!s.matches("\\d{13}")) return false;
        int sum = 0;
        for (int i = 0; i < 13; i++) sum += (s.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return sum % 10 == 0;
    }

    public static boolean isValid(String raw) {
        String s = clean(raw);
        return s.length() == 10 ? isValid10(s) : isValid13(s);
    }

    /** Converts a valid ISBN-10 to ISBN-13 (978 prefix, recomputed check digit). */
    public static String to13(String isbn10) {
        String s = clean(isbn10);
        if (!isValid10(s)) throw new IllegalArgumentException("Invalid ISBN-10: " + isbn10);
        String core = "978" + s.substring(0, 9);
        int sum = 0;
        for (int i = 0; i < 12; i++) sum += (core.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return core + ((10 - sum % 10) % 10);
    }
}
