package demo;

import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@ImportResource("classpath:/aop-config.xml")
public class DemoConfiguration {

	public static void main(String[] args) {
		SpringApplication.run(DemoConfiguration.class, args);
	}

    /*
     * We have a custom DataSource that knows about load balancing.  We also
     * have 'advice' defined in aop-config.xml which reacts to @Transactional
     * notation and makes sure that our ReadBalancingDataSource knows whether
     * we're in a read-only transaction, and can deal with retries and
     * rerouting.
     */
    @ConfigurationProperties(prefix = "app.datasource")
    @Bean
    public DataSource dataSource() {
        return new ReadBalancingDataSource();
    }
}
