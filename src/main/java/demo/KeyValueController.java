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
 * the proposed synchronous_replay feature.
 *
 * Examples of usage:
 *
 * curl http://localhost:9000/key-value/banana
 * curl -X PUT -H "Content-Type: text/plain" -d 'yellow' localhost:9000/key-value/banana
 */
@Controller
@RequestMapping("/key-value/{key}")
public class KeyValueController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /*
     * TODO #1: Figure out how to put an annotation here that says this is read
     * only.  It needs to be AOP 'around' advice, and it needs to call
     * ReadBalancingDataSource.setRead(true) before and ...(false) after.
     * Ideally using Spring @Transactional(readOnly=true)
     *
     * TODO #2: Figure out how to intercept exceptions.  Then look out for
     * PostgreSQL error 40P02 "synchronous_replay is not available".  If it is
     * caught, then blacklist the server that it came from
     * (ReadBalancingDataSource.backoff(connection.somehowGetPool()),  and
     * retry the whole request up to N times.  Ideally without hardcoding
     * 40P02.  There are other errors that it makes sense to retry (but not
     * blacklist) for too: 40P01 (deadlock), 40001 (serialization failure).
     * This behaviour should be achievable with 'around' advice, and would
     * probably be best to configure generally rather than having to add
     * annotations.
     */
    @Transactional(readOnly=true)
    @RequestMapping(method=RequestMethod.GET)
    public @ResponseBody KeyValuePair get(@PathVariable String key) {
        // TODO figure out how to generate a 404 if key not found
        String value =
            jdbcTemplate.queryForObject("SELECT value FROM key_value WHERE key = ?",
                                        new Object[] { key }, String.class);

        return new KeyValuePair(key, value);
    }

    @Transactional()
    @RequestMapping(method=RequestMethod.PUT)
    public @ResponseBody KeyValuePair put(@PathVariable String key, @RequestBody String value) {
        jdbcTemplate.update("INSERT INTO key_value (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value", key, value);
        return new KeyValuePair(key, value);
    }
}
