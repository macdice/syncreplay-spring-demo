package demo;

import java.lang.reflect.Method;
import java.sql.SQLException;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;               

/*
 * Advice that tells TransactionRouter whether this thread's current
 * transaction is read-only, for routing purposes.  It also tells
 * TransactionRouter to blacklist the most recently accessed server for a
 * period of time if it is known to be unable to serve synchronous_replay
 * transactions currently.
 */
public class TransactionRoutingInterceptor implements MethodInterceptor {
    TransactionRouter transactionRouter;

    public void setTransactionRouter(TransactionRouter transactionRouter) {
        this.transactionRouter = transactionRouter;
    }

    public Object invoke(MethodInvocation i) throws Throwable {
        Method method = i.getMethod();
        Transactional transactional = method.getAnnotation(Transactional.class);
        transactionRouter.readOnly(transactional != null && transactional.readOnly());
        try {
            return i.proceed();
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
