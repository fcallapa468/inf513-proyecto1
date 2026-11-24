package bo.uagrm.bolsa.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static final HikariDataSource ds = buildDataSource();

    private DatabaseManager() {}

    private static HikariDataSource buildDataSource() {
        HikariConfig cfg = new HikariConfig();
        
        String url = Config.DB_URL;
        if (!url.contains("sslmode=")) {
            url += (url.contains("?") ? "&" : "?") + "sslmode=disable";
        }
        cfg.setJdbcUrl(url);
        cfg.setUsername(Config.DB_USER);
        cfg.setPassword(Config.DB_PASSWORD);
        cfg.setDriverClassName(Config.DB_DRIVER);

        cfg.setMaximumPoolSize(Config.MAX_THREADS + 1);
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(30_000);
        cfg.setValidationTimeout(5_000);
        cfg.setIdleTimeout(600_000);
        cfg.setMaxLifetime(1_800_000);
        cfg.setInitializationFailTimeout(60_000);
        cfg.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        cfg.setPoolName("bolsa-pool");

        log.info("Pool HikariCP inicializado -> {}", url);
        return new HikariDataSource(cfg);
    }

    public static Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public static void shutdown() {
        if (ds != null && !ds.isClosed()) {
            ds.close();
            log.info("Pool HikariCP cerrado correctamente.");
        }
    }
}
