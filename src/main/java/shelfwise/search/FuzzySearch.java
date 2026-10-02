package shelfwise.search;

import java.util.*;
import shelfwise.model.Book;

/**
 * Typo-tolerant title/author search. Each query word is matched to its closest word in the
 * title or author by Damerau–Levenshtein distance; a book's score is the mean word similarity.
 */
public class FuzzySearch {
    private record Indexed(Book book, String[] words) {}
    public record Hit(Book book, double score) {}

    private final List<Indexed> index = new ArrayList<>();

    public FuzzySearch(Collection<Book> books) {
        for (Book b : books) index.add(new Indexed(b, words(b.title() + " " + b.authors())));
    }

    public List<Hit> search(String query, int k) {
        String[] q = words(query);
        if (q.length == 0) return List.of();
        PriorityQueue<Hit> best = new PriorityQueue<>(Comparator.comparingDouble(Hit::score)
                .thenComparingLong(h -> h.book().popularity()));
        for (Indexed ix : index) {
            double total = 0;
            for (String w : q) {
                double s = 0;
                for (String t : ix.words()) s = Math.max(s, similarity(w, t));
                total += s;
            }
            Hit h = new Hit(ix.book(), total / q.length);
            best.add(h);
            if (best.size() > k) best.poll();
        }
        List<Hit> out = new ArrayList<>(best);
        out.sort(Comparator.comparingDouble(Hit::score).reversed().thenComparing(h -> -h.book().popularity()));
        return out;
    }

    /** Plain case-insensitive substring match, ranked by popularity: the baseline a basic system uses. */
    public static List<Book> substring(Collection<Book> books, String query, int k) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        return books.stream().filter(b -> (b.title() + " " + b.authors()).toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparingLong(Book::popularity).reversed()).limit(k).toList();
    }

    static String[] words(String s) {
        return Arrays.stream(s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(w -> !w.isBlank()).toArray(String[]::new);
    }

    static double similarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        if (b.startsWith(a) && a.length() >= 3) return 0.95;
        int d = distance(a, b);
        return 1.0 - (double) d / Math.max(a.length(), b.length());
    }

    /** Optimal-string-alignment Damerau–Levenshtein distance. */
    public static int distance(String a, String b) {
        int n = a.length(), m = b.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1))
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
            }
        }
        return d[n][m];
    }
}
