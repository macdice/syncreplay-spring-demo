package demo;

import java.lang.reflect.Method;
import java.sql.SQLException;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;               

/*
 * Advice that manages connection routing, in cooperation with
 * ReadBalancingDataSource.  Takes the readOnly property from a Transactional
 * annotation and tells the ReadBalancingDataSource that it can feel free to
 * send any queries run by this thread to a read-only database.  If a 40P02
 * error is raised indicating that a read-only server is not able to handle
 * synchronous replay transactions currently, then tell the
 * ReadBalancingDataSource to blacklist it for a while.
 *
 * This needs to be configured to run *before* the Spring transaction
 * management, so that the read-only property can be passed to the
 * ReadBalancingDataSource before a connection is obtained.
 */
public class TransactionRouteInterceptor implements MethodInterceptor {
    public Object invoke(MethodInvocation i) throws Throwable {
        Method method = i.getMethod();
        Transactional transactional = method.getAnnotation(Transactional.class);
        ReadBalancingDataSource.setReadOnly(transactional.readOnly());
        try {
            return i.proceed();
        } catch (ConcurrencyFailureException e) {
            if (e.getRootCause() instanceof SQLException) {
                SQLException s = (SQLException) e.getRootCause();
                if (s.getSQLState().equals("40P02")) {
                    ReadBalancingDataSource.blacklistCurrentReadPool();
                }
            }
            throw e;
        }
    }
}
