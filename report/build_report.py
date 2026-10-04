"""Builds the PBL report (.docx) in the Chennai Institute of Technology format.

    python report/build_report.py [pages.json]

pages.json (optional) maps TOC/figure/table keys to printed page numbers; it
is produced by report/paginate.py from the rendered PDF.
"""
from __future__ import annotations

import copy
import json
import sys
from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK, WD_TAB_ALIGNMENT, WD_TAB_LEADER
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Emu, Inches, Pt, RGBColor

ROOT = Path(__file__).resolve().parent.parent
FIG = ROOT / "figures"
ASSET = ROOT / "report" / "assets"
R = json.loads((ROOT / "results" / "results.json").read_text())
PAGES = json.loads(Path(sys.argv[1]).read_text()) if len(sys.argv) > 1 and Path(sys.argv[1]).exists() else {}

TITLE = "SHELFWISE – A Demand-Aware, Tamper-Evident Library Management System in Java"
TITLE_SHORT = TITLE
M1, R1 = "SAI CHARAN A", "2104251040844"
M2, R2 = "ASHFAQ MUHAMMED A", "2104251040096"
m1, m2 = "Sai Charan A", "Ashfaq Muhammed A"
REPO = "https://github.com/MarcussRico/shelfwise-library-management"
FONT = "Times New Roman"
ORANGE = "ED7D31"


def pct(x, d=1):
    return f"{x * 100:.{d}f}%"


CAT, SIM, RACE, LED, SR, TST = R["catalogue"], R["simulation"], R["race"], R["ledger"], R["search"], R["tests"]
FX, AD = SIM["Fixed"], SIM["Demand-adaptive"]
SENS = {(s["min"], s["k"]): s["r"] for s in SIM["sensitivity"]}
TAMP = LED["tamper"]

doc = Document()
st = doc.styles["Normal"]
st.font.name = FONT
st.font.size = Pt(12)
st.element.rPr.rFonts.set(qn("w:eastAsia"), FONT)
st.paragraph_format.space_after = Pt(0)
st.paragraph_format.space_before = Pt(0)

sec = doc.sections[0]
sec.page_height, sec.page_width = Cm(29.7), Cm(21.0)
sec.left_margin = Cm(3.0)
sec.right_margin = Cm(2.2)
sec.top_margin = Cm(2.4)
sec.bottom_margin = Cm(2.2)
sec.header_distance = Cm(0.8)


# ---------------------------------------------------------------- primitives
def para(text="", size=12, bold=False, italic=False, align="justify", before=0, after=6, line=1.5, color=None,
         keep=False):
    p = doc.add_paragraph()
    p.alignment = {"justify": WD_ALIGN_PARAGRAPH.JUSTIFY, "center": WD_ALIGN_PARAGRAPH.CENTER,
                   "left": WD_ALIGN_PARAGRAPH.LEFT, "right": WD_ALIGN_PARAGRAPH.RIGHT}[align]
    pf = p.paragraph_format
    pf.space_before, pf.space_after, pf.line_spacing = Pt(before), Pt(after), line
    if keep:
        pf.keep_with_next = True
    if text:
        rich(p, text, size, bold, italic, color)
    return p


def rich(p, text, size=12, bold=False, italic=False, color=None):
    """**bold** spans inside text are honoured."""
    parts = text.split("**")
    for i, part in enumerate(parts):
        if not part:
            continue
        r = p.add_run(part)
        r.font.size = Pt(size)
        r.font.name = FONT
        r.bold = bold or (i % 2 == 1)
        r.italic = italic
        if color:
            r.font.color.rgb = RGBColor.from_string(color)
    return p


def bullet(text, size=12):
    p = para("", align="justify", after=4, line=1.5)
    pf = p.paragraph_format
    pf.left_indent, pf.first_line_indent = Cm(0.9), Cm(-0.5)
    rich(p, "• " + text, size)
    return p


def heading_chapter(num, title, key):
    para(f"CHAPTER {num}", 16, True, align="center", after=6, keep=True)
    p = para(title.upper(), 16, True, align="center", after=14, keep=True)
    bookmark(key)
    return p


def h2(text, key=None):
    para(text, 14, True, align="left", before=10, after=6, keep=True)
    if key:
        bookmark(key)


def h3(text):
    para(text, 12.5, True, italic=True, align="left", before=6, after=4, keep=True)


def bookmark(key):
    # pagination markers are found by text in the PDF, so nothing to do here
    return key


def page_break():
    doc.add_paragraph().add_run().add_break(WD_BREAK.PAGE)


def picture(path, width_cm, caption=None, key=None):
    p = para("", align="center", after=2, line=1.0, keep=True)
    p.add_run().add_picture(str(path), width=Cm(width_cm))
    if caption:
        para(caption, 11, True, italic=True, align="center", after=10, line=1.15)


def caption(text):
    para(text, 11, True, italic=True, align="center", before=4, after=10, line=1.15)


def set_cell_bg(cell, hexcolor):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hexcolor)
    tcPr.append(shd)


def table(headers, rows, widths_cm, size=10.5, header_bg="D9D9D9", align_center_cols=()):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    for i, h in enumerate(headers):
        c = t.rows[0].cells[i]
        c.text = ""
        rich(c.paragraphs[0], h, size, True)
        c.paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.CENTER
        set_cell_bg(c, header_bg)
    for row in rows:
        cells = t.add_row().cells
        for i, v in enumerate(row):
            cells[i].text = ""
            rich(cells[i].paragraphs[0], str(v), size)
            cells[i].paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.CENTER if i in align_center_cols else WD_ALIGN_PARAGRAPH.LEFT
            cells[i].paragraphs[0].paragraph_format.line_spacing = 1.1
    for row in t.rows:
        for i, w in enumerate(widths_cm):
            row.cells[i].width = Cm(w)
        trPr = row._tr.get_or_add_trPr()
        cant = OxmlElement("w:cantSplit")
        trPr.append(cant)
    fix_widths(t, widths_cm)
    # repeat header row
    trPr = t.rows[0]._tr.get_or_add_trPr()
    th = OxmlElement("w:tblHeader")
    trPr.append(th)
    return t


def fix_widths(t, widths_cm):
    t.autofit = False
    tbl = t._tbl
    tblPr = tbl.tblPr
    for tag in ("w:tblW", "w:tblLayout"):
        for el in tblPr.findall(qn(tag)):
            tblPr.remove(el)
    tw = OxmlElement("w:tblW")
    tw.set(qn("w:w"), str(int(sum(widths_cm) * 567)))
    tw.set(qn("w:type"), "dxa")
    tblPr.append(tw)
    lay = OxmlElement("w:tblLayout")
    lay.set(qn("w:type"), "fixed")
    tblPr.append(lay)
    for gc, w in zip(tbl.tblGrid.findall(qn("w:gridCol")), widths_cm):
        gc.set(qn("w:w"), str(int(w * 567)))
    for row in t.rows:
        for c, w in zip(row.cells, widths_cm):
            c.width = Cm(w)


def code(text, size=8.5):
    t = doc.add_table(rows=1, cols=1)
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    c = t.rows[0].cells[0]
    fix_widths(t, [15.6])
    set_cell_bg(c, "F2F2F2")
    c.text = ""
    p = c.paragraphs[0]
    p.paragraph_format.line_spacing = 1.0
    for i, line in enumerate(text.strip("\n").split("\n")):
        r = p.add_run(line)
        r.font.name = "Courier New"
        r.font.size = Pt(size)
        r._element.rPr.rFonts.set(qn("w:eastAsia"), "Courier New")
        if i < len(text.strip("\n").split("\n")) - 1:
            r.add_break()
    return t


def orange_box(paragraphs, size=13):
    t = doc.add_table(rows=1, cols=1)
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    c = t.rows[0].cells[0]
    fix_widths(t, [15.6])
    tcPr = c._tc.get_or_add_tcPr()
    borders = OxmlElement("w:tcBorders")
    for edge in ("top", "left", "bottom", "right"):
        el = OxmlElement(f"w:{edge}")
        el.set(qn("w:val"), "double")
        el.set(qn("w:sz"), "18")
        el.set(qn("w:space"), "0")
        el.set(qn("w:color"), ORANGE)
        borders.append(el)
    tcPr.append(borders)
    mar = OxmlElement("w:tcMar")
    for edge, v in (("top", 160), ("bottom", 160), ("left", 220), ("right", 220)):
        el = OxmlElement(f"w:{edge}")
        el.set(qn("w:w"), str(v))
        el.set(qn("w:type"), "dxa")
        mar.append(el)
    tcPr.append(mar)
    c.text = ""
    first = True
    for label, text in paragraphs:
        p = c.paragraphs[0] if first else c.add_paragraph()
        first = False
        p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
        p.paragraph_format.line_spacing = 1.6
        p.paragraph_format.space_after = Pt(4)
        if label:
            p.paragraph_format.left_indent = Cm(1.25)
            p.paragraph_format.first_line_indent = Cm(-1.25)
            r = p.add_run(label + ". ")
            r.bold = True
            r.font.size = Pt(size)
            r.font.color.rgb = RGBColor(0xC0, 0x00, 0x00) if label.startswith("IM") else None
        r = p.add_run(text)
        r.font.size = Pt(size)


def toc_line(text, key, bold=False, indent=0.0, size=12):
    p = para("", align="left", after=3, line=1.15)
    pf = p.paragraph_format
    pf.left_indent = Cm(indent)
    pf.tab_stops.add_tab_stop(Cm(15.7), WD_TAB_ALIGNMENT.RIGHT, WD_TAB_LEADER.DOTS)
    rich(p, text, size, bold)
    r = p.add_run("\t" + str(PAGES.get(key, "00")))
    r.font.size = Pt(size)
    r.bold = bold


# ------------------------------------------------- section/header helpers
def new_section(start_type=WD_SECTION.NEW_PAGE):
    s = doc.add_section(start_type)
    return s


def unlink(section):
    for part in (section.header, section.footer, section.first_page_header, section.first_page_footer):
        part.is_linked_to_previous = False


def clear_header(section):
    for part in (section.header, section.footer):
        for p in part.paragraphs:
            for r in list(p.runs):
                r._element.getparent().remove(r._element)


def page_field(paragraph):
    r = paragraph.add_run()
    for kind, txt in (("begin", None), (None, "PAGE"), ("end", None)):
        if kind:
            el = OxmlElement("w:fldChar")
            el.set(qn("w:fldCharType"), kind)
            r._element.append(el)
        else:
            it = OxmlElement("w:instrText")
            it.set(qn("xml:space"), "preserve")
            it.text = txt
            r._element.append(it)
    r.font.size = Pt(12)


def anchor_watermark(run, path, width_cm, x_cm, y_cm):
    """Insert a picture behind the text, absolutely positioned on the page."""
    run.add_picture(str(path), width=Cm(width_cm))
    inline = run._element.xpath(".//wp:inline")[0]
    cx, cy = inline.extent.cx, inline.extent.cy
    graphic = inline.xpath("./a:graphic")[0]
    docPr = inline.xpath("./wp:docPr")[0]
    anchor = OxmlElement("wp:anchor")
    for k, v in dict(distT="0", distB="0", distL="0", distR="0", simplePos="0", relativeHeight="0",
                     behindDoc="1", locked="0", layoutInCell="1", allowOverlap="1").items():
        anchor.set(k, v)
    sp = OxmlElement("wp:simplePos")
    sp.set("x", "0")
    sp.set("y", "0")
    anchor.append(sp)
    for tag, rel, off in (("wp:positionH", "page", x_cm), ("wp:positionV", "page", y_cm)):
        el = OxmlElement(tag)
        el.set("relativeFrom", rel)
        o = OxmlElement("wp:posOffset")
        o.text = str(int(Cm(off)))
        el.append(o)
        anchor.append(el)
    ext = OxmlElement("wp:extent")
    ext.set("cx", str(cx))
    ext.set("cy", str(cy))
    anchor.append(ext)
    ee = OxmlElement("wp:effectExtent")
    for k in ("l", "t", "r", "b"):
        ee.set(k, "0")
    anchor.append(ee)
    anchor.append(OxmlElement("wp:wrapNone"))
    anchor.append(copy.deepcopy(docPr))
    anchor.append(OxmlElement("wp:cNvGraphicFramePr"))
    anchor.append(copy.deepcopy(graphic))
    inline.getparent().replace(inline, anchor)



# ===================================================================== COVER
para(TITLE_SHORT, 18, True, align="center", after=18, line=1.15)
para("A PROJECT BASED LEARNING (PBL) REPORT", 13, True, align="center", after=12, line=1.2)
para("Submitted by", 13, True, True, align="center", after=12, line=1.2)
para(f"{M1} - {R1}", 14, True, align="center", after=8, line=1.2)
para(f"{M2} - {R2}", 14, True, align="center", after=18, line=1.2)
para("Submitted in partial fulfilment of the requirements", 13, True, True, align="center", after=6, line=1.2)
para("for the", 13, True, True, align="center", after=6, line=1.2)
para("Project-Based Learning component of Java Programming", 13, True, True, align="center", after=14, line=1.2)
para("BACHELOR OF ENGINEERING", 15, True, align="center", after=10, line=1.2)
para("in", 11, True, True, align="center", after=10, line=1.2)
para("COMPUTER SCIENCE AND ENGINEERING", 13, True, align="center", after=12, line=1.2)
p = para("", align="center", after=10, line=1.0)
p.add_run().add_picture(str(ASSET / "p1_x71.jpeg"), width=Cm(5.6))
para("CHENNAI INSTITUTE OF TECHNOLOGY, CHENNAI", 13, True, align="center", after=2, line=1.0)
p = para("", align="center", after=6, line=1.0)
p.add_run().add_picture(str(ASSET / "p1_x72.png"), width=Cm(3.2))
para("Affiliated to Anna University, Chennai", 14, True, align="center", after=4, line=1.2)
para("(Autonomous)", 10.5, True, align="center", after=4, line=1.2)
para("OCTOBER 2026", 12, align="center", after=0, line=1.2)

# ======================================= SECTION 2: banner pages with watermark
s2 = new_section()
unlink(s2)
hp = s2.header.paragraphs[0]
hp.alignment = WD_ALIGN_PARAGRAPH.CENTER
hp.add_run().add_picture(str(ASSET / "p2_x106.jpeg"), width=Cm(15.8))
anchor_watermark(s2.header.add_paragraph().add_run(), ASSET / "p2_x105.png", 15.0, 3.0, 12.6)

para("", after=6)
para("Vision of the Institute:", 15, True, align="left", after=10)
orange_box([(None, "To be an eminent centre for Academia, Industry and Research by imparting knowledge, relevant "
                   "practices and inculcating human values to address global challenges through novelty and sustainability.")])
para("", after=14)
para("Mission of the Institute:", 15, True, align="left", after=10)
orange_box([
    ("IM1", "To creates next generation leaders by effective teaching learning methodologies and in still Scientifics Park in them to meet the global challenges."),
    ("IM2", "To transform lives through deployment of emerging technology, novelty and sustainability."),
    ("IM3", "To inculcate human values and ethical principles to cater the societal needs."),
    ("IM4", "To contributes towards the research ecosystem by providing a suitable, effective platform for interaction between industry, academia and R & D establishments."),
    ("IM5", "To nurture incubation centres enabling structured entrepreneurship and start-ups."),
])
page_break()

para("DEPARTMENT OF", 15, True, align="center", after=0)
para("COMPUTER SCIENCE AND ENGINEERING", 15, True, align="center", after=12)
para("Vision of the Department:", 15, True, align="left", after=10)
orange_box([(None, "To Excel in the emerging areas of Computer Science and Engineering by imparting knowledge, relevant "
                   "practices and inculcating human values to transform the students as potential resources to contribute "
                   "innovatively through advanced computing in real time situations.")])
para("", after=16)
para("Mission of the Department:", 15, True, align="left", after=10)
orange_box([
    ("DM1", "To provide strong fundamentals and technical skills for Computer Science applications through effective teaching learning methodologies."),
    ("DM2", "To transform lives of the students by nurturing ethical values, creativity and novelty to become Entrepreneurs and establish start-ups."),
    ("DM3", "To habituate the students to focus on sustainable solutions to improve the quality of life and the welfare of the society."),
])
page_break()

# --- Bonafide
para("", after=4)
para("BONAFIDE CERTIFICATE", 15, True, align="center", after=14)
para(f"This is to certify that the Project–Based Learning report titled **“{TITLE}”** is a Bonafide record of work "
     f"carried out by **{m1} [{R1}]**, **{m2} [{R2}]** of the Department of Computer Science and Engineering, "
     "Chennai Institute of Technology, as part of the continuous, mentor–guided Project-Based Learning (PBL) "
     "component of the Java Programming course during the academic year [2026–2027] under my supervision.",
     12.5, after=60, line=1.7)
sig = doc.add_table(rows=1, cols=2)
sig.alignment = WD_TABLE_ALIGNMENT.CENTER
blocks = [
    [("SIGNATURE", True), ("Dr. S. PAVITHRA, M.E., Ph.D.,", False), ("Professor and Head,", True),
     ("Dept. of Computer Science and Engineering", False), ("Chennai Institute of Technology,", False), ("Chennai – 69.", False)],
    [("SIGNATURE", True), ("Ms. SWATHI L,", False), ("MENTOR", True), ("Assistant Professor", True),
     ("Dept. of Computer Science and Engineering", False), ("Chennai Institute of Technology,", False), ("Chennai – 69.", False)],
]
fix_widths(sig, [7.9, 7.9])
for cell, lines in zip(sig.rows[0].cells, blocks):
    cell.text = ""
    for i, (txt, b) in enumerate(lines):
        p = cell.paragraphs[0] if i == 0 else cell.add_paragraph()
        p.paragraph_format.line_spacing = 1.1
        rich(p, txt, 12.5, b)
para("", after=60)
para("Submitted for the final review held on …………………….", 12.5, align="left", after=50)
p = para("", align="left")
p.paragraph_format.tab_stops.add_tab_stop(Cm(15.8), WD_TAB_ALIGNMENT.RIGHT)
rich(p, "Internal Examiner\tExternal Examiner", 12.5, True)
page_break()

# --- Declaration
para("", after=4)
para("DECLARATION", 13, True, align="center", after=14)
para(f"I/We jointly declare that the PBL report on **“{TITLE}”** is the result of original work done by us and best "
     "of our knowledge, similar work has not been submitted to **“ANNA UNIVERSITY, CHENNAI”** for the requirement "
     "of Degree of **BACHELOR OF ENGINEERING.** This PBL report is submitted on the partial fulfilment of the "
     "requirement of the award of Degree of **COMPUTER SCIENCE AND ENGINEERING.**", 12.5, after=90, line=1.7)
para("Signature", 13, True, align="right", after=40)
para(m1, 12.5, align="right", after=40)
para(m2, 12.5, align="right", after=30)
para("Place: Chennai", 13, align="left", after=6)
para("Date: 02.10.2026", 13, align="left")
page_break()

# --- Acknowledgement
para("", after=4)
para("ACKNOWLEDGEMENT", 14, True, align="center", after=14)
for t in [
    "We wish to express our sincere gratitude to our honourable Chairman **SHRI. P. SRIRAM** for providing immense facilities at our institution.",
    "We are very proud to render our thanks to our Principal **Dr. A. RAMESH M.E., Ph.D.,** for the facilities and the encouragement given by him toward the progress and completion of our project.",
    "We would like to express special thanks and gratitude to our Dean **Dr. V. SRINIVASA RAO M.E., Ph.D.,** who has been a key source of motivation to us throughout the completion of our course and project work.",
    "We proudly render our immense gratitude to the Head of the Department **Dr. S. PAVITHRA M.E., Ph.D.,** for her effective leadership, encouragement and guidance throughout the project.",
    "We would like to extend our thanks to the Project Co-ordinator **R. POORNIMA LAKSHMI, M.E., Assistant Professor,** Department of Computer Science and Engineering, for their valuable suggestions throughout this project.",
    "We wish to acknowledge the help received from our class advisors **Dr. G. IRIN LORETTA, M.E., Assistant Professor,** and **S. E. NEELA KANDAN, M.Tech., Assistant Professor,** of the Department of Computer Science and Engineering and others for providing valuable suggestions and for the successful completion of the project.",
]:
    para(t, 12, after=10, line=1.5)
para("", after=40)
para(f"{m1} ({R1})", 13, align="right", after=4)
para(f"{m2} ({R2})", 13, align="right")

# ======================================= SECTION 3: abstract & lists, no header
s3 = new_section()          # header (banner + watermark) carries over from section 2

WAIT_CUT = 1 - AD["mean_wait"] / FX["mean_wait"]
P90_CUT = 1 - AD["p90_wait"] / FX["p90_wait"]
REQ_UP = AD["requests"] / FX["requests"] - 1
REPAIR = CAT["repaired_leading_zeros"] / CAT["rows"]
NAIVE = RACE["naive_oversold"] / RACE["trials"]
DBNAIVE = RACE["db_naive_oversold"] / RACE["db_trials"]
TAMPER_N = sum(v["trials"] for v in TAMP.values())
TAMPER_OK = sum(v["located"] for v in TAMP.values())

para("ABSTRACT", 14, True, align="center", after=10)
para(
    "Most library management systems treat every book the same way: a fixed loan period, a hold queue that "
    "moves only as fast as the slowest borrower, a search box that fails on a single typing mistake, and a "
    "transaction history that anyone with database access can quietly edit. SHELFWISE is a Java desktop "
    "library management system built to address those four weaknesses and to measure each improvement. It "
    "is written in Java 21 with Swing for the interface and JDBC over an SQLite database, structured in "
    "presentation, service and data-access layers with an abstract Member hierarchy, a LoanPolicy strategy "
    "interface and a custom exception hierarchy. The catalogue is loaded from the public goodbooks-10k "
    f"dataset; ISBN checksum validation found that {pct(REPAIR)} of its 10,000 ISBNs had lost their leading "
    "zeros and repaired them automatically. A demand-adaptive loan policy shortens the loan only when other "
    "members are waiting; in a year-long, five-seed simulation of 400 members borrowing in proportion to real "
    f"Goodreads popularity it cut the mean wait for a held title from {FX['mean_wait']:.1f} to "
    f"{AD['mean_wait']:.1f} days ({pct(WAIT_CUT)}) while leaving loans of uncontested books unchanged, at the "
    "cost of more books returned unfinished. Issuing a book uses a single conditional SQL update, which "
    f"prevented the double issue of a last copy that a read-then-write implementation produced in "
    f"{pct(DBNAIVE, 0)} of concurrent trials. Every issue, return and hold is written to a SHA-256 hash-chained "
    f"audit ledger that detected and located all {TAMPER_N:,} simulated tampering attempts. A Damerau–Levenshtein "
    f"search found the intended book for {pct(SR['fuzzy_top1'])} of single-typo queries, against "
    f"{pct(SR['substring_top1'])} for substring matching. All {TST['passed']} JUnit tests pass, with "
    f"{pct(TST['coverage_core_line'])} line coverage of the non-interface code.", 12, after=10, line=1.5)
para("**Keywords:** Library management system, Java, JDBC, Object-oriented design, Loan policy, Concurrency, "
     "Hash chain, Approximate string matching.", 12, align="left", line=1.3)
page_break()

para("TABLE OF CONTENTS", 14, True, align="center", after=12)
TOC = [
    ("CHAPTER 1: INTRODUCTION", "ch1", True, 0),
    ("1.1 Background", "s1.1", False, 0.3), ("1.2 Driving Question", "s1.2", False, 0.3),
    ("1.3 Objectives", "s1.3", False, 0.3), ("1.4 Scope and Limitations", "s1.4", False, 0.3),
    ("1.5 Novelty and Contributions", "s1.5", False, 0.3),
    ("CHAPTER 2: CONCEPT EXPLORATION", "ch2", True, 0),
    ("2.1 Related Approaches", "s2.1", False, 0.3), ("2.2 Summary Table", "s2.2", False, 0.3),
    ("2.3 What This Told Us", "s2.3", False, 0.3),
    ("CHAPTER 3: PROJECT PLANNING AND TEAM ORGANISATION", "ch3", True, 0),
    ("3.1 Weekly PBL Progress Log", "s3.1", False, 0.3), ("3.2 Requirements", "s3.2", False, 0.3),
    ("3.3 Feasibility", "s3.3", False, 0.3),
    ("CHAPTER 4: ITERATIVE DESIGN AND DEVELOPMENT", "ch4", True, 0),
    ("4.1 System Architecture", "s4.1", False, 0.3), ("4.2 Iteration 1 — Baseline", "s4.2", False, 0.3),
    ("4.3 Iteration 2 — Refinement", "s4.3", False, 0.3), ("4.4 Final Approach", "s4.4", False, 0.3),
    ("4.5 Testing and Execution", "s4.5", False, 0.3),
    ("CHAPTER 5: IMPLEMENTATION", "ch5", True, 0),
    ("5.1 Module Description", "s5.1", False, 0.3), ("5.2 Key Code Snippets", "s5.2", False, 0.3),
    ("5.3 User Interface / Demo", "s5.3", False, 0.3),
    ("CHAPTER 6: RESULTS AND DISCUSSION", "ch6", True, 0),
    ("6.1 Evaluation Metrics", "s6.1", False, 0.3), ("6.2 Results Across Iterations", "s6.2", False, 0.3),
    ("6.3 Discussion", "s6.3", False, 0.3), ("6.4 Limitations", "s6.4", False, 0.3),
    ("CHAPTER 7: TEAM REFLECTION AND LEARNING OUTCOMES", "ch7", True, 0),
    ("7.1 Individual Reflections", "s7.1", False, 0.3), ("7.2 Team Learning", "s7.2", False, 0.3),
    ("7.3 Course Outcomes — Evidence Summary", "s7.3", False, 0.3),
    ("CHAPTER 8: CONCLUSION AND FUTURE SCOPE", "ch8", True, 0),
    ("8.1 Conclusion", "s8.1", False, 0.3), ("8.2 Future Scope", "s8.2", False, 0.3),
    ("REFERENCES", "refs", True, 0),
    ("APPENDIX", "app", True, 0),
    ("A.1 Full Source Code", "a1", False, 0.3), ("A.2 Complete Weekly PBL Log", "a2", False, 0.3),
    ("A.3 Self and Peer Assessment", "a3", False, 0.3), ("A.4 PBL Poster", "a4", False, 0.3),
]
for text, key, b, ind in TOC:
    toc_line(text, key, b, ind, 11.5 if not b else 12)
page_break()

FIGS = [
    ("Figure 4.1", "Layered Architecture of SHELFWISE", "f4.1"),
    ("Figure 4.2", "Hash-Chained Audit Ledger", "f4.2"),
    ("Figure 5.1", "Circulation Desk: Hold Placed for a Busy Title", "f5.1"),
    ("Figure 5.2", "Circulation Desk: Loan Shortened Because Members Are Waiting", "f5.2"),
    ("Figure 5.3", "Catalogue: Typo-Tolerant Search", "f5.3"),
    ("Figure 5.4", "Demand Board", "f5.4"),
    ("Figure 5.5", "Audit Ledger: Chain Verified", "f5.5"),
    ("Figure 5.6", "Audit Ledger: Tampering Detected and Located", "f5.6"),
    ("Figure 6.1", "Fixed vs Demand-Adaptive Loans (Simulated Year)", "f6.1"),
    ("Figure 6.2", "Wait Time Against Cost to Borrowers for Nine Policy Settings", "f6.2"),
    ("Figure 6.3", "Double Issue of the Last Copy Under Concurrent Requests", "f6.3"),
    ("Figure 6.4", "Search Accuracy With and Without Typing Mistakes", "f6.4"),
]
TABS = [
    ("Table 2.1", "Summary of Related Approaches", "t2.1"),
    ("Table 3.1", "Weekly PBL Progress Log", "t3.1"),
    ("Table 3.2", "Hardware and Software Requirements", "t3.2"),
    ("Table 4.1", "Configuration and Test Settings", "t4.1"),
    ("Table 6.1", "Results Across Iterations", "t6.1"),
    ("Table 6.2", "Simulated Year of Circulation, Fixed vs Adaptive", "t6.2"),
    ("Table 6.3", "Tamper Detection by Attack Type", "t6.3"),
    ("Table 6.4", "JUnit Tests and Coverage by Package", "t6.4"),
]
para("LIST OF FIGURES", 14, True, align="center", after=12)
for a, b, k in FIGS:
    toc_line(f"{a}  {b}", k, False, 0, 11.5)
page_break()
para("LIST OF TABLES", 14, True, align="center", after=12)
for a, b, k in TABS:
    toc_line(f"{a}  {b}", k, False, 0, 11.5)
page_break()

para("LIST OF ABBREVIATIONS", 14, True, align="center", after=14)
table(["Abbreviation", "Full Form"], [
    ("LMS", "Library Management System"), ("PBL", "Project-Based Learning"), ("OOP", "Object-Oriented Programming"),
    ("JDBC", "Java Database Connectivity"), ("DAO", "Data Access Object"), ("SQL", "Structured Query Language"),
    ("GUI", "Graphical User Interface"), ("JVM", "Java Virtual Machine"), ("JDK", "Java Development Kit"),
    ("ISBN", "International Standard Book Number"), ("CSV", "Comma-Separated Values"),
    ("SHA-256", "Secure Hash Algorithm, 256-bit"), ("CAS", "Compare-And-Set"), ("WAL", "Write-Ahead Logging"),
    ("CRUD", "Create, Read, Update, Delete"), ("ILS", "Integrated Library System"),
], [3.6, 11.4], size=11)
page_break()

para("TEAM ROLES AND RESPONSIBILITIES", 14, True, align="center", after=12)
para("Both members jointly owned problem framing, the weekly mentor-review cycle, the literature exploration in "
     "Chapter 2 and the final report. The division below reflects primary ownership of the build; both members "
     "reviewed and tested the full system before each milestone.", 12, after=14)
table(["Team Member", "Primary Role", "Key Responsibilities"], [
    (m1, "Project Lead — Core Logic, Database & Evaluation",
     "Class design (Member hierarchy, records, exception hierarchy); LibraryService with JDBC transactions; "
     "LoanPolicy interface and the demand-adaptive policy; atomic issue and the concurrency benchmark; the "
     "SHA-256 audit ledger; the circulation simulator and every experiment in Chapter 6."),
    (m2, "Interface, Search & Testing",
     "Swing interface with FlatLaf (circulation desk, catalogue, demand board, audit ledger); Damerau–Levenshtein "
     "search; catalogue loader with ISBN validation and repair; the JUnit test suite and coverage run; screenshots."),
], [3.4, 4.0, 8.4], size=11)

# ======================================= SECTION 4: main body with page numbers
s4 = new_section()          # same banner + watermark; page numbers bottom centre from 1
s4.footer.is_linked_to_previous = False
pgNumType = OxmlElement("w:pgNumType")
pgNumType.set(qn("w:start"), "1")
s4._sectPr.append(pgNumType)
fp = s4.footer.paragraphs[0]
fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
page_field(fp)

# ---------------------------------------------------------------- CHAPTER 1
heading_chapter(1, "Introduction", "ch1")
h2("1.1 Background", "s1.1")
para("A college or department library runs on a handful of rules: who may borrow how many books, for how long, "
     "what happens when someone else is waiting, and what a late return costs. Paper registers enforce these rules "
     "inconsistently, and most software replacements simply copy the register into a database. They keep the same "
     "fixed loan period for every book, so a best-seller with five people waiting is lent for exactly as long as a "
     "book nobody has asked for in years.")
para("Three further problems are easy to overlook until they cause real harm. When two librarians issue the last "
     "copy of a book at the same moment, a system that checks availability and then updates it in two separate steps "
     "can lend the same copy twice. Imported catalogue data is often damaged; spreadsheets, for example, silently "
     "drop the leading zero of an ISBN. And a fines and loans history kept in an ordinary table can be edited by "
     "anyone with database access without leaving a trace.")
para("Java suits this problem well. The domain maps onto classes and records, the rules map onto methods that can "
     "be unit-tested, JDBC gives direct control over transactions, and Swing delivers a desktop interface that runs "
     "on the library’s own machine. SHELFWISE uses all of these, and each core Java concept in the course — "
     "inheritance, interfaces, polymorphism, exceptions, collections, concurrency, the java.time API and JDBC — "
     "has a concrete job in it.")
h2("1.2 Driving Question", "s1.2")
para("The question the team set out to answer was: **“Can a Java library management system do more than "
     "digitise the register — can it make popular books reach readers sooner, stay correct when two librarians "
     "act at once, and prove that its own records have not been altered?”**")
para("This narrowed into a buildable task: model the library’s rules as classes; implement them over JDBC with "
     "transactional, concurrency-safe operations; replace the fixed loan period with a policy that responds to the "
     "hold queue; record every transaction in a tamper-evident ledger; and measure each of these against the "
     "conventional approach.")
h2("1.3 Objectives", "s1.3")
for t in [
    "To design the library domain using object-oriented principles: an abstract Member class with Student and "
    "Faculty subclasses, immutable records for books and loans, a LoanPolicy interface, and a custom exception "
    "hierarchy for every rule violation.",
    "To implement persistent storage with JDBC and PreparedStatement over SQLite, with one transaction per "
    "operation and an issue operation that cannot lend the same copy twice under concurrent requests.",
    "To design a demand-adaptive loan policy and evaluate it against fixed loans in a simulated year of "
    "circulation driven by real book-popularity data.",
    "To record every issue, return and hold in a SHA-256 hash-chained ledger that detects and locates any "
    "later alteration.",
    "To build a Swing interface with typo-tolerant search, validate catalogue ISBNs by checksum, and test the "
    "system with a JUnit suite measured by code coverage.",
    "To document weekly progress and mentor feedback across the PBL cycle and reflect on the team’s division of work.",
]:
    bullet(t)
h2("1.4 Scope and Limitations", "s1.4")
para("**In scope.** A single-library desktop application: catalogue of 1,000 titles (1,250 copies), 300 members, "
     "issue and return with automatic due dates and fines, a first-come-first-served hold queue, typo-tolerant search, "
     "a demand board of the most-wanted titles, and an audit ledger with one-click verification.")
para("**Out of scope.** There is no multi-branch support, web or mobile client, barcode or RFID hardware, e-mail or "
     "SMS notification, or online payment; fines are recorded in rupees and settled at the desk. The loan-policy "
     "results come from simulation, not a live library, and the ledger detects tampering but cannot prevent someone "
     "with full database access from deleting the entire database. These limits are discussed in Section 6.4.")
h2("1.5 Novelty and Contributions", "s1.5")
para("The project goes beyond a conventional library CRUD application in five measurable ways:")
for t in [
    f"**Demand-adaptive loans.** The loan period shortens only when members are waiting — in simulation, the mean "
    f"wait for a held title fell by {pct(WAIT_CUT)} and {pct(REQ_UP)} more requests were served, with loans of "
    "uncontested books unchanged.",
    f"**Race-free issue.** A single conditional SQL update makes double issue of the last copy impossible; the "
    f"read-then-write version double-issued in {pct(DBNAIVE, 0)} of concurrent trials.",
    f"**Tamper-evident ledger.** Every transaction is SHA-256 hash-chained; all {TAMPER_N:,} simulated edits, "
    "deletions and reorderings were detected and located to the exact entry.",
    f"**Data repair on import.** ISBN-10 checksum validation recovered {CAT['repaired_leading_zeros']:,} of 10,000 "
    "ISBNs whose leading zeros had been lost, instead of rejecting two-thirds of the catalogue.",
    f"**Typo-tolerant search.** Damerau–Levenshtein matching found the right book for {pct(SR['fuzzy_top1'])} of "
    f"single-typo queries against {pct(SR['substring_top1'])} for substring search, with no loss on clean queries.",
]:
    bullet(t)
page_break()

# ---------------------------------------------------------------- CHAPTER 2
heading_chapter(2, "Concept Exploration", "ch2")
h2("2.1 Related Approaches", "s2.1")
h3("2.1.1 Conventional library systems and loan rules")
para("Open-source integrated library systems such as Koha define circulation through a matrix of patron category "
     "and item type: each cell fixes a loan length, a renewal count and a fine rate. The approach is simple and "
     "predictable but blind to demand — a title with a long hold queue keeps the same loan period as one nobody "
     "wants. Most student projects follow the same model with a single fixed period.")
h3("2.1.2 Loan period and book availability")
para("Operations research on libraries addressed this decades ago. Morse [8] modelled circulation as a queueing "
     "system and showed that the probability a wanted book is on the shelf depends on both demand and loan length. "
     "Buckland [9] studied book availability at a university library and reported that matching loan periods to "
     "demand — shorter loans for heavily requested titles — raised the chance that a reader found the book they "
     "wanted. These findings are rarely carried into software, because fixed rules are easier to implement.")
h3("2.1.3 Correctness under concurrency")
para("Goetz et al. [3] describe check-then-act as the classic race condition: a thread checks a condition, and "
     "before it acts another thread changes it. Issuing the last copy is exactly this pattern. Database systems "
     "offer atomic single-statement updates and transactions for this purpose; SQLite documents how each statement "
     "commits atomically [13]. Bloch [1] recommends try-with-resources and immutable value classes to keep such "
     "code correct.")
h3("2.1.4 Tamper-evident records")
para("Haber and Stornetta [4] proposed linking documents by cryptographic hashes so that altering any one breaks "
     "every later link — the idea behind today’s blockchains. Schneier and Kelsey [5] applied hash chains to audit "
     "logs so that an attacker who gains access cannot modify past entries undetected. A library’s fines and loans "
     "history is a natural fit for this technique, which needs no network or third party.")
h3("2.1.5 Approximate string matching")
para("Damerau [6] found that over 80% of misspellings are a single insertion, deletion, substitution or "
     "transposition of adjacent letters, and Levenshtein [7] defined the edit distance that counts such operations. "
     "Matching search words by Damerau–Levenshtein distance therefore tolerates most real typing mistakes.")
h2("2.2 Summary Table", "s2.2")
table(["Ref.", "Approach", "Context", "Reported Result / Relevance"], [
    ("[8]", "Queueing model of circulation", "Library operations research", "Shelf availability depends on loan length and demand"),
    ("[9]", "Demand-matched loan periods", "University library study", "Shorter loans for popular titles raised availability"),
    ("[3]", "Check-then-act analysis", "Java concurrency", "Separate check and update allows double action"),
    ("[4]", "Hash-linked time-stamping", "Digital documents", "Altering one record breaks all later links"),
    ("[5]", "Secure audit logs", "Computer forensics", "Past log entries cannot be changed undetected"),
    ("[6]", "Single-error spelling correction", "Misspelling analysis", "Over 80% of errors are one edit"),
], [1.2, 4.4, 4.0, 6.2], size=10.5)
caption("Table 2.1 — Summary of Related Approaches")
h2("2.3 What This Told Us", "s2.3")
para("The exploration gave the project its shape. From [8] and [9] came the central idea: the loan period should "
     "respond to the hold queue, and the change should be tested against fixed loans rather than assumed to help. "
     "From [3] and [13] came the rule that the availability check and the decrement must be one atomic statement. "
     "From [4] and [5] came the audit ledger, chosen because it adds integrity without any server or network. And "
     "from [6] came the decision to match search words by Damerau–Levenshtein distance. We stayed with core Java, "
     "JDBC and Swing rather than a framework such as Spring and Hibernate, because the course assesses "
     "object-oriented design and database connectivity, which a framework would hide.")
page_break()

# ---------------------------------------------------------------- CHAPTER 3
heading_chapter(3, "Project Planning and Team Organisation", "ch3")
h2("3.1 Weekly PBL Progress Log", "s3.1")
para("The project ran across the twelve-week PBL cycle from the Zeroth Review on 27 June 2026, where the team "
     "presented the problem statement, to the final testing and report. The table summarises milestones, work "
     "completed and the mentor feedback that shaped each iteration.")
LOG = [
    ("1–2", "Problem framing (Zeroth Review, 27 June 2026)",
     "Presented the problem statement: a library system for a college library. Listed the library’s rules (limits, "
     "loan length, holds, fines) and studied existing systems.",
     "Problem approved; asked the team to go beyond basic CRUD and identify a problem real libraries face."),
    ("3–4", "Concept exploration, design",
     "Reviewed loan-period research, concurrency and audit-log literature (Chapter 2). Designed the schema, the "
     "Member hierarchy and the layered architecture.",
     "Asked for the lending rules to live in one service layer, not in the interface."),
    ("5–6", "Iteration 1 — baseline",
     "Implemented the conventional approach: fixed loans, substring search, check-then-act issue, strict ISBN "
     "parsing. Loaded the goodbooks-10k catalogue.",
     "Asked why two-thirds of the catalogue was rejected on import; suggested checking the data, not the code."),
    ("7–8", "Iteration 2 — refinement",
     "Found the lost leading zeros and added ISBN checksum repair; replaced check-then-act with a conditional SQL "
     "update; added transactions, custom exceptions and Damerau–Levenshtein search; started the JUnit suite.",
     "Asked for proof that the issue operation is now safe — a test, not an argument."),
    ("9–10", "Final approach",
     "Added the LoanPolicy interface and demand-adaptive policy, the circulation simulator, the SHA-256 audit "
     "ledger and the Swing interface.",
     "Asked the team to report the cost of shorter loans honestly, not only the benefit."),
    ("11–12", "Testing, evaluation, report",
     "Ran the concurrency, tamper, search and simulation experiments; completed 39 JUnit tests with coverage; "
     "captured the interface; compiled this report.",
     "Final system and evaluation accepted for the concluding review."),
]
table(["Week", "Milestone / Task", "Work Done", "Mentor Remarks"], LOG, [1.4, 3.4, 6.3, 4.7], size=10)
caption("Table 3.1 — Weekly PBL Progress Log")
h2("3.2 Requirements", "s3.2")
para(f"**Data.** The catalogue comes from goodbooks-10k [11], a public dataset of the 10,000 most-rated books on "
     f"Goodreads with title, authors, year, ISBN-10 and rating count. After ISBN validation and repair, the "
     f"{CAT['kept']:,} most popular titles were kept, with 3 copies each for the top 50, 2 for the next 150 and 1 "
     f"for the rest ({CAT['copies']:,} copies in all). The rating count drives simulated demand. The demo database "
     "adds 300 members (270 students, 30 faculty) and 60 days of activity generated through the real service layer.")
table(["Category", "Requirement"], [
    ("Processor / RAM", "Any dual-core CPU / 4 GB RAM (developed on an Apple M-series laptop)"),
    ("Programming language", "Java 21 (LTS) — records, switch expressions, text blocks"),
    ("Libraries", "Java SE (Swing, JDBC, java.time, java.security, java.util.concurrent); sqlite-jdbc 3.46; "
                  "FlatLaf 3.5 (look and feel); JUnit 5.11; JaCoCo 0.8.12 (coverage)"),
    ("Database", "SQLite (embedded, file-based) through JDBC — no server to install"),
    ("Build and run", "javac / java with a build script; any IDE (IntelliJ IDEA, VS Code, Eclipse)"),
    ("Version control", "Git and GitHub (repository in Appendix A.1)"),
], [4.4, 11.4], size=11)
caption("Table 3.2 — Hardware and Software Requirements")
h2("3.3 Feasibility", "s3.3")
para("Everything runs on an ordinary laptop with no server: SQLite is embedded, the JDK and libraries are free, "
     f"and the full experiment suite — five simulated years per policy across eleven policy settings, "
     f"{RACE['trials']:,} in-memory and {RACE['db_trials']} database race trials, {TAMPER_N:,} tamper trials and "
     f"{SR['queries']} search queries — completes in about fifteen seconds. The work divided cleanly between core "
     "logic and interface, with the service layer’s method signatures agreed early so both members could build in "
     "parallel.")
page_break()

# ---------------------------------------------------------------- CHAPTER 4
heading_chapter(4, "Iterative Design and Development", "ch4")
h2("4.1 System Architecture", "s4.1")
para("SHELFWISE is organised in layers so that a change in one does not ripple through the others (Figure 4.1). "
     "The Swing presentation layer only collects input and shows results. The service layer, LibraryService, holds "
     "every rule and runs each operation as one JDBC transaction. It delegates the loan length to a LoanPolicy and "
     "writes each event to the audit ledger in the same transaction. The DAO layer performs all SQL through "
     "PreparedStatement, and the SQLite database enforces integrity with CHECK constraints and foreign keys.")
picture(FIG / "fig_architecture.png", 15.6)
caption("Figure 4.1 — Layered Architecture of SHELFWISE")
para("A request flows down: the screen collects a member and a title, the service checks the borrowing limit and "
     "the hold queue, the DAO takes a copy atomically, the policy sets the due date, and the ledger records the "
     "event. Results and LibraryException messages flow back up, so the librarian sees “Karthik P already has 3 "
     "books, the most allowed” rather than an SQL error.")
h2("4.2 Iteration 1 — Baseline", "s4.2")
para("The first version followed the textbook design that most library projects use, and each part of it is kept "
     "in the code as a measurable baseline:")
for t in [
    "**Fixed loans** (FixedLoanPolicy): 14 days for students and 28 for faculty, renewable once, regardless of demand.",
    "**Substring search**: a title or author matches only if it contains the query exactly.",
    "**Check-then-act issue**: read the available count, and if it is above zero, write back count − 1.",
    "**Strict ISBN parsing**: an ISBN that is not ten valid characters is rejected.",
]:
    bullet(t)
para("It worked for the simple path, and the problems appeared as soon as real data and real use were applied. "
     f"Strict parsing rejected {CAT['repaired_leading_zeros'] + CAT['rejected']:,} of the 10,000 catalogue rows. "
     f"Substring search found the right book for only {pct(SR['substring_top1'])} of queries with one typing "
     f"mistake. Under concurrent requests, check-then-act issued the last copy more than once in "
     f"{pct(NAIVE)} of in-memory trials and {pct(DBNAIVE, 0)} of database trials. And in simulation, fixed loans "
     f"left members waiting {FX['mean_wait']:.1f} days on average for a held title.")
h2("4.3 Iteration 2 — Refinement", "s4.3")
para("Iteration 2 fixed the correctness problems. Investigating the rejected rows showed that most ISBNs were nine "
     "digits or fewer: the leading zeros had been dropped when the data passed through a spreadsheet. The loader now "
     "left-pads a short ISBN and accepts it only if the ISBN-10 checksum then validates, which recovered "
     f"{CAT['repaired_leading_zeros']:,} ISBNs ({pct(REPAIR)}). As an independent check, "
     f"{CAT['crosscheck']['agree']:,} of the repaired ISBNs ({pct(CAT['crosscheck']['agree'] / CAT['repaired_leading_zeros'])}) "
     f"match the dataset’s own ISBN-13 column; the {CAT['crosscheck']['disagree']} that do not point to a different "
     f"edition in that column. Only {CAT['rejected']} rows remained rejected, and every accepted ISBN is converted to ISBN-13.")
para("Issue was rewritten as a single conditional statement — UPDATE books SET available = available − 1 WHERE "
     "id = ? AND available > 0 — and the service now treats “one row changed” as success. The availability check and "
     "the decrement can no longer be separated. Every operation became a transaction with rollback on any exception; "
     "rule violations became typed exceptions (LimitReached, AlreadyHeld, NotOnLoan, InvalidInput, NotFound); and "
     "search moved to Damerau–Levenshtein word matching. The JUnit suite was started in this iteration.")
h2("4.4 Final Approach", "s4.4")
para("**Demand-adaptive loans.** LoanPolicy is an interface with one method, termsFor(member, holdsWaiting, "
     "issueDate), returning a LoanTerms record of due date, length, renewability and a plain-language reason. "
     "FixedLoanPolicy and DemandAdaptivePolicy are interchangeable implementations — the Strategy pattern [2] — so "
     "the service, the simulator and the tests use either without change. The adaptive rule is:")
para("days = max(minDays, round(base / (1 + k × holdsWaiting)))", 12, italic=True, align="center", after=6)
para("with base = 14 days for students and 28 for faculty, minDays = 7 and k = 0.5. With nobody waiting the loan is "
     "exactly the standard one; with two waiting a student gets 7 days. A shortened loan cannot be renewed, and the "
     "reason is shown to the librarian and stored with the loan.")
para("**Hash-chained audit ledger.** Every issue, return and hold appends an entry whose SHA-256 hash covers its "
     "sequence number, date, type, details and the previous entry’s hash (Figure 4.2). The entry is written inside "
     "the same transaction as the change it records, so the two always commit together. Verification recomputes "
     "every hash and reports the first entry that fails.")
picture(FIG / "fig_chain.png", 15.6)
caption("Figure 4.2 — Hash-Chained Audit Ledger")
para("**Interface.** A Swing window with a FlatLaf look and feel offers four tabs: a circulation desk with live "
     "typo-tolerant search and an explanation of every due date, the catalogue, a demand board of titles with "
     "waiting members and their next loan length, and the audit ledger with a Verify button.")
h2("4.5 Testing and Execution", "s4.5")
para("Testing combined unit tests with four experiments, each designed to measure one claim:")
table(["Component", "Configuration"], [
    ("JUnit 5 suite", f"{TST['total']} tests in 4 classes: ISBN (8), loan policy and model (10), service with a real "
                      "SQLite database (13), ledger and search (8); coverage by JaCoCo"),
    ("Circulation simulation", "365 days after a 30-day warm-up; 360 students + 40 faculty; Poisson(45) requests/day; "
                               "titles chosen in proportion to Goodreads rating count; reading time log-normal "
                               "(median 10 days, σ 0.6); 25% of overrunning borrowers return late (mean 3 days); "
                               "renew once if nobody is waiting; seeds 1–5"),
    ("Policy sensitivity", "minDays ∈ {5, 7, 10} × k ∈ {0.25, 0.5, 1.0}, same five seeds"),
    ("Concurrency", f"{RACE['trials']:,} trials × {RACE['threads']} threads (in-memory); {RACE['db_trials']} trials × 8 "
                    "threads on SQLite; one copy, all threads released by a CountDownLatch"),
    ("Tamper detection", f"{LED['entries']:,}-entry ledger; {TAMPER_N:,} trials: edit a fine, change a date, delete an "
                         "entry, swap two entries (250 each)"),
    ("Search", f"{SR['queries']} queries of 1–3 consecutive title words, each with one random typing mistake "
               "(substitution, deletion, insertion or transposition); seed 11"),
], [4.0, 11.8], size=10.5)
caption("Table 4.1 — Configuration and Test Settings")
page_break()

# ---------------------------------------------------------------- CHAPTER 5
heading_chapter(5, "Implementation", "ch5")
h2("5.1 Module Description", "s5.1")
for t in [
    "**model/** — Member (abstract) with Student and Faculty overriding borrowLimit() and baseLoanDays(); Book, "
    "Loan and LoanTerms as Java records with validation in compact constructors.",
    "**policy/** — the LoanPolicy interface, FixedLoanPolicy and DemandAdaptivePolicy.",
    "**service/** — LibraryService (issue, return, hold queue, fines with java.time.ChronoUnit, queries) and the "
    "LibraryException hierarchy.",
    "**store/** — Database (connection and schema), BookDao, MemberDao, LedgerStore, and Catalogue (CSV loader "
    "with ISBN repair).",
    "**ledger/** — HashChain (SHA-256 hashing and verification) and the LedgerEntry record.",
    "**search/** — FuzzySearch (Damerau–Levenshtein word matching) and the substring baseline.",
    "**util/** — Isbn: ISBN-10 and ISBN-13 checksums and conversion.",
    "**sim/** — CirculationSimulator, a day-by-day discrete-event model of a year of borrowing.",
    "**ui/** — MainWindow (four Swing tabs) and Screens, which drives the window by its real buttons to capture "
    "the figures in this chapter. **app/** — Main, Seeder (demo database) and Experiments (every result in Chapter 6).",
]:
    bullet(t)
h2("5.2 Key Code Snippets", "s5.2")
para("Polymorphism in the domain: each member type decides its own limits, and every rule in the system asks the "
     "object rather than checking its type.")
code('''public abstract class Member {
    public abstract int borrowLimit();       // how many books at once
    public abstract int baseLoanDays();      // loan length when nobody is waiting
}
public class Student extends Member {
    @Override public int borrowLimit()  { return 3; }
    @Override public int baseLoanDays() { return 14; }
}
public class Faculty extends Member {
    @Override public int borrowLimit()  { return 6; }
    @Override public int baseLoanDays() { return 28; }
}''')
caption("Code 5.1 — Member hierarchy (model/)")
para("The demand-adaptive policy, one implementation of the LoanPolicy interface:")
code('''@Override
public LoanTerms termsFor(Member m, int holdsWaiting, LocalDate issued) {
    int base = m.baseLoanDays();
    if (holdsWaiting <= 0)                   // nobody waiting: standard loan
        return new LoanTerms(issued.plusDays(base), base, true, "...");
    int days = Math.max(minDays,
            (int) Math.round(base / (1 + k * holdsWaiting)));
    return new LoanTerms(issued.plusDays(days), days, false, "...");
}''')
caption("Code 5.2 — DemandAdaptivePolicy.termsFor (policy/)")
para("Taking a copy off the shelf. The check and the decrement are one SQL statement, so two librarians can never "
     "issue the last copy twice:")
code('''public boolean takeCopy(Connection c, int bookId) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement(
            "UPDATE books SET available = available - 1 "
          + "WHERE id=? AND available > 0")) {
        ps.setInt(1, bookId);
        return ps.executeUpdate() == 1;  // 0 rows = no copy was free
    }
}''')
caption("Code 5.3 — Atomic issue (store/BookDao)")
para("Verifying the audit ledger:")
code('''public static int firstBroken(List<LedgerEntry> entries) {
    String prev = GENESIS;                         // 64 zeros
    for (int i = 0; i < entries.size(); i++) {
        LedgerEntry e = entries.get(i);
        String expect = hash(e.seq(), e.timestamp(), e.type(),
                             e.payload(), e.prevHash());
        if (e.seq() != i + 1 || !e.prevHash().equals(prev)
                || !e.hash().equals(expect))
            return i;                              // first broken entry
        prev = e.hash();
    }
    return -1;                                     // chain intact
}''')
caption("Code 5.4 — Hash-chain verification (ledger/HashChain)")
h2("5.3 User Interface / Demo", "s5.3")
para("All screenshots below were produced by driving the real window: the program types into the fields and "
     "presses the same buttons a librarian would, then saves the window as an image.")
picture(FIG / "ui_1_issue_hold.png", 15.2)
caption("Figure 5.1 — Circulation Desk: Hold Placed for a Busy Title")
para("In Figure 5.1 the librarian searches with a typo (“theh unger games”), the correct title is the first match, "
     "and because all three copies are out, member 42 is placed sixth in the hold queue.")
picture(FIG / "ui_2_issue_adaptive.png", 15.2)
caption("Figure 5.2 — Circulation Desk: Loan Shortened Because Members Are Waiting")
para("When a copy comes back, the first member in the queue collects it (Figure 5.2). With five members still "
     "waiting, the loan is 7 days instead of 14 and cannot be renewed, and the reason is shown and stored with the "
     "loan. The member’s other loan, for an uncontested title, kept the standard 14 days.")
picture(FIG / "ui_3_catalogue.png", 15.2)
caption("Figure 5.3 — Catalogue: Typo-Tolerant Search")
picture(FIG / "ui_4_board.png", 15.2)
caption("Figure 5.4 — Demand Board")
para("The catalogue search “tolkein hobit” (two misspellings) returns The Hobbit first (Figure 5.3). The demand "
     "board (Figure 5.4) lists titles with members waiting and the length of the next loan for each.")
picture(FIG / "ui_5_ledger_ok.png", 15.2)
caption("Figure 5.5 — Audit Ledger: Chain Verified")
picture(FIG / "ui_6_ledger_tampered.png", 15.2)
caption("Figure 5.6 — Audit Ledger: Tampering Detected and Located")
para("Figure 5.5 shows the verified chain. For Figure 5.6, one recorded fine was altered directly in the database "
     "with an SQL UPDATE, bypassing the application entirely — exactly what a dishonest insider could do. Pressing "
     "Verify flags the altered entry by number.")
page_break()

# ---------------------------------------------------------------- CHAPTER 6
heading_chapter(6, "Results and Discussion", "ch6")
h2("6.1 Evaluation Metrics", "s6.1")
para("Each claim was measured by the metric that matches it. **Test pass rate and line/branch coverage** show "
     "that the rules behave as specified and how much code the tests reach. For the loan policy, **mean and "
     "90th-percentile hold wait** measure what readers experience, **requests served** measures throughput, and "
     "**loans returned unfinished**, **late returns** and **fines** measure the cost of shorter loans to borrowers — "
     "a policy that only reported the benefit would be misleading. For concurrency, the measure is the **share of "
     "trials in which one copy was issued more than once**, which must be zero. For the ledger it is the "
     "**detection rate and exact location rate** of tampering, and for search the **top-1 and top-5 hit rate** "
     "with and without typing mistakes. All experiments are seeded and re-runnable with one command.")
h2("6.2 Results Across Iterations", "s6.2")
table(["Measure", "Iteration 1 (baseline)", "Iteration 2 (refinement)", "Final approach"], [
    ("Catalogue ISBNs accepted", f"{CAT['valid_as_is']:,} / 10,000 ({pct(CAT['valid_as_is'] / CAT['rows'])})",
     f"{CAT['valid_as_is'] + CAT['repaired_leading_zeros']:,} / 10,000 ({pct((CAT['valid_as_is'] + CAT['repaired_leading_zeros']) / CAT['rows'])})", "same"),
    ("Last copy issued twice (JDBC, 8 threads)", f"{RACE['db_naive_oversold']} / {RACE['db_trials']} trials",
     f"{RACE['db_safe_oversold']} / {RACE['db_trials']} trials", "same"),
    ("Search, one typo, top-1", pct(SR["substring_top1"]), pct(SR["fuzzy_top1"]), "same"),
    ("Mean hold wait (simulated)", f"{FX['mean_wait']:.1f} days", f"{FX['mean_wait']:.1f} days",
     f"{AD['mean_wait']:.1f} days"),
    ("Tampering detected", "not recorded", "not recorded", f"{TAMPER_OK:,} / {TAMPER_N:,}"),
    ("JUnit tests passing", "—", "suite started", f"{TST['passed']} / {TST['total']}"),
], [4.6, 3.6, 3.8, 3.8], size=10, align_center_cols=(1, 2, 3))
caption("Table 6.1 — Results Across Iterations")
para("Table 6.1 shows each iteration solving a different problem. Iteration 2 fixed correctness: data import, "
     "concurrency and search. The final approach changed how the library behaves — how quickly popular books "
     "reach readers — and made its history verifiable.")
table(["Measure (mean of 5 seeds)", "Fixed loans", "Demand-adaptive", "Change"], [
    ("Mean wait for a held title", f"{FX['mean_wait']:.2f} days", f"{AD['mean_wait']:.2f} days", f"−{pct(WAIT_CUT)}"),
    ("90th-percentile wait", f"{FX['p90_wait']:.1f} days", f"{AD['p90_wait']:.1f} days", f"−{pct(P90_CUT)}"),
    ("Requests served in the year", f"{FX['requests']:,.0f}", f"{AD['requests']:,.0f}", f"+{pct(REQ_UP)}"),
    ("Loans handed over from the hold queue", f"{FX['served_from_queue']:,.0f}", f"{AD['served_from_queue']:,.0f}",
     f"+{pct(AD['served_from_queue'] / FX['served_from_queue'] - 1)}"),
    ("Mean length of uncontested loans", f"{FX['uncontested_mean_days']:.1f} days", f"{AD['uncontested_mean_days']:.1f} days", "≈ unchanged"),
    ("Loans returned unfinished", pct(FX["unfinished_share"]), pct(AD["unfinished_share"]), "higher (cost)"),
    ("Late returns", pct(FX["late_share"]), pct(AD["late_share"]), "higher (cost)"),
    ("Fines in the year", f"Rs {FX['fines']:,.0f}", f"Rs {AD['fines']:,.0f}", "higher (cost)"),
], [5.6, 3.2, 3.4, 3.6], size=10, align_center_cols=(1, 2, 3))
caption("Table 6.2 — Simulated Year of Circulation, Fixed vs Adaptive")
picture(FIG / "fig_sim.png", 15.0)
caption("Figure 6.1 — Fixed vs Demand-Adaptive Loans (Simulated Year)")
para(f"Adaptive loans cut the average wait for a held title by {pct(WAIT_CUT)} and the 90th-percentile wait from "
     f"{FX['p90_wait']:.0f} to {AD['p90_wait']:.0f} days. Because members spend less time blocked in queues, "
     f"{pct(REQ_UP)} more requests were served across the year. Loans of books nobody was waiting for kept their "
     "standard length. The cost is real: more borrowers of popular titles had to return a book before finishing it "
     f"({pct(FX['unfinished_share'])} → {pct(AD['unfinished_share'])} of loans), and late returns and fines rose. "
     "Figure 6.2 shows how the policy’s two parameters trade these against each other.")
picture(FIG / "fig_sensitivity.png", 15.0)
caption("Figure 6.2 — Wait Time Against Cost to Borrowers for Nine Policy Settings")
picture(FIG / "fig_race.png", 15.0)
caption("Figure 6.3 — Double Issue of the Last Copy Under Concurrent Requests")
para(f"Figure 6.3: with check-then-act, the last copy was issued more than once in {pct(NAIVE)} of "
     f"{RACE['trials']:,} in-memory trials ({RACE['naive_extra_copies']:,} phantom loans) and in every one of the "
     f"{RACE['db_trials']} database trials. The compare-and-set and conditional-UPDATE versions never did.")
table(["Attack on the ledger", "Trials", "Detected", "Located to the exact entry"], [
    (k.replace("_", " ").capitalize(), v["trials"], v["detected"], v["located"]) for k, v in TAMP.items()
], [6.0, 2.6, 2.6, 4.6], size=10.5, align_center_cols=(1, 2, 3))
caption("Table 6.3 — Tamper Detection by Attack Type")
para(f"Every one of the {TAMPER_N:,} attacks was detected and located (Table 6.3). Verifying all "
     f"{LED['entries']:,} entries took {LED['verify_ms']:.1f} ms, so verification can run every time the ledger "
     "tab is opened.")
picture(FIG / "fig_search.png", 15.0)
caption("Figure 6.4 — Search Accuracy With and Without Typing Mistakes")
para(f"With one typing mistake, substring search found the intended book first in only {pct(SR['substring_top1'])} "
     f"of {SR['queries']} queries; Damerau–Levenshtein matching found it first in {pct(SR['fuzzy_top1'])} and within "
     f"the top five in {pct(SR['fuzzy_top5'])}, at {SR['fuzzy_ms']:.1f} ms per query over 1,000 titles. On clean "
     f"queries the two are equal ({pct(SR['clean_substring_top1'])} and {pct(SR['clean_fuzzy_top1'])}); the remaining "
     "misses are short queries like “The Lord” that genuinely match several books.")
pk = TST["packages"]
table(["Package", "Lines", "Line coverage", "Branch coverage"], [
    (p, pk[p]["lines"], pct(pk[p]["line"]), pct(pk[p]["branch"])) for p in
    ["util", "search", "ledger", "model", "policy", "service", "store", "sim", "app", "ui"]
], [4.0, 2.6, 4.2, 4.2], size=10.5, align_center_cols=(1, 2, 3))
caption("Table 6.4 — JUnit Tests and Coverage by Package")
para(f"All {TST['total']} tests pass. Coverage of the code that holds the rules — util, search, ledger, model, policy, "
     f"service and store — is {pct(TST['coverage_core_line'])} of lines and {pct(TST['coverage_core_branch'])} of "
     f"branches. The interface, simulator and application packages are exercised by the screenshot run and the "
     f"experiments rather than unit tests, which brings overall line coverage to {pct(TST['coverage_all_line'])}.")
h2("6.3 Discussion", "s6.3")
para("**The policy changes who waits, not how much reading happens.** Shortening loans on contested titles moves "
     "time from the current borrower to the people queuing behind them. That is a deliberate fairness choice: a "
     "best-seller with five people waiting is a shared resource, while an uncontested book is not, and the policy "
     "treats them differently. The sensitivity study makes the choice explicit — a librarian who wants the shortest "
     "queues can choose a 5-day minimum and k = 1.0 (a mean wait of "
     f"{SENS[(5, 1.0)]['mean_wait']:.1f} days), and one who wants to protect readers can choose a 10-day minimum "
     f"(wait {SENS[(10, 0.5)]['mean_wait']:.1f} days, unfinished loans {pct(SENS[(10, 0.5)]['unfinished_share'])}).")
para("**Correctness failures were invisible until they were measured.** The check-then-act version passed every "
     "single-user test, and the ISBN problem looked like bad data until the checksum showed the numbers were "
     "recoverable. Both were found only by testing under realistic conditions — many threads, the real catalogue — "
     "which is why the experiments in this chapter use real data and real concurrency rather than toy examples.")
para("**Integrity is cheap.** The ledger adds one SHA-256 hash per transaction and verifies five thousand entries "
     "in about ten milliseconds, yet it turns a silently editable fines table into one where any alteration is "
     "detected and pinpointed.")
h2("6.4 Limitations", "s6.4")
for t in [
    "**Simulated circulation.** The loan-policy results come from a model of borrower behaviour (reading times, "
    "lateness, renewals), not a live library, and the renewal decision is made when the book is issued. The "
    "direction of the effect is robust across nine settings and five seeds; its exact size in a real library is not known.",
    "**Popularity data.** Demand follows Goodreads rating counts, which reflect a global readership, not a college "
    "library’s; a technical library would see a different pattern.",
    "**The ledger detects but cannot prevent.** Someone with full database access could delete the whole ledger or "
    "rebuild it from scratch; anchoring the latest hash outside the database (printed or e-mailed daily) would close that gap.",
    "**Single machine.** SQLite and Swing suit one library desk; several branches would need a database server and "
    "a networked client.",
    f"**Interface not unit-tested.** The Swing code is exercised by the scripted screenshot run but not by assertions, "
    f"so overall line coverage is {pct(TST['coverage_all_line'])}.",
]:
    bullet(t)
page_break()

# ---------------------------------------------------------------- CHAPTER 7
heading_chapter(7, "Team Reflection and Learning Outcomes", "ch7")
h2("7.1 Individual Reflections", "s7.1")
para(f"**{m1}:** I led the project and built the core: the class design, the service layer with JDBC transactions, "
     "the loan policies, the atomic issue, the audit ledger, the simulator and all the experiments. The lesson I value "
     "most is that code which passes every test can still be wrong. Our check-then-act issue worked perfectly with one "
     "user and failed in every concurrent database trial. Seeing that number changed how I think about shared state. "
     "Designing LoanPolicy as an interface was the other turning point: once the policy was a separate object, the "
     "same service could run in the app, the simulator and the tests, which is what made the comparison fair.")
para(f"**{m2}:** I built the Swing interface, the typo-tolerant search, the catalogue loader and the JUnit suite. The "
     "ISBN problem taught me to question data before code: two-thirds of the catalogue looked invalid until the "
     "checksum showed the numbers had only lost their leading zeros. On the interface, the hardest part was making "
     "every rule visible — showing why a loan is 7 days instead of 14 — so the librarian can explain it to the "
     "member. Writing tests against a real SQLite database rather than mocks also caught problems that simpler tests missed.")
h2("7.2 Team Learning", "s7.2")
para("Agreeing the service layer’s method signatures early let the interface and the core be built in parallel; the "
     "interface could be developed against the real service from the start. Keeping each baseline in the code — the "
     "fixed policy, substring search, the naive issue — meant every improvement in Chapter 6 is a measured comparison "
     "rather than a claim.")
para("Two mentor remarks changed our direction. The first, at the Iteration 1 review, was to examine the data rather "
     "than the code when two-thirds of the catalogue was rejected, which led to the ISBN repair. The second was to "
     "report the cost of shorter loans honestly; that is why Table 6.2 lists unfinished loans and fines next to the "
     "wait-time gain. If we restarted, we would write the concurrency test before the issue code and the simulator "
     "before choosing the policy parameters.")
h2("7.3 Course Outcomes — Evidence Summary", "s7.3")
for t in [
    "**Object-oriented design:** abstract classes and inheritance (Member → Student, Faculty), interfaces and "
    "polymorphism (LoanPolicy), encapsulation, immutable records and a custom exception hierarchy (Chapters 4–5).",
    "**Database connectivity:** JDBC with PreparedStatement throughout, transactions with rollback, the DAO pattern, "
    "and constraints enforced in the schema.",
    "**Concurrency and correctness:** a measured race condition and two correct fixes (compare-and-set and a "
    "conditional SQL update), tested with ExecutorService and CountDownLatch.",
    "**Standard library depth:** java.time for due dates and fines, java.security for SHA-256, java.util.concurrent, "
    "collections, records, text blocks and switch expressions.",
    f"**Testing and teamwork:** {TST['total']} JUnit tests with coverage, a weekly log of mentor feedback (Table 3.1) "
    "and a clear division of work.",
]:
    bullet(t)
page_break()

# ---------------------------------------------------------------- CHAPTER 8
heading_chapter(8, "Conclusion and Future Scope", "ch8")
h2("8.1 Conclusion", "s8.1")
para("SHELFWISE shows that a Java library management system can do more than digitise the register. Its "
     "demand-adaptive loan policy shortened the wait for popular books by "
     f"{pct(WAIT_CUT)} in a simulated year while leaving uncontested loans unchanged, and the cost to borrowers is "
     f"measured and adjustable. A single conditional SQL update eliminated a double-issue race that the naive "
     f"approach hit in {pct(DBNAIVE, 0)} of concurrent trials. A SHA-256 hash chain detected and located all "
     f"{TAMPER_N:,} tampering attempts. ISBN checksum repair recovered {CAT['repaired_leading_zeros']:,} damaged "
     f"catalogue records, and typo-tolerant search found the intended book {pct(SR['fuzzy_top1'])} of the time "
     f"instead of {pct(SR['substring_top1'])}. All {TST['total']} unit tests pass. The driving question is answered: "
     "with careful object-oriented design, JDBC transactions and measured experiments, the system makes popular "
     "books reach readers sooner, stays correct under concurrent use, and can prove its records are intact.")
h2("8.2 Future Scope", "s8.2")
for t in [
    "Pilot the adaptive policy in the department library and compare real wait times with the simulation.",
    "Anchor the ledger’s latest hash outside the database each day so that wholesale deletion is also detectable.",
    "Move to a client–server design (PostgreSQL and a REST API) for several branches and a member-facing web or mobile view.",
    "Notify the next member in the queue by e-mail or SMS when a held copy is returned.",
    "Learn each title’s typical reading time from past loans and use it to set fairer adaptive loan lengths.",
]:
    bullet(t)
page_break()

# ---------------------------------------------------------------- REFERENCES
para("REFERENCES", 14, True, align="center", after=12)
REFS = [
    "J. Bloch, Effective Java, 3rd ed. Boston, MA, USA: Addison-Wesley, 2018.",
    "E. Gamma, R. Helm, R. Johnson, and J. Vlissides, Design Patterns: Elements of Reusable Object-Oriented Software. Reading, MA, USA: Addison-Wesley, 1994.",
    "B. Goetz, T. Peierls, J. Bloch, J. Bowbeer, D. Holmes, and D. Lea, Java Concurrency in Practice. Boston, MA, USA: Addison-Wesley, 2006.",
    "S. Haber and W. S. Stornetta, “How to time-stamp a digital document,” Journal of Cryptology, vol. 3, no. 2, pp. 99–111, 1991.",
    "B. Schneier and J. Kelsey, “Secure audit logs to support computer forensics,” ACM Trans. Information and System Security, vol. 2, no. 2, pp. 159–176, 1999.",
    "F. J. Damerau, “A technique for computer detection and correction of spelling errors,” Communications of the ACM, vol. 7, no. 3, pp. 171–176, 1964.",
    "V. I. Levenshtein, “Binary codes capable of correcting deletions, insertions, and reversals,” Soviet Physics Doklady, vol. 10, no. 8, pp. 707–710, 1966.",
    "P. M. Morse, Library Effectiveness: A Systems Approach. Cambridge, MA, USA: MIT Press, 1968.",
    "M. K. Buckland, Book Availability and the Library User. New York, NY, USA: Pergamon Press, 1975.",
    "International ISBN Agency, ISBN Users’ Manual, 7th ed. London, U.K., 2017.",
    "Z. Zając, “goodbooks-10k: a new dataset for book recommendations,” GitHub repository, 2017. [Online]. Available: https://github.com/zygmuntz/goodbooks-10k",
    "Oracle, “JDBC Basics,” The Java Tutorials. [Online]. Available: https://docs.oracle.com/javase/tutorial/jdbc/basics/",
    "SQLite Consortium, “Atomic Commit in SQLite.” [Online]. Available: https://www.sqlite.org/atomiccommit.html",
    "National Institute of Standards and Technology, Secure Hash Standard (SHS), FIPS PUB 180-4, 2015.",
    "FormDev Software, “FlatLaf — Flat Look and Feel.” [Online]. Available: https://www.formdev.com/flatlaf/",
]
for i, r in enumerate(REFS, 1):
    p = para("", align="justify", after=5, line=1.3)
    p.paragraph_format.left_indent = Cm(0.9)
    p.paragraph_format.first_line_indent = Cm(-0.9)
    rich(p, f"[{i}] {r}", 11.5)
page_break()

# ---------------------------------------------------------------- APPENDIX
para("APPENDIX", 14, True, align="center", after=12)
h2("A.1 Full source code:")
para(REPO, 12, align="left", color="1F4E9A")
para("The repository contains the application (src/main/java/shelfwise), the JUnit tests (src/test/java), the "
     "catalogue data, the experiment runner, the raw results (results/results.json), the scripts that drew every "
     "figure, and a build script that compiles, tests and runs everything.", 12)
h2("A.2 Complete weekly PBL log and mentor sign-offs:")
para("The full week-by-week log is given in Table 3.1 (Chapter 3); it spans the twelve-week PBL cycle beginning at "
     "the Zeroth Review on 27 June 2026.", 12)
h2("A.3 Self and Peer Assessment")
table(["Team Member", "Self-Rated Contribution (%)", "Peer-Rated Contribution (%)", "Remarks"], [
    (m1, "60%", "60%", "Led the project; class design, service layer and JDBC, loan policies, atomic issue, audit ledger, simulator and experiments"),
    (m2, "40%", "40%", "Swing interface, typo-tolerant search, catalogue loader with ISBN repair, JUnit suite and screenshots"),
], [3.6, 3.0, 3.0, 6.2], size=10.5, align_center_cols=(1, 2))
page_break()
h2("A.4 PBL Poster")
if (ROOT / "poster" / "poster_rotated.png").exists():
    picture(ROOT / "poster" / "poster_rotated.png", 15.2)

out = ROOT / "report" / "PBL_Report_Library_Management_SHELFWISE.docx"
doc.save(out)
print("saved", out)
