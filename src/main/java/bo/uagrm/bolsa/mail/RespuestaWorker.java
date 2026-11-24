package bo.uagrm.bolsa.mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import bo.uagrm.bolsa.commands.CommandResponse;
import bo.uagrm.bolsa.config.Config;
import bo.uagrm.bolsa.config.DatabaseManager;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class RespuestaWorker implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(RespuestaWorker.class);
    public static volatile boolean MODO_SIMULADOR = false;
    private final String rawEmail;
    private String remitente = "";

    public static synchronized void registrarAuditoria(String remitente, String asunto, String comando, boolean exito, String detalle, long duracionMs) {
        try {
            File logDir = new File("logs");
            if (!logDir.exists()) logDir.mkdirs();
            File logFile = new File(logDir, "access.log");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String desc = (detalle != null && !detalle.isBlank()) ? detalle.split("\n")[0].trim() : "";
            if (desc.length() > 80) desc = desc.substring(0, 77) + "...";
            String linea = String.format("[%s] [REMITENTE: %s] [ASUNTO: %s] [COMANDO: %s] [ESTADO: %s] [DURACION: %dms] - %s",
                    timestamp,
                    (remitente != null && !remitente.isBlank()) ? remitente : "DESCONOCIDO",
                    (asunto != null && !asunto.isBlank()) ? asunto : "(Sin Asunto)",
                    (comando != null && !comando.isBlank()) ? comando : "(Vacio)",
                    exito ? "EXITO" : "ERROR",
                    duracionMs,
                    desc);
            try (FileWriter fw = new FileWriter(logFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(linea);
            }
        } catch (Exception e) {
            log.warn("No se pudo escribir en access.log: {}", e.getMessage());
        }
    }
    public RespuestaWorker(String rawEmail) {
        this.rawEmail = rawEmail;
    }
        public static CommandResponse procesarComandoDirecto(String remitente, String comando) {
        RespuestaWorker worker = new RespuestaWorker("");
        worker.remitente = (remitente != null && !remitente.isBlank()) ? remitente.trim() : "usuario.prueba@gmail.com";
        return worker.ejecutarProcesamiento(null, comando);
    }
    private boolean esComandoValido(String cmd) {
        if (cmd == null || cmd.isBlank()) return false;
        String lower = cmd.trim().toLowerCase();
        return lower.startsWith("help") ||
               lower.startsWith("list") ||
               lower.startsWith("show") ||
               lower.startsWith("insert") ||
               lower.startsWith("update") ||
               lower.startsWith("delete") ||
               lower.startsWith("ver_planes") ||
               lower.startsWith("planes") ||
               lower.startsWith("ver_ofertas") ||
               lower.startsWith("ofertas") ||
               lower.startsWith("postular") ||
               lower.startsWith("mis_postulaciones") ||
               lower.startsWith("ver_candidatos") ||
               lower.startsWith("evaluar_candidato") ||
               lower.startsWith("agendar_entrevista") ||
               lower.startsWith("publicar_oferta") ||
               lower.startsWith("moderar") ||
               lower.startsWith("reporte") ||
               lower.startsWith("pagar_plan") ||
               lower.startsWith("comprar_plan") ||
               lower.startsWith("verificar_pago") ||
               lower.startsWith("registrar_estudiante") ||
               lower.startsWith("registrar_empresa") ||
               lower.startsWith("registrar_director") ||
               lower.startsWith("registrar_secretaria");
    }
    private List<String> extraerComandos(String asunto, String cuerpo) {
        List<String> lista = new ArrayList<>();
        // 1. Prioridad 1: Revisar Asunto (Segun la guia del Ing. Balcazar)
        if (asunto != null && !asunto.isBlank()) {
            String clean = asunto.replaceAll("(?i)^(re|fwd|rv)\\s*:\\s*", "")
                                 .replaceAll("^[*>•—\\-\\s\\t]+", "")
                                 .replaceAll("[*\\s\\t]+$", "")
                                 .trim();
            if (clean.contains(";")) {
                for (String part : clean.split(";")) {
                    String p = part.trim();
                    if (!p.isEmpty() && esComandoValido(p)) {
                        lista.add(p);
                    }
                }
            } else if (esComandoValido(clean)) {
                lista.add(clean);
            }
        }
        // 2. Prioridad 2: Si no hubo comandos validos en el Asunto, revisar el Cuerpo
        if (lista.isEmpty() && cuerpo != null && !cuerpo.isBlank()) {
            // Eliminar bloques de cita HTML de clientes como Gmail o Thunderbird
            String cleanHtml = cuerpo.replaceAll("(?is)<div[^>]*class=[\"\']gmail_quote[\"\'][^>]*>.*?</div>", "")
                                     .replaceAll("(?is)<blockquote[^>]*>.*?</blockquote>", "");
            String cleanCuerpo = cleanHtml.replaceAll("<[^>]*>", "").replaceAll("&nbsp;", " ").trim();
            // Buscar únicamente la primera instrucción válida, ignorando citas (>) y encabezados de respuesta
            List<String> lineasValidas = new ArrayList<>();
            for (String linea : cleanCuerpo.split("\n")) {
                String l = linea.trim();
                if (l.matches("(?i)^(el\\s.*escribi[oó]:|on\\s.*wrote:|de:\\s.*|from:\\s.*|-----original message-----).*$")) {
                    break;
                }
                if (l.startsWith(">")) {
                    continue;
                }
                l = l.replaceAll("^[*>•—\\-\\s\\t]+", "").replaceAll("[*\\s\\t]+$", "").trim();
                if (!l.isEmpty()) {
                    lineasValidas.add(l);
                }
            }
            if (!lineasValidas.isEmpty()) {
                String primera = lineasValidas.get(0);
                // Soportar reconstrucción si el comando abre corchete y cierra en la siguiente línea
                if (primera.contains("[") && !primera.contains("]")) {
                    StringBuilder sb = new StringBuilder(primera);
                    for (int i = 1; i < lineasValidas.size(); i++) {
                        sb.append(" ").append(lineasValidas.get(i));
                        if (lineasValidas.get(i).contains("]")) break;
                    }
                    primera = sb.toString();
                }
                if (primera.contains(";")) {
                    for (String part : primera.split(";")) {
                        String p = part.trim();
                        if (!p.isEmpty() && esComandoValido(p)) {
                            lista.add(p);
                        }
                    }
                } else if (esComandoValido(primera)) {
                    lista.add(primera);
                }
            }
        }
        // 3. Fallback: Si no se identificó como comando válido, tomar el texto escrito para mostrar el error explicativo
        if (lista.isEmpty()) {
            if (asunto != null && !asunto.trim().isEmpty() && !asunto.matches("(?i)^(re|fwd|rv)\\s*:\\s*.*$")) {
                lista.add(asunto.trim());
            } else if (cuerpo != null && !cuerpo.trim().isEmpty()) {
                String[] lines = cuerpo.replaceAll("<[^>]*>", "").trim().split("\n");
                if (lines.length > 0 && !lines[0].trim().isEmpty()) {
                    lista.add(lines[0].trim());
                }
            }
        }
        return lista;
    }
    public CommandResponse ejecutarProcesamiento(String asunto, String cuerpo) {
        long t0 = System.currentTimeMillis();
        List<String> comandos = extraerComandos(asunto, cuerpo);
        if (comandos.isEmpty()) {
            String error = "No se detectó ningún comando en el correo.\nUsa HELP; para consultar el manual de comandos disponibles.";
            CommandResponse resp = new CommandResponse(false, error, MailFormatter.formatErrorHtml("Comando Vacío", error));
            registrarAuditoria(this.remitente, asunto, "SIN_COMANDO", false, error, System.currentTimeMillis() - t0);
            return resp;
        }
        CommandResponse respuestaFinal;
        // Si es 1 solo comando, ejecución directa limpia y artesanal
        if (comandos.size() == 1) {
            respuestaFinal = ejecutarComando(comandos.get(0));
        } else {
            // Si son 2 o más comandos, ejecución ordenada sencilla (sin bloques modernos)
            StringBuilder msgText = new StringBuilder();
            StringBuilder msgHtml = new StringBuilder();
            boolean allSuccess = true;
            msgText.append("=== BOLSA DE TRABAJOS UAGRM - RESULTADOS ===\n\n");
            for (int i = 0; i < comandos.size(); i++) {
                String cmd = comandos.get(i);
                CommandResponse r = ejecutarComando(cmd);
                if (!r.isSuccess()) allSuccess = false;
                msgText.append(String.format("--- ORDEN [%d/%d]: %s [%s] ---\n", i + 1, comandos.size(), cmd, r.isSuccess() ? "EXITO" : "ERROR"));
                msgText.append(r.getMessage()).append("\n\n");
                msgHtml.append("<div style=\"margin-bottom: 25px; border-bottom: 1px solid #777777; padding-bottom: 15px;\">\n");
                msgHtml.append("  <p style=\"font-size: 13px; font-weight: bold; margin: 0 0 10px 0;\">\n");
                msgHtml.append("    Orden #").append(i + 1).append(": <code>").append(cmd).append("</code> &mdash; ");
                msgHtml.append("    <span style=\"color: ").append(r.isSuccess() ? "#008000" : "#cc0000").append("; font-weight: bold;\">")
                       .append(r.isSuccess() ? "EXITO" : "ERROR").append("</span>\n");
                msgHtml.append("  </p>\n");
                String inner = r.getHtmlMessage();
                if (inner != null) {
                    inner = inner.replaceAll("(?i)<\\/?(html|body)[^>]*>", "").trim();
                    msgHtml.append("  ").append(inner).append("\n");
                }
                msgHtml.append("</div>\n");
            }
            String fullHtml = MailFormatter.wrapHtml("Bolsa de Trabajos UAGRM - Resultados", msgHtml.toString(), allSuccess);
            respuestaFinal = new CommandResponse(allSuccess, msgText.toString(), fullHtml);
        }
        long duracion = System.currentTimeMillis() - t0;
        registrarAuditoria(this.remitente, asunto, String.join("; ", comandos), respuestaFinal.isSuccess(), respuestaFinal.getMessage(), duracion);
        return respuestaFinal;
    }
    @Override
    public void run() {
        EmailParser email = new EmailParser(rawEmail);
        this.remitente = email.getRemitente();
        String asunto    = email.getAsunto() != null ? email.getAsunto().trim() : "";
        String cuerpoRaw = email.getComando() != null ? email.getComando() : "";
        if (remitente.isEmpty()) {
            log.warn("Correo ignorado: remitente vacío");
            return;
        }
        // Evitar bucles infinitos si el correo proviene del propio sistema o es una prueba interna
        String userSys = Config.MAIL_USERNAME.toLowerCase().trim();
        if (remitente.toLowerCase().contains(userSys + "@") || asunto.toLowerCase().contains("prueba interna")) {
            log.info("Correo ignorado: proviene del propio sistema o prueba interna ('{}') para evitar bucles.", remitente);
            return;
        }
        log.info("Procesando correo de remitente: '{}' | Asunto: '{}'", remitente, asunto);
        CommandResponse respuesta = ejecutarProcesamiento(asunto, cuerpoRaw);
        SmtpCliente smtp = new SmtpCliente(
                Config.MAIL_SERVER,
                Config.MAIL_USERNAME,
                Config.MAIL_PASSWORD,
                Integer.parseInt(Config.MAIL_SMTP_PORT)
        );
        String subjectRespuesta = asunto.isEmpty() ? "Respuesta Bolsa de Trabajos" : (asunto.toLowerCase().startsWith("re:") ? asunto : "Re: " + asunto);
        smtp.enviarRespuesta(remitente, subjectRespuesta, respuesta.getMessage(), respuesta.getHtmlMessage());
    }
    private CommandResponse ejecutarComando(String comando) {
        try {
            String trimmed = comando.trim();
            String cmdUpper = trimmed.toUpperCase();
            // HELP
            if (cmdUpper.equals("HELP") || cmdUpper.equals("HELP;") || cmdUpper.startsWith("HELP[") || cmdUpper.startsWith("HELP;")) {
                return new CommandResponse(true, getHelpPageText(), getHelpPageHtml());
            }
            // COMANDOS DE NEGOCIO (ATRIBUTOS SEMÁNTICOS)
            if (cmdUpper.equals("VER_PLANES") || cmdUpper.equals("VER_PLANES;") ||
                cmdUpper.startsWith("VER_PLANES[") || cmdUpper.startsWith("VER_PLANES;") ||
                cmdUpper.equals("PLANES") || cmdUpper.equals("PLANES;")) {
                return ejecutarVerPlanes();
            }
            if (cmdUpper.startsWith("VER_OFERTAS") || cmdUpper.startsWith("OFERTAS")) {
                return ejecutarVerOfertas(trimmed);
            }
            if (cmdUpper.startsWith("MIS_POSTULACIONES")) {
                return ejecutarMisPostulaciones(trimmed);
            }
            if (cmdUpper.startsWith("POSTULAR[")) {
                return ejecutarPostular(trimmed);
            }
            if (cmdUpper.startsWith("PUBLICAR_OFERTA[")) {
                return ejecutarPublicarOferta(trimmed);
            }
            if (cmdUpper.startsWith("VER_CANDIDATOS[")) {
                return ejecutarVerCandidatos(trimmed);
            }
            if (cmdUpper.startsWith("EVALUAR_CANDIDATO[")) {
                return ejecutarEvaluarCandidato(trimmed);
            }
            if (cmdUpper.startsWith("AGENDAR_ENTREVISTA[")) {
                return ejecutarAgendarEntrevista(trimmed);
            }
            if (cmdUpper.startsWith("MODERAR[")) {
                return ejecutarModerar(trimmed);
            }
            if (cmdUpper.startsWith("REPORTE[")) {
                return ejecutarReporte(trimmed);
            }
            if (cmdUpper.startsWith("REGISTRAR_DIRECTOR[")) {
                return ejecutarRegistrarDirector(trimmed);
            }
            if (cmdUpper.startsWith("REGISTRAR_SECRETARIA[")) {
                return ejecutarRegistrarSecretaria(trimmed);
            }
            if (cmdUpper.startsWith("REGISTRAR_ESTUDIANTE[")) {
                return ejecutarRegistrarEstudiante(trimmed);
            }
            if (cmdUpper.startsWith("REGISTRAR_EMPRESA[")) {
                return ejecutarRegistrarEmpresa(trimmed);
            }
            if (cmdUpper.startsWith("PAGAR_PLAN") || cmdUpper.startsWith("COMPRAR_PLAN")) {
                return ejecutarComprarPlan(trimmed);
            }
            if (cmdUpper.startsWith("VERIFICAR_PAGO[")) {
                return ejecutarVerificarPago(trimmed);
            }
            // COMANDOS CRUD ESTÁNDAR: ACCION[tabla:args] o ACCION[tabla, args];
            Matcher m = Pattern.compile(
                "^([A-Za-z_]+)(?:\\s*\\[\\s*([A-Za-z0-9_]+)(?:\\s*[:\\s,]\\s*(.*?))?\\s*\\])?\\s*;?$",
                Pattern.CASE_INSENSITIVE
            ).matcher(trimmed);
            if (!m.matches()) {
                String error = "Comando no reconocido o sintaxis incorrecta: " + comando
                        + "\nUsa HELP[]; para consultar el manual de comandos.";
                return new CommandResponse(false, error, MailFormatter.formatErrorHtml("Sintaxis No Válida", error));
            }
            String accion = m.group(1).toUpperCase();
            String tablaRaw = m.group(2);
            String args = m.group(3);
            if (tablaRaw == null || tablaRaw.isBlank()) {
                String error = "Debe especificar la tabla para la acción " + accion + " (Ej: " + accion + "[ofertas];)";
                return new CommandResponse(false, error, MailFormatter.formatErrorHtml("Parámetro Faltante", error));
            }
            String tabla = normalizarTabla(tablaRaw);
            if (tabla == null) {
                String error = "La tabla o vista '" + tablaRaw + "' no existe o no está permitida en este sistema.";
                return new CommandResponse(false, error, MailFormatter.formatErrorHtml("Tabla Inválida", error));
            }
            switch (accion) {
                case "LIST":
                    return ejecutarProceduralList(tabla, args);
                case "SHOW":
                    return ejecutarProceduralShow(tabla, args);
                case "INSERT":
                    return ejecutarProceduralInsert(tabla, args);
                case "UPDATE":
                    return ejecutarProceduralUpdate(tabla, args);
                case "DELETE":
                    return ejecutarProceduralDelete(tabla, args);
                default:
                    String err = "Acción '" + accion + "' no implementada. Usa LIST, SHOW, INSERT, UPDATE, DELETE o HELP.";
                    return new CommandResponse(false, err, MailFormatter.formatErrorHtml("Acción Inválida", err));
            }
        } catch (Exception e) {
            log.error("Error al ejecutar comando: {}", e.getMessage(), e);
            String msg = "Error al ejecutar el comando: " + e.getMessage();
            return new CommandResponse(false, msg, MailFormatter.formatErrorHtml("Error de Ejecución", msg));
        }
    }
    private String normalizarTabla(String tablaRaw) {
        String t = tablaRaw.toLowerCase().trim();
        switch (t) {
            case "roles": case "rol": return "roles";
            case "usuarios": case "usuario": case "users": case "user": return "usuarios";
            case "estudiantes": case "estudiante": return "estudiantes";
            case "empresas": case "empresa": return "empresas";
            case "habilidades": case "habilidad": return "habilidades";
            case "estudiante_habilidades": case "estudiante_habilidad": return "estudiante_habilidades";
            case "areas_laborales": case "area_laboral": case "areas": case "area": return "areas_laborales";
            case "planes": case "plan": return "planes";
            case "suscripciones": case "suscripcion": return "suscripciones";
            case "pagos": case "pago": return "pagos";
            case "facturas": case "factura": return "facturas";
            case "ofertas": case "oferta": return "ofertas";
            case "oferta_habilidades": case "oferta_habilidad": return "oferta_habilidades";
            case "postulaciones": case "postulacion": return "postulaciones";
            case "seguimientos": case "seguimiento": return "seguimientos";
            case "entrevistas": case "entrevista": return "entrevistas";
            case "moderaciones": case "moderacion": return "moderaciones";
            case "v_ofertas_disponibles": return "v_ofertas_disponibles";
            case "v_ofertas_pendientes_moderacion": return "v_ofertas_pendientes_moderacion";
            case "v_candidatos_oferta": return "v_candidatos_oferta";
            case "v_mis_postulaciones": return "v_mis_postulaciones";
            case "v_entrevistas_programadas": return "v_entrevistas_programadas";
            case "v_reporte_empleabilidad": return "v_reporte_empleabilidad";
            case "v_resumen_postulaciones": return "v_resumen_postulaciones";
            case "v_recaudacion_suscripciones": return "v_recaudacion_suscripciones";
            case "v_ranking_empresas": return "v_ranking_empresas";
            case "v_facturas_emitidas": return "v_facturas_emitidas";
            case "v_registro_estudiante": return "v_registro_estudiante";
            case "v_registro_empresa": return "v_registro_empresa";
            default: return null;
        }
    }
    // =========================================================================
    // IMPLEMENTACIÓN COMANDOS DE NEGOCIO
    // =========================================================================
    private CommandResponse ejecutarPostular(String cmd) throws Exception {
        String args = extraerArgumentos(cmd);
        List<String> params = parseTokens(args);
        if (params.isEmpty()) {
            return new CommandResponse(false, "Uso: POSTULAR[oferta_id, mensaje_opcional];\nO con ID estudiante: POSTULAR[oferta_id, estudiante_id, mensaje];",
                    MailFormatter.formatErrorHtml("Parámetros Faltantes", "Formato: POSTULAR[oferta_id, mensaje]; o POSTULAR[oferta_id, estudiante_id, mensaje];"));
        }
        int ofertaId = Integer.parseInt(params.get(0).replaceAll("[^0-9]", ""));
        int estudianteId = -1;
        String mensaje = "Postulación enviada vía correo electrónico.";
        if (params.size() >= 3 && params.get(1).matches("\\d+")) {
            estudianteId = Integer.parseInt(params.get(1));
            mensaje = params.get(2).trim();
        } else if (params.size() == 2 && params.get(1).matches("\\d+")) {
            estudianteId = Integer.parseInt(params.get(1));
        } else if (params.size() >= 2) {
            mensaje = params.get(1).trim();
        }
        // Si no se pasó estudiante_id, identificar al estudiante por su correo
        if (estudianteId == -1) {
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT est.id_estudiantes FROM estudiantes est JOIN usuarios u ON est.id_usuarios = u.id_usuarios WHERE LOWER(u.email) = LOWER(?)")) {
                ps.setString(1, remitente);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        estudianteId = rs.getInt(1);
                    }
                }
            }
        }
        // Si aún es -1 (ej. docente o correo de pruebas), usar estudiante 1 por defecto para no bloquear la prueba
        if (estudianteId == -1) {
            estudianteId = 1;
        }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO postulaciones (id_estudiantes, id_ofertas, mensaje_presentacion, estado) VALUES (?, ?, ?, 'POSTULADO') " +
                     "ON CONFLICT (id_estudiantes, id_ofertas) DO UPDATE SET mensaje_presentacion = EXCLUDED.mensaje_presentacion")) {
            ps.setInt(1, estudianteId);
            ps.setInt(2, ofertaId);
            ps.setString(3, mensaje);
            ps.executeUpdate();
        }
        String msg = "¡Felicidades! Postulación registrada exitosamente a la oferta #" + ofertaId + " (Estudiante ID #" + estudianteId + ")."
                + "\nPuedes consultar el estado con el comando: MIS_POSTULACIONES[];";
        return new CommandResponse(true, msg, MailFormatter.formatSuccessHtml("Postulación Registrada", msg));
    }
    private CommandResponse ejecutarPublicarOferta(String cmd) throws Exception {
        String args = extraerArgumentos(cmd);
        List<String> params = parseTokens(args);
        if (params.size() < 4) {
            String uso = "Uso: PUBLICAR_OFERTA[titulo, area, salario, modalidad, vacantes, habilidades];\n"
                    + "O con ID empresa: PUBLICAR_OFERTA[empresa_id, titulo, area, salario, modalidad, vacantes, habilidades];\n"
                    + "Ejemplo: PUBLICAR_OFERTA[Desarrollador Java Junior, Software, 4000, REMOTO, 2, Java];";
            return new CommandResponse(false, uso, MailFormatter.formatErrorHtml("Parámetros Insuficientes", uso));
        }
        int empresaId = -1;
        int idx = 0;
        if (params.get(0).matches("\\d+") && params.size() >= 5) {
            empresaId = Integer.parseInt(params.get(0));
            idx = 1;
        }
        String titulo = params.get(idx);
        String areaStr = params.size() > idx + 1 ? params.get(idx + 1) : "Desarrollo de Software";
        double salario = params.size() > idx + 2 ? Double.parseDouble(params.get(idx + 2).replaceAll("[^0-9.]", "")) : 0.0;
        String modalidad = params.size() > idx + 3 ? params.get(idx + 3).toUpperCase() : "PRESENCIAL";
        int vacantes = params.size() > idx + 4 ? Integer.parseInt(params.get(idx + 4).replaceAll("[^0-9]", "")) : 1;
        String habilidades = params.size() > idx + 5 ? params.get(idx + 5) : "Conocimientos generales";
        if (empresaId == -1) {
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT emp.id_empresas FROM empresas emp JOIN usuarios u ON emp.id_usuarios = u.id_usuarios WHERE LOWER(u.email) = LOWER(?)")) {
                ps.setString(1, remitente);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        empresaId = rs.getInt(1);
                    }
                }
            }
        }
        // Fallback: empresa #1 si el docente o un correo no registrado envía el comando
        if (empresaId <= 0) {
            empresaId = 1;
        }
        int areaId = 1;
        if (areaStr.matches("\\d+")) {
            areaId = Integer.parseInt(areaStr);
        } else {
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id_areas_laborales FROM areas_laborales WHERE LOWER(nombre) LIKE LOWER(?) LIMIT 1")) {
                ps.setString(1, "%" + areaStr + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) areaId = rs.getInt(1);
                }
            }
        }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO ofertas (id_empresas, id_areas_laborales, titulo, descripcion, modalidad, tipo_contrato, ubicacion, salario, vacantes) " +
                     "VALUES (?, ?, ?, ?, ?, 'TIEMPO_COMPLETO', 'Santa Cruz', ?, ?) RETURNING id_ofertas, estado")) {
            ps.setInt(1, empresaId);
            ps.setInt(2, areaId);
            ps.setString(3, titulo);
            ps.setString(4, "Requisitos y habilidades: " + habilidades);
            ps.setString(5, modalidad.matches("PRESENCIAL|REMOTO|HIBRIDO") ? modalidad : "PRESENCIAL");
            ps.setDouble(6, salario);
            ps.setInt(7, vacantes <= 0 ? 1 : vacantes);
            try (ResultSet rs = ps.executeQuery()) {
                int ofertaId = 0;
                String estado = "PUBLICADA";
                if (rs.next()) {
                    ofertaId = rs.getInt(1);
                    estado = rs.getString(2);
                }
                String msg = "Oferta #" + ofertaId + " ('" + titulo + "') creada exitosamente para Empresa #" + empresaId + ".\n"
                        + "Estado: " + estado + (estado.equals("PENDIENTE_MODERACION") ? " (Sujeta a moderación previa del Director)" : " (Publicada directamente)");
                return new CommandResponse(true, msg, MailFormatter.formatSuccessHtml("Oferta Publicada", msg));
            }
        }
    }
    private CommandResponse ejecutarVerOfertas(String cmd) throws Exception {
        String args = extraerArgumentos(cmd);
        if (args.isBlank()) {
            return ejecutarSelectQuery("SELECT * FROM v_ofertas_disponibles", "Catálogo de Ofertas Disponibles");
        }
        if (args.matches("\\d+")) {
            int id = Integer.parseInt(args);
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM v_ofertas_disponibles WHERE oferta_id = ?")) {
                ps.setInt(1, id);
                return ejecutarSelectPreparedStatement(ps, "Oferta Laboral #" + id);
            }
        } else {
            String query = "%" + args.trim().toLowerCase() + "%";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM v_ofertas_disponibles WHERE LOWER(area) LIKE ? OR LOWER(titulo) LIKE ? OR LOWER(empresa) LIKE ?")) {
                ps.setString(1, query);
                ps.setString(2, query);
                ps.setString(3, query);
                return ejecutarSelectPreparedStatement(ps, "Ofertas Filtradas: " + args.trim());
            }
        }
    }
    private CommandResponse ejecutarMisPostulaciones(String cmd) throws Exception {
        String args = extraerArgumentos(cmd);
        if (!args.isBlank()) {
            int id = Integer.parseInt(args.replaceAll("[^0-9]", ""));
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM v_mis_postulaciones WHERE estudiante_id = ?")) {
                ps.setInt(1, id);
                return ejecutarSelectPreparedStatement(ps, "Mis Postulaciones Laborales (#" + id + ")");
            }
        } else {
            boolean hasRows = false;
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM v_mis_postulaciones WHERE LOWER(email_estudiante) = LOWER(?) LIMIT 1")) {
                ps.setString(1, remitente);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) hasRows = true;
                }
            }
            if (hasRows) {
                try (Connection conn = DatabaseManager.getConnection();
                     PreparedStatement ps = conn.prepareStatement("SELECT * FROM v_mis_postulaciones WHERE LOWER(email_estudiante) = LOWER(?)")) {
                    ps.setString(1, remitente);
                    return ejecutarSelectPreparedStatement(ps, "Mis Postulaciones Laborales");
                }
            }
        }
        return ejecutarSelectQuery("SELECT * FROM v_mis_postulaciones", "Mis Postulaciones Laborales");
    }
    private CommandResponse ejecutarVerCandidatos(String cmd) throws Exception {
        String args = extraerArgumentos(cmd);
        if (!args.isBlank()) {
            int ofertaId = Integer.parseInt(args.replaceAll("[^0-9]", ""));
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM v_candidatos_oferta WHERE oferta_id = ?")) {
                ps.setInt(1, ofertaId);
                return ejecutarSelectPreparedStatement(ps, "Candidatos Postulados - Oferta #" + ofertaId);
            }
        }
        return ejecutarSelectQuery("SELECT * FROM v_candidatos_oferta", "Candidatos Postulados");
    }
    private CommandResponse ejecutarEvaluarCandidato(String cmd) throws Exception {
        String args = extraerArgumentos(cmd);
        List<String> p = parseTokens(args);
        if (p.size() < 3) {
            String uso = "Uso: EVALUAR_CANDIDATO[postulacion_id, etapa, observaciones];\n"
                    + "Etapas válidas: REVISION_CV, PRESELECCIONADO, ENTREVISTA, CONTRATADO, NO_SELECCIONADO\n"
                    + "Ejemplo: EVALUAR_CANDIDATO[1, PRESELECCIONADO, Excelente perfil técnico y portafolio];";
            return new CommandResponse(false, uso, MailFormatter.formatErrorHtml("Parámetros Insuficientes", uso));
        }
        int postulacionId = Integer.parseInt(p.get(0).replaceAll("[^0-9]", ""));
        String etapaRaw = p.get(1).toUpperCase().trim();
        String etapa;
        String estadoPostulacion;
        if (etapaRaw.contains("ENTREV") || etapaRaw.contains("RRHH")) {
            etapa = "ENTREVISTA_RRHH";
            estadoPostulacion = "ENTREVISTA";
        } else if (etapaRaw.contains("TEC")) {
            etapa = "PRUEBA_TECNICA";
            estadoPostulacion = "EN_REVISION";
        } else if (etapaRaw.contains("CONTRA") || (etapaRaw.contains("SELEC") && !etapaRaw.contains("NO"))) {
            etapa = "CONTRATADO";
            estadoPostulacion = "CONTRATADO";
        } else if (etapaRaw.contains("NO") || etapaRaw.contains("DESC") || etapaRaw.contains("RECH")) {
            etapa = "DESCARTADO";
            estadoPostulacion = "RECHAZADO";
        } else if (etapaRaw.contains("PRE")) {
            etapa = "REVISION_CV";
            estadoPostulacion = "PRESELECCIONADO";
        } else {
            etapa = "REVISION_CV";
            estadoPostulacion = "EN_REVISION";
        }
        String observaciones;
        Double calificacion = null;
        if (p.size() >= 4 && p.get(2).matches("^[0-9]+(\\.[0-9]+)?$")) {
            calificacion = Double.parseDouble(p.get(2));
            observaciones = p.get(3).trim();
        } else {
            observaciones = p.get(2).trim();
        }
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO seguimientos (id_postulaciones, etapa, evaluador, observaciones) " +
                    "VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, postulacionId);
                ps.setString(2, etapa);
                ps.setString(3, remitente);
                ps.setString(4, observaciones);
                ps.executeUpdate();
            }
            try (PreparedStatement ps2 = conn.prepareStatement(
                    "UPDATE postulaciones SET estado = ? WHERE id_postulaciones = ?")) {
                ps2.setString(1, estadoPostulacion);
                ps2.setInt(2, postulacionId);
                ps2.executeUpdate();
            }
        }
        String msg = "Evaluación registrada para la postulación #" + postulacionId + ". Estado actualizado a: " + estadoPostulacion + " (" + etapa + ")";
        return new CommandResponse(true, msg, MailFormatter.formatSuccessHtml("Candidato Evaluado", msg));
    }
    private CommandResponse ejecutarAgendarEntrevista(String cmd) throws Exception {
        String args = extraerArgumentos(cmd);
        List<String> p = parseTokens(args);
        if (p.size() < 4) {
            String uso = "Uso: AGENDAR_ENTREVISTA[postulacion_id, fecha_hora, modalidad, enlace_meet_o_lugar, entrevistador, telefono];\n"
                    + "Ejemplo: AGENDAR_ENTREVISTA[1, 2026-11-05 15:30, VIRTUAL, https://meet.google.com/abc-defg-hij, Carlos Mendoza, 75012345];";
            return new CommandResponse(false, uso, MailFormatter.formatErrorHtml("Parámetros Insuficientes", uso));
        }
        int postulacionId = Integer.parseInt(p.get(0).replaceAll("[^0-9]", ""));
        String fechaHora = p.get(1).trim();
        String modalidad = p.get(2).toUpperCase().trim();
        String link = p.get(3).trim();
        String entrevistador = p.size() > 4 ? p.get(4).trim() : "Equipo Reclutador";
        String telefono = p.size() > 5 ? p.get(5).replaceAll("[^0-9]", "") : "75012345";
        int entId = -1;
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO entrevistas (id_postulaciones, fecha_hora, modalidad, lugar_o_enlace, entrevistador, telefono_contacto, estado) " +
                    "VALUES (?, ?::TIMESTAMP, ?, ?, ?, ?, 'PROGRAMADA') RETURNING id_entrevistas")) {
                ps.setInt(1, postulacionId);
                ps.setString(2, fechaHora);
                ps.setString(3, modalidad.matches("VIRTUAL|PRESENCIAL") ? modalidad : "VIRTUAL");
                ps.setString(4, link);
                ps.setString(5, entrevistador);
                ps.setString(6, telefono);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) entId = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE postulaciones SET estado = 'ENTREVISTA' WHERE id_postulaciones = ?")) {
                ps.setInt(1, postulacionId);
                ps.executeUpdate();
            }
        }
        String plain = "Entrevista #" + entId + " programada con éxito para la Postulación #" + postulacionId + ".\n"
                + "Fecha y Hora: " + fechaHora + "\n"
                + "Modalidad: " + modalidad + "\n"
                + "Enlace / Lugar: " + link + "\n"
                + "Entrevistador: " + entrevistador + "\n"
                + "Teléfono: " + telefono;
        String waLink = "https://wa.me/591" + telefono + "?text=Hola,%20tengo%20confirmada%20mi%20entrevista%20el%20" + fechaHora.replace(" ", "%20");
        String html = "<div style=\"padding: 10px;\">\n"
                + "  <h3 style=\"color: #0d6efd; margin-top: 0;\">📅 Cita de Entrevista Programada</h3>\n"
                + "  <table style=\"font-size: 14px; line-height: 1.8;\">\n"
                + "    <tr><td><b>Postulación:</b></td><td>#" + postulacionId + " (Estado actualizado a ENTREVISTA)</td></tr>\n"
                + "    <tr><td><b>Fecha y Hora:</b></td><td>" + fechaHora + "</td></tr>\n"
                + "    <tr><td><b>Modalidad:</b></td><td>" + modalidad + "</td></tr>\n"
                + "    <tr><td><b>Entrevistador:</b></td><td>" + entrevistador + "</td></tr>\n"
                + "    <tr><td><b>Enlace / Lugar:</b></td><td><a href=\"" + link + "\" target=\"_blank\">" + link + "</a></td></tr>\n"
                + "  </table>\n"
                + "  <div style=\"margin-top: 15px;\">\n"
                + "    <a href=\"" + link + "\" target=\"_blank\" style=\"background-color:#0d6efd; color:#fff; padding:8px 14px; border-radius:5px; text-decoration:none; font-weight:bold; margin-right:10px;\">🎥 Unirse a Google Meet</a>\n"
                + "    <a href=\"" + waLink + "\" target=\"_blank\" style=\"background-color:#25D366; color:#fff; padding:8px 14px; border-radius:5px; text-decoration:none; font-weight:bold;\">💬 Abrir WhatsApp</a>\n"
                + "  </div>\n"
                + "</div>";
        return new CommandResponse(true, plain, MailFormatter.wrapHtml("Entrevista Programada", html, true));
    }
    private CommandResponse ejecutarModerar(String cmd) throws Exception {
        String args = extraerArgumentos(cmd);
        List<String> p = parseTokens(args);
        if (p.size() < 2) {
            String uso = "Uso: MODERAR[tipo_entidad, entidad_id, accion, motivo];\n"
                    + "O formato simplificado: MODERAR[entidad_id, APROBAR|RECHAZAR];\n"
                    + "Ejemplo: MODERAR[EMPRESA, 1, APROBAR, Documentacion legal verificada];\n"
                    + "Ejemplo: MODERAR[1, APROBAR];";
            return new CommandResponse(false, uso, MailFormatter.formatErrorHtml("Parámetros Insuficientes", uso));
        }
        String entidad = "OFERTA";
        int entidadId = 1;
        String accion = "APROBAR";
        String motivo = "Aprobado por Dirección Académica";

        if (p.size() >= 4) {
            entidad = p.get(0).toUpperCase().trim();
            entidadId = Integer.parseInt(p.get(1).replaceAll("[^0-9]", ""));
            accion = p.get(2).toUpperCase().trim();
            motivo = p.get(3).trim();
        } else if (p.size() == 3) {
            if (p.get(0).matches("(?i)EMPRESA|OFERTA")) {
                entidad = p.get(0).toUpperCase().trim();
                entidadId = Integer.parseInt(p.get(1).replaceAll("[^0-9]", ""));
                accion = p.get(2).toUpperCase().trim();
            } else {
                entidadId = Integer.parseInt(p.get(0).replaceAll("[^0-9]", ""));
                accion = p.get(1).toUpperCase().trim();
                motivo = p.get(2).trim();
            }
        } else if (p.size() == 2) {
            entidadId = Integer.parseInt(p.get(0).replaceAll("[^0-9]", ""));
            accion = p.get(1).toUpperCase().trim();
        }

        boolean esAprobar = accion.contains("APROB") || accion.contains("SI") || accion.equals("OK");

        try (Connection conn = DatabaseManager.getConnection()) {
            if (entidad.equals("EMPRESA")) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE empresas SET verificada = ? WHERE id_empresas = ?")) {
                    ps.setBoolean(1, esAprobar);
                    ps.setInt(2, entidadId);
                    ps.executeUpdate();
                }
            } else {
                // Oferta: el CHECK solo acepta ('BORRADOR', 'PENDIENTE_MODERACION', 'PUBLICADA', 'CERRADA')
                String estado = esAprobar ? "PUBLICADA" : "CERRADA";
                try (PreparedStatement ps = conn.prepareStatement("UPDATE ofertas SET estado = ? WHERE id_ofertas = ?")) {
                    ps.setString(1, estado);
                    ps.setInt(2, entidadId);
                    ps.executeUpdate();
                }
            }
            // Identificar al usuario moderador por el remitente o usuario 1 por defecto
            int moderadorId = 1;
            try (PreparedStatement psMod = conn.prepareStatement("SELECT id_usuarios FROM usuarios WHERE LOWER(email) = LOWER(?) LIMIT 1")) {
                psMod.setString(1, remitente);
                try (ResultSet rsMod = psMod.executeQuery()) {
                    if (rsMod.next()) moderadorId = rsMod.getInt(1);
                }
            }
            // Registrar auditoría en moderaciones
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO moderaciones (id_usuarios, tipo_entidad, entidad_id, accion, motivo) VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, moderadorId);
                ps.setString(2, entidad);
                ps.setInt(3, entidadId);
                ps.setString(4, esAprobar ? "APROBAR" : "RECHAZAR");
                ps.setString(5, motivo);
                ps.executeUpdate();
            }
        }
        String msg = "Moderación aplicada con éxito: " + entidad + " #" + entidadId + " -> " + (esAprobar ? "APROBADA" : "RECHAZADA (CERRADA)") + " (" + motivo + ")";
        return new CommandResponse(true, msg, MailFormatter.formatSuccessHtml("Moderación Registrada", msg));
    }
    private CommandResponse ejecutarReporte(String cmd) throws Exception {
        String args = extraerArgumentos(cmd).toLowerCase();
        if (args.contains("emplea")) {
            return ejecutarSelectQuery("SELECT * FROM v_reporte_empleabilidad", "Reporte de Empleabilidad por Carrera");
        } else if (args.contains("postula")) {
            return ejecutarSelectQuery(
                "SELECT o.id_ofertas AS oferta_id, o.titulo AS vacante, e.nombre_comercial AS empresa, COUNT(p.id_postulaciones) AS total_postulantes, o.vacantes, o.estado " +
                "FROM ofertas o " +
                "JOIN empresas e ON o.id_empresas = e.id_empresas " +
                "LEFT JOIN postulaciones p ON o.id_ofertas = p.id_ofertas " +
                "GROUP BY o.id_ofertas, o.titulo, e.nombre_comercial, o.vacantes, o.estado " +
                "ORDER BY total_postulantes DESC",
                "Resumen de Postulaciones por Vacante"
            );
        } else if (args.contains("recauda") || args.contains("pago")) {
            return ejecutarSelectQuery("SELECT * FROM v_recaudacion_suscripciones", "Recaudación por Suscripciones");
        } else if (args.contains("empresa")) {
            return ejecutarSelectQuery("SELECT * FROM v_ranking_empresas", "Ranking de Empresas Empleadoras");
        } else if (args.contains("factura")) {
            return ejecutarSelectQuery("SELECT * FROM v_facturas_emitidas", "Listado de Facturas Electrónicas Emitidas");
        }
        return ejecutarSelectQuery("SELECT * FROM v_reporte_empleabilidad", "Reporte de Empleabilidad por Carrera");
    }
    private CommandResponse ejecutarRegistrarDirector(String cmd) throws Exception {
        String args = extraerArgumentos(cmd);
        List<String> p = parseTokens(args);
        if (p.size() < 6) {
            String uso = "Uso: REGISTRAR_DIRECTOR[ci, nombre, apellido, email, password, telefono];\n"
                    + "Ejemplo: REGISTRAR_DIRECTOR[1001, Roberto, Gomez, director.roberto@gmail.com, dir123, 70011223];";
            return new CommandResponse(false, uso, MailFormatter.formatErrorHtml("Parámetros Insuficientes", uso));
        }
        String ci = p.get(0);
        String nombre = p.get(1);
        String apellido = p.get(2);
        String email = p.get(3);
        String pass = p.get(4);
        String tel = p.get(5);
        int idUsuario = 0;
        String fotoUrl = "https://ui-avatars.com/api/?name=" + nombre.replace(" ", "+") + "+" + apellido.replace(" ", "+") + "&background=0D8ABC&color=fff";
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO usuarios (id_roles, ci, nombre, apellido, email, password, telefono, foto_url) " +
                    "VALUES (1, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, ci);
                ps.setString(2, nombre);
                ps.setString(3, apellido);
                ps.setString(4, email);
                ps.setString(5, pass);
                ps.setString(6, tel);
                ps.setString(7, fotoUrl);
                ps.executeUpdate();
            }
            try (PreparedStatement psSel = conn.prepareStatement(
                    "SELECT id_usuarios, foto_url FROM usuarios WHERE email = ? ORDER BY id_usuarios DESC LIMIT 1")) {
                psSel.setString(1, email);
                try (ResultSet rs = psSel.executeQuery()) {
                    if (rs.next()) {
                        idUsuario = rs.getInt("id_usuarios");
                        if (rs.getString("foto_url") != null) fotoUrl = rs.getString("foto_url");
                    }
                }
            }
        }
        StringBuilder text = new StringBuilder();
        text.append("======================================================================\n");
        text.append("         BOLSA DE TRABAJOS UAGRM - CONFIRMACIÓN DE REGISTRO\n");
        text.append("======================================================================\n\n");
        text.append("Director/a ").append(nombre).append(" ").append(apellido).append(" registrado/a exitosamente.\n\n");
        text.append("--- CREDENCIAL DE AUTORIDAD ACADÉMICA ---\n");
        text.append("ID Usuario:      #").append(idUsuario > 0 ? idUsuario : "(Nuevo)").append("\n");
        text.append("Nombre Completo: ").append(nombre).append(" ").append(apellido).append("\n");
        text.append("Cédula (CI):     ").append(ci).append("\n");
        text.append("Email Oficial:   ").append(email).append("\n");
        text.append("Teléfono:        ").append(tel).append("\n");
        text.append("Rol Asignado:    DIRECTOR DE CARRERA (Máxima Autoridad)\n");
        text.append("Estado:          ACTIVO / HABILITADO\n\n");
        text.append("--- COMANDOS DE GESTIÓN DISPONIBLES ---\n");
        text.append("1. Moderar oferta laboral pendiente:\n   MODERAR[oferta_id, APROBADA]; (o RECHAZADA)\n\n");
        text.append("2. Consultar reportes gerenciales:\n   REPORTE[empleabilidad];\n   REPORTE[empresas];\n   REPORTE[facturas];\n\n");
        text.append("3. Listar empresas registradas:\n   LIST[empresas];\n\n");
        text.append("======================================================================\n");
        StringBuilder html = new StringBuilder();
        html.append("<table border=\"1\" cellpadding=\"4\" style=\"border-collapse: collapse; font-family: Arial, sans-serif; font-size: 12px; border: 1px solid #555555; width: 100%; max-width: 600px;\">\n");
        html.append("  <tr style=\"background-color: #e2e2e2;\"><th colspan=\"2\" style=\"border: 1px solid #555555; padding: 5px; text-align: left; font-size: 13px;\">FICHA DE DIRECTOR REGISTRADO</th></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; width: 160px; border: 1px solid #555555; padding: 4px;\">ID Usuario:</td><td style=\"border: 1px solid #555555; padding: 4px;\">#").append(idUsuario > 0 ? idUsuario : "(Nuevo)").append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Nombre Completo:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(nombre).append(" ").append(apellido).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Cédula (CI):</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(ci).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Email Oficial:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(email).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Teléfono:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(tel).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Rol Institucional:</td><td style=\"border: 1px solid #555555; padding: 4px;\">DIRECTOR DE CARRERA</td></tr>\n");
        html.append("</table>\n");
        html.append("<p style=\"font-size: 11px; color: #555555; margin-top: 8px;\"><b>Comandos Disponibles:</b> <code>MODERAR[oferta_id, APROBADA];</code> | <code>REPORTE[empleabilidad];</code> | <code>REPORTE[empresas];</code></p>\n");
        if (!MODO_SIMULADOR && email != null && !email.trim().equalsIgnoreCase(remitente.trim()) && email.contains("@")) {
            try {
                SmtpCliente smtpExtra = new SmtpCliente(
                        Config.MAIL_SERVER, Config.MAIL_USERNAME, Config.MAIL_PASSWORD, Integer.parseInt(Config.MAIL_SMTP_PORT));
                smtpExtra.enviarRespuesta(email.trim(), "Bienvenido a la Bolsa de Trabajos UAGRM - Credencial de Director", text.toString(), MailFormatter.wrapHtml("Credencial de Autoridad Académica", html.toString(), true));
            } catch (Exception ex) {
                log.warn("No se pudo enviar copia a {}: {}", email, ex.getMessage());
            }
        }
        return new CommandResponse(true, text.toString(), MailFormatter.wrapHtml("Registro de Director Confirmado", html.toString(), true));
    }
    private CommandResponse ejecutarRegistrarSecretaria(String cmd) throws Exception {
        String args = extraerArgumentos(cmd);
        List<String> p = parseTokens(args);
        if (p.size() < 6) {
            String uso = "Uso: REGISTRAR_SECRETARIA[ci, nombre, apellido, email, password, telefono];\n"
                    + "Ejemplo: REGISTRAR_SECRETARIA[1002, Carla, Suarez, carla.suarez@gmail.com, sec123, 70044556];";
            return new CommandResponse(false, uso, MailFormatter.formatErrorHtml("Parámetros Insuficientes", uso));
        }
        String ci = p.get(0);
        String nombre = p.get(1);
        String apellido = p.get(2);
        String email = p.get(3);
        String pass = p.get(4);
        String tel = p.get(5);
        int idUsuario = 0;
        String fotoUrl = "https://ui-avatars.com/api/?name=" + nombre.replace(" ", "+") + "+" + apellido.replace(" ", "+") + "&background=E91E63&color=fff";
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO usuarios (id_roles, ci, nombre, apellido, email, password, telefono, foto_url) " +
                    "VALUES (2, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, ci);
                ps.setString(2, nombre);
                ps.setString(3, apellido);
                ps.setString(4, email);
                ps.setString(5, pass);
                ps.setString(6, tel);
                ps.setString(7, fotoUrl);
                ps.executeUpdate();
            }
            try (PreparedStatement psSel = conn.prepareStatement(
                    "SELECT id_usuarios, foto_url FROM usuarios WHERE email = ? ORDER BY id_usuarios DESC LIMIT 1")) {
                psSel.setString(1, email);
                try (ResultSet rs = psSel.executeQuery()) {
                    if (rs.next()) {
                        idUsuario = rs.getInt("id_usuarios");
                        if (rs.getString("foto_url") != null) fotoUrl = rs.getString("foto_url");
                    }
                }
            }
        }
        StringBuilder text = new StringBuilder();
        text.append("======================================================================\n");
        text.append("         BOLSA DE TRABAJOS UAGRM - CONFIRMACIÓN DE REGISTRO\n");
        text.append("======================================================================\n\n");
        text.append("Secretaria ").append(nombre).append(" ").append(apellido).append(" registrada exitosamente.\n\n");
        text.append("--- CREDENCIAL DE PERSONAL ADMINISTRATIVO ---\n");
        text.append("ID Usuario:      #").append(idUsuario > 0 ? idUsuario : "(Nuevo)").append("\n");
        text.append("Nombre Completo: ").append(nombre).append(" ").append(apellido).append("\n");
        text.append("Cédula (CI):     ").append(ci).append("\n");
        text.append("Email Oficial:   ").append(email).append("\n");
        text.append("Teléfono:        ").append(tel).append("\n");
        text.append("Rol Asignado:    SECRETARIA ADMINISTRATIVA\n");
        text.append("Estado:          ACTIVO / HABILITADO\n\n");
        text.append("--- COMANDOS DE GESTIÓN DISPONIBLES ---\n");
        text.append("1. Moderar oferta laboral pendiente:\n   MODERAR[oferta_id, APROBADA];\n\n");
        text.append("2. Consultar reportes:\n   REPORTE[empleabilidad];\n\n");
        text.append("3. Listar ofertas:\n   LIST[ofertas];\n\n");
        text.append("======================================================================\n");
        StringBuilder html = new StringBuilder();
        html.append("<table border=\"1\" cellpadding=\"4\" style=\"border-collapse: collapse; font-family: Arial, sans-serif; font-size: 12px; border: 1px solid #555555; width: 100%; max-width: 600px;\">\n");
        html.append("  <tr style=\"background-color: #e2e2e2;\"><th colspan=\"2\" style=\"border: 1px solid #555555; padding: 5px; text-align: left; font-size: 13px;\">FICHA DE SECRETARIA REGISTRADA</th></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; width: 160px; border: 1px solid #555555; padding: 4px;\">ID Usuario:</td><td style=\"border: 1px solid #555555; padding: 4px;\">#").append(idUsuario > 0 ? idUsuario : "(Nuevo)").append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Nombre Completo:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(nombre).append(" ").append(apellido).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Cédula (CI):</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(ci).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Email Oficial:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(email).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Teléfono:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(tel).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Rol Institucional:</td><td style=\"border: 1px solid #555555; padding: 4px;\">SECRETARIA ADMINISTRATIVA</td></tr>\n");
        html.append("</table>\n");
        html.append("<p style=\"font-size: 11px; color: #555555; margin-top: 8px;\"><b>Comandos Disponibles:</b> <code>MODERAR[oferta_id, APROBADA];</code> | <code>LIST[ofertas];</code> | <code>REPORTE[empleabilidad];</code></p>\n");
        if (!MODO_SIMULADOR && email != null && !email.trim().equalsIgnoreCase(remitente.trim()) && email.contains("@")) {
            try {
                SmtpCliente smtpExtra = new SmtpCliente(
                        Config.MAIL_SERVER, Config.MAIL_USERNAME, Config.MAIL_PASSWORD, Integer.parseInt(Config.MAIL_SMTP_PORT));
                smtpExtra.enviarRespuesta(email.trim(), "Bienvenido a la Bolsa de Trabajos UAGRM - Credencial de Secretaria", text.toString(), MailFormatter.wrapHtml("Credencial Administrativa", html.toString(), true));
            } catch (Exception ex) {
                log.warn("No se pudo enviar copia a {}: {}", email, ex.getMessage());
            }
        }
        return new CommandResponse(true, text.toString(), MailFormatter.wrapHtml("Registro de Secretaria Confirmado", html.toString(), true));
    }
    private CommandResponse ejecutarRegistrarEstudiante(String cmd) throws Exception {
        String args = extraerArgumentos(cmd);
        List<String> p = parseTokens(args);
        if (p.size() < 8) {
            String uso = "Uso: REGISTRAR_ESTUDIANTE[ci, nombre, apellido, email, password, telefono, ru, carrera, semestre, perfil, cv_url];\n"
                    + "Ejemplo: REGISTRAR_ESTUDIANTE[2190123, Juan, Perez, juan@gmail.com, 1234, 77001122, 219012345, Ingenieria en Sistemas, 8, Desarrollador Java, https://drive.google.com/cv.pdf];";
            return new CommandResponse(false, uso, MailFormatter.formatErrorHtml("Parámetros Insuficientes", uso));
        }
        String ci = p.get(0);
        String nombre = p.get(1);
        String apellido = p.get(2);
        String email = p.get(3);
        String pass = p.get(4);
        String tel = p.get(5);
        String ru = p.get(6);
        String carrera = p.get(7);
        int semestre = p.size() > 8 ? Integer.parseInt(p.get(8).replaceAll("[^0-9]", "")) : 1;
        String perfil = p.size() > 9 ? p.get(9) : "Estudiante UAGRM";
        String cvUrl = p.size() > 10 ? p.get(10) : "https://drive.google.com/cv";
        int idEstudiante = 0;
        int idUsuario = 0;
        String fotoUrl = "https://ui-avatars.com/api/?name=" + nombre.replace(" ", "+") + "+" + apellido.replace(" ", "+") + "&background=0D8ABC&color=fff";
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO v_registro_estudiante (ci, nombre, apellido, email, password, telefono, ru, carrera, semestre, perfil_profesional, cv_url) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, ci);
                ps.setString(2, nombre);
                ps.setString(3, apellido);
                ps.setString(4, email);
                ps.setString(5, pass);
                ps.setString(6, tel);
                ps.setString(7, ru);
                ps.setString(8, carrera);
                ps.setInt(9, semestre);
                ps.setString(10, perfil);
                ps.setString(11, cvUrl);
                ps.executeUpdate();
            }
            try (PreparedStatement psSel = conn.prepareStatement(
                    "SELECT e.id_estudiantes AS est_id, u.id_usuarios AS usr_id, u.foto_url " +
                    "FROM estudiantes e JOIN usuarios u ON e.id_usuarios = u.id_usuarios " +
                    "WHERE u.email = ? OR e.ru = ? ORDER BY e.id_estudiantes DESC LIMIT 1")) {
                psSel.setString(1, email);
                psSel.setString(2, ru);
                try (ResultSet rs = psSel.executeQuery()) {
                    if (rs.next()) {
                        idEstudiante = rs.getInt("est_id");
                        idUsuario = rs.getInt("usr_id");
                        if (rs.getString("foto_url") != null) fotoUrl = rs.getString("foto_url");
                    }
                }
            }
        }
        StringBuilder text = new StringBuilder();
        text.append("======================================================================\n");
        text.append("         BOLSA DE TRABAJOS UAGRM - CONFIRMACIÓN DE REGISTRO\n");
        text.append("======================================================================\n\n");
        text.append("¡Felicidades ").append(nombre).append("! Tu registro como estudiante ha sido procesado exitosamente.\n\n");
        text.append("--- FICHA DE MATRÍCULA ACADÉMICA ---\n");
        text.append("ID Estudiante:   #").append(idEstudiante > 0 ? idEstudiante : "(Generado)").append("\n");
        text.append("Registro Univ.:  ").append(ru).append("\n");
        text.append("Estudiante:      ").append(nombre).append(" ").append(apellido).append("\n");
        text.append("Cédula (CI):     ").append(ci).append("\n");
        text.append("Carrera:         ").append(carrera).append(" (Semestre ").append(semestre).append(")\n");
        text.append("Email Oficial:   ").append(email).append("\n");
        text.append("Teléfono:        ").append(tel).append("\n");
        text.append("Perfil:          ").append(perfil).append("\n");
        text.append("Enlace CV:       ").append(cvUrl).append("\n");
        text.append("Estado Cuenta:   ACTIVO / HABILITADO\n\n");
        text.append("--- COMANDOS DISPONIBLES PARA EMPEZAR ---\n");
        text.append("1. Consultar ofertas de trabajo disponibles:\n   VER_OFERTAS;\n\n");
        text.append("2. Postularte a una vacante (usando el ID de la oferta):\n   POSTULAR[oferta_id];\n   (Ejemplo: POSTULAR[1];)\n\n");
        text.append("3. Ver el estado de tus postulaciones:\n   MIS_POSTULACIONES;\n\n");
        text.append("======================================================================\n");
        StringBuilder html = new StringBuilder();
        html.append("<table border=\"1\" cellpadding=\"4\" style=\"border-collapse: collapse; font-family: Arial, sans-serif; font-size: 12px; border: 1px solid #555555; width: 100%; max-width: 600px;\">\n");
        html.append("  <tr style=\"background-color: #e2e2e2;\"><th colspan=\"2\" style=\"border: 1px solid #555555; padding: 5px; text-align: left; font-size: 13px;\">FICHA DE REGISTRO DE ESTUDIANTE</th></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; width: 160px; border: 1px solid #555555; padding: 4px;\">ID Estudiante:</td><td style=\"border: 1px solid #555555; padding: 4px;\">#").append(idEstudiante > 0 ? idEstudiante : "(Nuevo)").append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Registro Univ. (RU):</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(ru).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Estudiante:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(nombre).append(" ").append(apellido).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Cédula (CI):</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(ci).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Carrera:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(carrera).append(" (Semestre ").append(semestre).append(")</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Correo:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(email).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Teléfono:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(tel).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Perfil Profesional:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(perfil).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">CV Enlace:</td><td style=\"border: 1px solid #555555; padding: 4px;\"><a href=\"").append(cvUrl).append("\">").append(cvUrl).append("</a></td></tr>\n");
        html.append("</table>\n");
        html.append("<p style=\"font-size: 11px; color: #555555; margin-top: 8px;\"><b>Comandos Disponibles:</b> <code>VER_OFERTAS;</code> | <code>POSTULAR[oferta_id];</code> | <code>MIS_POSTULACIONES;</code></p>\n");
        if (!MODO_SIMULADOR && email != null && !email.trim().equalsIgnoreCase(remitente.trim()) && email.contains("@")) {
            try {
                SmtpCliente smtpExtra = new SmtpCliente(
                        Config.MAIL_SERVER, Config.MAIL_USERNAME, Config.MAIL_PASSWORD, Integer.parseInt(Config.MAIL_SMTP_PORT));
                smtpExtra.enviarRespuesta(email.trim(), "Bienvenido a la Bolsa de Trabajos UAGRM - Credencial Académica", text.toString(), MailFormatter.wrapHtml("Credencial Digital UAGRM", html.toString(), true));
            } catch (Exception ex) {
                log.warn("No se pudo enviar copia extra a {}: {}", email, ex.getMessage());
            }
        }
        return new CommandResponse(true, text.toString(), MailFormatter.wrapHtml("Registro de Estudiante Confirmado", html.toString(), true));
    }
    private CommandResponse ejecutarRegistrarEmpresa(String cmd) throws Exception {
        String args = extraerArgumentos(cmd);
        List<String> p = parseTokens(args);
        if (p.size() < 8) {
            String uso = "Uso: REGISTRAR_EMPRESA[ci, nombre_rep, apellido_rep, email, password, telefono, nit, razon_social, nombre_comercial, rubro, direccion, sitio_web];\n"
                    + "Ejemplo: REGISTRAR_EMPRESA[4019283, Carlos, Mendoza, rrhh@tigo.bo, emp123, 71000001, 1020304050, TELECEL S.A., Tigo Bolivia, Telecomunicaciones, Doble Via Km 6, https://tigo.com.bo];";
            return new CommandResponse(false, uso, MailFormatter.formatErrorHtml("Parámetros Insuficientes", uso));
        }
        String ci = p.get(0);
        String nombreRep = p.get(1);
        String apellidoRep = p.get(2);
        String email = p.get(3);
        String pass = p.get(4);
        String tel = p.get(5);
        String nit = p.get(6);
        String razonSocial = p.get(7);
        String nombreComercial = p.size() > 8 ? p.get(8) : razonSocial;
        String rubro = p.size() > 9 ? p.get(9) : "Tecnología";
        String direccion = p.size() > 10 ? p.get(10) : "Santa Cruz de la Sierra";
        String web = p.size() > 11 ? p.get(11) : "https://sitio.com";
        String repLegal = nombreRep + " " + apellidoRep;
        int idEmpresa = 0;
        String planNombre = "Plan Bienvenida Gratuito (30 días)";
        String fechaFinPlan = "30 días posteriores al registro";
        String logoUrl = "https://ui-avatars.com/api/?name=" + nombreComercial.replace(" ", "+") + "&background=198754&color=fff";
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO v_registro_empresa (ci, nombre, apellido, email, password, telefono, nit, razon_social, nombre_comercial, sector_industrial, direccion, sitio_web, representante_legal, telefono_contacto) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, ci);
                ps.setString(2, nombreRep);
                ps.setString(3, apellidoRep);
                ps.setString(4, email);
                ps.setString(5, pass);
                ps.setString(6, tel);
                ps.setString(7, nit);
                ps.setString(8, razonSocial);
                ps.setString(9, nombreComercial);
                ps.setString(10, rubro);
                ps.setString(11, direccion);
                ps.setString(12, web);
                ps.setString(13, repLegal);
                ps.setString(14, tel);
                ps.executeUpdate();
            }
            try (PreparedStatement psSel = conn.prepareStatement(
                    "SELECT e.id_empresas AS emp_id, e.logo_url, s.fecha_fin, p.nombre AS plan_nom " +
                    "FROM empresas e " +
                    "JOIN usuarios u ON e.id_usuarios = u.id_usuarios " +
                    "LEFT JOIN suscripciones s ON e.id_empresas = s.id_empresas AND s.estado = 'ACTIVA' " +
                    "LEFT JOIN planes p ON s.id_planes = p.id_planes " +
                    "WHERE e.nit = ? OR u.email = ? ORDER BY e.id_empresas DESC LIMIT 1")) {
                psSel.setString(1, nit);
                psSel.setString(2, email);
                try (ResultSet rs = psSel.executeQuery()) {
                    if (rs.next()) {
                        idEmpresa = rs.getInt("emp_id");
                        if (rs.getString("logo_url") != null) logoUrl = rs.getString("logo_url");
                        if (rs.getString("plan_nom") != null) planNombre = rs.getString("plan_nom");
                        if (rs.getString("fecha_fin") != null) fechaFinPlan = rs.getString("fecha_fin");
                    }
                }
            }
        }
        StringBuilder text = new StringBuilder();
        text.append("======================================================================\n");
        text.append("         BOLSA DE TRABAJOS UAGRM - CONFIRMACIÓN DE EMPRESA\n");
        text.append("======================================================================\n\n");
        text.append("¡Bienvenido/a ").append(nombreComercial).append("!\n");
        text.append("Tu registro corporativo ha sido procesado exitosamente en la plataforma.\n\n");
        text.append("--- FICHA DE LA EMPRESA ---\n");
        text.append("ID Empresa:          #").append(idEmpresa > 0 ? idEmpresa : "(Generado)").append("\n");
        text.append("Nombre Comercial:    ").append(nombreComercial).append("\n");
        text.append("Razón Social:        ").append(razonSocial).append("\n");
        text.append("NIT:                 ").append(nit).append("\n");
        text.append("Sector / Rubro:      ").append(rubro).append("\n");
        text.append("Representante Legal: ").append(repLegal).append("\n");
        text.append("Email Corporativo:   ").append(email).append("\n");
        text.append("Teléfono Contacto:   ").append(tel).append("\n");
        text.append("Dirección:           ").append(direccion).append("\n");
        text.append("Sitio Web:           ").append(web).append("\n");
        text.append("Estado Verificación: EN REVISIÓN (Pendiente aprobación de Dirección)\n");
        text.append("Plan de Bienvenida:  ").append(planNombre).append(" (Vence: ").append(fechaFinPlan).append(")\n\n");
        text.append("--- COMANDOS DISPONIBLES PARA EMPEZAR ---\n");
        text.append("1. Publicar una oferta laboral:\n");
        text.append("   PUBLICAR_OFERTA['Desarrollador Junior', 'Requisitos...', 2, 4500, 'Tiempo Completo', 'Hibrido', '2026-12-31', 1];\n\n");
        text.append("2. Ver candidatos postulados a tus ofertas:\n   VER_CANDIDATOS[oferta_id];\n\n");
        text.append("3. Adquirir planes de publicación y destacados con QR:\n   VER_PLANES;\n\n");
        text.append("======================================================================\n");
        StringBuilder html = new StringBuilder();
        html.append("<table border=\"1\" cellpadding=\"4\" style=\"border-collapse: collapse; font-family: Arial, sans-serif; font-size: 12px; border: 1px solid #555555; width: 100%; max-width: 600px;\">\n");
        html.append("  <tr style=\"background-color: #e2e2e2;\"><th colspan=\"2\" style=\"border: 1px solid #555555; padding: 5px; text-align: left; font-size: 13px;\">FICHA DE REGISTRO DE EMPRESA</th></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; width: 160px; border: 1px solid #555555; padding: 4px;\">ID Empresa:</td><td style=\"border: 1px solid #555555; padding: 4px;\">#").append(idEmpresa > 0 ? idEmpresa : "(Nuevo)").append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Nombre Comercial:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(nombreComercial).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Razón Social:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(razonSocial).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">NIT:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(nit).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Sector / Rubro:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(rubro).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Representante:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(repLegal).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Email:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(email).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Teléfono:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(tel).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Dirección:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(direccion).append("</td></tr>\n");
        html.append("  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Suscripción Inicial:</td><td style=\"border: 1px solid #555555; padding: 4px;\">").append(planNombre).append(" (Vence: ").append(fechaFinPlan).append(")</td></tr>\n");
        html.append("</table>\n");
        html.append("<p style=\"font-size: 11px; color: #555555; margin-top: 8px;\"><b>Comandos Disponibles:</b> <code>PUBLICAR_OFERTA[...];</code> | <code>VER_CANDIDATOS[oferta_id];</code> | <code>VER_PLANES;</code></p>\n");
        if (!MODO_SIMULADOR && email != null && !email.trim().equalsIgnoreCase(remitente.trim()) && email.contains("@")) {
            try {
                SmtpCliente smtpExtra = new SmtpCliente(
                        Config.MAIL_SERVER, Config.MAIL_USERNAME, Config.MAIL_PASSWORD, Integer.parseInt(Config.MAIL_SMTP_PORT));
                smtpExtra.enviarRespuesta(email.trim(), "Bienvenido a la Bolsa de Trabajos UAGRM - Ficha Corporativa", text.toString(), MailFormatter.wrapHtml("Ficha Corporativa UAGRM", html.toString(), true));
            } catch (Exception ex) {
                log.warn("No se pudo enviar copia extra a {}: {}", email, ex.getMessage());
            }
        }
        return new CommandResponse(true, text.toString(), MailFormatter.wrapHtml("Registro de Empresa Confirmado", html.toString(), true));
    }
    private CommandResponse ejecutarVerPlanes() throws Exception {
        StringBuilder text = new StringBuilder();
        text.append("=== PLANES DE SUSCRIPCIÓN PARA EMPRESAS ===\n\n");
        StringBuilder html = new StringBuilder();
        html.append("<table border=\"1\" cellpadding=\"4\" style=\"border-collapse: collapse; font-family: Arial, sans-serif; font-size: 12px; border: 1px solid #555555; width: 100%;\">\n");
        html.append("  <thead>\n");
        html.append("    <tr style=\"background-color: #e2e2e2; color: #000000; font-weight: bold; text-align: left;\">\n");
        html.append("      <th style=\"border: 1px solid #555555; padding: 4px;\">ID</th>\n");
        html.append("      <th style=\"border: 1px solid #555555; padding: 4px;\">Plan</th>\n");
        html.append("      <th style=\"border: 1px solid #555555; padding: 4px;\">Precio (Bs)</th>\n");
        html.append("      <th style=\"border: 1px solid #555555; padding: 4px;\">Duración</th>\n");
        html.append("      <th style=\"border: 1px solid #555555; padding: 4px;\">Límite Vacantes</th>\n");
        html.append("      <th style=\"border: 1px solid #555555; padding: 4px;\">Publicación</th>\n");
        html.append("      <th style=\"border: 1px solid #555555; padding: 4px;\">Descripción / Beneficios</th>\n");
        html.append("      <th style=\"border: 1px solid #555555; padding: 4px;\">Comando de Compra</th>\n");
        html.append("    </tr>\n");
        html.append("  </thead>\n");
        html.append("  <tbody>\n");
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT id_planes, nombre, precio, duracion_dias, limite_ofertas, requiere_moderacion, es_gratuito, descripcion " +
                     "FROM planes WHERE estado = 'ACTIVO' ORDER BY precio ASC")) {
            while (rs.next()) {
                int id = rs.getInt("id_planes");
                String nombre = rs.getString("nombre");
                double precio = rs.getDouble("precio");
                int duracion = rs.getInt("duracion_dias");
                int limite = rs.getInt("limite_ofertas");
                boolean requiereMod = rs.getBoolean("requiere_moderacion");
                String desc = rs.getString("descripcion");
                text.append(String.format("PLAN #%d: %s\n", id, nombre));
                text.append(String.format("  Precio: Bs. %.2f (%d días)\n", precio, duracion));
                text.append(String.format("  Límite: %d vacantes/mes\n", limite));
                text.append(String.format("  Publicación: %s\n", requiereMod ? "Sujeta a moderación" : "Publicación directa"));
                text.append(String.format("  Detalle: %s\n", desc));
                text.append(String.format("  Comprar: COMPRAR_PLAN[tu_empresa_id, %d];\n\n", id));
                html.append("    <tr>\n");
                html.append("      <td style=\"border: 1px solid #555555; padding: 4px;\">#").append(id).append("</td>\n");
                html.append("      <td style=\"border: 1px solid #555555; padding: 4px; font-weight: bold;\">").append(nombre).append("</td>\n");
                html.append("      <td style=\"border: 1px solid #555555; padding: 4px;\">Bs ").append(String.format("%.2f", precio)).append("</td>\n");
                html.append("      <td style=\"border: 1px solid #555555; padding: 4px;\">").append(duracion).append(" días</td>\n");
                html.append("      <td style=\"border: 1px solid #555555; padding: 4px;\">").append(limite).append(" vacantes</td>\n");
                html.append("      <td style=\"border: 1px solid #555555; padding: 4px;\">").append(requiereMod ? "Revisión previa" : "Directa").append("</td>\n");
                html.append("      <td style=\"border: 1px solid #555555; padding: 4px;\">").append(desc).append("</td>\n");
                html.append("      <td style=\"border: 1px solid #555555; padding: 4px; font-family: monospace;\">COMPRAR_PLAN[empresa_id, ").append(id).append("];</td>\n");
                html.append("    </tr>\n");
            }
        }
        html.append("  </tbody>\n");
        html.append("</table>\n");
        html.append("<p style=\"font-size: 12px; margin-top: 10px;\"><b>¿Cómo comprar un plan?</b> Envía: <code>COMPRAR_PLAN[empresa_id, plan_id];</code> (ej: <code>COMPRAR_PLAN[1, 2];</code>). Recibirás tu código QR oficial de Pago Fácil Bolivia para pagar desde tu app bancaria.</p>\n");
        text.append("------------------------------------------------------------\n");
        text.append("¿CÓMO COMPRAR UN PLAN?\n");
        text.append("Envía un correo con el comando:\n");
        text.append("  COMPRAR_PLAN[empresa_id, plan_id];\n\n");
        text.append("Ejemplo:\n");
        text.append("  COMPRAR_PLAN[1, 2];\n\n");
        text.append("Recibirás el código QR oficial de Pago Fácil para pagar al instante.\n");
        return new CommandResponse(true, text.toString(), MailFormatter.wrapHtml("Catálogo de Planes de Suscripción", html.toString(), true));
    }
    private CommandResponse ejecutarComprarPlan(String cmd) throws Exception {
        String args = extraerArgumentos(cmd);
        if (args.isBlank()) {
            String err = "Uso del comando:\n  COMPRAR_PLAN[empresa_id, plan_id];\n\n"
                       + "Ejemplo: COMPRAR_PLAN[1, 2]; (Para adquirir el Plan Básico para la empresa #1).\n"
                       + "Para consultar los planes disponibles, envía: VER_PLANES;";
            return new CommandResponse(false, err, MailFormatter.formatErrorHtml("Parámetros Faltantes", err));
        }
        int empresaId = -1;
        int planId = -1;
        if (args.contains("=")) {
            for (String part : args.split(",")) {
                String[] kv = part.split("=");
                if (kv.length == 2) {
                    String k = kv[0].trim().toLowerCase();
                    String v = kv[1].trim().replaceAll("[^0-9]", "");
                    if (k.contains("empresa")) {
                        empresaId = Integer.parseInt(v);
                    } else if (k.contains("plan")) {
                        planId = Integer.parseInt(v);
                    }
                }
            }
        } else {
            List<String> tokens = parseTokens(args);
            if (tokens.size() >= 2) {
                empresaId = Integer.parseInt(tokens.get(0).replaceAll("[^0-9]", ""));
                planId = Integer.parseInt(tokens.get(1).replaceAll("[^0-9]", ""));
            } else if (tokens.size() == 1) {
                planId = Integer.parseInt(tokens.get(0).replaceAll("[^0-9]", ""));
            }
        }
        if (empresaId <= 0) {
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT emp.id_empresas FROM empresas emp JOIN usuarios u ON emp.id_usuarios = u.id_usuarios WHERE LOWER(u.email) = LOWER(?)")) {
                ps.setString(1, remitente);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        empresaId = rs.getInt(1);
                    }
                }
            }
        }
        if (empresaId <= 0) {
            String err = "No se pudo identificar la empresa. Por favor especifica tu ID de empresa:\n"
                       + "  COMPRAR_PLAN[empresa_id, plan_id];\n"
                       + "Ejemplo: COMPRAR_PLAN[1, 2];";
            return new CommandResponse(false, err, MailFormatter.formatErrorHtml("ID de Empresa Requerido", err));
        }
        if (planId <= 0) {
            String err = "Debes indicar el ID del plan que deseas comprar:\n"
                       + "  COMPRAR_PLAN[" + empresaId + ", plan_id];\n"
                       + "Envía VER_PLANES; para ver la lista de planes y sus IDs.";
            return new CommandResponse(false, err, MailFormatter.formatErrorHtml("ID de Plan Requerido", err));
        }
        // 1. Obtener datos de la empresa
        String razonSocial = "";
        String nombreComercial = "";
        String nit = "";
        String ci = "1001";
        String telefono = "70011223";
        String emailEmpresa = remitente;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT emp.razon_social, emp.nombre_comercial, emp.nit, u.ci, u.telefono, u.email " +
                     "FROM empresas emp JOIN usuarios u ON emp.id_usuarios = u.id_usuarios WHERE emp.id_empresas = ?")) {
            ps.setInt(1, empresaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    razonSocial = rs.getString(1);
                    nombreComercial = rs.getString(2);
                    nit = rs.getString(3);
                    if (rs.getString(4) != null) ci = rs.getString(4);
                    if (rs.getString(5) != null) telefono = rs.getString(5);
                    if (rs.getString(6) != null) emailEmpresa = rs.getString(6);
                } else {
                    String err = "La empresa con ID #" + empresaId + " no existe en el sistema.";
                    return new CommandResponse(false, err, MailFormatter.formatErrorHtml("Empresa No Encontrada", err));
                }
            }
        }
        // 2. Obtener datos del plan
        String planNombre = "";
        double precio = 0.0;
        int duracion = 30;
        int limiteOfertas = 1;
        boolean esGratuito = false;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT nombre, precio, duracion_dias, limite_ofertas, es_gratuito FROM planes WHERE id_planes = ? AND estado = 'ACTIVO'")) {
            ps.setInt(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    planNombre = rs.getString(1);
                    precio = rs.getDouble(2);
                    duracion = rs.getInt(3);
                    limiteOfertas = rs.getInt(4);
                    esGratuito = rs.getBoolean(5);
                } else {
                    String err = "El plan con ID #" + planId + " no existe o no está activo.\nEnvía VER_PLANES; para ver los planes válidos.";
                    return new CommandResponse(false, err, MailFormatter.formatErrorHtml("Plan No Encontrado", err));
                }
            }
        }
        // 3. Si el plan es gratuito
        if (precio <= 0.0 || esGratuito) {
            int suscripcionId = -1;
            try (Connection conn = DatabaseManager.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE suscripciones SET estado = 'VENCIDA' WHERE id_empresas = ? AND estado = 'ACTIVA'")) {
                    ps.setInt(1, empresaId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO suscripciones (id_empresas, id_planes, fecha_inicio, fecha_fin, ofertas_publicadas, estado) " +
                        "VALUES (?, ?, CURRENT_DATE, CURRENT_DATE + (? || ' days')::INTERVAL, 0, 'ACTIVA') RETURNING id_suscripciones")) {
                    ps.setInt(1, empresaId);
                    ps.setInt(2, planId);
                    ps.setInt(3, duracion);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) suscripcionId = rs.getInt(1);
                    }
                }
            }
            String msg = "¡Plan Gratuito activado exitosamente para " + nombreComercial + " (ID #" + empresaId + ")!\n"
                       + "Suscripción ID: #" + suscripcionId + "\n"
                       + "Duración: " + duracion + " días | Límite: " + limiteOfertas + " oferta(s) (Sujetas a moderación de Dirección).";
            return new CommandResponse(true, msg, MailFormatter.formatSuccessHtml("Plan Gratuito Activado", msg));
        }
        // 4. Si es un plan de pago: Registrar suscripción pendiente y generar QR con Pago Fácil
        int suscripcionId = -1;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO suscripciones (id_empresas, id_planes, fecha_inicio, fecha_fin, ofertas_publicadas, estado) " +
                     "VALUES (?, ?, CURRENT_DATE, CURRENT_DATE + (? || ' days')::INTERVAL, 0, 'PENDIENTE_PAGO') RETURNING id_suscripciones")) {
            ps.setInt(1, empresaId);
            ps.setInt(2, planId);
            ps.setInt(3, duracion);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) suscripcionId = rs.getInt(1);
            }
        }
        bo.uagrm.bolsa.services.PagoFacilService pf = new bo.uagrm.bolsa.services.PagoFacilService();
        bo.uagrm.bolsa.services.PagoFacilService.QrResponse qr = pf.generarQr(
                suscripcionId, planNombre, precio, razonSocial, ci, telefono, emailEmpresa
        );
        int pagoId = -1;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO pagos (id_empresas, id_suscripciones, monto, metodo_pago, referencia_pago, referencia_qr, estado) " +
                     "VALUES (?, ?, ?, 'QR', ?, ?, 'PENDIENTE') RETURNING id_pagos")) {
            ps.setInt(1, empresaId);
            ps.setInt(2, suscripcionId);
            ps.setDouble(3, precio);
            ps.setString(4, qr.getPagofacilTransactionId());
            ps.setString(5, qr.getQrBase64());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) pagoId = rs.getInt(1);
            }
        }
        String plain = "ORDEN DE PAGO QR GENERADA - PAGO FÁCIL BOLIVIA\n"
                + "====================================================\n"
                + "Empresa: " + nombreComercial + " (ID #" + empresaId + ")\n"
                + "Razón Social: " + razonSocial + " | NIT: " + nit + "\n"
                + "Plan Seleccionado: " + planNombre + " (ID #" + planId + ")\n"
                + "Monto a Pagar: Bs. " + String.format("%.2f", precio) + "\n"
                + "ID de Pago: #" + pagoId + "\n"
                + "ID Transacción Pago Fácil: " + qr.getPagofacilTransactionId() + "\n\n"
                + "PASOS PARA PAGAR:\n"
                + "1. Abre la app de tu banco móvil en tu celular.\n"
                + "2. Escanea la imagen QR adjunta en la versión HTML de este correo.\n"
                + "3. Confirma el pago por Bs. " + String.format("%.2f", precio) + ".\n"
                + "4. Una vez realizado el pago, responde con el comando:\n"
                + "   VERIFICAR_PAGO[" + pagoId + "];\n\n"
                + "Al verificar, tu suscripción se activará automáticamente y se emitirá tu factura digital oficial.";
        String html = "<table border=\"1\" cellpadding=\"4\" style=\"border-collapse: collapse; font-family: Arial, sans-serif; font-size: 12px; border: 1px solid #555555; width: 100%; max-width: 600px;\">\n"
                + "  <tr style=\"background-color: #e2e2e2;\"><th colspan=\"2\" style=\"border: 1px solid #555555; padding: 5px; text-align: left; font-size: 13px;\">ORDEN DE PAGO QR - PAGO FÁCIL BOLIVIA</th></tr>\n"
                + "  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; width: 160px; border: 1px solid #555555; padding: 4px;\">Empresa:</td><td style=\"border: 1px solid #555555; padding: 4px;\">" + nombreComercial + " (#" + empresaId + ")</td></tr>\n"
                + "  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Razón Social:</td><td style=\"border: 1px solid #555555; padding: 4px;\">" + razonSocial + "</td></tr>\n"
                + "  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">NIT:</td><td style=\"border: 1px solid #555555; padding: 4px;\">" + nit + "</td></tr>\n"
                + "  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Plan Solicitado:</td><td style=\"border: 1px solid #555555; padding: 4px;\">" + planNombre + " (#" + planId + ")</td></tr>\n"
                + "  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Límite Vacantes:</td><td style=\"border: 1px solid #555555; padding: 4px;\">" + limiteOfertas + " ofertas / mes (Publicación Directa)</td></tr>\n"
                + "  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">Total a Pagar:</td><td style=\"border: 1px solid #555555; padding: 4px; font-weight: bold; color: #008000;\">Bs. " + String.format("%.2f", precio) + "</td></tr>\n"
                + "  <tr><td style=\"background-color: #e2e2e2; font-weight: bold; border: 1px solid #555555; padding: 4px;\">ID Pago / Transac.:</td><td style=\"border: 1px solid #555555; padding: 4px;\">#" + pagoId + " (Transac: " + qr.getPagofacilTransactionId() + ")</td></tr>\n"
                + "</table>\n"
                + "<div style=\"margin: 15px 0; text-align: left;\">\n"
                + "  <img src=\"data:image/png;base64," + qr.getQrBase64() + "\" alt=\"QR Pago Fácil\" style=\"width: 250px; height: 250px; border: 1px solid #555555;\" />\n"
                + "</div>\n"
                + "<p style=\"font-size: 12px; margin: 6px 0;\"><b>Instrucciones:</b> Escanea el QR desde tu banca móvil y confirma la transferencia por <b>Bs. " + String.format("%.2f", precio) + "</b>.<br>\n"
                + "Una vez realizado el pago, responde con el comando: <code>VERIFICAR_PAGO[" + pagoId + "];</code> para activar tu suscripción inmediatamente.</p>\n";
        return new CommandResponse(true, plain, MailFormatter.wrapHtml("Orden de Pago QR - " + planNombre, html, true));
    }
    private CommandResponse ejecutarVerificarPago(String cmd) throws Exception {
        String args = extraerArgumentos(cmd);
        if (args.isBlank()) {
            return new CommandResponse(false, "Uso: VERIFICAR_PAGO[pago_id];",
                    MailFormatter.formatErrorHtml("Falta ID", "Uso: VERIFICAR_PAGO[pago_id];"));
        }
        int pagoId = Integer.parseInt(args.replaceAll("[^0-9]", ""));
        String transId = "";
        int suscripcionId = -1;
        String estadoActual = "";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT referencia_pago, id_suscripciones, estado FROM pagos WHERE id_pagos = ?")) {
            ps.setInt(1, pagoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    transId = rs.getString(1);
                    suscripcionId = rs.getInt(2);
                    estadoActual = rs.getString(3);
                } else {
                    return new CommandResponse(false, "El pago #" + pagoId + " no existe.",
                            MailFormatter.formatErrorHtml("Pago No Encontrado", "El pago #" + pagoId + " no existe."));
                }
            }
        }
        if ("APROBADO".equalsIgnoreCase(estadoActual)) {
            String ya = "El pago #" + pagoId + " ya se encuentra APROBADO y la suscripción está activa.";
            return new CommandResponse(true, ya, MailFormatter.formatSuccessHtml("Pago Ya Verificado", ya));
        }
        bo.uagrm.bolsa.services.PagoFacilService pf = new bo.uagrm.bolsa.services.PagoFacilService();
        int statusPasarela = pf.consultarTransaccion(transId);
        if (statusPasarela == 2 || statusPasarela == 5) {
            String numFactura = "";
            String codigoAuth = "";
            String razonCliente = "";
            String nitCliente = "";
            double montoFactura = 0.0;
            String conceptoFactura = "";
            try (Connection conn = DatabaseManager.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE pagos SET estado = 'APROBADO' WHERE id_pagos = ?")) {
                    ps.setInt(1, pagoId);
                    ps.executeUpdate();
                }
                if (suscripcionId > 0) {
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE suscripciones SET estado = 'ACTIVA' WHERE id_suscripciones = ?")) {
                        ps.setInt(1, suscripcionId);
                        ps.executeUpdate();
                    }
                }
                // Consultar la factura generada automáticamente por el trigger tr_generar_factura_pago
                try (PreparedStatement psF = conn.prepareStatement(
                        "SELECT numero_factura, codigo_autorizacion, razon_social_cliente, nit_cliente, monto_total, concepto " +
                        "FROM facturas WHERE id_pagos = ?")) {
                    psF.setInt(1, pagoId);
                    try (ResultSet rsF = psF.executeQuery()) {
                        if (rsF.next()) {
                            numFactura = rsF.getString(1);
                            codigoAuth = rsF.getString(2);
                            razonCliente = rsF.getString(3);
                            nitCliente = rsF.getString(4);
                            montoFactura = rsF.getDouble(5);
                            conceptoFactura = rsF.getString(6);
                        }
                    }
                }
            }
            StringBuilder htmlSuccess = new StringBuilder();
            htmlSuccess.append("<div style=\"background-color: #d1e7dd; color: #0f5132; padding: 12px 15px; border-radius: 6px; font-weight: bold; margin-bottom: 15px;\">\n");
            htmlSuccess.append("  ✓ ¡PAGO CONFIRMADO Y SUSCRIPCIÓN ACTIVADA!\n");
            htmlSuccess.append("</div>\n");
            htmlSuccess.append("<p style=\"font-size: 14px;\">La pasarela Pago Fácil Bolivia confirmó la transacción con éxito. Tu suscripción #<b>").append(suscripcionId).append("</b> ya se encuentra activa para publicar ofertas laborales.</p>\n");
            if (!numFactura.isEmpty()) {
                htmlSuccess.append("<div style=\"border: 2px dashed #198754; background: #f8fff9; padding: 18px; border-radius: 8px; margin-top: 20px; font-family: Arial, sans-serif;\">\n")
                           .append("  <div style=\"display: flex; justify-content: space-between; border-bottom: 2px solid #198754; padding-bottom: 8px; margin-bottom: 12px;\">\n")
                           .append("    <div>\n")
                           .append("      <h4 style=\"margin: 0; color: #198754; font-size: 16px;\">COMPROBANTE FISCAL ELECTRÓNICO</h4>\n")
                           .append("      <span style=\"font-size: 11px; color: #555;\">Bolsa de Trabajo UAGRM - FICCT | NIT: 1029384756</span>\n")
                           .append("    </div>\n")
                           .append("    <div style=\"text-align: right;\">\n")
                           .append("      <span style=\"background: #198754; color: #fff; padding: 3px 8px; border-radius: 4px; font-size: 11px; font-weight: bold;\">PAGADO</span>\n")
                           .append("    </div>\n")
                           .append("  </div>\n")
                           .append("  <table style=\"width: 100%; font-size: 13px; line-height: 1.6;\">\n")
                           .append("    <tr><td style=\"color:#666;\">Factura N°:</td><td><b>").append(numFactura).append("</b></td></tr>\n")
                           .append("    <tr><td style=\"color:#666;\">Razón Social:</td><td><b>").append(razonCliente).append("</b></td></tr>\n")
                           .append("    <tr><td style=\"color:#666;\">NIT / CI:</td><td>").append(nitCliente).append("</td></tr>\n")
                           .append("    <tr><td style=\"color:#666;\">Concepto:</td><td>").append(conceptoFactura).append("</td></tr>\n")
                           .append("    <tr><td style=\"color:#666;\">Monto Total:</td><td style=\"font-size: 16px; font-weight: bold; color: #198754;\">Bs. ").append(String.format("%.2f", montoFactura)).append("</td></tr>\n")
                           .append("    <tr><td style=\"color:#666;\">Código Autorización:</td><td style=\"font-family: monospace; font-size: 11px;\">").append(codigoAuth).append("</td></tr>\n")
                           .append("  </table>\n")
                           .append("</div>\n");
            }
            String msg = "¡Pago confirmado con éxito en Pago Fácil! Tu suscripción #" + suscripcionId + " ha sido ACTIVADA."
                       + (!numFactura.isEmpty() ? "\nFactura oficial emitida: " + numFactura + " por Bs. " + String.format("%.2f", montoFactura) : "");
            return new CommandResponse(true, msg, MailFormatter.wrapHtml("Suscripción Activada & Factura Digital", htmlSuccess.toString(), true));
        } else {
            String pend = "El pago #" + pagoId + " (Transacción #" + transId + ") aún figura como pendiente en Pago Fácil (Código estado: " + statusPasarela + ").\n"
                    + "Por favor completa la transferencia desde tu banca móvil y vuelve a verificar.";
            return new CommandResponse(false, pend, MailFormatter.formatErrorHtml("Pago Aún No Confirmado", pend));
        }
    }
    // =========================================================================
    // COMANDOS CRUD ESTÁNDAR (LIST, SHOW, INSERT, UPDATE, DELETE)
    // =========================================================================
    private CommandResponse ejecutarProceduralList(String tabla, String args) throws Exception {
        String cols = "*";
        if (args != null && !args.isBlank()) {
            cols = args.trim();
        }
        // Seguridad: Ocultar columna password si se consulta usuarios
        if (tabla.equals("usuarios") && cols.equals("*")) {
            cols = "id_usuarios, id_roles, ci, nombre, apellido, email, telefono, estado, fecha_registro";
        }
        String pkCol = "id_" + tabla;
        String sql = "SELECT " + cols + " FROM " + tabla;
        try {
            sql += " ORDER BY " + pkCol;
            return ejecutarSelectQuery(sql, "Listado de " + tabla);
        } catch (SQLException e) {
            // Vistas o tablas sin columna de PK directa
            return ejecutarSelectQuery("SELECT " + cols + " FROM " + tabla, "Listado de " + tabla);
        }
    }

    private CommandResponse ejecutarProceduralShow(String tabla, String args) throws Exception {
        if (args == null || args.isBlank()) {
            return new CommandResponse(false, "Debe especificar el ID a consultar. Ejemplo: SHOW[" + tabla + ", 1]; o SHOW[" + tabla + ": 1];",
                    MailFormatter.formatErrorHtml("Falta ID", "Uso: SHOW[" + tabla + ", ID]; o SHOW[" + tabla + ": ID];"));
        }
        int id = Integer.parseInt(args.replaceAll("[^0-9]", ""));
        String cols = "*";
        if (tabla.equals("usuarios")) {
            cols = "id_usuarios, id_roles, ci, nombre, apellido, email, telefono, estado, fecha_registro";
        }
        String pkCol = "id_" + tabla;
        String sql = "SELECT " + cols + " FROM " + tabla + " WHERE " + pkCol + " = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ejecutarSelectPreparedStatement(ps, "Ficha Detallada: " + tabla + " #" + id);
        }
    }

    private CommandResponse ejecutarProceduralInsert(String tabla, String args) throws Exception {
        if (args == null || args.isBlank()) {
            return new CommandResponse(false, "Debe especificar los valores para insertar en " + tabla,
                    MailFormatter.formatErrorHtml("Faltan Datos", "Uso: INSERT[" + tabla + ": valores...];"));
        }
        String sql = "INSERT INTO " + tabla + " VALUES (" + args + ")";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
        String msg = "Registro insertado exitosamente en la tabla '" + tabla + "'.";
        return new CommandResponse(true, msg, MailFormatter.formatSuccessHtml("Inserción Exitosa", msg));
    }

    private CommandResponse ejecutarProceduralUpdate(String tabla, String args) throws Exception {
        if (args == null || !args.contains(",")) {
            return new CommandResponse(false, "Uso: UPDATE[" + tabla + ", id, columna=valor];\nO: UPDATE[" + tabla + ": id, columna=valor];",
                    MailFormatter.formatErrorHtml("Sintaxis Incorrecta", "Uso: UPDATE[" + tabla + ", id, columna=valor];"));
        }
        String[] partes = args.split(",", 2);
        int id = Integer.parseInt(partes[0].replaceAll("[^0-9]", ""));
        String setClause = partes[1].trim();
        // Soportar también coma en vez de igual: UPDATE[estudiantes, 1, semestre, 8]
        if (!setClause.contains("=") && setClause.contains(",")) {
            String[] kv = setClause.split(",", 2);
            String col = kv[0].trim();
            String val = kv[1].trim();
            if (val.matches("^-?\\d+(\\.\\d+)?$")) {
                setClause = col + " = " + val;
            } else {
                setClause = col + " = '" + val.replace("'", "''") + "'";
            }
        }
        String pkCol = "id_" + tabla;
        String sql = "UPDATE " + tabla + " SET " + setClause + " WHERE " + pkCol + " = ?";
        int filas;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            filas = ps.executeUpdate();
        }
        String msg = "Se actualizaron " + filas + " registro(s) en '" + tabla + "' (ID #" + id + ").";
        return new CommandResponse(true, msg, MailFormatter.formatSuccessHtml("Actualización Exitosa", msg));
    }

    private CommandResponse ejecutarProceduralDelete(String tabla, String args) throws Exception {
        if (args == null || args.isBlank()) {
            return new CommandResponse(false, "Debe especificar el ID a eliminar. Ejemplo: DELETE[" + tabla + ", 1]; o DELETE[" + tabla + ": 1];",
                    MailFormatter.formatErrorHtml("Falta ID", "Uso: DELETE[" + tabla + ", ID]; o DELETE[" + tabla + ": ID];"));
        }
        int id = Integer.parseInt(args.replaceAll("[^0-9]", ""));
        String pkCol = "id_" + tabla;
        String sql = "DELETE FROM " + tabla + " WHERE " + pkCol + " = ?";
        int filas;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            filas = ps.executeUpdate();
        }
        String msg = "Se eliminaron " + filas + " registro(s) de '" + tabla + "' (ID #" + id + ").";
        return new CommandResponse(true, msg, MailFormatter.formatSuccessHtml("Eliminación Exitosa", msg));
    }

    private CommandResponse ejecutarSelectPreparedStatement(PreparedStatement ps, String titulo) throws Exception {
        try (ResultSet rs = ps.executeQuery()) {
            return formatearResultSet(rs, titulo);
        }
    }

    private CommandResponse ejecutarSelectQuery(String sql, String titulo) throws Exception {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return formatearResultSet(rs, titulo);
        }
    }

    private CommandResponse formatearResultSet(ResultSet rs, String titulo) throws Exception {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        StringBuilder text = new StringBuilder();
        text.append("=== ").append(titulo.toUpperCase()).append(" ===\n");
        StringBuilder html = new StringBuilder();
        html.append("<table border=\"1\" cellpadding=\"4\" style=\"border-collapse: collapse; font-family: Arial, sans-serif; font-size: 12px; border: 1px solid #555555; width: 100%;\">\n");
        html.append("  <thead>\n    <tr style=\"background-color: #e2e2e2; color: #000000; font-weight: bold; text-align: left;\">\n");
        for (int i = 1; i <= colCount; i++) {
            String name = meta.getColumnLabel(i);
            html.append("      <th style=\"border: 1px solid #555555; padding: 4px;\">")
                .append(name.toUpperCase()).append("</th>\n");
            text.append(String.format("%-18s", name.toUpperCase()));
        }
        html.append("    </tr>\n  </thead>\n  <tbody>\n");
        text.append("\n").append("-".repeat(Math.max(18, colCount * 18))).append("\n");
        int rows = 0;
        while (rs.next()) {
            rows++;
            html.append("    <tr>\n");
            for (int i = 1; i <= colCount; i++) {
                Object val = rs.getObject(i);
                String valStr = val != null ? val.toString() : "—";
                html.append("      <td style=\"border: 1px solid #555555; padding: 4px;\">").append(valStr).append("</td>\n");
                text.append(String.format("%-18s", valStr.length() > 17 ? valStr.substring(0, 14) + "..." : valStr));
            }
            html.append("    </tr>\n");
            text.append("\n");
        }
        html.append("  </tbody>\n</table>\n");
        html.append("<p style=\"font-size: 11px; color: #555555; margin-top: 8px;\">Total de registros: ").append(rows).append("</p>\n");
        text.append("\nTotal registros: ").append(rows).append("\n");
        return new CommandResponse(true, text.toString(), MailFormatter.wrapHtml(titulo, html.toString(), true));
    }
    private String extraerArgumentos(String cmd) {
        int open = cmd.indexOf('[');
        int close = cmd.lastIndexOf(']');
        if (open != -1 && close != -1 && close > open) {
            return cmd.substring(open + 1, close).trim();
        }
        return "";
    }
    private List<String> parseTokens(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher m = Pattern.compile("([^',\\s][^,]*?|\"[^\"]*\"|'[^']*')(?=\\s*,|\\s*$)").matcher(text);
        while (m.find()) {
            String token = m.group(1).trim();
            if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) {
                token = token.substring(1, token.length() - 1);
            }
            tokens.add(token);
        }
        return tokens;
    }
    // =========================================================================
    // MANUAL HELP
    // =========================================================================
    private String getHelpPageText() {
        return "=== GUIA DE CASOS DE USO Y COMANDOS - GRUPO 04 ===\n\n"
             + "CU1. Usuarios y Roles (Tablas: usuarios, roles, estudiantes, empresas):\n"
             + "  REGISTRAR_DIRECTOR[ci, nombre, apellido, email, password, telefono];\n"
             + "  REGISTRAR_SECRETARIA[ci, nombre, apellido, email, password, telefono];\n"
             + "  REGISTRAR_ESTUDIANTE[ci, nombre, apellido, email, password, telefono, ru, carrera, semestre, perfil, cv_url];\n"
             + "  REGISTRAR_EMPRESA[ci, nom_rep, ape_rep, email, password, tel, nit, razon, comercial, rubro, direccion, sitio_web];\n"
             + "  LIST[usuarios];\n"
             + "  LIST[roles];\n"
             + "  SHOW[usuarios, id]; (o SHOW[usuarios: id];)\n\n"
             + "CU2 & CU3. Perfiles e Informacion (Tablas: estudiantes, empresas, habilidades, areas_laborales):\n"
             + "  LIST[estudiantes];\n"
             + "  SHOW[estudiantes, id];\n"
             + "  LIST[empresas];\n"
             + "  SHOW[empresas, id];\n"
             + "  LIST[areas_laborales];\n"
             + "  LIST[habilidades];\n\n"
             + "CU4. Planes y Publicacion (Tablas: planes, suscripciones, pagos, facturas, ofertas):\n"
             + "  VER_PLANES;\n"
             + "  COMPRAR_PLAN[empresa_id, plan_id];\n"
             + "  VERIFICAR_PAGO[pago_id];\n"
             + "  PUBLICAR_OFERTA[titulo, area, salario, modalidad, vacantes, skills];\n"
             + "  PUBLICAR_OFERTA[empresa_id, titulo, area, salario, modalidad, vacantes, skills];\n"
             + "  LIST[planes];\n"
             + "  LIST[facturas];\n\n"
             + "CU5. Ofertas y Postulaciones (Tablas: ofertas, postulaciones):\n"
             + "  VER_OFERTAS;\n"
             + "  VER_OFERTAS[area_laboral]; (Ej: VER_OFERTAS[Software];)\n"
             + "  POSTULAR[oferta_id, mensaje];\n"
             + "  POSTULAR[oferta_id, estudiante_id, mensaje];\n"
             + "  MIS_POSTULACIONES;\n"
             + "  MIS_POSTULACIONES[estudiante_id];\n\n"
             + "CU6. Candidatos y Citas (Tablas: seguimientos, entrevistas):\n"
             + "  VER_CANDIDATOS[oferta_id];\n"
             + "  EVALUAR_CANDIDATO[postulacion_id, etapa, observaciones];\n"
             + "  AGENDAR_ENTREVISTA[postulacion_id, fecha_hora, modalidad, link_meet, entrevistador, telefono];\n"
             + "  LIST[entrevistas];\n\n"
             + "CU7. Moderacion (Tablas: moderaciones):\n"
             + "  MODERAR[oferta_id, APROBAR|RECHAZAR];\n"
             + "  MODERAR[EMPRESA|OFERTA, id, APROBAR|RECHAZAR, motivo];\n"
             + "  LIST[v_ofertas_pendientes_moderacion];\n\n"
             + "CU8. Reportes y Metricas (Vistas estadisticas):\n"
             + "  REPORTE[empleabilidad];\n"
             + "  REPORTE[postulaciones];\n"
             + "  REPORTE[recaudacion];\n"
             + "  REPORTE[empresas];\n"
             + "  REPORTE[facturas];\n";
    }
    private String getHelpPageHtml() {
        return MailFormatter.wrapHtml("Guia de Casos de Uso y Comandos",
            "<table border=\"1\" cellpadding=\"4\" style=\"border-collapse: collapse; font-family: Arial, sans-serif; font-size: 12px; border: 1px solid #555555; width: 100%;\">\n" +
            "  <thead>\n" +
            "    <tr style=\"background-color: #e2e2e2; font-weight: bold; text-align: left;\">\n" +
            "      <th width=\"28%\" style=\"border: 1px solid #555555; padding: 4px;\">Caso de Uso / Modulo</th>\n" +
            "      <th width=\"72%\" style=\"border: 1px solid #555555; padding: 4px;\">Comandos Disponibles (Aceptan ',' o ':')</th>\n" +
            "    </tr>\n" +
            "  </thead>\n" +
            "  <tbody>\n" +
            "    <!-- CU1: Usuarios y Roles -->\n" +
            "    <tr>\n" +
            "      <td rowspan=\"7\" style=\"font-weight: bold; border: 1px solid #555555; padding: 4px; vertical-align: top;\">\n" +
            "        CU1. Usuarios y Roles<br>\n" +
            "        <span style=\"font-size: 10px; font-weight: normal; color: #555555;\">Tablas: usuarios, roles, estudiantes, empresas</span>\n" +
            "      </td>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">REGISTRAR_DIRECTOR[ci, nombre, apellido, email, password, telefono];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">REGISTRAR_SECRETARIA[ci, nombre, apellido, email, password, telefono];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">REGISTRAR_ESTUDIANTE[ci, nombre, apellido, email, password, telefono, ru, carrera, semestre, perfil, cv_url];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">REGISTRAR_EMPRESA[ci, nom_rep, ape_rep, email, password, tel, nit, razon, comercial, rubro, dir, web];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">LIST[usuarios];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">LIST[roles];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">SHOW[usuarios, id]; (o SHOW[usuarios: id];)</td>\n" +
            "    </tr>\n" +
            "    <!-- CU2: Perfiles e Informacion -->\n" +
            "    <tr>\n" +
            "      <td rowspan=\"6\" style=\"font-weight: bold; border: 1px solid #555555; padding: 4px; vertical-align: top;\">\n" +
            "        CU2 & CU3. Perfiles e Informacion<br>\n" +
            "        <span style=\"font-size: 10px; font-weight: normal; color: #555555;\">Tablas: estudiantes, empresas, habilidades, areas_laborales</span>\n" +
            "      </td>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">LIST[estudiantes];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">SHOW[estudiantes, id]; (o SHOW[estudiantes: id];)</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">LIST[empresas];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">SHOW[empresas, id]; (o SHOW[empresas: id];)</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">LIST[areas_laborales];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">LIST[habilidades];</td>\n" +
            "    </tr>\n" +
            "    <!-- CU4: Planes y Publicacion -->\n" +
            "    <tr>\n" +
            "      <td rowspan=\"6\" style=\"font-weight: bold; border: 1px solid #555555; padding: 4px; vertical-align: top;\">\n" +
            "        CU4. Planes y Publicacion<br>\n" +
            "        <span style=\"font-size: 10px; font-weight: normal; color: #555555;\">Tablas: planes, suscripciones, pagos, facturas, ofertas</span>\n" +
            "      </td>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">VER_PLANES;</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">COMPRAR_PLAN[empresa_id, plan_id];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">VERIFICAR_PAGO[pago_id];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">PUBLICAR_OFERTA[titulo, area, salario, modalidad, vacantes, skills];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">PUBLICAR_OFERTA[empresa_id, titulo, area, salario, modalidad, vacantes, skills];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">LIST[facturas];</td>\n" +
            "    </tr>\n" +
            "    <!-- CU5: Ofertas y Postulaciones -->\n" +
            "    <tr>\n" +
            "      <td rowspan=\"6\" style=\"font-weight: bold; border: 1px solid #555555; padding: 4px; vertical-align: top;\">\n" +
            "        CU5. Ofertas y Postulaciones<br>\n" +
            "        <span style=\"font-size: 10px; font-weight: normal; color: #555555;\">Tablas: ofertas, postulaciones</span>\n" +
            "      </td>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">VER_OFERTAS;</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">VER_OFERTAS[area_laboral]; (Ej: VER_OFERTAS[Software];)</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">POSTULAR[oferta_id, mensaje];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">POSTULAR[oferta_id, estudiante_id, mensaje];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">MIS_POSTULACIONES;</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">MIS_POSTULACIONES[estudiante_id];</td>\n" +
            "    </tr>\n" +
            "    <!-- CU6: Candidatos y Citas -->\n" +
            "    <tr>\n" +
            "      <td rowspan=\"4\" style=\"font-weight: bold; border: 1px solid #555555; padding: 4px; vertical-align: top;\">\n" +
            "        CU6. Candidatos y Citas<br>\n" +
            "        <span style=\"font-size: 10px; font-weight: normal; color: #555555;\">Tablas: seguimientos, entrevistas</span>\n" +
            "      </td>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">VER_CANDIDATOS[oferta_id];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">EVALUAR_CANDIDATO[postulacion_id, etapa, observaciones];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">AGENDAR_ENTREVISTA[postulacion_id, fecha_hora, modalidad, link_meet, entrevistador, telefono];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">LIST[entrevistas];</td>\n" +
            "    </tr>\n" +
            "    <!-- CU7: Moderacion -->\n" +
            "    <tr>\n" +
            "      <td rowspan=\"3\" style=\"font-weight: bold; border: 1px solid #555555; padding: 4px; vertical-align: top;\">\n" +
            "        CU7. Moderacion<br>\n" +
            "        <span style=\"font-size: 10px; font-weight: normal; color: #555555;\">Tablas: moderaciones</span>\n" +
            "      </td>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">MODERAR[oferta_id, APROBAR|RECHAZAR];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">MODERAR[EMPRESA|OFERTA, id, APROBAR|RECHAZAR, motivo];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">LIST[v_ofertas_pendientes_moderacion];</td>\n" +
            "    </tr>\n" +
            "    <!-- CU8: Reportes -->\n" +
            "    <tr>\n" +
            "      <td rowspan=\"5\" style=\"font-weight: bold; border: 1px solid #555555; padding: 4px; vertical-align: top;\">\n" +
            "        CU8. Reportes y Metricas<br>\n" +
            "        <span style=\"font-size: 10px; font-weight: normal; color: #555555;\">Vistas estadisticas</span>\n" +
            "      </td>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">REPORTE[empleabilidad];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">REPORTE[postulaciones];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">REPORTE[recaudacion];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">REPORTE[empresas];</td>\n" +
            "    </tr>\n" +
            "    <tr>\n" +
            "      <td style=\"font-family: monospace; border: 1px solid #555555; padding: 4px; line-height: 1.5;\">REPORTE[facturas];</td>\n" +
            "    </tr>\n" +
            "  </tbody>\n" +
            "</table>",
            true
        );
    }
}
