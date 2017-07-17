package demo;

import java.lang.Class;
import java.lang.reflect.Method;
import org.aopalliance.aop.Advice;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.StaticMethodMatcherPointcut;            
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/*
 * An advisor that will enable TransactionRoutingInterceptor on any method
 * that is annotated as Transactional.
 */
@Component
public class TransactionRoutingAdvisor extends AbstractPointcutAdvisor {
    private final StaticMethodMatcherPointcut pointcut = new
        StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                return method.isAnnotationPresent(Transactional.class);
            }
        };

    @Autowired
    private TransactionRoutingInterceptor interceptor;

    @Override
    public Pointcut getPointcut() {
        return this.pointcut;
    }

    @Override
    public Advice getAdvice() {
        return this.interceptor;
    }
}
