package bo.uagrm.bolsa.simulador;

import bo.uagrm.bolsa.commands.CommandResponse;
import bo.uagrm.bolsa.mail.RespuestaWorker;

import java.util.Scanner;

public class DemostracionGuiada {

    public static void main(String[] args) {
        RespuestaWorker.MODO_SIMULADOR = true;
        Scanner scanner = new Scanner(System.in);

        System.out.println("================================================================================");
        System.out.println("       DEMOSTRACIÓN INTERACTIVA DE FLUJO COMPLETO - GRUPO 04 TECNOWEB           ");
        System.out.println("                 (Ejecución Paso a Paso - Sin enviar emails)                    ");
        System.out.println("================================================================================\n");
        System.out.println("Este guión recorre la historia completa del sistema en 9 pasos lógicos.");
        System.out.println("Presiona [ENTER] para avanzar paso a paso.\n");

        String[][] pasos = {
            {
                "PASO 1: REGISTRAR UNA NUEVA EMPRESA (CU1 & CU3)",
                "La empresa 'NovaRed Soluciones' se registra en la Bolsa de Trabajos UAGRM.",
                "contacto@novared.bo",
                "REGISTRAR_EMPRESA[6543210, Roberto, Paz, contacto@novared.bo, emp123, 75512345, 1029384700, NOVARED S.R.L., NovaRed Soluciones, Telecomunicaciones, Av. Cristo Redentor 450, https://novared.bo];"
            },
            {
                "PASO 2: CONSULTAR Y ACTIVAR PLAN EMPRESARIAL (CU4)",
                "La empresa consulta los planes disponibles y adquiere el Plan de Prueba Gratuita.",
                "contacto@novared.bo",
                "VER_PLANES;"
            },
            {
                "PASO 3: PUBLICAR VACANTE LABORAL (CU4 & CU5)",
                "La empresa publica la vacante 'Ingeniero de Redes y Telecomunicaciones'. Quedará pendiente de moderación.",
                "contacto@novared.bo",
                "PUBLICAR_OFERTA[Ingeniero de Redes y Telecomunicaciones, Redes y Telecomunicaciones, 5500, HIBRIDO, 2, Cisco CCNA y Firewalls];"
            },
            {
                "PASO 4: MODERACIÓN POR EL DIRECTOR DE CARRERA (CU7)",
                "El Director revisa las vacantes pendientes y aprueba la publicación de NovaRed.",
                "director.roberto@gmail.com",
                "LIST[v_ofertas_pendientes_moderacion];"
            },
            {
                "PASO 5: EL DIRECTOR APRUEBA LA OFERTA",
                "El Director ejecuta la aprobación oficial para que sea visible por los estudiantes.",
                "director.roberto@gmail.com",
                "MODERAR[OFERTA, 4, APROBAR, Cumple perfil para titulados de la facultad];"
            },
            {
                "PASO 6: REGISTRO DE UN NUEVO ESTUDIANTE (CU1 & CU2)",
                "Un estudiante de último semestre se registra en el sistema con su perfil y CV.",
                "andres.aguilera@uagrm.edu.bo",
                "REGISTRAR_ESTUDIANTE[9238471, Andres, Aguilera, andres.aguilera@uagrm.edu.bo, est123, 71234567, 221088771, Ingenieria en Redes, 8, Especialista en Routing y Switching, https://cv.andres.bo];"
            },
            {
                "PASO 7: EL ESTUDIANTE CONSULTA VACANTES Y SE POSTULA (CU5)",
                "Andres consulta las ofertas en su área y se postula formalmente a la vacante.",
                "andres.aguilera@uagrm.edu.bo",
                "VER_OFERTAS[Redes];"
            },
            {
                "PASO 8: POSTULACIÓN DEL ESTUDIANTE A LA OFERTA",
                "Andres envía su postulación con su carta de presentación.",
                "andres.aguilera@uagrm.edu.bo",
                "POSTULAR[4, Cuento con certificacion CCNA y disponibilidad inmediata para modalidad hibrida.];"
            },
            {
                "PASO 9: EL ESTUDIANTE REVISA SUS POSTULACIONES",
                "El estudiante verifica el estado de su postulación.",
                "andres.aguilera@uagrm.edu.bo",
                "MIS_POSTULACIONES;"
            },
            {
                "PASO 10: LA EMPRESA EVALÚA AL CANDIDATO (CU6)",
                "NovaRed revisa quiénes postularon a su oferta y evalúa a Andres pasando a etapa de entrevista.",
                "contacto@novared.bo",
                "VER_CANDIDATOS[4];"
            },
            {
                "PASO 11: EVALUAR POSTULACIÓN Y AGENDAR ENTREVISTA (CU6)",
                "La empresa agenda la entrevista oficial por Google Meet.",
                "contacto@novared.bo",
                "EVALUAR_CANDIDATO[3, ENTREVISTA, Perfil preseleccionado con excelentes aptitudes tecnicas];"
            },
            {
                "PASO 12: PROGRAMAR CITA DE ENTREVISTA",
                "Se formaliza el enlace de reunión y horario con el candidato.",
                "contacto@novared.bo",
                "AGENDAR_ENTREVISTA[3, 2026-11-29 10:00:00, REMOTO, https://meet.google.com/novared-tech, Ing. Roberto Paz, 75512345];"
            },
            {
                "PASO 13: REPORTES FINALES INSTITUCIONALES (CU8)",
                "Dirección de carrera consulta las estadísticas consolidadas del sistema.",
                "director.roberto@gmail.com",
                "REPORTE[postulaciones];"
            },
            {
                "PASO 14: REPORTE DE EMPLEABILIDAD POR CARRERA",
                "Métricas de inserción laboral por carreras de la facultad.",
                "director.roberto@gmail.com",
                "REPORTE[empleabilidad];"
            }
        };

        for (int i = 0; i < pasos.length; i++) {
            String titulo = pasos[i][0];
            String explicacion = pasos[i][1];
            String remitente = pasos[i][2];
            String comando = pasos[i][3];

            System.out.println("--------------------------------------------------------------------------------");
            System.out.println(">>> " + titulo);
            System.out.println("    Explicación: " + explicacion);
            System.out.println("    Remitente:   " + remitente);
            System.out.println("    Comando SQL: " + comando);
            System.out.print("\nPresiona [ENTER] para ejecutar este paso (o escribe 's' para saltar)... ");
            
            String input = scanner.hasNextLine() ? scanner.nextLine() : "";
            if (input.equalsIgnoreCase("s")) {
                System.out.println("(Paso saltado)\n");
                continue;
            }

            long t0 = System.currentTimeMillis();
            try {
                CommandResponse resp = RespuestaWorker.procesarComandoDirecto(remitente, comando);
                long tiempo = System.currentTimeMillis() - t0;
                System.out.println("\n[RESPUESTA DEL SERVIDOR - " + (resp.isSuccess() ? "EXITO" : "ERROR") + " (" + tiempo + "ms)]:");
                System.out.println(resp.getMessage().trim());
            } catch (Exception ex) {
                System.err.println("\n[ERROR]: " + ex.getMessage());
            }
            System.out.println("\n");
        }

        System.out.println("================================================================================");
        System.out.println("   ¡DEMOSTRACIÓN FINALIZADA CON ÉXITO!                                          ");
        System.out.println("   Puedes restablecer la base de datos con: restablecer_bd.bat                  ");
        System.out.println("================================================================================");
    }
}
