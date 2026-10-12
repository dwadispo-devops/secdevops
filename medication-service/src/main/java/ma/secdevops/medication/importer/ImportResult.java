package ma.secdevops.medication.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulateur de statistiques pour un import.
 * Thread-safe pour une utilisation dans un contexte mono-thread (CommandLineRunner).
 */
public class ImportResult {

    private int linesRead = 0;
    private int linesImported = 0;
    private final List<String> ignoredReasons = new ArrayList<>();

    public void incrementRead() {
        linesRead++;
    }

    public void incrementImported() {
        linesImported++;
    }

    public void setLinesImported(int count) {
        this.linesImported = count;
    }

    public void addIgnored(String reason) {
        ignoredReasons.add(reason);
    }

    public int getLinesRead() {
        return linesRead;
    }

    public int getLinesImported() {
        return linesImported;
    }

    public int getIgnoredCount() {
        return ignoredReasons.size();
    }

    public List<String> getIgnoredReasons() {
        return List.copyOf(ignoredReasons);
    }
}
