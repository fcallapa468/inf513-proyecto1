package bo.uagrm.bolsa;

import bo.uagrm.bolsa.commands.CommandResponse;
import bo.uagrm.bolsa.mail.RespuestaWorker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MultiCommandTest {

    @Test
    public void testComandoSimple() {
        CommandResponse resp = RespuestaWorker.procesarComandoDirecto("mateo.morales@gmail.com", "VER_PLANES;");
        System.out.println("=== TEST 1 (VER_PLANES) RESULTADO ===");
        System.out.println(resp.getMessage());
        assertTrue(resp.isSuccess(), "VER_PLANES debería ejecutarse con éxito");
        assertTrue(resp.getMessage().contains("PLANES") || resp.getMessage().contains("Prueba Gratuita"), "Debe listar planes");
    }

    @Test
    public void testMultiplesComandos() {
        CommandResponse resp = RespuestaWorker.procesarComandoDirecto("mateo.morales@gmail.com", "VER_PLANES; VER_OFERTAS;");
        System.out.println("=== TEST 2 (MULTIPLE VER_PLANES + VER_OFERTAS) RESULTADO ===");
        System.out.println(resp.getMessage());
        assertTrue(resp.isSuccess(), "Lote de comandos debería ejecutarse con éxito");
        assertTrue(resp.getMessage().contains("ORDEN [1/2]"), "Debe contener el encabezado del comando 1");
        assertTrue(resp.getMessage().contains("ORDEN [2/2]"), "Debe contener el encabezado del comando 2");
    }
}
