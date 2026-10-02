package shelfwise.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import shelfwise.model.Book;
import shelfwise.util.Isbn;

/** Loads the goodbooks-10k catalogue, repairing and validating ISBNs on the way in. */
public final class Catalogue {
    public record LoadReport(int rows, int validAsIs, int repairedLeadingZeros, int rejected, List<Book> books) {}

    private Catalogue() {}

    public static LoadReport load(Path csv, int keep) throws IOException {
        List<String> lines = Files.readAllLines(csv);
        List<String> header = parse(lines.get(0));
        int iIsbn = header.indexOf("isbn"), iTitle = header.indexOf("title"), iAuth = header.indexOf("authors"),
                iYear = header.indexOf("original_publication_year"), iPop = header.indexOf("ratings_count");
        int valid = 0, repaired = 0, rejected = 0;
        List<Book> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int r = 1; r < lines.size(); r++) {
            List<String> f = parse(lines.get(r));
            if (f.size() <= iPop) { rejected++; continue; }
            String raw = Isbn.clean(f.get(iIsbn));
            String isbn10 = null;
            if (Isbn.isValid10(raw)) { isbn10 = raw; valid++; }
            else if (raw.matches("[\\dX]{7,9}") && Isbn.isValid10("0".repeat(10 - raw.length()) + raw)) {
                isbn10 = "0".repeat(10 - raw.length()) + raw; repaired++;
            } else { rejected++; continue; }
            String isbn13 = Isbn.to13(isbn10);
            if (!seen.add(isbn13)) { rejected++; continue; }
            int year = f.get(iYear).isBlank() ? 0 : (int) Double.parseDouble(f.get(iYear));
            out.add(new Book(out.size() + 1, isbn13, f.get(iTitle).trim(), f.get(iAuth).trim(), year,
                    Long.parseLong(f.get(iPop)), 1, 1));
        }
        out.sort(Comparator.comparingLong(Book::popularity).reversed());
        List<Book> kept = new ArrayList<>();
        for (int i = 0; i < Math.min(keep, out.size()); i++) {
            Book b = out.get(i);
            int copies = i < 50 ? 3 : i < 200 ? 2 : 1;
            kept.add(new Book(i + 1, b.isbn13(), b.title(), b.authors(), b.year(), b.popularity(), copies, copies));
        }
        return new LoadReport(lines.size() - 1, valid, repaired, rejected, kept);
    }

    /** Minimal RFC-4180 CSV line parser (quoted fields, doubled quotes). */
    static List<String> parse(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (q) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { sb.append('"'); i++; }
                else if (ch == '"') q = false;
                else sb.append(ch);
            } else if (ch == '"') q = true;
            else if (ch == ',') { out.add(sb.toString()); sb.setLength(0); }
            else sb.append(ch);
        }
        out.add(sb.toString());
        return out;
    }
}
