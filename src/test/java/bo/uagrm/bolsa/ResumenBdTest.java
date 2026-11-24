package bo.uagrm.bolsa;

import bo.uagrm.bolsa.config.DatabaseManager;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class ResumenBdTest {
    @Test
    public void imprimirResumen() throws Exception {
        String[] tablas = {
            "roles", "usuarios", "estudiantes", "empresas",
            "habilidades", "estudiante_habilidades", "areas_laborales",
            "planes", "suscripciones", "pagos", "facturas",
            "ofertas", "oferta_habilidades", "postulaciones",
            "seguimientos", "entrevistas", "moderaciones"
        };

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            System.out.println("=== ESTADO ACTUAL DE TABLAS EN db_grupo04sc ===");
            for (String t : tablas) {
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + t)) {
                    if (rs.next()) {
                        System.out.printf("  - %-25s: %3d registros%n", t, rs.getInt(1));
                    }
                } catch (Exception ex) {
                    System.out.printf("  - %-25s: ERROR (%s)%n", t, ex.getMessage());
                }
            }
        }
    }
}
