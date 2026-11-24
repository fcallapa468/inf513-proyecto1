package bo.uagrm.bolsa;

import bo.uagrm.bolsa.commands.CommandResponse;
import bo.uagrm.bolsa.config.DatabaseManager;
import bo.uagrm.bolsa.mail.RespuestaWorker;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class PipelineEtapasTest {

    @Test
    public void testFlujoEtapasHumano() throws Exception {
        // 1. Probar PRESELECCIONADO
        CommandResponse r1 = RespuestaWorker.procesarComandoDirecto(
                "rrhh@tigo.com.bo",
                "EVALUAR_CANDIDATO[1, PRESELECCIONADO, Excelente perfil técnico y buen portafolio];"
        );
        System.out.println("Respuesta R1: " + r1.getMessage());
        assertTrue(r1.isSuccess(), "Debería evaluar a PRESELECCIONADO con éxito");

        // Verificar en BD
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT estado FROM postulaciones WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("PRESELECCIONADO", rs.getString("estado"));
            }
        }

        // 2. Probar sinónimo DESCARTADO -> debe normalizarse a NO_SELECCIONADO
        CommandResponse r2 = RespuestaWorker.procesarComandoDirecto(
                "rrhh@tigo.com.bo",
                "EVALUAR_CANDIDATO[1, DESCARTADO, Se requiere disponibilidad en turno mañana];"
        );
        System.out.println("Respuesta R2: " + r2.getMessage());
        assertTrue(r2.isSuccess(), "Debería mapear DESCARTADO a NO_SELECCIONADO");

        // Verificar en BD que el estado quedó como NO_SELECCIONADO
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT estado FROM postulaciones WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("NO_SELECCIONADO", rs.getString("estado"));
            }
        }

        // 3. Probar VER_CANDIDATOS[1]
        CommandResponse r3 = RespuestaWorker.procesarComandoDirecto(
                "rrhh@tigo.com.bo",
                "VER_CANDIDATOS[1];"
        );
        System.out.println("Respuesta R3: " + r3.getMessage());
        assertTrue(r3.isSuccess(), "VER_CANDIDATOS debe ejecutarse correctamente sobre la nueva vista");
        assertTrue(r3.getMessage().contains("ETAPA_ACTUAL") || r3.getMessage().contains("NO_SELECCIONADO"));
    }
}
