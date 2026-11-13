package com.example.copilot.aiassistant.infrastructure.mongo;

import com.mongodb.MongoException;

/** Classifies Mongo failures found anywhere in a cause chain. */
public final class MongoConflicts {

    static final int WRITE_CONFLICT = 112;
    static final int DUPLICATE_KEY = 11000;

    private MongoConflicts() {
    }

    /**
     * A write conflict (112), a unique-index violation (11000), or a transaction failure the driver labels
     * TransientTransactionError / UnknownTransactionCommitResult: the caller should reload and try again.
     * Anything else (e.g. 121 document validation failure) is not a conflict.
     */
    public static boolean isConflict(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof MongoException m && (isWriteConflictOrDuplicateKey(m)
                    || m.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                    || m.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL))) {
                return true;
            }
        }
        return false;
    }

    /** Narrower than {@link #isConflict}: only another writer (112) or the unique index (11000) won. */
    public static boolean isWriteConflictOrDuplicateKey(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof MongoException m && isWriteConflictOrDuplicateKey(m)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWriteConflictOrDuplicateKey(MongoException m) {
        return m.getCode() == WRITE_CONFLICT || m.getCode() == DUPLICATE_KEY;
    }
}
