package demo;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import java.sql.SQLException;

public class RouteTransactionAdvice implements MethodInterceptor {
    public Object invoke(MethodInvocation i) throws Throwable {
        // TODO: fish out the Transactional(readOnly=X) annotation and
        // call ReadBalancingDataSource.setReadOnly(X)!
        try {
            return i.proceed();
        } catch (SQLException e) {
            if (e.getSQLState().equals("40P02")) {
                ReadBalancingDataSource.blacklistCurrentReadPool();
            }
            throw e;
        }
    }
}
