# SHELFWISE — a library system that moves the queue

**A demand-aware, race-free, tamper-evident library management system in Java.**

Project-Based Learning (Java Programming) · Department of Computer Science and Engineering ·
Chennai Institute of Technology · 2026–2027
**Sai Charan A** (2104251040844) · **Ashfaq Muhammed A** (2104251040096) · Mentor: **Ms. Swathi L**

🌐 **Project page:** https://shelfwise-site.vercel.app (screenshots, report, poster, and a runnable download)

---

## The pitch in 30 seconds

Most library management projects digitise the paper register: add a book, issue it, return it, done.
Shelfwise starts from four problems a real library has, fixes each one in Java, and **measures the fix**:

| Problem in a typical system | What Shelfwise does | Measured result |
|---|---|---|
| Every book gets the same 14-day loan, even when 5 people are waiting for it | **Demand-adaptive loans**: the loan is shortened only when there is a queue | Mean wait for a held book **17.0 → 11.9 days (−29.9%)** |
| Two librarians can issue the **last copy** at the same moment | **Race-free issue**: check and decrement in one atomic SQL statement | Double issues: **150/150 → 0/150** |
| Anyone with database access can quietly delete a fine | **SHA-256 hash-chained audit ledger** | **1,000 / 1,000** tampering attempts caught *and located* |
| Searching "Hary Poter" finds nothing | **Typo-tolerant search** (Damerau–Levenshtein) | Right book first **75.6%** vs **1.6%** |
| Imported catalogue data is quietly broken | **ISBN checksum repair** on import | **6,587 of 10,000** broken ISBNs recovered, **99.8%** verified |

Built on real data: the **goodbooks-10k** dataset (10,000 books with real Goodreads popularity).
**39 / 39 JUnit 5 tests pass**, with 74.5% line coverage of the non-UI code.

---

## The five "wow" features, explained

### 1. Demand-adaptive loans — *the headline idea*

**The problem.** A fixed loan period ignores demand. A rarely read textbook and a bestseller with a waiting list both go out for 14 days.

**The idea.** Size each loan when the book is issued, using how many members are waiting for it:

```
days = max(7, round(base / (1 + 0.5 × membersWaiting)))
```

| Members waiting | Student loan (base 14) | Faculty loan (base 28) |
|---|---|---|
| 0 | **14 days** (unchanged) | **28 days** (unchanged) |
| 1 | 9 days | 19 days |
| 2 | 7 days | 14 days |
| 4+ | 7 days (floor) | 9 days |

- **Nobody is waiting, so nothing changes.** Readers of uncontested books never notice the policy.
- A shortened loan **can't be renewed**, because someone is queued for it.
- The librarian sees the reason on screen, e.g. *"2 members are waiting for this title, so the loan is 7 days (standard 14) and cannot be renewed."*

**How we proved it.** `CirculationSimulator` runs a day-by-day simulation of one library year: 400 members (360 students, 40 faculty), about 45 requests a day weighted by **real Goodreads popularity**, random reading times, late returns, renewals and a FIFO hold queue. Both policies run on the **same random seeds** (5 seeds), so the only difference between runs is the loan policy.

| Metric | Fixed | Demand-adaptive |
|---|---|---|
| Mean wait for a held title | 17.0 days | **11.9 days (−29.9%)** |
| 90th-percentile wait | 31.6 days | **23.2 days** |
| Requests served from the queue | 4,414 | **4,876** |
| Loans on uncontested books | 15.7 days | 15.6 days (*unchanged*) |
| Loans returned unfinished | 4.1% | 10.1% ← **the cost, reported honestly** |

**Sensitivity analysis.** We tried floors of 5, 7 and 10 days and strengths of 0.25, 0.5 and 1.0. The most aggressive setting (5 days, k = 1.0) cuts waits further to 9.6 days, but 12.3% of loans end unfinished. **7 days with k = 0.5** is the balance we chose.

**OOP angle.** `LoanPolicy` is an interface (Strategy pattern) with `FixedLoanPolicy` and `DemandAdaptivePolicy`. The simulator and the app take either one, which is what makes the comparison fair.

---

### 2. Race-free issue — *a real concurrency bug, fixed*

**The problem.** The usual code does this:

```java
if (book.available > 0) {        // 1. read
    book.available--;            // 2. write
}
```

If two librarians click **Issue** together, both reads see `available = 1`, and the **last copy is issued twice**.

**The fix** (`BookDao.takeCopy`) puts the check and the decrement into one statement, so the database does both atomically:

```sql
UPDATE books SET available = available - 1 WHERE id = ? AND available > 0
```

If it updates 1 row, the member gets the copy. If it updates 0 rows, the copy is gone and the member joins the hold queue. A `CHECK(available >= 0 AND available <= copies)` constraint on the table backs this up.

**Measured** (`Experiments.java`):
- In memory, 16 threads, 2,000 trials: the naive version over-issued **931 times**; ours **0**.
- Against the real SQLite database, 8 threads, 150 trials: the naive version double-issued **150 / 150**; ours **0 / 150**.

Every operation (issue, return, hold) also runs in **one JDBC transaction** with rollback on any error, so the database is never left half-updated.

---

### 3. SHA-256 hash-chained audit ledger — *blockchain-style integrity, without a blockchain*

**The problem.** Fines and loan history live in a database, and anyone with access can change a fine to zero or delete a late return. A normal system would never notice.

**The idea.** Every issue, return, hold and fine is appended to a ledger. Each entry stores the hash of the entry before it:

```
hash(n) = SHA-256( seq | timestamp | type | payload | hash(n-1) )
```

Editing, deleting or reordering any past entry changes its hash, which breaks the link to every entry after it. `HashChain.firstBroken()` walks the chain and reports **the exact entry** where it breaks.

- The ledger entry is written **in the same transaction** as the change it records, so the two can't get out of sync.
- **Measured:** 4 kinds of attack (edit a fine, delete an entry, swap two entries, back-date a return), 250 trials each. **1,000 / 1,000 were detected, and every one was located at the right entry.**
- **Fast:** verifying 5,000 entries takes about **9 ms**.

---

### 4. Typo-tolerant search

**The problem.** A substring search for `hary poter` returns nothing.

**The fix** (`FuzzySearch`) matches each query word to the closest word in the title or author using **Damerau–Levenshtein distance**. That distance counts insertions, deletions, substitutions **and swapped neighbouring letters** ("teh" → "the") as single edits. Ties are broken by popularity.

**Measured** on 500 real titles, each with one typo introduced:
- Right book ranked **first: 75.6%** (substring search: 1.6%)
- Right book in the **top 5: 89%**
- On queries with **no typo**, fuzzy search is just as accurate as substring search (79.8% vs 78.8%), so it costs nothing.
- About **5 ms** per query over the whole catalogue.

---

### 5. ISBN checksum repair — *a finding in the real data*

When we imported goodbooks-10k, **6,587 of the 10,000 ISBNs were invalid**. They had **lost their leading zeros**: `0439023483` had become `439023483`, because the column had been treated as a number somewhere along the way (spreadsheets do this).

`Catalogue.load()` pads each broken ISBN back to 10 digits and accepts it **only if the ISBN-10 checksum passes**. It then converts the result to ISBN-13 with a recomputed check digit.

| Rows | Valid as-is | **Repaired** | Rejected |
|---|---|---|---|
| 10,000 | 2,690 | **6,587** | 723 |

A separate cross-check against the dataset's own ISBN-13 column agrees **99.8%** of the time (6,571 agree, 13 disagree). The app keeps the 1,000 most popular titles, with 1,250 copies in total.

---

## Live demo script (about 5 minutes)

**Before the review:**
```bash
cd ~/projects/shelfwise
/opt/homebrew/opt/openjdk@21/bin/java -cp "out/main:lib/*" shelfwise.app.Main
```
(Or `./build.sh run`, if `java` 21+ is on your PATH. Note that it recompiles and wipes `out/`.)
The app's "today" is fixed at **2 Oct 2026**, so the demo is the same every time.

| # | Tab | Do this | Say this |
|---|---|---|---|
| 1 | **Circulation desk** | Issue a book nobody is waiting for | "No queue, so the standard 14-day loan. The policy doesn't touch uncontested books." |
| 2 | **Demand board** | Show the titles with the most holds | "These titles have waiting lists. This is where the policy acts." |
| 3 | **Circulation desk** | Issue a book that has holds | "2 members are waiting, so this loan is 7 days and can't be renewed. The reason is shown to the librarian." |
| 4 | **Circulation desk** | Try to issue a title with no free copy | "It joins the hold queue. The free-copy check is one atomic SQL statement, so two desks can't take the same last copy." |
| 5 | **Catalogue** | Search `hary poter`, `hobit`, then `teh great gatsby` | "One typo, right book first. The last one has swapped letters, which Damerau–Levenshtein counts as a single edit." |
| 6 | **Audit ledger** | Click **Verify chain** → *intact* | "Every issue, return and fine is SHA-256 chained." |
| 7 | **Terminal** | Run the **tamper** command below, then click **Verify chain** again | "I just waived a ₹2 fine directly in the database. It's caught, and it points at entry #280." |
| 8 | **Terminal** | Run the **undo** command, then **Verify chain** | "Put it back, and the chain verifies again." |

**Tamper** (waives a ₹2 late fine directly in the database):
```bash
sqlite3 ~/projects/shelfwise/shelfwise.db "UPDATE ledger SET payload = replace(payload,'fine=2','fine=0') WHERE seq = 280;"
```
**Undo** (restores it; **always run this after the demo**):
```bash
sqlite3 ~/projects/shelfwise/shelfwise.db "UPDATE ledger SET payload = replace(payload,'fine=0','fine=2') WHERE seq = 280;"
```

> ⚠️ `./build.sh screens` tampers with the ledger on purpose to take the "tampering detected" screenshot, and it does not undo that. Don't run it on your demo database before the review. If you do, rerun the **undo** above with `'fine=02'` in place of `'fine=0'`.

---

## Object-oriented design

| Concept | Where |
|---|---|
| **Abstraction and inheritance** | Abstract `Member` → `Student` (3 books, 14 days) / `Faculty` (6 books, 28 days) |
| **Polymorphism** | `member.borrowLimit()` and `member.baseLoanDays()`: the service never checks the member type |
| **Interface + Strategy pattern** | `LoanPolicy` → `FixedLoanPolicy`, `DemandAdaptivePolicy` (can be swapped at runtime) |
| **Records (Java 16+)** | `Book`, `Loan`, `LoanTerms`, `LedgerEntry`, plus result records in `LibraryService` |
| **Custom exception hierarchy** | `LibraryException` → `NotFound`, `LimitReached`, `AlreadyHeld`, `NotOnLoan`, `InvalidInput` |
| **DAO pattern + JDBC** | `BookDao`, `MemberDao`, `LedgerStore`. **Only `PreparedStatement`** is used, so no SQL injection |
| **Transactions** | Each issue, return or hold is one transaction with `commit` / `rollback` |
| **Concurrency** | Atomic conditional `UPDATE`, SQLite WAL mode, `busy_timeout` |
| **Encapsulation** | All library rules live in `LibraryService`; the UI only calls it |

```
ui (Swing + FlatLaf)  ──►  service.LibraryService  ──►  store (DAOs, JDBC)  ──►  SQLite
                                 │                         └─ LedgerStore ──► ledger.HashChain
                                 ├─ policy.LoanPolicy (Fixed | DemandAdaptive)
                                 └─ search.FuzzySearch, util.Isbn
sim.CirculationSimulator  ─── uses the same LoanPolicy classes to measure them
```

### Project map

```
src/main/java/shelfwise/
  app/       Main (launch), Seeder (demo database), Experiments (every number in the report)
  model/     Member, Student, Faculty, Book, Loan, LoanTerms
  policy/    LoanPolicy, FixedLoanPolicy, DemandAdaptivePolicy
  service/   LibraryService (rules + transactions), LibraryException
  store/     Database (schema), BookDao, MemberDao, LedgerStore, Catalogue (CSV + ISBN repair)
  ledger/    HashChain, LedgerEntry
  search/    FuzzySearch
  util/      Isbn
  sim/       CirculationSimulator
  ui/        MainWindow (4 tabs), Screens (scripted screenshots)
src/test/java/shelfwise/   39 JUnit 5 tests: Isbn (8), Policy (10), Service (13), Ledger & Search (8)
data/books.csv             goodbooks-10k
results/results.json       raw output of every experiment
report/, poster/, figures/ PBL report, poster, charts and screenshots
```

**Tech:** Java 21 · Swing + FlatLaf · JDBC · SQLite · JUnit 5 · JaCoCo · Python (matplotlib) for the charts. About 1,800 lines of Java.

---

## Build and run

Needs **JDK 21+**. Every library is already in `lib/`, so there is no Maven or Gradle step.

```bash
./build.sh run           # launch the app (builds the demo database on first run)
./build.sh test          # 39 JUnit tests + coverage report in out/coverage/
./build.sh experiments   # regenerates every number in results/results.json (~15 s)
./build.sh screens       # drives the real window and saves figures/ui_*.png (see warning above)
python3 scripts/isbn_crosscheck.py && python3 scripts/charts.py   # charts (matplotlib)
```

A ready-to-run download (JAR, libraries, data, and launchers for Windows and macOS/Linux) is on the
[project page](https://shelfwise-site.vercel.app).

---

## Questions the panel may ask

**Why not always use short loans?** Readers of books nobody else wants would be punished for no reason. The policy only acts when someone is waiting, and our data shows uncontested loans are unchanged (15.7 vs 15.6 days).

**Isn't the shorter loan unfair to the borrower?** It's a trade-off, and we measured it: unfinished loans rise from 4.1% to 10.1%, and fines rise too. A reader in a queue waits 5 fewer days on average. The floor of 7 days and the sensitivity analysis are how we keep it reasonable.

**Why not use `synchronized` for the race condition?** `synchronized` only protects threads inside one JVM. Two librarian PCs run two JVMs against one database, so the guarantee has to come from the database itself, which is what the conditional `UPDATE` does.

**Can't an attacker just recompute all the hashes?** Yes, if they rewrite the whole chain from the edited entry onwards. The chain makes tampering *detectable*, not impossible. The fix, listed as future work, is to periodically publish the latest hash somewhere the attacker can't reach (a printed register, an email, or another server). Then a rewritten chain no longer matches the published hash.

**Why SHA-256?** It's in the JDK (`MessageDigest`), it's the standard cryptographic hash, and no practical collisions are known. At about 9 ms per 5,000 entries, it's effectively free.

**Why Damerau–Levenshtein and not plain Levenshtein?** Swapped letters ("teh") are one of the most common typos. Plain Levenshtein counts a swap as 2 edits; Damerau counts it as 1.

**Why SQLite?** It needs no server, is a single file and is ACID with WAL mode, which suits one library's data. The DAO layer and the plain SQL would move to MySQL or PostgreSQL with only a JDBC URL change.

**How do you know the simulation is fair?** Both policies run on the same seeds, so they see the same members, the same requests and the same reading times. Only the policy differs. We report mean ± SD across 5 seeds.

**What does the coverage number mean?** 74.5% line coverage of the core logic (policy, service, ledger, search, ISBN, model). The Swing UI and the simulator driver are tested by running them, not by unit tests.

---

## Limitations and future scope

- **Pilot in a real department library** to replace simulated demand with real data.
- **Off-site hash anchoring**, so the ledger survives a full-chain rewrite.
- **SMS or email alerts** when a held book becomes available.
- Multi-branch support with a server database (PostgreSQL).

---

*Data: [goodbooks-10k](https://github.com/zygmuntz/goodbooks-10k) (`data/books.csv`). Every number in this README comes from `results/results.json`, produced by `./build.sh experiments`.*
