package bo.uagrm.bolsa.mail;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bo.uagrm.bolsa.config.Config;

public class MailListener {

    private static final Logger log = LoggerFactory.getLogger(MailListener.class);

    private MailListener() {}

    public static void start() {
        ExecutorService pool = Executors.newFixedThreadPool(Config.MAX_THREADS);

        log.info("Escuchando en {}@{} cada {} ms",
                Config.MAIL_USERNAME, Config.MAIL_SERVER, Config.MAIL_REFRESH_TIME_MS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            pool.shutdown();
            try { pool.awaitTermination(10, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) {}
        }));

        while (!Thread.currentThread().isInterrupted()) {
            procesarBandeja(pool);
            try {
                Thread.sleep(Config.MAIL_REFRESH_TIME_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void procesarBandeja(ExecutorService pool) {
        PopCliente pop = new PopCliente(
                Config.MAIL_SERVER,
                Config.MAIL_USERNAME,
                Config.MAIL_PASSWORD,
                Integer.parseInt(Config.MAIL_POP_PORT)
        );

        try {
            pop.conectar();
            if (!pop.login()) {
                log.error("Login POP3 fallido, se omite este ciclo");
                return;
            }

            int total = pop.contarEmails();
            log.info("Correos en bandeja: {}", total);

            for (int i = 1; i <= total; i++) {
                String raw = pop.leer(i);
                if (raw != null && !raw.isBlank()) {
                    pop.borrar(i);
                    pool.submit(new RespuestaWorker(raw));
                }
            }

        } catch (Exception e) {
            log.error("Error al procesar bandeja: {}", e.getMessage());
        } finally {
            try { pop.desconectar(); } catch (Exception ignored) {}
        }
    }
}
