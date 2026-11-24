package bo.uagrm.bolsa.simulador;

import bo.uagrm.bolsa.commands.CommandResponse;
import bo.uagrm.bolsa.mail.RespuestaWorker;

public class PruebaFlujoPresentacion {

    public static void main(String[] args) {
        RespuestaWorker.MODO_SIMULADOR = true;

        System.out.println("================================================================================");
        System.out.println("     VERIFICACION AUTOMATICA DE LA DEMOSTRACION PARA LA PRESENTACION           ");
        System.out.println("                   Bolsa de Trabajos UAGRM - Grupo 04                           ");
        System.out.println("================================================================================\n");

        String[][] flujo = {
            {
                "1. CONSULTAR AYUDA DEL SISTEMA (HELP)",
                "docente@uagrm.edu.bo",
                "HELP;"
            },
            {
                "2. REGISTRAR AUTORIDAD (DIRECTOR DE CARRERA)",
                "docente@uagrm.edu.bo",
                "REGISTRAR_DIRECTOR[4567890, Carlos, Roca, carlos.roca@uagrm.edu.bo, dir123, 77012345];"
            },
            {
                "3. REGISTRAR NUEVA EMPRESA (EMPLEADOR)",
                "rrhh@novared.bo",
                "REGISTRAR_EMPRESA[6543210, Roberto, Paz, rrhh@novared.bo, emp123, 75512345, 1029384700, NOVARED S.R.L., NovaRed Soluciones, Telecomunicaciones, Av. Cristo Redentor 450, https://novared.bo];"
            },
            {
                "4. CONSULTAR CATÁLOGO DE PLANES",
                "rrhh@novared.bo",
                "VER_PLANES;"
            },
            {
                "5. PUBLICAR VACANTE LABORAL (QUEDA PENDIENTE DE MODERACIÓN)",
                "rrhh@novared.bo",
                "PUBLICAR_OFERTA[Ingeniero DevOps Cloud, Desarrollo de Software, 6000, REMOTO, 2, Docker Kubernetes AWS];"
            },
            {
                "6. EL DIRECTOR REVISA OFERTAS PENDIENTES DE MODERACIÓN",
                "carlos.roca@uagrm.edu.bo",
                "LIST[v_ofertas_pendientes_moderacion];"
            },
            {
                "7. EL DIRECTOR APRUEBA LA OFERTA (#3)",
                "carlos.roca@uagrm.edu.bo",
                "MODERAR[3, APROBAR];"
            },
            {
                "8. REGISTRAR UN NUEVO ESTUDIANTE",
                "lucas.mendez@uagrm.edu.bo",
                "REGISTRAR_ESTUDIANTE[8765432, Lucas, Mendez, lucas.mendez@uagrm.edu.bo, est123, 78901234, 221099881, Ingenieria en Sistemas, 9, DevOps y Cloud, https://github.com/lucas];"
            },
            {
                "9. EL ESTUDIANTE CONSULTA OFERTAS PUBLICADAS",
                "lucas.mendez@uagrm.edu.bo",
                "VER_OFERTAS;"
            },
            {
                "10. EL ESTUDIANTE SE POSTULA A LA OFERTA (#3)",
                "lucas.mendez@uagrm.edu.bo",
                "POSTULAR[3, Cuento con certificaciones en Docker y AWS, disponibilidad inmediata.];"
            },
            {
                "11. EL ESTUDIANTE VERIFICA SUS POSTULACIONES",
                "lucas.mendez@uagrm.edu.bo",
                "MIS_POSTULACIONES;"
            },
            {
                "12. LA EMPRESA REVISA LOS CANDIDATOS POSTULADOS",
                "rrhh@novared.bo",
                "VER_CANDIDATOS[3];"
            },
            {
                "13. LA EMPRESA EVALÚA AL CANDIDATO (#3)",
                "rrhh@novared.bo",
                "EVALUAR_CANDIDATO[3, ENTREVISTA, Excelente perfil tecnico y certificaciones];"
            },
            {
                "14. LA EMPRESA AGENDA ENTREVISTA VIRTUAL",
                "rrhh@novared.bo",
                "AGENDAR_ENTREVISTA[3, 2026-11-28 15:00, VIRTUAL, https://meet.google.com/devops-meet, Lic. Roberto Paz, 75512345];"
            },
            {
                "15. REPORTE GERENCIAL: POSTULACIONES POR VACANTE",
                "carlos.roca@uagrm.edu.bo",
                "REPORTE[postulaciones];"
            },
            {
                "16. REPORTE GERENCIAL: EMPLEABILIDAD POR CARRERA",
                "carlos.roca@uagrm.edu.bo",
                "REPORTE[empleabilidad];"
            },
            {
                "17. REPORTE GERENCIAL: RECAUDACIÓN POR PLANES",
                "carlos.roca@uagrm.edu.bo",
                "REPORTE[recaudacion];"
            }
        };

        int exitos = 0;
        int fallos = 0;

        for (int i = 0; i < flujo.length; i++) {
            String paso = flujo[i][0];
            String remitente = flujo[i][1];
            String cmd = flujo[i][2];

            System.out.printf("[%02d/%02d] %s\n", (i + 1), flujo.length, paso);
            System.out.printf("       Remitente: %s\n", remitente);
            System.out.printf("       Comando:   %s\n", cmd);

            try {
                CommandResponse resp = RespuestaWorker.procesarComandoDirecto(remitente, cmd);
                if (resp.isSuccess()) {
                    exitos++;
                    System.out.println("       Resultado: ✅ EXITO -> " + extraerPrimeraLinea(resp.getMessage()));
                } else {
                    fallos++;
                    System.out.println("       Resultado: ❌ ERROR -> " + extraerPrimeraLinea(resp.getMessage()));
                }
            } catch (Exception e) {
                fallos++;
                System.out.println("       Resultado: 💥 EXCEPCION -> " + e.getMessage());
            }
            System.out.println();
        }

        System.out.println("================================================================================");
        System.out.printf("RESUMEN DE PRUEBA: %d Exitosos | %d Fallados | Total: %d\n", exitos, fallos, flujo.length);
        if (fallos == 0) {
            System.out.println("¡TODO EL FLUJO DE LA PRESENTACIÓN FUNCIONA AL 100%! LISTO PARA MAÑANA.");
        } else {
            System.out.println("ATENCIÓN: Revisa los errores anteriores antes de la presentación.");
        }
        System.out.println("================================================================================\n");
        System.out.println("CONSEJO: Para dejar la base de datos limpia otra vez, ejecuta: restablecer_bd.bat");
    }

    private static String extraerPrimeraLinea(String msg) {
        if (msg == null) return "";
        String[] lines = msg.trim().split("\n");
        for (String l : lines) {
            String t = l.trim();
            if (!t.isEmpty() && !t.startsWith("===") && !t.startsWith("---")) {
                if (t.length() > 65) return t.substring(0, 62) + "...";
                return t;
            }
        }
        return lines[0].length() > 65 ? lines[0].substring(0, 62) + "..." : lines[0];
    }
}
