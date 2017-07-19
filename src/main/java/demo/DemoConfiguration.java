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
@ImportResource({"classpath:/aop-config.xml",
                 "classpath:/database-config.xml"})
public class DemoConfiguration {
    public static void main(String[] args) {
        SpringApplication.run(DemoConfiguration.class, args);
    }
}
