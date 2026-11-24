package bo.uagrm.bolsa;

import bo.uagrm.bolsa.config.DatabaseManager;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class VerificarBdEstadoTest {

    @Test
    public void verificar() throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            System.out.println("=== 1. COLUMNAS DE LA VISTA v_candidatos_oferta ===");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT column_name, data_type FROM information_schema.columns " +
                    "WHERE table_name = 'v_candidatos_oferta' ORDER BY ordinal_position")) {
                while (rs.next()) {
                    System.out.printf("  - %-20s : %s%n", rs.getString(1), rs.getString(2));
                }
            }

            System.out.println("\n=== 2. CONSTRAINT DE ETAPAS EN SEGUIMIENTOS ===");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint " +
                    "WHERE conname = 'seguimientos_etapa_check'")) {
                while (rs.next()) {
                    System.out.println("  " + rs.getString(1) + " -> " + rs.getString(2));
                }
            }

            System.out.println("\n=== 3. CONSTRAINT DE ESTADOS EN POSTULACIONES ===");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint " +
                    "WHERE conname = 'postulaciones_estado_check'")) {
                while (rs.next()) {
                    System.out.println("  " + rs.getString(1) + " -> " + rs.getString(2));
                }
            }

            System.out.println("\n=== 4. REGISTROS ACTUALES EN POSTULACIONES ===");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT id, estudiante_id, oferta_id, estado, fecha_postulacion FROM postulaciones")) {
                while (rs.next()) {
                    System.out.printf("  Postulacion #%d: Oferta #%d | Estudiante #%d | Estado: %s%n",
                            rs.getInt(1), rs.getInt(3), rs.getInt(2), rs.getString(4));
                }
            }

            System.out.println("\n=== 5. REGISTROS ACTUALES EN SEGUIMIENTOS ===");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT id, postulacion_id, etapa, evaluador, calificacion, observaciones FROM seguimientos")) {
                while (rs.next()) {
                    System.out.printf("  Seguimiento #%d: Postulacion #%d | Etapa: %s | Evaluador: %s | Calificacion: %s | Obs: %s%n",
                            rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getObject(5), rs.getString(6));
                }
            }
        }
    }
}
