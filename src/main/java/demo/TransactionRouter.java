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
 * Supported routing policies are:
 *
 * RANDOM: read-only queries are randomly distributed
 * ROUND_ROBIN: read-only queries are rotated through all read-only servers
 * THREAD_ROUND_ROBIN: thread-local round-robin
 *
 * Client code should call TransactionRouter.readOnly(...) to indicate whether
 * the next connection should be from a read-only connection pool.  The
 * intended way to do that is by configuring TransactionRoutingInterceptor
 * to read Spring @Transactional(readOnly=X) annotations.  Client code should
 * call TransactionRouter.blacklist() to indicate that the most recently
 * accessed server should not be used again for a backoff period.  Again, that
 * can be done automatically by TransactionRoutingInterceptor.
 */
public class TransactionRouter implements DataSource {
    private DataSource writeDataSource;
    private ReadSlot[] readSlots;
    private Random random = new Random();
    private int routingPolicy = RANDOM;
    private long blacklistTime = 5000;
    private AtomicLong roundRobinNext = new AtomicLong(0);
    private static ThreadLocal<Boolean> readOnly = new ThreadLocal();
    private static ThreadLocal<ReadSlot> currentReadSlot = new ThreadLocal();

    public static int THREAD_ROUND_ROBIN = 1;
    public static int ROUND_ROBIN = 2;
    public static int RANDOM = 3;

    class ReadSlot {
        private TransactionRouter owner;
        private int index;
        private DataSource dataSource;
        private AtomicLong blacklistedUntil;
        private long blacklistTime;

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
            blacklistedUntil.set(System.currentTimeMillis() + owner.getBlacklistTime());
        }

        public int getIndex() {
            return index;
        }

        public DataSource getDataSource() {
            return dataSource;
        }
    };

    public void setRoutingPolicy(String routingPolicy) {
        if ("RANDOM".equals(routingPolicy)) {
            this.routingPolicy = RANDOM;
        } else if ("THREAD_ROUND_ROBIN".equals(routingPolicy)) {
            this.routingPolicy = THREAD_ROUND_ROBIN;
        } else if ("ROUND_ROBIN".equals(routingPolicy)) {
            this.routingPolicy = ROUND_ROBIN;
        } else {
            throw new RuntimeException("unknown routing policy");
        }
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

    public void setBlacklistTime(long blacklistTime) {
        this.blacklistTime = blacklistTime;
    }

    public long getBlacklistTime() {
        return blacklistTime;
    }

    /**
     * Set the read-only flag for the current thread.  This should be called
     * by TransactionRoutingInterceptor based on @Transactional annotations.
     */
    public static void readOnly(boolean value) {
        readOnly.set(value);
    }

    /**
     * Blacklist the read DataSource most recently obtained by the current
     * thread.  This should be called by TransactionRoutingInterceptor when
     * certain errors are intercepted.
     */
    public static void blacklist() {
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

            if (routingPolicy == THREAD_ROUND_ROBIN && currentReadSlot.get() != null) {
                start = (currentReadSlot.get().getIndex() + 1) % readSlots.length;
            } else if (routingPolicy == ROUND_ROBIN) {
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
System.out.println("using read pool " + readSlot.index);
                    return readSlot.dataSource;
                }
            }
        }
        currentReadSlot.set(null);
System.out.println("using write pool");
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
