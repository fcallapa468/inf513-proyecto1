package bo.uagrm.bolsa;

import bo.uagrm.bolsa.config.DatabaseManager;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class DropCalificacionBdTest {

    @Test
    public void dropColumnCalificacion() throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            System.out.println("Ejecutando: ALTER TABLE seguimientos DROP COLUMN IF EXISTS calificacion CASCADE;");
            stmt.execute("ALTER TABLE seguimientos DROP COLUMN IF EXISTS calificacion CASCADE");

            System.out.println("Verificando que la columna 'calificacion' ya no exista en seguimientos...");
            boolean existe = false;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_name = 'seguimientos' AND column_name = 'calificacion'")) {
                if (rs.next()) {
                    existe = true;
                }
            }
            assertFalse(existe, "La columna calificacion NO debe existir en la tabla seguimientos");
            System.out.println("COLUMNA 'calificacion' ELIMINADA CON ÉXITO DE POSTGRESQL.");

            System.out.println("Columnas actuales de seguimientos:");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT column_name, data_type FROM information_schema.columns " +
                    "WHERE table_name = 'seguimientos' ORDER BY ordinal_position")) {
                while (rs.next()) {
                    System.out.printf("  - %-20s : %s%n", rs.getString(1), rs.getString(2));
                }
            }
        }
    }
}
