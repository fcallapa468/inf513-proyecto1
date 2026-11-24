package bo.uagrm.bolsa;

import bo.uagrm.bolsa.config.DatabaseManager;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.Statement;

public class MigracionEtapasTest {
    @Test
    public void aplicarCambios() throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            System.out.println("1. Actualizando CHECK de seguimientos...");
            stmt.execute("ALTER TABLE seguimientos DROP CONSTRAINT IF EXISTS seguimientos_etapa_check");
            stmt.execute("ALTER TABLE seguimientos ADD CONSTRAINT seguimientos_etapa_check " +
                    "CHECK (etapa IN ('REVISION_CV', 'PRESELECCIONADO', 'ENTREVISTA', 'CONTRATADO', 'NO_SELECCIONADO', 'DESCARTADO', 'PRUEBA_TECNICA', 'ENTREVISTA_RRHH', 'ENTREVISTA_TECNICA'))");

            System.out.println("2. Asegurando calificacion nullable en seguimientos...");
            stmt.execute("ALTER TABLE seguimientos ALTER COLUMN calificacion DROP NOT NULL");

            System.out.println("3. Actualizando CHECK de postulaciones...");
            stmt.execute("ALTER TABLE postulaciones DROP CONSTRAINT IF EXISTS postulaciones_estado_check");
            stmt.execute("ALTER TABLE postulaciones ADD CONSTRAINT postulaciones_estado_check " +
                    "CHECK (estado IN ('POSTULADO', 'REVISION_CV', 'PRESELECCIONADO', 'ENTREVISTA', 'CONTRATADO', 'NO_SELECCIONADO', 'EN_REVISION', 'RECHAZADO', 'DESCARTADO'))");

            System.out.println("4. Migrando registros existentes de 'DESCARTADO' a 'NO_SELECCIONADO'...");
            stmt.execute("UPDATE seguimientos SET etapa = 'NO_SELECCIONADO' WHERE etapa IN ('DESCARTADO', 'RECHAZADO')");
            stmt.execute("UPDATE postulaciones SET estado = 'NO_SELECCIONADO' WHERE estado IN ('DESCARTADO', 'RECHAZADO')");

            System.out.println("5. Actualizando vista v_candidatos_oferta (eliminando calificacion_promedio)...");
            stmt.execute("DROP VIEW IF EXISTS v_candidatos_oferta CASCADE");
            stmt.execute("CREATE VIEW v_candidatos_oferta AS " +
                    "SELECT " +
                    "    p.id AS postulacion_id, " +
                    "    o.id AS oferta_id, " +
                    "    o.titulo AS oferta, " +
                    "    CONCAT(u.nombre, ' ', u.apellido) AS postulante, " +
                    "    est.ru, " +
                    "    est.carrera, " +
                    "    est.semestre, " +
                    "    u.telefono, " +
                    "    u.email, " +
                    "    p.estado AS etapa_actual, " +
                    "    p.fecha_postulacion, " +
                    "    COALESCE(p.cv_adjunto_url, est.cv_url) AS cv_enlace " +
                    "FROM postulaciones p " +
                    "JOIN ofertas o ON p.oferta_id = o.id " +
                    "JOIN estudiantes est ON p.estudiante_id = est.id " +
                    "JOIN usuarios u ON est.usuario_id = u.id");

            System.out.println("MIGRACIÓN COMPLETADA CON ÉXITO EN POSTGRESQL.");
        }
    }
}
