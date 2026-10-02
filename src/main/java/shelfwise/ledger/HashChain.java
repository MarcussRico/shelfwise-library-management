package shelfwise.ledger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * SHA-256 hash chain: each entry's hash covers its own fields and the previous hash,
 * so changing, deleting or reordering any past entry breaks every link after it.
 */
public final class HashChain {
    public static final String GENESIS = "0".repeat(64);

    private HashChain() {}

    public static String hash(long seq, String timestamp, String type, String payload, String prevHash) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String body = seq + "|" + timestamp + "|" + type + "|" + payload + "|" + prevHash;
            return HexFormat.of().formatHex(md.digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Returns the index of the first entry that fails verification, or -1 if the chain is intact. */
    public static int firstBroken(List<LedgerEntry> entries) {
        String prev = GENESIS;
        for (int i = 0; i < entries.size(); i++) {
            LedgerEntry e = entries.get(i);
            if (e.seq() != i + 1 || !e.prevHash().equals(prev)
                    || !e.hash().equals(hash(e.seq(), e.timestamp(), e.type(), e.payload(), e.prevHash()))) {
                return i;
            }
            prev = e.hash();
        }
        return -1;
    }
}
