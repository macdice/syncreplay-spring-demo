package demo;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.PrintWriter;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import javax.sql.DataSource;

/**
 * A toy load balancing DataSource implementation.
 *
 * Manages a set of other DataSources.  For now, hard coded to use Hikari and
 * PostgreSQL!  Should instead use dependency injection to configure the
 * managed pools.  How?
 */
public class ReadBalancingDataSource implements DataSource {
    private DataSource writePool;
    private ReadDataSource[] readPools;
    private static ThreadLocal<Boolean> readOnly = new ThreadLocal();
    private static ThreadLocal<ReadDataSource> currentReadPool = new ThreadLocal();
    private Random random = new Random();

    class ReadDataSource {
        public ReadDataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            this.blacklistedUntil = new AtomicLong(-1);
        }
        DataSource dataSource;
        AtomicLong blacklistedUntil;

        public boolean isBlacklisted() {
            long currentBlacklistedUntil;
            do {
                currentBlacklistedUntil = this.blacklistedUntil.get();

                if (currentBlacklistedUntil == -1) {
                    return false;
                }

                if (currentBlacklistedUntil < blacklistClockTime()) {
                    return true;
                }
            } while (!blacklistedUntil.compareAndSet(currentBlacklistedUntil, -1)); // CAS loop
            // if we are here that means we successfully marked datasource as non black listed
            return false;
        }

        public void blacklist() {
            blacklistedUntil.set(System.currentTimeMillis() + 5000);
        }

        private long blacklistClockTime() {
            return System.currentTimeMillis();
        }
    }

    public void setWriteUrl(String url) {
        /* TODO: defer until first getConnection call */
        HikariConfig config = new HikariConfig();
        config.setConnectionInitSql("SET synchronous_replay = on");
        config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl(url);
        writePool = new HikariDataSource(config);
    }

    public void setReadUrls(String urls) {
        /* TODO: defer until first getConnection call */
        String[] split = urls.split(",");
        readPools = new ReadDataSource[split.length];
        HikariConfig config = new HikariConfig();
        config.setConnectionInitSql("SET synchronous_replay = on");
        config.setDriverClassName("org.postgresql.Driver");
        for (int i = 0; i < split.length; ++i) {
            config.setJdbcUrl(split[i]);
            readPools[i] = new ReadDataSource(new HikariDataSource(config));
        }
    }

    /**
     * Set the read-only flag for the current thread.  Should be called
     * by advice wrapping transactional methods.
     */
    public static void setReadOnly(boolean value) {
        readOnly.set(value);
    }

    private DataSource chooseDataSource() {
        if (Boolean.TRUE.equals(readOnly.get())) {
            int start = random.nextInt(readPools.length);
            for(int i = 0; i < readPools.length; i++) {
                int offsettedIndex = i + start;
                int wrappedIndex = offsettedIndex >= readPools.length ? offsettedIndex - readPools.length : offsettedIndex;
                ReadDataSource readPool = readPools[wrappedIndex];
                if (!readPool.isBlacklisted()) {
                    currentReadPool.set(readPool);
System.out.println("using read pool " + wrappedIndex);
                    return readPool.dataSource;
                }
            }
            currentReadPool.set(null);
System.out.println("falling back to write pool");
            return writePool;
         } else {
            currentReadPool.set(null);
System.out.println("using write pool");
            return writePool;
        }
    }

    public static void blacklistCurrentReadPool() {
        if (currentReadPool.get() != null) {
            currentReadPool.get().blacklist();
        }
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
