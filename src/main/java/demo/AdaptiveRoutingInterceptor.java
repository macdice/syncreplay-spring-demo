package demo;

import java.lang.reflect.Method;
import java.lang.ThreadLocal;
import java.sql.SQLException;
import java.util.Hashtable;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.stereotype.Component;

/**
 * Advice that learns whether a method is read-only and tells the
 * TransactionRouter, for routing purposes.  It also tells the
 * TransactionRouter to blacklist the most recently accessed server for a
 * period of time if it is known to be unable to serve synchronous_replay
 * transactions currently.
 *
 * Initially all methods are considered to represent read-only transactions.
 * When a 25006 error is intercepted, the method is recorded as not read-only
 * for future invocations.  In that case TransientDataAccessResourceException
 * is thrown, which could be caught by application code or by something like
 * Spring Retry if configured.
 */
public class AdaptiveRoutingInterceptor implements MethodInterceptor {
    TransactionRouter transactionRouter;
    ThreadLocal<Hashtable<Method, Boolean>> localTable;
    Hashtable<Method, Boolean> table;

    public AdaptiveRoutingInterceptor(TransactionRouter transactionRouter) {
        this.transactionRouter = transactionRouter;
        this.localTable = new ThreadLocal();
        this.table = new Hashtable();
    }

    public Object invoke(MethodInvocation i) throws Throwable {
        boolean readOnly;
        Method method = i.getMethod();

        // create thread-local cache on demand
        if (localTable.get() == null) {
            localTable.set(new Hashtable());
        }

        if (localTable.get().get(method) == null) {
            // not in thread-local cache: go to shared table, defaulting to
            // assumption that this method is read-only until we see evidence
            // otherwise
            synchronized (this) {
                readOnly = table.getOrDefault(method, Boolean.TRUE);
            }
            localTable.get().put(method, Boolean.TRUE);
        } else {
            readOnly = localTable.get().get(method);
        }

        transactionRouter.readOnly(readOnly);

        try {
            return i.proceed();
        } catch (UncategorizedSQLException e) {
            if (e.getRootCause() instanceof SQLException) {
                SQLException s = (SQLException) e.getRootCause();
                if (s.getSQLState().equals("25006")) {
                    // remember thread that the method is not read-only in
                    // both thread-local and shared table
                    localTable.get().put(method, Boolean.FALSE);
                    synchronized (this) {
                        table.put(method, Boolean.FALSE);
                    }
                    // throw an exception that can be used as a signal to
                    // retry using eg Spring Retry
                    throw new TransientDataAccessResourceException("Can't run this transactional method on a read-only server; future invocations will be routed to a writable server", e.getRootCause());
                }
            }
            throw e;
        } catch (ConcurrencyFailureException e) {
            if (e.getRootCause() instanceof SQLException) {
                SQLException s = (SQLException) e.getRootCause();
                if (s.getSQLState().equals("40P02")) {
                    transactionRouter.blacklist();
                }
            }
            throw e;
        }
    }
}
