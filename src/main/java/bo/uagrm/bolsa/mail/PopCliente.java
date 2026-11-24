package bo.uagrm.bolsa.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;

public class PopCliente {

    private static final Logger log = LoggerFactory.getLogger(PopCliente.class);

    private final String servidor;
    private final String usuario;
    private final String contrasena;
    private final int puerto;

    private Socket socket;
    private BufferedReader entrada;
    private BufferedWriter salida;
    private boolean conectado = false;

    public PopCliente(String servidor, String usuario, String contrasena, int puerto) {
        this.servidor = servidor;
        this.usuario = usuario;
        this.contrasena = contrasena;
        this.puerto = puerto;
    }

    public void conectar() throws IOException {
        socket = new Socket(servidor, puerto);
        entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        salida  = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        String bienvenida = entrada.readLine();
        log.debug("S: {}", bienvenida);
        conectado = true;
    }

    public boolean login() throws IOException {
        enviar("USER " + usuario);
        leerLinea();
        enviar("PASS " + contrasena);
        String resp = leerLinea();
        boolean ok = resp.startsWith("+OK");
        if (!ok) log.warn("Login POP3 fallido para {}", usuario);
        return ok;
    }

    public int contarEmails() throws IOException {
        enviar("STAT");
        String resp = leerLinea();
        if (!resp.startsWith("+OK")) return 0;
        String[] partes = resp.split(" ");
        return partes.length >= 2 ? Integer.parseInt(partes[1]) : 0;
    }

    public String leer(int index) throws IOException {
        enviar("RETR " + index);
        String estado = leerLinea();
        if (!estado.startsWith("+OK")) return null;

        StringBuilder sb = new StringBuilder();
        String linea;
        while ((linea = entrada.readLine()) != null) {
            if (linea.equals(".")) break;
            if (linea.startsWith("..")) linea = linea.substring(1);
            sb.append(linea).append("\r\n");
        }
        return sb.toString();
    }

    public boolean borrar(int index) throws IOException {
        enviar("DELE " + index);
        return leerLinea().startsWith("+OK");
    }

    public void desconectar() {
        if (!conectado) return;
        try {
            enviar("QUIT");
            leerLinea();
        } catch (Exception ignored) {}
        try { if (entrada != null) entrada.close(); } catch (Exception ignored) {}
        try { if (salida  != null) salida.close();  } catch (Exception ignored) {}
        try { if (socket  != null) socket.close();  } catch (Exception ignored) {}
        conectado = false;
    }

    private void enviar(String cmd) throws IOException {
        log.debug("C: {}", cmd.startsWith("PASS") ? "PASS ****" : cmd);
        salida.write(cmd + "\r\n");
        salida.flush();
    }

    private String leerLinea() throws IOException {
        String linea = entrada.readLine();
        log.debug("S: {}", linea);
        return linea != null ? linea : "";
    }
}
