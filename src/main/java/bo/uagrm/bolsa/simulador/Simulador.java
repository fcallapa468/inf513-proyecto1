package bo.uagrm.bolsa.simulador;

import bo.uagrm.bolsa.commands.CommandResponse;
import bo.uagrm.bolsa.mail.RespuestaWorker;

import java.awt.Desktop;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Scanner;

public class Simulador {

    public static void main(String[] args) {
        RespuestaWorker.MODO_SIMULADOR = true;
        System.out.println("================================================================================");
        System.out.println("       SIMULADOR DE COMANDOS POR CORREO - BOLSA DE TRABAJOS UAGRM");
        System.out.println("                       Grupo 04 - TecnoWeb");
        System.out.println("================================================================================");
        System.out.println(" • Base de Datos: mail.tecnoweb.org.bo:5432 / db_grupo04sc (PostgreSQL Real)");
        System.out.println(" • Escribe tus comandos con o sin punto y coma final (ej: HELP; o VER_PLANES;)");
        System.out.println(" • Para cambiar el remitente simulado:  remitente: mi_correo@gmail.com");
        System.out.println(" • Para salir:                          salir");
        System.out.println("================================================================================\n");

        Scanner scanner = new Scanner(System.in);
        String remitente = "usuario.prueba@gmail.com";

        while (true) {
            System.out.print("[" + remitente + "] > ");
            if (!scanner.hasNextLine()) break;
            String linea = scanner.nextLine();
            if (linea == null) break;

            String trimmed = linea.trim();
            if (trimmed.equalsIgnoreCase("salir") || trimmed.equalsIgnoreCase("exit")) {
                System.out.println("Cerrando simulador...");
                break;
            }
            if (trimmed.isEmpty()) continue;

            if (trimmed.toLowerCase().startsWith("remitente:") || trimmed.toLowerCase().startsWith("set_remitente:")) {
                String nuevo = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                if (!nuevo.isEmpty()) {
                    remitente = nuevo;
                    System.out.println(">> Remitente simulado cambiado a: " + remitente + "\n");
                }
                continue;
            }

            long inicio = System.currentTimeMillis();
            try {
                CommandResponse resp = RespuestaWorker.procesarComandoDirecto(remitente, trimmed);
                long tiempo = System.currentTimeMillis() - inicio;

                System.out.println("\n----------------------- [RESPUESTA DEL SISTEMA (TEXTO)] -----------------------");
                System.out.println(resp.getMessage());
                System.out.println("-------------------------------------------------------------------------------");
                System.out.printf("Estado: %s | Tiempo de ejecucion: %d ms\n", resp.isSuccess() ? "EXITO" : "ERROR", tiempo);

                if (resp.getHtmlMessage() != null && !resp.getHtmlMessage().isBlank()) {
                    File htmlFile = new File("ultima_respuesta.html");
                    Files.writeString(htmlFile.toPath(), resp.getHtmlMessage(), StandardCharsets.UTF_8);
                    System.out.println(">> Correo HTML generado en: " + htmlFile.getAbsolutePath());
                    try {
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(htmlFile.toURI());
                            System.out.println(">> Abierto automaticamente en tu navegador web.");
                        }
                    } catch (Exception ex) {
                        System.out.println(">> Puedes abrir 'ultima_respuesta.html' en tu navegador para ver el diseno.");
                    }
                }
                System.out.println();
            } catch (Exception e) {
                System.err.println("\n[ERROR DE EJECUCION]: " + e.getMessage());
                e.printStackTrace();
                System.out.println();
            }
        }
    }
}
