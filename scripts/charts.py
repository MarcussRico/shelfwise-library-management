"""Draws every chart and diagram in the report from results/results.json."""
import json
from pathlib import Path
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

ROOT = Path(__file__).resolve().parent.parent
FIG = ROOT / "figures"
R = json.loads((ROOT / "results" / "results.json").read_text())
INK, ACC, MUT, PRU, F1, F2 = "#1f2a36", "#9a3b2b", "#8a8f98", "#2b4a6f", "#f7f1e6", "#e9eff6"
plt.rcParams.update({"font.family": "serif", "font.size": 10, "axes.spines.top": False, "axes.spines.right": False,
                     "axes.edgecolor": INK, "figure.dpi": 200})

S = R["simulation"]
fx, ad = S["Fixed"], S["Demand-adaptive"]

# 1. wait time + trade-offs
fig, axes = plt.subplots(1, 3, figsize=(7.4, 2.8))
pairs = [("Mean wait for a held title (days)", "mean_wait", 1), ("90th-percentile wait (days)", "p90_wait", 1),
         ("Loans returned unfinished (%)", "unfinished_share", 100)]
for ax, (t, k, m) in zip(axes, pairs):
    v = [fx[k] * m, ad[k] * m]
    b = ax.bar(["Fixed", "Adaptive"], v, color=[MUT, ACC], width=0.6)
    for rect, val in zip(b, v):
        ax.text(rect.get_x() + rect.get_width() / 2, val, f"{val:.1f}", ha="center", va="bottom", fontsize=9)
    ax.set_title(t, fontsize=8.6)
    ax.set_ylim(0, max(v) * 1.25)
fig.tight_layout()
fig.savefig(FIG / "fig_sim.png")
plt.close(fig)

# 2. sensitivity
fig, ax = plt.subplots(figsize=(7.2, 3.0))
for mn, col, ls in [(5, ACC, "-"), (7, PRU, "--"), (10, MUT, ":")]:
    pts = [s for s in S["sensitivity"] if s["min"] == mn]
    ax.plot([p["r"]["unfinished_share"] * 100 for p in pts], [p["r"]["mean_wait"] for p in pts], ls, marker="o",
            color=col, label=f"minimum loan {mn} days")
    for p in pts:
        ax.annotate(f"k={p['k']}", (p["r"]["unfinished_share"] * 100, p["r"]["mean_wait"]), textcoords="offset points",
                    xytext=(4, 4), fontsize=7, color=col)
ax.scatter([fx["unfinished_share"] * 100], [fx["mean_wait"]], color=INK, zorder=5, label="fixed loans (baseline)")
ax.set_xlabel("Loans returned unfinished (%)  — cost to borrowers")
ax.set_ylabel("Mean hold wait (days)")
ax.legend(frameon=False, fontsize=7.5)
fig.tight_layout()
fig.savefig(FIG / "fig_sensitivity.png")
plt.close(fig)

# 3. race
rc = R["race"]
fig, ax = plt.subplots(figsize=(7.2, 2.6))
labels = ["In-memory, check-then-act", "In-memory, compare-and-set", "JDBC, read-then-write", "JDBC, conditional UPDATE"]
vals = [rc["naive_oversold"] / rc["trials"] * 100, rc["safe_oversold"] / rc["trials"] * 100,
        rc["db_naive_oversold"] / rc["db_trials"] * 100, rc["db_safe_oversold"] / rc["db_trials"] * 100]
b = ax.barh(labels, vals, color=[MUT, ACC, MUT, ACC])
for rect, v in zip(b, vals):
    ax.text(v + 1, rect.get_y() + rect.get_height() / 2, f"{v:.1f}%", va="center", fontsize=9)
ax.set_xlim(0, 115)
ax.invert_yaxis()
ax.set_xlabel("Trials in which the last copy was issued more than once (%)")
fig.tight_layout()
fig.savefig(FIG / "fig_race.png")
plt.close(fig)

# 4. search
sr = R["search"]
fig, ax = plt.subplots(figsize=(7.2, 2.6))
groups = ["Clean query, top-1", "One typo, top-1", "One typo, top-5"]
sub = [sr["clean_substring_top1"], sr["substring_top1"], sr["substring_top5"]]
fz = [sr["clean_fuzzy_top1"], sr["fuzzy_top1"], sr["fuzzy_top5"]]
x = range(3)
ax.bar([i - 0.18 for i in x], [v * 100 for v in sub], 0.36, color=MUT, label="Substring match (baseline)")
ax.bar([i + 0.18 for i in x], [v * 100 for v in fz], 0.36, color=ACC, label="Damerau–Levenshtein (ours)")
for i in x:
    ax.text(i - 0.18, sub[i] * 100 + 1, f"{sub[i]*100:.1f}", ha="center", fontsize=8)
    ax.text(i + 0.18, fz[i] * 100 + 1, f"{fz[i]*100:.1f}", ha="center", fontsize=8)
ax.set_xticks(list(x), groups)
ax.set_ylabel("Correct book found (%)")
ax.set_ylim(0, 105)
ax.legend(frameon=False, fontsize=8, loc="upper left")
fig.tight_layout()
fig.savefig(FIG / "fig_search.png")
plt.close(fig)


# 5. architecture
def box(ax, x, y, w, h, t, b="", fc=F1, ec=INK):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.02,rounding_size=0.06", fc=fc, ec=ec, lw=1.1))
    ax.text(x + w / 2, y + h - 0.14, t, ha="center", va="top", fontsize=9, weight="bold", color=INK)
    if b:
        ax.text(x + w / 2, y + h - 0.42, b, ha="center", va="top", fontsize=7.4, color="#3b4552", linespacing=1.3)


def arr(ax, a, b2, c=INK):
    ax.add_patch(FancyArrowPatch(a, b2, arrowstyle="-|>", mutation_scale=10, color=c, lw=1.1))


fig, ax = plt.subplots(figsize=(8.6, 5.2))
ax.set_xlim(0, 10); ax.set_ylim(0, 6.2); ax.axis("off")
box(ax, 0.1, 5.0, 9.8, 1.0, "Presentation — Swing + FlatLaf (ui/)",
    "Circulation desk  ·  Catalogue  ·  Demand board  ·  Audit ledger")
ax.text(5.0, 5.12, "Model: Member (abstract) → Student, Faculty  ·  Book, Loan, LoanTerms (records)  ·  LibraryException hierarchy",
        ha="center", fontsize=7.4, color=ACC)
box(ax, 0.1, 3.0, 4.75, 1.65, "Service layer — LibraryService", "issue / return / hold queue\nborrow limits · fines (java.time)\none JDBC transaction per operation", fc=F2, ec=PRU)
box(ax, 5.15, 3.0, 2.3, 1.65, "LoanPolicy", "interface (strategy)\nFixedLoanPolicy\nDemandAdaptivePolicy", fc=F2, ec=PRU)
box(ax, 7.6, 3.0, 2.3, 1.65, "FuzzySearch", "Damerau–Levenshtein\nword matching\nover title + author", fc=F2, ec=PRU)
box(ax, 0.1, 1.15, 3.1, 1.5, "DAO layer (store/)", "BookDao · MemberDao\nPreparedStatement only\natomic takeCopy()")
box(ax, 3.45, 1.15, 3.1, 1.5, "Audit ledger", "LedgerStore + HashChain\nSHA-256 linked entries\nverify() → first broken #")
box(ax, 6.8, 1.15, 3.1, 1.5, "Catalogue loader", "goodbooks-10k CSV\nISBN-10 checksum repair\n→ ISBN-13")
box(ax, 0.1, 0.05, 9.8, 0.8, "SQLite database (JDBC)", "books · members · loans · holds · ledger  —  CHECK constraints, foreign keys, WAL")
arr(ax, (2.4, 5.0), (2.4, 4.65)); arr(ax, (8.7, 5.0), (8.7, 4.65))
arr(ax, (4.85, 3.8), (5.15, 3.8), ACC)
arr(ax, (1.6, 3.0), (1.6, 2.65)); arr(ax, (4.0, 3.0), (4.6, 2.65))
arr(ax, (1.6, 1.15), (1.6, 0.85)); arr(ax, (5.0, 1.15), (5.0, 0.85)); arr(ax, (8.3, 1.15), (8.3, 0.85))
fig.tight_layout()
fig.savefig(FIG / "fig_architecture.png", bbox_inches="tight")
plt.close(fig)

# 6. hash chain
fig, ax = plt.subplots(figsize=(8.6, 2.4))
ax.set_xlim(0, 10); ax.set_ylim(0, 2.6); ax.axis("off")
for i, (t, p) in enumerate([("#1 ISSUE", "loan=1 book=19\nmember=7 due=…"), ("#2 HOLD", "book=1 member=42\nposition=6"),
                            ("#3 RETURN", "loan=1 late=3\nfine=6"), ("#4 ISSUE", "loan=2 days=7\nwaiting=5")]):
    x = 0.1 + i * 2.5
    box(ax, x, 0.55, 2.1, 1.75, t, p + "\n\nprev = hash(#" + (str(i) if i else "0…0") + ")\nhash = SHA-256(all above)",
        fc=F2 if i == 2 else F1, ec=ACC if i == 2 else INK)
    if i < 3:
        arr(ax, (x + 2.1, 1.4), (x + 2.5, 1.4), ACC)
ax.text(5.0, 0.18, "Edit any field of #3 and its hash no longer matches; delete or reorder it and #4's prev link breaks.",
        ha="center", fontsize=8.2, color=ACC)
fig.tight_layout()
fig.savefig(FIG / "fig_chain.png", bbox_inches="tight")
plt.close(fig)
print("charts done")
