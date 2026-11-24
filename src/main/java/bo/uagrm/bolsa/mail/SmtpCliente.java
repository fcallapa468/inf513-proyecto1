package bo.uagrm.bolsa.mail;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmtpCliente {

    private static final Logger log = LoggerFactory.getLogger(SmtpCliente.class);

    private final String servidor;
    private final String emailSistema;
    private final String contrasena;
    private final int puerto;

    public SmtpCliente(String servidor, String emailSistema, String contrasena, int puerto) {
        this.servidor = servidor;
        this.emailSistema = emailSistema;
        this.contrasena = contrasena;
        this.puerto = puerto;
    }

    /*
     * Envía un correo de respuesta al remitente con el resultado del comando.
     * Basado en la implementación probada y funcional del proyecto de referencia.
     */
    public void enviarRespuesta(String destinatario, String asunto, String cuerpoTexto, String cuerpoHtml) {
        Socket socket = null;
        BufferedReader entrada = null;
        BufferedWriter salida = null;

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(servidor, puerto), 15000);
            socket.setSoTimeout(60000);

            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            salida  = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            // Construir la dirección completa del remitente si solo es el usuario
            String dominio = "tecnoweb.org.bo";
            if (servidor != null && servidor.contains(".") && !servidor.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) {
                dominio = servidor.startsWith("mail.") ? servidor.substring(5) : servidor;
            }
            String remitenteCompleto = emailSistema;
            if (!remitenteCompleto.contains("@")) {
                remitenteCompleto = remitenteCompleto + "@" + dominio;
            } else if (remitenteCompleto.matches(".*@\\d+\\.\\d+\\.\\d+\\.\\d+.*")) {
                remitenteCompleto = remitenteCompleto.replaceAll("@\\d+\\.\\d+\\.\\d+\\.\\d+", "@" + dominio);
            }

            // 1. Leer saludo inicial (220)
            String bienvenida = entrada.readLine();
            log.info("SMTP S: {}", bienvenida);

            // 2. Enviar HELO
            enviar(salida, "HELO mail.tecnoweb.org.bo");
            leer(entrada);

            // 3. MAIL FROM
            enviar(salida, "MAIL FROM: <" + remitenteCompleto + ">");
            leer(entrada);

            // 4. RCPT TO
            enviar(salida, "RCPT TO: <" + destinatario + ">");
            String rcptResp = entrada.readLine();
            log.info("SMTP S: {}", rcptResp);
            if (rcptResp != null && !rcptResp.startsWith("250")) {
                log.error("El servidor SMTP rechazó al destinatario: {}", rcptResp);
                enviar(salida, "QUIT");
                return;
            }

            // 5. DATA
            enviar(salida, "DATA");
            leer(entrada);

            // 6. Preparar contenido del correo
            String data = "";
            String subjectHeader = asunto != null && !asunto.isBlank()
                    ? (asunto.toUpperCase().startsWith("RE:") ? asunto : "RE: " + asunto)
                    : "RE: Respuesta Bolsa de Trabajos";

            String fechaRfc = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.ZonedDateTime.now());
            String messageId = "<" + System.currentTimeMillis() + "." + Math.abs(destinatario.hashCode()) + "@" + dominio + ">";

            if (cuerpoHtml != null && cuerpoHtml.contains("data:image/png;base64,")) {
                String boundaryMixed = "boundary-mixed-" + System.currentTimeMillis();
                String boundaryAlt = "boundary-alt-" + System.currentTimeMillis();

                // Extraer el base64 del QR
                String qrBase64 = "";
                Matcher m = Pattern.compile("data:image/png;base64,([^\"]+)").matcher(cuerpoHtml);
                if (m.find()) {
                    qrBase64 = m.group(1);
                }

                // Reemplazar la URI base64 por el CID de la imagen
                String htmlConCid = cuerpoHtml.replaceAll("src=\"data:image/[^\"]+base64,[^\"]+\"", "src=\"cid:qrcode\"");

                data = "Date: " + fechaRfc + "\r\n"
                    + "Message-ID: " + messageId + "\r\n"
                    + "From: " + remitenteCompleto + "\r\n"
                    + "To: " + destinatario + "\r\n"
                    + "Subject: " + subjectHeader + "\r\n"
                    + "MIME-Version: 1.0\r\n"
                    + "Content-Type: multipart/related; boundary=\"" + boundaryMixed + "\"\r\n"
                    + "\r\n"
                    + "--" + boundaryMixed + "\r\n"
                    + "Content-Type: multipart/alternative; boundary=\"" + boundaryAlt + "\"\r\n"
                    + "\r\n"
                    + "--" + boundaryAlt + "\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n\r\n"
                    + (cuerpoTexto != null ? cuerpoTexto : "") + "\r\n\r\n"
                    + "--" + boundaryAlt + "\r\n"
                    + "Content-Type: text/html; charset=UTF-8\r\n\r\n"
                    + htmlConCid + "\r\n\r\n"
                    + "--" + boundaryAlt + "--\r\n"
                    + "\r\n"
                    + "--" + boundaryMixed + "\r\n"
                    + "Content-Type: image/png\r\n"
                    + "Content-Transfer-Encoding: base64\r\n"
                    + "Content-ID: <qrcode>\r\n"
                    + "Content-Disposition: inline; filename=\"qrcode.png\"\r\n"
                    + "\r\n"
                    + qrBase64 + "\r\n\r\n"
                    + "--" + boundaryMixed + "--\r\n"
                    + ".";
            } else {
                String boundary = "boundary-bolsa-" + System.currentTimeMillis();
                data = "Date: " + fechaRfc + "\r\n"
                    + "Message-ID: " + messageId + "\r\n"
                    + "From: " + remitenteCompleto + "\r\n"
                    + "To: " + destinatario + "\r\n"
                    + "Subject: " + subjectHeader + "\r\n"
                    + "MIME-Version: 1.0\r\n"
                    + "Content-Type: multipart/alternative; boundary=\"" + boundary + "\"\r\n"
                    + "\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n\r\n"
                    + (cuerpoTexto != null ? cuerpoTexto : "") + "\r\n\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Type: text/html; charset=UTF-8\r\n\r\n"
                    + (cuerpoHtml != null ? cuerpoHtml : "") + "\r\n\r\n"
                    + "--" + boundary + "--\r\n"
                    + ".";
            }

            // Normalización estricta: asegurar que todos los saltos de línea sean exactamente \r\n
            // Esto previene que Sendmail/Postfix rechace el mensaje o se quede colgado esperando el fin de datos.
            String dataNormalizada = data.replace("\r\n", "\n").replace("\n", "\r\n");
            enviar(salida, dataNormalizada);
            leer(entrada);

            // 7. QUIT
            enviar(salida, "QUIT");
            leer(entrada);

            log.info("Respuesta enviada a {} con asunto '{}'", destinatario, subjectHeader);
        } catch (IOException e) {
            log.error("Error al enviar correo por SMTP a {}: {}", destinatario, e.getMessage());
        } finally {
            try { if (entrada != null) entrada.close(); } catch (Exception ignored) {}
            try { if (salida != null) salida.close(); } catch (Exception ignored) {}
            try { if (socket != null && !socket.isClosed()) socket.close(); } catch (Exception ignored) {}
        }
    }

    private void enviar(BufferedWriter w, String cmd) throws IOException {
        String logCmd = cmd.length() > 80 ? cmd.substring(0, 80) + "..." : cmd;
        // Evitar loggear el bloque entero de DATA línea por línea
        if (cmd.startsWith("From: ")) {
            log.info("SMTP C: [Enviando cabeceras y cuerpo MIME...]");
        } else {
            log.info("SMTP C: {}", logCmd);
        }
        w.write(cmd + "\r\n");
        w.flush();
    }

    private void leer(BufferedReader r) throws IOException {
        String linea = r.readLine();
        log.info("SMTP S: {}", linea);
    }
}
