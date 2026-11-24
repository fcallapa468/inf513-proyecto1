package bo.uagrm.bolsa.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import bo.uagrm.bolsa.config.Config;

public class PagoFacilService {

    private static final Logger log = LoggerFactory.getLogger(PagoFacilService.class);

    private final HttpClient client;
    private final String baseUrl;
    private final String tokenService;
    private final String tokenSecret;

    private static String cachedToken = null;
    private static long tokenExpiryMillis = 0;

    public PagoFacilService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.baseUrl = Config.PAGOFACIL_BASE_URL;
        this.tokenService = Config.PAGOFACIL_TOKEN_SERVICE;
        this.tokenSecret = Config.PAGOFACIL_TOKEN_SECRET;
    }

    public synchronized String getAccessToken() throws Exception {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiryMillis) {
            return cachedToken;
        }

        log.info("Autenticando con Pago Fácil en {}/login...", baseUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login"))
                .header("tcTokenService", tokenService)
                .header("tcTokenSecret", tokenSecret)
                .header("Content-Type", "application/json")
                .header("Response-Language", "es")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Error de autenticación con Pago Fácil: HTTP " + response.statusCode() + " - " + response.body());
        }

        String body = response.body();
        String token = extractJsonField(body, "accessToken");
        String expiresStr = extractJsonFieldAny(body, "expiresInMinutes");

        if (token == null) {
            throw new RuntimeException("No se recibió el accessToken en la respuesta de login: " + body);
        }

        cachedToken = token;
        double expiresMinutes = expiresStr != null ? Double.parseDouble(expiresStr) : 10.0;
        tokenExpiryMillis = System.currentTimeMillis() + (long)((expiresMinutes - 2.0) * 60 * 1000);

        log.info("Autenticación con Pago Fácil exitosa. Token válido por {} minutos.", expiresMinutes);
        return cachedToken;
    }

    public QrResponse generarQr(long suscripcionId, String planNombre, double monto, String clienteNombre, String clienteCi, String clienteTelefono, String clienteCorreo) throws Exception {
        String token = getAccessToken();

        String companyTxId = "BOLSA-" + suscripcionId + "-" + (System.currentTimeMillis() / 1000);

        // Para evitar cobros reales durante pruebas usamos 0.01 BOB si monto > 0
        double montoCobro = monto > 0 ? 0.01 : 0.01;

        String json = "{\n" +
                "    \"paymentMethod\": 34,\n" +
                "    \"clientName\": \"" + escapeJson(clienteNombre) + "\",\n" +
                "    \"documentType\": 1,\n" +
                "    \"documentId\": \"" + escapeJson(clienteCi) + "\",\n" +
                "    \"phoneNumber\": \"" + escapeJson(clienteTelefono) + "\",\n" +
                "    \"email\": \"" + escapeJson(clienteCorreo) + "\",\n" +
                "    \"paymentNumber\": \"" + companyTxId + "\",\n" +
                "    \"amount\": " + montoCobro + ",\n" +
                "    \"currency\": 2,\n" +
                "    \"clientCode\": \"" + suscripcionId + "\",\n" +
                "    \"callbackUrl\": \"https://uagrm.edu.bo/callback\",\n" +
                "    \"orderDetail\": [\n" +
                "        {\n" +
                "            \"serial\": 1,\n" +
                "            \"product\": \"Plan " + escapeJson(planNombre) + " - Bolsa UAGRM\",\n" +
                "            \"quantity\": 1,\n" +
                "            \"price\": " + montoCobro + ",\n" +
                "            \"discount\": 0,\n" +
                "            \"total\": " + montoCobro + "\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        log.info("Generando QR en Pago Fácil para suscripción #{} con ID de empresa {}...", suscripcionId, companyTxId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/generate-qr"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Response-Language", "es")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Error al generar QR: HTTP " + response.statusCode() + " - " + response.body());
        }

        String body = response.body();
        String errorStr = extractJsonFieldAny(body, "error");

        if (errorStr != null && !errorStr.equals("0")) {
            String msg = extractJsonField(body, "message");
            throw new RuntimeException("Pago Fácil error " + errorStr + ": " + msg);
        }

        String valuesJson = extractJsonSection(body, "values");
        String transactionId = extractJsonFieldAny(valuesJson, "transactionId");
        String qrBase64 = extractJsonField(valuesJson, "qrBase64");

        if (transactionId == null || qrBase64 == null) {
            throw new RuntimeException("La respuesta de Pago Fácil no contiene transactionId o qrBase64: " + body);
        }

        qrBase64 = qrBase64.replace("\\/", "/");

        return new QrResponse(transactionId, companyTxId, qrBase64);
    }

    public int consultarTransaccion(String pagofacilTransactionId) throws Exception {
        String token = getAccessToken();

        String json = "{\n" +
                "    \"pagofacilTransactionId\": " + pagofacilTransactionId + "\n" +
                "}";

        log.info("Consultando estado de transacción #{} en Pago Fácil...", pagofacilTransactionId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/query-transaction"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Response-Language", "es")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Error al consultar transacción: HTTP " + response.statusCode() + " - " + response.body());
        }

        String body = response.body();
        String errorStr = extractJsonFieldAny(body, "error");

        if (errorStr != null && !errorStr.equals("0")) {
            String msg = extractJsonField(body, "message");
            throw new RuntimeException("Pago Fácil error " + errorStr + ": " + msg);
        }

        String valuesJson = extractJsonSection(body, "values");
        String statusStr = extractJsonFieldAny(valuesJson, "paymentStatus");

        if (statusStr == null) {
            throw new RuntimeException("No se encontró el estado de pago (paymentStatus) en la respuesta: " + body);
        }

        int status = Integer.parseInt(statusStr);
        log.info("Estado de transacción #{} en Pago Fácil: {} (status={})", pagofacilTransactionId, statusStr, status);
        return status;
    }

    // ================================================================== //
    //  JSON parsing utilities (sin librerías de terceros)
    // ================================================================== //

    private static String extractJsonField(String json, String field) {
        if (json == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + field + "\"[\\s:]*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String extractJsonFieldAny(String json, String field) {
        if (json == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + field + "\"[\\s:]*([^,}\\]]+)").matcher(json);
        if (m.find()) {
            return m.group(1).replace("\"", "").trim();
        }
        return null;
    }

    private static String extractJsonSection(String json, String sectionName) {
        if (json == null) return null;
        int idx = json.indexOf("\"" + sectionName + "\"");
        if (idx == -1) return null;
        int braceStart = json.indexOf("{", idx);
        if (braceStart == -1) return null;

        int depth = 0;
        int end = -1;
        for (int i = braceStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }
        return end != -1 ? json.substring(braceStart, end + 1) : null;
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    public static class QrResponse {
        private final String pagofacilTransactionId;
        private final String companyTransactionId;
        private final String qrBase64;

        public QrResponse(String pagofacilTransactionId, String companyTransactionId, String qrBase64) {
            this.pagofacilTransactionId = pagofacilTransactionId;
            this.companyTransactionId = companyTransactionId;
            this.qrBase64 = qrBase64;
        }

        public String getPagofacilTransactionId() { return pagofacilTransactionId; }
        public String getCompanyTransactionId() { return companyTransactionId; }
        public String getQrBase64() { return qrBase64; }
    }
}
