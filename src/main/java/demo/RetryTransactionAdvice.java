package demo;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import java.sql.SQLException;

/*
 * Advice to retry a method invocation if certain error codes are raised by
 * PostgreSQL.  Of main interest for this demo is the new error 40P02 raised
 * by the synchronous_replay patch, but also handles a couple of others just
 * to make the point that this is a generally useful mechanism.
 *
 * This should be configurable, but has hard-coded parameters for now.
 * It may be possible to use the RetryAdvice that comes built-in to Spring for
 * this, to avoid writing any new code?
 */
public class RetryTransactionAdvice implements MethodInterceptor {
    public Object invoke(MethodInvocation i) throws Throwable {
        int retries = 0;
        for (;;) {
            try {
                return i.proceed();
            } catch (SQLException e) {
                if (retries == 3) {
                   throw e; // too many retries
                }

                if (e.getSQLState().equals("40001") || /* serialization fail */
                    e.getSQLState().equals("40P01") || /* deadlock */
                    e.getSQLState().equals("40P02")) { /* sync replay fail */
                    System.out.println("retrying!");
                    ++retries;
                    continue; // retry
                }
                throw e; // not an error we know how to handle
            }
        }
    }
}
