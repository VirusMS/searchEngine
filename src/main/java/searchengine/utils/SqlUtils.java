package searchengine.utils;

import org.springframework.jdbc.core.JdbcTemplate;

import java.text.SimpleDateFormat;

public class SqlUtils {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static boolean sendBatchRequest(JdbcTemplate jdbcTemplate, Integer batchSize, Integer maxBatchSize, String command, StringBuilder values, int startPageId) {
        int curBatchSize = batchSize;

        if (++curBatchSize >= maxBatchSize && !values.isEmpty()) {
            //System.out.println("DEBUG (sendBatchRequest): current SQL request: \n" + command + values);
            jdbcTemplate.execute(command + values);

            String updateSql = "UPDATE site SET status_time = '"
                    + dateFormat.format(System.currentTimeMillis()) + "' WHERE id = " + startPageId;
            jdbcTemplate.execute(updateSql);

            return true;
        }
        return false;
    }

    public static void sendLastRequest(JdbcTemplate jdbcTemplate, String command, String values) {
        if (!values.isEmpty()) {
            jdbcTemplate.execute(command + values);
        }
    }
}
