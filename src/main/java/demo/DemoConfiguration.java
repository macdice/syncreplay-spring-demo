package demo;

import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DemoConfiguration {

	public static void main(String[] args) {
		SpringApplication.run(DemoConfiguration.class, args);
	}

    @ConfigurationProperties(prefix = "app.datasource")
    @Bean
    public DataSource dataSource() {
        return new ReadBalancingDataSource();
    }
}
