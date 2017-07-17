package demo;

import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class DemoConfiguration {

	public static void main(String[] args) {
		SpringApplication.run(DemoConfiguration.class, args);
	}

    /*
     * TODO: What magic incantation is needed to get TransactionRetryAdvisor
     * and TransactionRoutingAdvisor to apply to the methods of all
     * components?  And to control the ordering, so that we have
     * TransactionRetryInterceptor, then TransactionRoutingInterceptor, then
     * Spring's usual transaction interceptor(s) that manage database
     * connections and transactions.
     */

    @ConfigurationProperties(prefix = "app.datasource")
    @Bean
    public DataSource dataSource() {
        return new ReadBalancingDataSource();
    }
}
