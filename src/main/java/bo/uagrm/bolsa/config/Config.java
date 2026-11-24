package bo.uagrm.bolsa.config;

import io.github.cdimascio.dotenv.Dotenv;

public final class Config {

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    // =========================
    // DATABASE
    // =========================

    public static final String DB_HOST =
            dotenv.get("DB_HOST", "mail.tecnoweb.org.bo");

    public static final String DB_PORT =
            dotenv.get("DB_PORT", "5432");

    public static final String DB_NAME =
            dotenv.get("DB_NAME", "db_grupo04sc");

    public static final String DB_USER =
            dotenv.get("DB_USER", "grupo04sc");

    public static final String DB_PASSWORD =
            dotenv.get("DB_PASSWORD", "grup004grup004*");

    public static final String DB_URL =
            "jdbc:postgresql://"
                    + DB_HOST
                    + ":"
                    + DB_PORT
                    + "/"
                    + DB_NAME;

    public static final String DB_DRIVER =
            "org.postgresql.Driver";

    // =========================
    // MAIL
    // =========================

    public static final String MAIL_SERVER =
            dotenv.get("MAIL_SERVER", "mail.tecnoweb.org.bo");

    public static final String MAIL_POP_PORT =
            dotenv.get("MAIL_POP_PORT", "110");

    public static final String MAIL_SMTP_PORT =
            dotenv.get("MAIL_SMTP_PORT", "25");

    public static final String MAIL_USERNAME =
            dotenv.get("MAIL_USERNAME", "grupo04sc");

    public static final String MAIL_PASSWORD =
            dotenv.get("MAIL_PASSWORD", "grup004grup004*");

    // =========================
    // APP
    // =========================

    public static final int MAIL_REFRESH_TIME_MS =
            Integer.parseInt(
                    dotenv.get("MAIL_REFRESH_TIME_MS", "12000")
            );

    public static final int MAX_THREADS =
            Integer.parseInt(
                    dotenv.get("MAX_THREADS", "5")
            );

    // =========================
    // PAGO FACIL
    // =========================

    public static final String PAGOFACIL_TOKEN_SERVICE =
            dotenv.get("PAGOFACIL_TOKEN_SERVICE");

    public static final String PAGOFACIL_TOKEN_SECRET =
            dotenv.get("PAGOFACIL_TOKEN_SECRET");

    public static final String PAGOFACIL_BASE_URL =
            dotenv.get("PAGOFACIL_BASE_URL", "https://masterqr.pagofacil.com.bo/api/services/v2");

    private Config() {}
}
