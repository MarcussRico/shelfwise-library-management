package shelfwise.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import javax.swing.SwingUtilities;
import shelfwise.policy.DemandAdaptivePolicy;
import shelfwise.service.LibraryService;
import shelfwise.store.Database;
import shelfwise.ui.MainWindow;

/** Starts the desktop app; builds the demo database on first run. */
public final class Main {
    public static void main(String[] args) throws Exception {
        Path db = Path.of("shelfwise.db");
        LibraryService svc = Files.exists(db)
                ? new LibraryService(new Database(db.toString()), new DemandAdaptivePolicy())
                : Seeder.seed(db, Path.of("data/books.csv"), 60, 2026);
        LocalDate today = LocalDate.of(2026, 10, 2);
        MainWindow.installLookAndFeel();
        SwingUtilities.invokeLater(() -> {
            try { new MainWindow(svc, today).setVisible(true); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
