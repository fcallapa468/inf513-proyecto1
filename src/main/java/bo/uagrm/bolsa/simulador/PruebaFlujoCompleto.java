package bo.uagrm.bolsa.simulador;

import bo.uagrm.bolsa.commands.CommandResponse;
import bo.uagrm.bolsa.mail.RespuestaWorker;

import java.util.LinkedHashMap;
import java.util.Map;

public class PruebaFlujoCompleto {

    public static void main(String[] args) {
        RespuestaWorker.MODO_SIMULADOR = true;

        System.out.println("================================================================================");
        System.out.println("    VERIFICACION AUTOMATICA DE FLUJO COMPLETO DE COMANDOS (SIN ENVIAR EMAILS)   ");
        System.out.println("================================================================================\n");

        Map<String, String[]> casos = new LinkedHashMap<>();

        // CU1: Usuarios y Roles
        casos.put("HELP", new String[]{"usuario.prueba@gmail.com", "HELP;"});
        casos.put("LIST[roles]", new String[]{"usuario.prueba@gmail.com", "LIST[roles];"});
        casos.put("LIST[usuarios]", new String[]{"usuario.prueba@gmail.com", "LIST[usuarios];"});
        casos.put("SHOW[usuarios, 1]", new String[]{"usuario.prueba@gmail.com", "SHOW[usuarios, 1];"});
        casos.put("SHOW[usuarios: 1]", new String[]{"usuario.prueba@gmail.com", "SHOW[usuarios: 1];"});

        // CU2 & CU3: Perfiles e Información
        casos.put("LIST[estudiantes]", new String[]{"usuario.prueba@gmail.com", "LIST[estudiantes];"});
        casos.put("SHOW[estudiantes, 1]", new String[]{"usuario.prueba@gmail.com", "SHOW[estudiantes, 1];"});
        casos.put("LIST[empresas]", new String[]{"usuario.prueba@gmail.com", "LIST[empresas];"});
        casos.put("SHOW[empresas, 1]", new String[]{"usuario.prueba@gmail.com", "SHOW[empresas, 1];"});
        casos.put("LIST[areas_laborales]", new String[]{"usuario.prueba@gmail.com", "LIST[areas_laborales];"});
        casos.put("LIST[habilidades]", new String[]{"usuario.prueba@gmail.com", "LIST[habilidades];"});

        // CU4: Planes y Publicación
        casos.put("VER_PLANES", new String[]{"usuario.prueba@gmail.com", "VER_PLANES;"});
        casos.put("LIST[planes]", new String[]{"usuario.prueba@gmail.com", "LIST[planes];"});
        casos.put("COMPRAR_PLAN[1, 1]", new String[]{"rrhh@tigo.com.bo", "COMPRAR_PLAN[1, 1];"});
        casos.put("LIST[facturas]", new String[]{"usuario.prueba@gmail.com", "LIST[facturas];"});

        // CU5: Ofertas y Postulaciones
        casos.put("VER_OFERTAS", new String[]{"usuario.prueba@gmail.com", "VER_OFERTAS;"});
        casos.put("VER_OFERTAS[Software]", new String[]{"usuario.prueba@gmail.com", "VER_OFERTAS[Software];"});
        casos.put("MIS_POSTULACIONES[1]", new String[]{"mateo.morales@gmail.com", "MIS_POSTULACIONES[1];"});
        casos.put("MIS_POSTULACIONES (por remitente)", new String[]{"mateo.morales@gmail.com", "MIS_POSTULACIONES;"});

        // CU6: Candidatos y Entrevistas
        casos.put("VER_CANDIDATOS[1]", new String[]{"rrhh@tigo.com.bo", "VER_CANDIDATOS[1];"});
        casos.put("EVALUAR_CANDIDATO[1, ENTREVISTA, Buen CV]", new String[]{"rrhh@tigo.com.bo", "EVALUAR_CANDIDATO[1, ENTREVISTA, Buen CV];"});
        casos.put("LIST[entrevistas]", new String[]{"usuario.prueba@gmail.com", "LIST[entrevistas];"});

        // CU7: Moderación
        casos.put("LIST[v_ofertas_pendientes_moderacion]", new String[]{"director.roberto@gmail.com", "LIST[v_ofertas_pendientes_moderacion];"});
        casos.put("MODERAR[3, APROBAR]", new String[]{"director.roberto@gmail.com", "MODERAR[3, APROBAR];"});

        // CU8: Reportes y Métricas
        casos.put("REPORTE[empleabilidad]", new String[]{"director.roberto@gmail.com", "REPORTE[empleabilidad];"});
        casos.put("REPORTE[postulaciones]", new String[]{"director.roberto@gmail.com", "REPORTE[postulaciones];"});
        casos.put("REPORTE[recaudacion]", new String[]{"director.roberto@gmail.com", "REPORTE[recaudacion];"});
        casos.put("REPORTE[empresas]", new String[]{"director.roberto@gmail.com", "REPORTE[empresas];"});
        casos.put("REPORTE[facturas]", new String[]{"director.roberto@gmail.com", "REPORTE[facturas];"});

        int pasados = 0;
        int fallados = 0;

        for (Map.Entry<String, String[]> entry : casos.entrySet()) {
            String etiqueta = entry.getKey();
            String remitente = entry.getValue()[0];
            String cmd = entry.getValue()[1];

            try {
                CommandResponse resp = RespuestaWorker.procesarComandoDirecto(remitente, cmd);
                String estado = resp.isSuccess() ? "✅ EXITO" : "❌ ERROR";
                if (resp.isSuccess()) {
                    pasados++;
                } else {
                    fallados++;
                }
                System.out.printf("[%s] %-35s -> %s\n", estado, cmd, extraerResumen(resp.getMessage()));
            } catch (Exception e) {
                fallados++;
                System.out.printf("[💥 EXCEPCION] %-35s -> %s\n", cmd, e.getMessage());
            }
        }

        System.out.println("\n================================================================================");
        System.out.printf("RESULTADOS: %d Pasados | %d Fallados | Total: %d\n", pasados, fallados, casos.size());
        System.out.println("================================================================================");
    }

    private static String extraerResumen(String msg) {
        if (msg == null) return "(vacio)";
        String[] lines = msg.trim().split("\n");
        for (String l : lines) {
            String t = l.trim();
            if (!t.isEmpty() && !t.startsWith("===") && !t.startsWith("---")) {
                if (t.length() > 60) return t.substring(0, 57) + "...";
                return t;
            }
        }
        return lines[0];
    }
}
