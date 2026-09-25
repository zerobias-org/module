package com.zerobias.module.x12.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * One unit of work in one SQL transaction on a connection that otherwise runs in
 * autocommit mode. Any {@link Throwable} rolls the unit back — an {@link Error} (OOM in
 * the middle of a batch) must not leave half a file behind any more than an
 * {@link SQLException} may.
 *
 * <p>Autocommit is restored only after a commit or a successful rollback. With the xerial
 * driver {@code setAutoCommit(true)} issues {@code COMMIT}, so restoring it after a failed
 * rollback would commit exactly the partial work the rollback was meant to discard; the
 * connection is closed instead (SQLite drops an uncommitted transaction on close) and the
 * store fails every later call until the process restarts.
 */
final class SqlTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(SqlTransaction.class);

    @FunctionalInterface
    interface Work<T> {
        T run() throws SQLException;
    }

    private SqlTransaction() {
    }

    static <T> T run(Connection conn, Work<T> work) throws SQLException {
        conn.setAutoCommit(false);
        final T out;
        try {
            out = work.run();
            conn.commit();
        } catch (Throwable failure) {
            try {
                conn.rollback();
            } catch (Throwable rollbackFailed) {
                failure.addSuppressed(rollbackFailed);
                LOG.error("rollback failed ({}); closing the buffer connection so the partial transaction "
                    + "is discarded instead of committed", rollbackFailed.toString());
                try {
                    conn.close();
                } catch (Throwable closeFailed) {
                    failure.addSuppressed(closeFailed);
                }
                throw failure;
            }
            conn.setAutoCommit(true);
            throw failure;
        }
        conn.setAutoCommit(true);
        return out;
    }
}
