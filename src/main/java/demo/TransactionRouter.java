package demo;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * A DataSource implementation that routes transactions to a set of
 * underlying connection pools based on a routing policy and per-transaction
 * read-only hints.
 *
 * Designed to be used along with either AdaptiveRoutingInterceptor or
 * DeclarativeRoutingInterceptor, or some other mechanism that can pass on
 * readOnly(x) and blacklist() hints.
 *
 * (A production version of this class should probably also deal with graceful
 * handling of connection failure for a database server that is down, and
 * perhaps also figure out which server is the primary/write server without
 * having to be told, but those are outside the scope of this
 * proof-of-concept for now.)
 */
public class TransactionRouter implements DataSource {
    private DataSource writeDataSource;
    private ReadSlot[] readSlots;
    private Random random = new Random();
    private RoutingPolicy routingPolicy = RoutingPolicy.RANDOM;
    private long blacklistTimeMillis = 5000;
    private AtomicLong roundRobinNext = new AtomicLong(0);
    private ThreadLocal<Boolean> readOnly = new ThreadLocal();
    private ThreadLocal<ReadSlot> currentReadSlot = new ThreadLocal();

    public enum RoutingPolicy {
        /* Choose read-only pools randomly. */
        RANDOM,
        /* Rotate through all available read-only pools. */
        ROUND_ROBIN,
        /* Rotate through all available read-only pools within each thread. */
        THREAD_ROUND_ROBIN
    };

    class ReadSlot {
        private TransactionRouter owner;
        private int index;
        private DataSource dataSource;
        private AtomicLong blacklistedUntil;

        public ReadSlot(TransactionRouter owner, int index, DataSource dataSource) {
            this.owner = owner;
            this.index = index;
            this.dataSource = dataSource;
            this.blacklistedUntil = new AtomicLong(-1);
        }

        public boolean isBlacklisted() {
            long time;
            do {
                time = blacklistedUntil.get();
                if (time == -1) {
                     return false;
                } else if (time > System.currentTimeMillis()) {
                     return true;
                }
            } while (!blacklistedUntil.compareAndSet(time, -1));
            return false;
        }

        public void blacklist() {
            blacklistedUntil.set(System.currentTimeMillis() + owner.getBlacklistTimeMillis());
        }

        public int getIndex() {
            return index;
        }

        public DataSource getDataSource() {
            return dataSource;
        }
    };

    public void setRoutingPolicy(RoutingPolicy routingPolicy) {
        this.routingPolicy = routingPolicy;
    }

    public void setWriteDataSource(DataSource writeDataSource) {
        this.writeDataSource = writeDataSource;
    }

    public void setReadDataSources(DataSource[] readDataSources) {
        this.readSlots = new ReadSlot[readDataSources.length];
        for (int i = 0; i < readDataSources.length; ++i) {
            this.readSlots[i] = new ReadSlot(this, i, readDataSources[i]);
        }
    }

    public void setBlacklistTimeMillis(long blacklistTimeMillis) {
        this.blacklistTimeMillis = blacklistTimeMillis;
    }

    public long getBlacklistTimeMillis() {
        return blacklistTimeMillis;
    }

    /**
     * Set the read-only flag for the current thread.
     *
     * This should ideally be called automatically by
     * DeclarativeRoutingInterceptor, AdaptiveRoutingInterceptor or similar
     * AOP mechanisms.
     */
    public void readOnly(boolean value) {
        readOnly.set(value);
    }

    /**
     * Blacklist the read DataSource most recently obtained by the current
     * thread.
     *
     * This should ideally be called automatically by
     * DeclarativeRoutingInterceptor, AdaptiveRoutingInterceptor or similar
     * AOP mechanisms.
     */
    public void blacklist() {
        if (currentReadSlot.get() != null) {
            currentReadSlot.get().blacklist();
        }
    }

    private DataSource chooseDataSource() {
        if (readSlots == null || writeDataSource == null) {
            throw new RuntimeException("not configured");
        }
        if (Boolean.TRUE.equals(readOnly.get()) && readSlots.length > 0) {
            int start;

            if (routingPolicy == RoutingPolicy.THREAD_ROUND_ROBIN && currentReadSlot.get() != null) {
                start = (currentReadSlot.get().getIndex() + 1) % readSlots.length;
            } else if (routingPolicy == RoutingPolicy.ROUND_ROBIN) {
                int next;
                do {
                    start = (int) roundRobinNext.get();
                    next = (start + 1) % readSlots.length;
                } while (!roundRobinNext.compareAndSet(start, next));
            } else {
                start = random.nextInt(readSlots.length);
            }

            for (int i = 0; i < readSlots.length; i++) {
                ReadSlot readSlot = readSlots[(start + i) % readSlots.length];
                if (!readSlot.isBlacklisted()) {
                    currentReadSlot.set(readSlot);
                    return readSlot.dataSource;
                }
            }
        }
        currentReadSlot.set(null);
        return writeDataSource;
    }

    public Connection getConnection() throws SQLException {
        return chooseDataSource().getConnection();
    }

    public Connection getConnection(String username, String password) throws SQLException {
        return chooseDataSource().getConnection(username, password);
    }

    /* TODO: The following should forward to the wrapped DataSource(s)? */

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }

    public void setLogWriter(PrintWriter out) throws SQLException {
    }

    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    public int getLoginTimeout() {
        return 0;
    }

    public void setLoginTimeout(int seconds) {
    }
};
