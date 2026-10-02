package shelfwise.ledger;

public record LedgerEntry(long seq, String timestamp, String type, String payload, String prevHash, String hash) {}
