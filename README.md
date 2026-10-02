# SHELFWISE — a demand-aware, tamper-evident library management system in Java

A Project-Based Learning (Java Programming) project, Department of Computer Science and Engineering,
Chennai Institute of Technology, 2026–2027 — **Sai Charan A** (2104251040844) and **Ashfaq Muhammed A** (2104251040096).

A Swing desktop library system over JDBC/SQLite that goes beyond CRUD:

| Feature | Result (from `results/results.json`) |
|---|---|
| **Demand-adaptive loans** — shorter loans only when members are waiting | mean hold wait 17.0 → 11.9 days (−29.9%) in a simulated year; uncontested loans unchanged; cost: unfinished loans 4.1% → 10.1% |
| **Race-free issue** — one conditional `UPDATE … WHERE available > 0` | last copy issued twice in 150/150 trials with read-then-write, 0/150 with ours |
| **SHA-256 hash-chained audit ledger** | 1,000 / 1,000 tampering attempts detected and located |
| **ISBN checksum repair** on import | 6,587 of 10,000 goodbooks-10k ISBNs had lost leading zeros — recovered; 99.8% match the dataset's ISBN-13 |
| **Typo-tolerant search** (Damerau–Levenshtein) | right book first for 75.6% of one-typo queries vs 1.6% for substring |
| **JUnit 5** | 39 / 39 pass; 74.5% line coverage of non-UI code |

OOP design: abstract `Member` → `Student` / `Faculty`, `LoanPolicy` interface (Strategy) with `FixedLoanPolicy` and
`DemandAdaptivePolicy`, records for `Book` / `Loan` / `LoanTerms`, a `LibraryException` hierarchy, DAOs with `PreparedStatement` only.

## Run

Needs JDK 21+. All libraries are in `lib/`.

```bash
./build.sh run           # launch the app (builds a demo database on first run)
./build.sh test          # 39 JUnit tests + coverage report in out/coverage/
./build.sh experiments   # every number in the report → results/results.json (~15 s)
./build.sh screens       # drives the real window and saves figures/ui_*.png
python3 scripts/isbn_crosscheck.py && python3 scripts/charts.py   # charts (matplotlib)
```

Data: [goodbooks-10k](https://github.com/zygmuntz/goodbooks-10k) (`data/books.csv`).
