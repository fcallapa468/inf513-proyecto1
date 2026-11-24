# Sistema Vía Correo Electrónico - Bolsa de Trabajos UAGRM (Grupo 04)

**Materia:** INF-513 Tecnología Web  
**Docente:** Ing. Evans Balcázar  
**Base de Datos:** PostgreSQL en `mail.tecnoweb.org.bo:5432/db_grupo04sc`  
**Servidor de Correo:** POP3 (110) y SMTP (25) en `mail.tecnoweb.org.bo`  

---

## 🚀 Guía Rápida para los Integrantes del Grupo

### 1. Requisitos Previos
* **Java JDK 17 o superior** instalado.
* Conexión a Internet (para conectarse a la base de datos de TecnoWeb).

---

### 2. ¿Cómo probar los comandos en tu PC? (Modo Simulador)
Para probar todo el flujo del sistema sin depender de las restricciones de IP de Sendmail ni enviar correos reales:

1. Haz doble clic en **`simular.bat`** (o ejecútalo desde la terminal).
2. Escribe cualquier comando (ej: `HELP;`, `VER_PLANES;`, `VER_OFERTAS;`, etc.).
3. Verás la respuesta en texto en la consola y automáticamente se abrirá **`ultima_respuesta.html`** en tu navegador para ver el diseño exacto tipo tabla Excel.
4. Para cambiar de usuario/remitente en el simulador:
   ```text
   remitente: rrhh@tigo.com.bo
   ```
5. Para salir escribe `salir`.

---

### 3. ¿Cómo probar si tu IP puede enviar correos reales?
1. Haz doble clic en **`probar_ip.bat`**.
2. En 2 segundos te dirá si tu proveedor de Internet (Tigo, Entel, etc.) tiene autorización en Sendmail:
   * **VERDE:** Tu IP está autorizada, puedes recibir y responder correos reales.
   * **ROJO (Relaying denied):** El docente no tiene tu IP en `/etc/mail/access`. Puedes pasarle tu IP para que la habilite o seguir usando `simular.bat`.

---

### 4. ¿Cómo ejecutar el Servidor de Correo Automático?
*(Úsalo si tu IP está en verde o cuando estés en la facultad)*
1. Haz doble clic en **`ejecutar.bat`**.
2. El sistema se conectará a `grupo04sc@mail.tecnoweb.org.bo` cada 5 segundos, procesará los correos entrantes y enviará las respuestas oficiales en HTML y texto.

---

## 📋 Comandos Disponibles por Caso de Uso

> **Nota de compatibilidad:** Todos los comandos soportan indistintamente separación por comas (`,`) o dos puntos (`:`), tanto en el **Asunto** como en el **Cuerpo** del correo.

### CU1. Usuarios y Roles
* `REGISTRAR_DIRECTOR[ci, nombre, apellido, email, password, telefono];`
* `REGISTRAR_SECRETARIA[ci, nombre, apellido, email, password, telefono];`
* `REGISTRAR_ESTUDIANTE[ci, nombre, apellido, email, password, telefono, ru, carrera, semestre, perfil, cv_url];`
* `REGISTRAR_EMPRESA[ci, nom_rep, ape_rep, email, password, tel, nit, razon, comercial, rubro, dir, web];`
* `LIST[usuarios];` | `LIST[roles];`
* `SHOW[usuarios, id];` *(o `SHOW[usuarios: id];`)*

### CU2. Perfil del Estudiante
* `SHOW[estudiantes, id];`
* `UPDATE[estudiantes, id, campo=nuevo_valor];` *(o `UPDATE[estudiantes, id, campo, nuevo_valor];`)*
* `LIST[habilidades];` | `LIST[estudiante_habilidades];`

### CU3. Perfil de la Empresa
* `SHOW[empresas, id];`
* `UPDATE[empresas, id, campo=nuevo_valor];`
* `LIST[areas_laborales];`

### CU4. Suscripciones y Publicación de Ofertas
* `VER_PLANES;`
* `COMPRAR_PLAN[empresa_id, plan_id];`
* `VERIFICAR_PAGO[pago_id];`
* `PUBLICAR_OFERTA[titulo, area, salario, modalidad, vacantes, skills];`
* `PUBLICAR_OFERTA[empresa_id, titulo, area, salario, modalidad, vacantes, skills];`
* `LIST[planes];` | `LIST[suscripciones];` | `LIST[facturas];`

### CU5. Ofertas y Postulaciones
* `VER_OFERTAS;`
* `VER_OFERTAS[area_laboral];` *(Ejemplo: `VER_OFERTAS[Software];`)*
* `POSTULAR[oferta_id, mensaje];`
* `POSTULAR[oferta_id, estudiante_id, mensaje];`
* `MIS_POSTULACIONES;`
* `MIS_POSTULACIONES[estudiante_id];`

### CU6. Candidatos, Selección y Citas
* `VER_CANDIDATOS[oferta_id];`
* `EVALUAR_CANDIDATO[postulacion_id, etapa, observaciones];`
  *(Etapas: REVISION_CV, PRESELECCIONADO, ENTREVISTA, CONTRATADO, NO_SELECCIONADO)*
* `AGENDAR_ENTREVISTA[postulacion_id, fecha_hora, modalidad, enlace_meet, entrevistador, telefono];`
* `LIST[entrevistas];`

### CU7. Moderación (Director)
* `MODERAR[oferta_id, APROBAR|RECHAZAR];`
* `MODERAR[EMPRESA|OFERTA, id, APROBAR|RECHAZAR, motivo];`
* `LIST[v_ofertas_pendientes_moderacion];`

### CU8. Reportes Analíticos
* `REPORTE[empleabilidad];`
* `REPORTE[postulaciones];`
* `REPORTE[recaudacion];`
* `REPORTE[empresas];`
* `REPORTE[facturas];`

---

## 📂 Bitácora del Servidor de Correo (Logs de Auditoría)
El sistema registra automáticamente todas las peticiones procesadas en archivos rotativos en la carpeta **`logs/`**:
* **`logs/access.log`**: Registra fecha, hora, remitente, asunto, comando ejecutado, estado (ÉXITO/ERROR) y tiempo de procesamiento en ms.
* **`logs/error.log`**: Registra advertencias y excepciones del servidor.
