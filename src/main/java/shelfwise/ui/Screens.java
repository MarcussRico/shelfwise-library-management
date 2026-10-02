package shelfwise.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import javax.imageio.ImageIO;
import javax.swing.*;
import shelfwise.app.Seeder;
import shelfwise.policy.DemandAdaptivePolicy;
import shelfwise.service.LibraryService;
import shelfwise.store.Database;

/** Drives the real window through real button clicks and saves each screen as a PNG at 2x. */
public final class Screens {
    public static void main(String[] args) throws Exception {
        Path dbPath = Path.of("shelfwise.db");
        LibraryService svc = Seeder.seed(dbPath, Path.of("data/books.csv"), 60, 2026);
        LocalDate today = LocalDate.of(2026, 10, 2);
        MainWindow.installLookAndFeel();
        File out = new File("figures");
        out.mkdirs();
        SwingUtilities.invokeAndWait(() -> {
            try {
                MainWindow w = new MainWindow(svc, today);
                w.setVisible(true);
                // 1. issue a contested best-seller with a typo in the search
                var hot = svc.hotTitles(1).get(0);
                w.memberField.setText("42");
                w.bookQuery.setText(typo(hot.book().title()));
                w.doIssue();
                shot(w, new File(out, "ui_1_issue_hold.png"));
                // 2. a copy of the hot title comes back; the first member in the queue collects it
                int hotId = hot.book().id();
                var back = svc.openLoans().stream().filter(l -> l.book().id() == hotId).findFirst().orElseThrow();
                w.loanField.setText(String.valueOf(back.loanId()));
                w.doReturn();
                long head = svc.scalar("SELECT member_id FROM holds WHERE book_id=" + hotId + " ORDER BY placed, id LIMIT 1");
                w.memberField.setText(String.valueOf(head));
                w.bookQuery.setText("hunger games");
                w.hitList.setSelectedIndex(0);
                w.doIssue();
                w.refreshMember();
                shot(w, new File(out, "ui_2_issue_adaptive.png"));
                // 3. catalogue fuzzy search
                w.tabs.setSelectedIndex(1);
                w.catQuery.setText("tolkein hobit");
                shot(w, new File(out, "ui_3_catalogue.png"));
                // 4. demand board
                w.tabs.setSelectedIndex(2);
                shot(w, new File(out, "ui_4_board.png"));
                // 5. ledger intact
                w.tabs.setSelectedIndex(3);
                w.verifyLedger();
                shot(w, new File(out, "ui_5_ledger_ok.png"));
                // 6. tamper with one fine directly in the database, then verify again
                try (Connection c = new Database(dbPath.toString()).connect(); Statement s = c.createStatement()) {
                    s.executeUpdate("UPDATE ledger SET payload = replace(payload, 'fine=', 'fine=0') WHERE seq = "
                            + "(SELECT seq FROM ledger WHERE type='RETURN' AND payload NOT LIKE '%fine=0' ORDER BY seq LIMIT 1)");
                }
                w.verifyLedger();
                shot(w, new File(out, "ui_6_ledger_tampered.png"));
                w.dispose();
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        System.exit(0);
    }

    static String typo(String title) {
        String t = title.replaceAll("\\(.*?\\)", "").trim().toLowerCase();
        return t.length() > 6 ? t.substring(0, 3) + t.charAt(4) + t.charAt(3) + t.substring(5) : t;
    }

    static void shot(JFrame f, File file) throws Exception {
        JComponent c = (JComponent) f.getContentPane();
        c.validate();
        c.paintImmediately(c.getBounds());
        BufferedImage img = new BufferedImage(c.getWidth() * 2, c.getHeight() * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(2, 2);
        c.printAll(g);
        g.dispose();
        ImageIO.write(img, "png", file);
    }
}
