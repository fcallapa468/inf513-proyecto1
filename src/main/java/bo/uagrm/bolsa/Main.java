package bo.uagrm.bolsa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bo.uagrm.bolsa.config.Config;
import bo.uagrm.bolsa.config.DatabaseManager;
import bo.uagrm.bolsa.mail.MailListener;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {

        log.info("========================================");
        log.info("  BOLSA DE TRABAJOS UAGRM - GRUPO 04    ");
        log.info("========================================");
        log.info("DB URL      : {}", Config.DB_URL);
        log.info("DB USER     : {}", Config.DB_USER);
        log.info("MAIL SERVER : {}", Config.MAIL_SERVER);
        log.info("MAIL USER   : {}", Config.MAIL_USERNAME);
        log.info("REFRESH MS  : {}", Config.MAIL_REFRESH_TIME_MS);

        // Verificar conexión a la Base de Datos
        try (var conn = DatabaseManager.getConnection()) {
            log.info("Conexión a PostgreSQL (TecnoWeb): OK");
        } catch (Exception e) {
            log.error("No se pudo conectar a la base de datos: {}", e.getMessage());
            System.exit(1);
        }

        // Hook para cerrar el pool de conexiones al terminar
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Apagando sistema de correo...");
            DatabaseManager.shutdown();
        }));

        // Iniciar el listener de correos
        MailListener.start();
    }
}
