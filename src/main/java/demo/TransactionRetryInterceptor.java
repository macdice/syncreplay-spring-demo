package demo;

import java.sql.SQLException;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;                               

/*
 * Advice to retry a method invocation if certain error codes are raised by
 * PostgreSQL.  Of main interest for this demo is the new error 40P02 raised
 * by the synchronous_replay patch, but also handles all 40* class errors
 * (reported by Spring as ConcurrencyFailureException), since this in in
 * theory also the right thing to do for serialiazation failures and
 * deadlocks.
 *
 * This should be configurable (or be replaced with Spring's RetryOperations),
 * but it's easy to understand as a few lines of code, for demonstration
 * purposes only.
 *
 * This needs to be configured to run *before* TransactionRouteInterceptor,
 * so that when we retry after a 40P02 error we'll be able to try again on
 * another server.
 */
public class TransactionRetryInterceptor implements MethodInterceptor {
    public Object invoke(MethodInvocation i) throws Throwable {
        for (int retries = 0;; ++retries) {
            try {
                return i.proceed();
            } catch (ConcurrencyFailureException e) {
                if (retries == 3) {
                    throw e;
                } else {
                    continue;
                }
            }
        }
    }
}
