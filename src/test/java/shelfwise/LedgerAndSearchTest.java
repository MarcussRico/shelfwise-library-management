package shelfwise;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import shelfwise.ledger.*;
import shelfwise.model.Book;
import shelfwise.search.FuzzySearch;

class LedgerAndSearchTest {
    static List<LedgerEntry> chain(int n) {
        List<LedgerEntry> out = new ArrayList<>();
        String prev = HashChain.GENESIS;
        for (int i = 1; i <= n; i++) {
            String h = HashChain.hash(i, "2026-10-0" + (1 + i % 9), "ISSUE", "loan=" + i, prev);
            out.add(new LedgerEntry(i, "2026-10-0" + (1 + i % 9), "ISSUE", "loan=" + i, prev, h));
            prev = h;
        }
        return out;
    }

    @Test void intactChainVerifies() { assertEquals(-1, HashChain.firstBroken(chain(50))); }
    @Test void editedPayloadIsLocated() {
        var c = chain(50); var e = c.get(20);
        c.set(20, new LedgerEntry(e.seq(), e.timestamp(), e.type(), "loan=999", e.prevHash(), e.hash()));
        assertEquals(20, HashChain.firstBroken(c));
    }
    @Test void deletedEntryIsLocated() { var c = chain(50); c.remove(10); assertEquals(10, HashChain.firstBroken(c)); }
    @Test void rehashedEditStillBreaksNextLink() {
        var c = chain(50); var e = c.get(5);
        String h = HashChain.hash(e.seq(), e.timestamp(), e.type(), "loan=0", e.prevHash());
        c.set(5, new LedgerEntry(e.seq(), e.timestamp(), e.type(), "loan=0", e.prevHash(), h));
        assertEquals(6, HashChain.firstBroken(c));
    }
    @Test void distanceCountsTransposition() { assertEquals(1, FuzzySearch.distance("hobit", "hobti")); }
    @Test void distanceBasic() { assertEquals(3, FuzzySearch.distance("kitten", "sitting")); }
    @Test void fuzzyFindsTypo() {
        var books = List.of(new Book(1, "9780000000002", "The Hobbit", "J.R.R. Tolkien", 1937, 100, 1, 1),
                new Book(2, "9780000000019", "Dune", "Frank Herbert", 1965, 90, 1, 1));
        assertEquals(1, new FuzzySearch(books).search("tolkein hobit", 1).get(0).book().id());
    }
    @Test void substringMissesTypo() {
        var books = List.of(new Book(1, "9780000000002", "The Hobbit", "J.R.R. Tolkien", 1937, 100, 1, 1));
        assertTrue(FuzzySearch.substring(books, "hobit", 5).isEmpty());
    }
}
