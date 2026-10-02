package shelfwise.ui;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import shelfwise.ledger.LedgerEntry;
import shelfwise.model.Book;
import shelfwise.model.Member;
import shelfwise.search.FuzzySearch;
import shelfwise.service.LibraryException;
import shelfwise.service.LibraryService;

/** The librarian's desktop: circulation desk, catalogue, demand board and audit ledger. */
public class MainWindow extends JFrame {
    static final Color INK = new Color(0x1F2A36), MUTED = new Color(0x6B7480), ACCENT = new Color(0x9A3B2B),
            PAPER = new Color(0xFAF7F1), OK = new Color(0x2F6B45), BAD = new Color(0xA12A1E), RULE = new Color(0xE3DACB);

    private final LibraryService svc;
    private LocalDate today;
    private FuzzySearch search;
    private List<Book> books;

    final JTabbedPane tabs = new JTabbedPane();
    // circulation
    final JTextField memberField = new JTextField("42", 6);
    final JTextField bookQuery = new JTextField(22);
    final DefaultListModel<Book> hits = new DefaultListModel<>();
    final JList<Book> hitList = new JList<>(hits);
    final JLabel outcome = new JLabel();
    final JTextField loanField = new JTextField(6);
    final DefaultTableModel memberLoans = model("Loan", "Title", "Issued", "Due", "Why this due date");
    // catalogue
    final JTextField catQuery = new JTextField(30);
    final DefaultTableModel catModel = model("Title", "Author", "ISBN-13", "Year", "Copies", "On shelf", "Waiting");
    // board
    final DefaultTableModel boardModel = model("Title", "Author", "Copies", "Waiting", "Next loan length");
    final JLabel boardStats = new JLabel();
    // ledger
    final DefaultTableModel ledgerModel = model("#", "Date", "Event", "Details", "Previous hash", "Hash");
    final JLabel ledgerBanner = new JLabel();

    public MainWindow(LibraryService svc, LocalDate today) throws SQLException {
        super("Shelfwise — Library Management System");
        this.svc = svc;
        this.today = today;
        reloadCatalogue();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(PAPER);
        root.add(header(), BorderLayout.NORTH);
        tabs.addTab("Circulation desk", circulation());
        tabs.addTab("Catalogue", catalogue());
        tabs.addTab("Demand board", board());
        tabs.addTab("Audit ledger", ledgerTab());
        tabs.setBorder(new EmptyBorder(4, 16, 16, 16));
        root.add(tabs, BorderLayout.CENTER);
        setContentPane(root);
        setSize(1320, 820);
        refreshAll();
    }

    // ------------------------------------------------------------------ layout
    private JComponent header() {
        JPanel h = new JPanel(new BorderLayout());
        h.setBackground(PAPER);
        h.setBorder(new EmptyBorder(16, 20, 8, 20));
        JLabel title = new JLabel("<html><span style='font-size:20pt;font-weight:bold;color:#1F2A36'>Shelfwise</span>"
                + "&nbsp;&nbsp;<span style='font-size:11pt;color:#6B7480'>Library Management System · Chennai Institute of Technology</span></html>");
        h.add(title, BorderLayout.WEST);
        JLabel right = new JLabel("<html><div style='text-align:right;color:#6B7480'>Today: <b style='color:#1F2A36'>" + today
                + "</b><br>Loan policy: <b style='color:#9A3B2B'>" + svc.policy().name() + "</b></div></html>");
        h.add(right, BorderLayout.EAST);
        return h;
    }

    private JComponent circulation() {
        JPanel p = new JPanel(new BorderLayout(16, 16));
        p.setBorder(new EmptyBorder(16, 16, 16, 16));
        JPanel left = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.fill = GridBagConstraints.HORIZONTAL; g.insets = new Insets(4, 0, 4, 0); g.weightx = 1;
        left.add(section("Issue a book"), g);
        left.add(labelled("Member ID", memberField), g);
        left.add(labelled("Find book (typos are fine)", bookQuery), g);
        hitList.setVisibleRowCount(6);
        hitList.setCellRenderer((list, b, i, sel, foc) -> {
            JLabel l = new JLabel("<html><b>" + esc(b.title()) + "</b><br><span style='color:#6B7480'>" + esc(b.authors())
                    + " · " + b.available() + "/" + b.copies() + " on shelf</span></html>");
            l.setBorder(new EmptyBorder(4, 8, 4, 8));
            l.setOpaque(true);
            l.setBackground(sel ? new Color(0xF1E3DE) : Color.WHITE);
            return l;
        });
        g.weighty = 1; g.fill = GridBagConstraints.BOTH;
        left.add(new JScrollPane(hitList), g);
        g.weighty = 0; g.fill = GridBagConstraints.HORIZONTAL;
        JButton issue = primary("Issue / place hold");
        issue.addActionListener(e -> doIssue());
        left.add(issue, g);
        left.add(section("Return a book"), g);
        JPanel ret = new JPanel(new BorderLayout(8, 0));
        ret.add(labelled("Loan number", loanField), BorderLayout.CENTER);
        JButton rb = new JButton("Return");
        rb.addActionListener(e -> doReturn());
        ret.add(rb, BorderLayout.EAST);
        left.add(ret, g);
        left.setPreferredSize(new Dimension(400, 600));
        bookQuery.getDocument().addDocumentListener(new SimpleListener(this::runSearch));

        JPanel right = new JPanel(new BorderLayout(0, 12));
        outcome.setVerticalAlignment(SwingConstants.TOP);
        outcome.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(RULE), new EmptyBorder(16, 18, 16, 18)));
        outcome.setOpaque(true);
        outcome.setBackground(Color.WHITE);
        outcome.setPreferredSize(new Dimension(600, 190));
        outcome.setText(card("Ready", "Enter a member and pick a title to issue it. The due date is set by the loan policy "
                + "and explained here.", MUTED));
        right.add(outcome, BorderLayout.NORTH);
        JPanel loans = new JPanel(new BorderLayout(0, 6));
        loans.add(section("This member's open loans"), BorderLayout.NORTH);
        loans.add(table(memberLoans, 60, 260, 90, 90, 420), BorderLayout.CENTER);
        right.add(loans, BorderLayout.CENTER);
        memberField.addActionListener(e -> refreshMember());
        p.add(left, BorderLayout.WEST);
        p.add(right, BorderLayout.CENTER);
        return p;
    }

    private JComponent catalogue() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(new EmptyBorder(16, 16, 16, 16));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        top.add(labelled("Search title or author", catQuery));
        catQuery.getDocument().addDocumentListener(new SimpleListener(this::refreshCatalogue));
        p.add(top, BorderLayout.NORTH);
        p.add(table(catModel, 380, 200, 130, 60, 60, 70, 70), BorderLayout.CENTER);
        return p;
    }

    private JComponent board() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(new EmptyBorder(16, 16, 16, 16));
        boardStats.setBorder(new EmptyBorder(0, 2, 6, 0));
        p.add(boardStats, BorderLayout.NORTH);
        p.add(table(boardModel, 420, 220, 70, 80, 360), BorderLayout.CENTER);
        return p;
    }

    private JComponent ledgerTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(new EmptyBorder(16, 16, 16, 16));
        JPanel top = new JPanel(new BorderLayout(12, 0));
        ledgerBanner.setOpaque(true);
        ledgerBanner.setBorder(new EmptyBorder(10, 14, 10, 14));
        top.add(ledgerBanner, BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton verify = primary("Verify chain");
        verify.addActionListener(e -> verifyLedger());
        btns.add(verify);
        top.add(btns, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);
        p.add(table(ledgerModel, 50, 90, 70, 420, 170, 170), BorderLayout.CENTER);
        return p;
    }

    // ------------------------------------------------------------------ actions
    public void runSearch() {
        hits.clear();
        String q = bookQuery.getText();
        if (q.isBlank()) return;
        for (var h : search.search(q, 6)) hits.addElement(fresh(h.book()));
        if (!hits.isEmpty()) hitList.setSelectedIndex(0);
    }

    public void doIssue() {
        Book b = hitList.getSelectedValue();
        if (b == null) { outcome.setText(card("Pick a title", "Search for a book and select it first.", BAD)); return; }
        try {
            int m = Integer.parseInt(memberField.getText().trim());
            var r = svc.issue(m, b.id(), today);
            if (r.issued()) {
                outcome.setText(card("Issued · loan #" + r.loanId(), esc(r.message()) + "<br><br><b>Due " + r.terms().due()
                        + " (" + r.terms().days() + " days).</b> " + esc(r.terms().reason()), OK));
                loanField.setText(String.valueOf(r.loanId()));
            } else {
                outcome.setText(card("Hold placed · position " + r.queuePosition(), esc(r.message())
                        + "<br><br>They will be offered the next copy that comes back.", ACCENT));
            }
            refreshAll();
        } catch (NumberFormatException e) {
            outcome.setText(card("Check the member ID", "Member ID must be a number.", BAD));
        } catch (LibraryException e) {
            outcome.setText(card("Not issued", esc(e.getMessage()), BAD));
        }
    }

    public void doReturn() {
        try {
            var r = svc.returnBook(Integer.parseInt(loanField.getText().trim()), today);
            outcome.setText(card(r.fine() > 0 ? "Returned late · fine Rs " + r.fine() : "Returned", esc(r.message()),
                    r.fine() > 0 ? ACCENT : OK));
            refreshAll();
        } catch (NumberFormatException e) {
            outcome.setText(card("Check the loan number", "Loan number must be a number.", BAD));
        } catch (LibraryException e) {
            outcome.setText(card("Not returned", esc(e.getMessage()), BAD));
        }
    }

    public void verifyLedger() {
        try {
            int broken = svc.ledger().verify();
            int n = ledgerModel.getRowCount();
            if (broken < 0) {
                ledgerBanner.setBackground(new Color(0xE6F0E8));
                ledgerBanner.setText("<html><b style='color:#2F6B45'>Chain intact.</b> All " + n
                        + " entries verified: every hash matches its contents and links to the one before it.</html>");
            } else {
                ledgerBanner.setBackground(new Color(0xF8E4E0));
                ledgerBanner.setText("<html><b style='color:#A12A1E'>Tampering detected at entry #" + (broken + 1)
                        + ".</b> Its stored hash no longer matches its contents, so every later entry is untrusted.</html>");
            }
        } catch (SQLException e) {
            ledgerBanner.setText("Ledger error: " + e.getMessage());
        }
    }

    public void setToday(LocalDate d) { today = d; }

    public void refreshAll() {
        try {
            reloadCatalogue();
            refreshMember();
            refreshCatalogue();
            refreshBoard();
            refreshLedger();
            verifyLedger();
        } catch (SQLException e) {
            outcome.setText(card("Database error", esc(e.getMessage()), BAD));
        }
    }

    void refreshMember() {
        memberLoans.setRowCount(0);
        try {
            int m = Integer.parseInt(memberField.getText().trim());
            for (var l : svc.openLoans()) if (l.member().getId() == m)
                memberLoans.addRow(new Object[]{l.loanId(), l.book().title(), l.issued(), l.due(), l.note()});
        } catch (Exception ignored) { }
    }

    void refreshCatalogue() {
        catModel.setRowCount(0);
        String q = catQuery.getText();
        List<Book> rows = q.isBlank() ? books.subList(0, Math.min(200, books.size()))
                : search.search(q, 40).stream().map(FuzzySearch.Hit::book).toList();
        for (Book b : rows) {
            int w = 0;
            try { w = svc.holdsWaiting(b.id()); } catch (SQLException ignored) { }
            catModel.addRow(new Object[]{b.title(), b.authors(), b.isbn13(), b.year(), b.copies(), b.available(), w});
        }
    }

    void refreshBoard() throws SQLException {
        boardModel.setRowCount(0);
        Member sample = Member.of(0, "student", "STUDENT");
        for (var h : svc.hotTitles(30)) {
            var t = svc.policy().termsFor(sample, h.holds(), today);
            boardModel.addRow(new Object[]{h.book().title(), h.book().authors(), h.book().copies(), h.holds(),
                    t.days() + " days for a student — " + (t.renewable() ? "renewable" : "no renewal")});
        }
        long out = svc.scalar("SELECT COUNT(*) FROM loans WHERE returned IS NULL");
        long holds = svc.scalar("SELECT COUNT(*) FROM holds");
        long overdue = svc.scalar("SELECT COUNT(*) FROM loans WHERE returned IS NULL AND due < '" + today + "'");
        long fines = svc.scalar("SELECT COALESCE(SUM(fine),0) FROM loans");
        boardStats.setText("<html><span style='font-size:13pt'><b>" + out + "</b> books on loan &nbsp;·&nbsp; <b>" + holds
                + "</b> members waiting &nbsp;·&nbsp; <b style='color:#9A3B2B'>" + overdue + "</b> overdue &nbsp;·&nbsp; Rs <b>" + fines
                + "</b> fines collected</span><br><span style='color:#6B7480'>Most-wanted titles. With people waiting, the next loan is "
                + "shortened so the queue moves faster.</span></html>");
    }

    void refreshLedger() throws SQLException {
        ledgerModel.setRowCount(0);
        List<LedgerEntry> all = svc.ledger().all();
        for (int i = all.size() - 1; i >= Math.max(0, all.size() - 400); i--) {
            LedgerEntry e = all.get(i);
            ledgerModel.addRow(new Object[]{e.seq(), e.timestamp(), e.type(), e.payload().replace(";", "  "),
                    e.prevHash().substring(0, 16) + "…", e.hash().substring(0, 16) + "…"});
        }
    }

    private void reloadCatalogue() throws SQLException {
        books = svc.books().all();
        search = new FuzzySearch(books);
    }

    private Book fresh(Book b) {
        try { return svc.books().find(b.id()).orElse(b); } catch (SQLException e) { return b; }
    }

    // ------------------------------------------------------------------ widgets
    static DefaultTableModel model(String... cols) {
        return new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
    }

    static JScrollPane table(DefaultTableModel m, int... widths) {
        JTable t = new JTable(m);
        t.setRowHeight(28);
        t.setShowHorizontalLines(true);
        t.setGridColor(RULE);
        t.getTableHeader().setReorderingAllowed(false);
        for (int i = 0; i < widths.length && i < t.getColumnCount(); i++) t.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(BorderFactory.createLineBorder(RULE));
        return sp;
    }

    static JComponent labelled(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        JLabel l = new JLabel(label);
        l.setForeground(MUTED);
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    static JLabel section(String s) {
        JLabel l = new JLabel(s);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 15f));
        l.setForeground(INK);
        l.setBorder(new EmptyBorder(10, 0, 2, 0));
        return l;
    }

    static JButton primary(String s) {
        JButton b = new JButton(s);
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.setBackground(ACCENT);
        b.setForeground(Color.WHITE);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        return b;
    }

    static String card(String title, String body, Color c) {
        String hex = String.format("#%06X", c.getRGB() & 0xFFFFFF);
        return "<html><div style='width:520px'><span style='font-size:15pt;font-weight:bold;color:" + hex + "'>" + title
                + "</span><br><br><span style='font-size:11.5pt;color:#1F2A36'>" + body + "</span></div></html>";
    }

    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    public static void installLookAndFeel() {
        FlatLightLaf.setup();
        UIManager.put("Component.accentColor", ACCENT);
        UIManager.put("Component.focusColor", new Color(0xC9A99F));
        UIManager.put("TabbedPane.underlineColor", ACCENT);
        UIManager.put("TabbedPane.inactiveUnderlineColor", ACCENT);
        UIManager.put("TabbedPane.selectedForeground", INK);
        UIManager.put("Panel.background", PAPER);
        UIManager.put("TabbedPane.background", PAPER);
        UIManager.put("Table.selectionBackground", new Color(0xF1E3DE));
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("defaultFont", new Font("SansSerif", Font.PLAIN, 13));
    }

    record SimpleListener(Runnable r) implements javax.swing.event.DocumentListener {
        public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
    }
}
