package demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * A simple RESTful key/value store.
 *
 * This is intended as a demonstration of load balancing with PostgreSQL using
 * the proposed synchronous_replay feature.  Nothing in this code is specific
 * to the demo: @Transactional and readOnly are standard Spring annotations
 * that are already used for Spring's declarative transaction support.
 *
 * The special sauce that enables correct handling of retries and routing is
 * in the custom DataSource and the custom 'advice' added to all
 * @Transactional methods, for which see DemoConfiguration.java.
 *
 * Examples of usage, to hit this service from the command line:
 *
 * curl http://localhost:9000/key-value/banana
 * curl -X PUT -H "Content-Type: text/plain" -d 'yellow' localhost:9000/key-value/banana
 */
@Controller
@RequestMapping("/key-value/{key}")
public class KeyValueController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Transactional(readOnly=true)
    @RequestMapping(method=RequestMethod.GET)
    public @ResponseBody KeyValuePair get(@PathVariable String key) {
        String value =
            jdbcTemplate.queryForObject("SELECT value FROM key_value WHERE key = ?",
                                        new Object[] { key }, String.class);

        return new KeyValuePair(key, value);
    }

    @Transactional
    @RequestMapping(method=RequestMethod.PUT)
    public @ResponseBody KeyValuePair put(@PathVariable String key, @RequestBody String value) {
        jdbcTemplate.update("INSERT INTO key_value (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value", key, value);
        return new KeyValuePair(key, value);
    }
}
