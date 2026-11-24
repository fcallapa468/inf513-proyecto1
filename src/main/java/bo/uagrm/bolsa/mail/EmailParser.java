package bo.uagrm.bolsa.mail;

public class EmailParser {

    private final String remitente;
    private final String asunto;
    private final String cuerpo;

    public EmailParser(String rawEmail) {
        String normalizado = rawEmail.replace("\r\n", "\n");
        String[] partes = normalizado.split("\n\n", 2);
        String cabeceras = partes[0].replaceAll("\n[ \t]+", " ");
        String contenido = partes.length > 1 ? partes[1] : "";

        String rem = "", asu = "";
        String encodingTop = "";
        for (String linea : cabeceras.split("\n")) {
            String lower = linea.toLowerCase();
            if (lower.startsWith("from:")) rem = normalizarDireccion(linea.substring(5).trim());
            else if (lower.startsWith("subject:")) asu = decodeMimeHeader(linea.substring(8).trim());
            else if (lower.startsWith("content-transfer-encoding:")) {
                encodingTop = linea.substring(26).trim().toLowerCase();
            }
        }
        this.remitente = rem;
        this.asunto    = asu;
        this.cuerpo    = extraerCuerpo(contenido, encodingTop).trim();
    }

    public String getRemitente() { return remitente; }
    public String getAsunto()    { return asunto; }
    public String getComando()   { return cuerpo; }

    public static String decodeMimeHeader(String value) {
        if (value == null || !value.contains("=?")) {
            return value;
        }

        StringBuilder sb = new StringBuilder();
        int lastPos = 0;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("=\\?([^?]+)\\?([BbQq])\\?([^?]*)\\?=");
        java.util.regex.Matcher matcher = pattern.matcher(value);

        boolean hasEncodedWords = false;
        while (matcher.find()) {
            hasEncodedWords = true;
            String between = value.substring(lastPos, matcher.start());
            if (lastPos == 0 || !between.trim().isEmpty()) {
                sb.append(between);
            }

            String charset = matcher.group(1);
            String encoding = matcher.group(2).toUpperCase();
            String encodedText = matcher.group(3);

            try {
                if (encoding.equals("B")) {
                    byte[] bytes = java.util.Base64.getDecoder().decode(encodedText);
                    sb.append(new String(bytes, charset));
                } else if (encoding.equals("Q")) {
                    sb.append(decodeQuotedPrintableHeader(encodedText, charset));
                } else {
                    sb.append(matcher.group(0));
                }
            } catch (Exception e) {
                sb.append(matcher.group(0));
            }
            lastPos = matcher.end();
        }

        if (hasEncodedWords) {
            if (lastPos < value.length()) {
                sb.append(value.substring(lastPos));
            }
            return sb.toString();
        }

        return value;
    }

    private static String decodeQuotedPrintableHeader(String text, String charset) throws java.io.UnsupportedEncodingException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '_') {
                buffer.write(' ');
            } else if (c == '=' && i + 2 < text.length()) {
                try {
                    int val = Integer.parseInt(text.substring(i + 1, i + 3), 16);
                    buffer.write(val);
                    i += 2;
                } catch (NumberFormatException e) {
                    buffer.write(c);
                }
            } else {
                buffer.write((byte) c);
            }
        }
        return buffer.toString(charset);
    }

    private String normalizarDireccion(String from) {
        int inicio = from.indexOf('<');
        int fin    = from.indexOf('>');
        if (inicio != -1 && fin > inicio) {
            return from.substring(inicio + 1, fin).trim().toLowerCase();
        }
        return from.replaceAll("\"", "").trim().toLowerCase();
    }

    private String extraerCuerpo(String contenido, String encodingGlobal) {
        String contentType = detectarContentType(contenido);
        if (contentType.contains("multipart")) {
            return extraerDeMultipart(contenido);
        }
        return decodificar(contenido, encodingGlobal);
    }

    private String extraerDeMultipart(String contenido) {
        String boundary = extraerBoundary(contenido);
        if (boundary == null) return contenido;

        String separador = "--" + boundary;
        String[] partes = contenido.split(java.util.regex.Pattern.quote(separador));

        String cuerpoTexto = "";
        String cuerpoHtml  = "";

        for (String parte : partes) {
            if (parte.isBlank() || parte.trim().equals("--")) continue;

            String normalizada = parte.replace("\r\n", "\n").stripLeading();
            String[] subPartes = normalizada.split("\n\n", 2);
            if (subPartes.length < 2) continue;

            String cabeceras = subPartes[0].toLowerCase();
            String cuerpo    = subPartes[1];

            String encoding = "";
            for (String l : cabeceras.split("\n")) {
                if (l.startsWith("content-transfer-encoding:")) {
                    encoding = l.substring(26).trim();
                }
            }

            if (cabeceras.contains("text/plain")) {
                cuerpoTexto = decodificar(cuerpo, encoding);
            } else if (cabeceras.contains("text/html") && cuerpoTexto.isEmpty()) {
                cuerpoHtml = decodificar(cuerpo, encoding);
            }
        }

        if (!cuerpoTexto.isEmpty()) return cuerpoTexto;
        if (!cuerpoHtml.isEmpty())  return limpiarHtml(cuerpoHtml);
        return contenido;
    }

    private String decodificar(String texto, String encoding) {
        if ("base64".equalsIgnoreCase(encoding)) {
            try {
                String limpia = texto.replaceAll("[\\r\\n\\s]", "");
                byte[] decoded = java.util.Base64.getDecoder().decode(limpia);
                return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                return texto;
            }
        }
        if ("quoted-printable".equalsIgnoreCase(encoding)) {
            return decodificarQuotedPrintable(texto);
        }
        return texto;
    }

    private String decodificarQuotedPrintable(String qp) {
        try {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            for (int i = 0; i < qp.length(); i++) {
                char c = qp.charAt(i);
                if (c == '=') {
                    if (i + 1 < qp.length() && (qp.charAt(i + 1) == '\n' || qp.charAt(i + 1) == '\r')) {
                        if (qp.charAt(i + 1) == '\r' && i + 2 < qp.length() && qp.charAt(i + 2) == '\n') i += 2;
                        else i += 1;
                    } else if (i + 2 < qp.length()) {
                        int val = Integer.parseInt(qp.substring(i + 1, i + 3), 16);
                        buffer.write(val);
                        i += 2;
                    }
                } else {
                    buffer.write((byte) c);
                }
            }
            return buffer.toString(java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return qp;
        }
    }

    private String detectarContentType(String contenido) {
        for (String linea : contenido.split("\n")) {
            if (linea.toLowerCase().startsWith("content-type:")) return linea.toLowerCase();
        }
        return "";
    }

    private String extraerBoundary(String contenido) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("boundary=\"?([^\";\\s]+)\"?", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(contenido);
        return m.find() ? m.group(1) : null;
    }

    private String limpiarHtml(String html) {
        return html.replaceAll("<style[^>]*>.*?</style>", "")
                   .replaceAll("<script[^>]*>.*?</script>", "")
                   .replaceAll("<[^>]+>", " ")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("[ \t]+", " ")
                   .trim();
    }
}
