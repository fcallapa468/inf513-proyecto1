-- =============================================================================
-- BASE DE DATOS: BOLSA DE TRABAJOS UAGRM (GRUPO 04 - TECNOWEB INF513)
-- Motor: PostgreSQL 18.x
-- Convencion: id_<nombredelatabla> para llaves primarias y foraneas
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS public;
DROP SCHEMA IF EXISTS agenda CASCADE;
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO grupo04sc;
GRANT ALL ON SCHEMA public TO public;

-- Vistas previas
DROP VIEW IF EXISTS v_reporte_empleabilidad CASCADE;
DROP VIEW IF EXISTS v_resumen_postulaciones CASCADE;
DROP VIEW IF EXISTS v_ofertas_disponibles CASCADE;
DROP VIEW IF EXISTS v_ofertas_pendientes_moderacion CASCADE;
DROP VIEW IF EXISTS v_candidatos_oferta CASCADE;
DROP VIEW IF EXISTS v_mis_postulaciones CASCADE;
DROP VIEW IF EXISTS v_entrevistas_programadas CASCADE;
DROP VIEW IF EXISTS v_recaudacion_suscripciones CASCADE;
DROP VIEW IF EXISTS v_ranking_empresas CASCADE;
DROP VIEW IF EXISTS v_facturas_emitidas CASCADE;
DROP VIEW IF EXISTS v_registro_estudiante CASCADE;
DROP VIEW IF EXISTS v_registro_empresa CASCADE;

-- Tablas previas
DROP TABLE IF EXISTS moderaciones CASCADE;
DROP TABLE IF EXISTS entrevistas CASCADE;
DROP TABLE IF EXISTS seguimientos CASCADE;
DROP TABLE IF EXISTS postulaciones CASCADE;
DROP TABLE IF EXISTS oferta_habilidades CASCADE;
DROP TABLE IF EXISTS ofertas CASCADE;
DROP TABLE IF EXISTS facturas CASCADE;
DROP TABLE IF EXISTS pagos CASCADE;
DROP TABLE IF EXISTS suscripciones CASCADE;
DROP TABLE IF EXISTS planes CASCADE;
DROP TABLE IF EXISTS areas_laborales CASCADE;
DROP TABLE IF EXISTS estudiante_habilidades CASCADE;
DROP TABLE IF EXISTS habilidades CASCADE;
DROP TABLE IF EXISTS empresas CASCADE;
DROP TABLE IF EXISTS estudiantes CASCADE;
DROP TABLE IF EXISTS usuarios CASCADE;
DROP TABLE IF EXISTS roles CASCADE;

-- =============================================================================
-- CU1. GESTION DE USUARIOS Y ROLES
-- =============================================================================

CREATE TABLE roles (
    id_roles SERIAL PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion VARCHAR(255)
);

CREATE TABLE usuarios (
    id_usuarios SERIAL PRIMARY KEY,
    id_roles INT NOT NULL REFERENCES roles(id_roles) ON DELETE RESTRICT,
    ci VARCHAR(30) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    telefono VARCHAR(30),
    foto_url TEXT DEFAULT 'https://ui-avatars.com/api/?name=User&background=random',
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'INACTIVO', 'BLOQUEADO')),
    fecha_registro DATE DEFAULT CURRENT_DATE
);

-- =============================================================================
-- CU2. GESTION DE PERFIL ACADEMICO / LABORAL DEL ESTUDIANTE
-- =============================================================================

CREATE TABLE estudiantes (
    id_estudiantes SERIAL PRIMARY KEY,
    id_usuarios INT NOT NULL UNIQUE REFERENCES usuarios(id_usuarios) ON DELETE CASCADE,
    ru VARCHAR(30) NOT NULL UNIQUE,
    carrera VARCHAR(120) NOT NULL,
    semestre INT DEFAULT 1 CHECK (semestre BETWEEN 1 AND 12),
    cv_url TEXT,
    perfil_profesional TEXT,
    formacion_academica TEXT,
    experiencia_laboral TEXT
);

CREATE TABLE habilidades (
    id_habilidades SERIAL PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL UNIQUE,
    categoria VARCHAR(40) NOT NULL DEFAULT 'TECNOLOGIA' 
        CHECK (categoria IN ('LENGUAJE', 'FRAMEWORK', 'DATABASE', 'DEVOPS', 'HERRAMIENTA', 'IDIOMA', 'SOFT_SKILL'))
);

CREATE TABLE estudiante_habilidades (
    id_estudiante_habilidades SERIAL PRIMARY KEY,
    id_estudiantes INT NOT NULL REFERENCES estudiantes(id_estudiantes) ON DELETE CASCADE,
    id_habilidades INT NOT NULL REFERENCES habilidades(id_habilidades) ON DELETE RESTRICT,
    nivel VARCHAR(20) NOT NULL DEFAULT 'INTERMEDIO' CHECK (nivel IN ('BASICO', 'INTERMEDIO', 'AVANZADO')),
    anios_experiencia INT DEFAULT 1 CHECK (anios_experiencia >= 0),
    UNIQUE (id_estudiantes, id_habilidades)
);

-- =============================================================================
-- CU3. GESTION DE REGISTRO E INFORMACION INSTITUCIONAL DE LA EMPRESA
-- =============================================================================

CREATE TABLE empresas (
    id_empresas SERIAL PRIMARY KEY,
    id_usuarios INT NOT NULL UNIQUE REFERENCES usuarios(id_usuarios) ON DELETE CASCADE,
    nit VARCHAR(50) NOT NULL UNIQUE,
    razon_social VARCHAR(150) NOT NULL,
    nombre_comercial VARCHAR(150) NOT NULL,
    sector_industrial VARCHAR(100) DEFAULT 'Tecnologia',
    direccion VARCHAR(255) NOT NULL,
    sitio_web VARCHAR(255),
    representante_legal VARCHAR(150) NOT NULL,
    telefono_contacto VARCHAR(30),
    descripcion TEXT,
    logo_url TEXT DEFAULT 'https://ui-avatars.com/api/?name=Empresa&background=0D8ABC&color=fff',
    verificada BOOLEAN DEFAULT FALSE
);

CREATE TABLE areas_laborales (
    id_areas_laborales SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    descripcion VARCHAR(255)
);

-- =============================================================================
-- CU4. GESTION DE SUSCRIPCIONES, PAGOS, FACTURACION Y PUBLICACION DE OFERTAS
-- =============================================================================

CREATE TABLE planes (
    id_planes SERIAL PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL UNIQUE,
    precio NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    duracion_dias INT NOT NULL DEFAULT 30,
    limite_ofertas INT NOT NULL DEFAULT 1,
    requiere_moderacion BOOLEAN DEFAULT FALSE,
    es_gratuito BOOLEAN DEFAULT FALSE,
    descripcion TEXT,
    estado VARCHAR(20) DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'INACTIVO'))
);

CREATE TABLE suscripciones (
    id_suscripciones SERIAL PRIMARY KEY,
    id_empresas INT NOT NULL REFERENCES empresas(id_empresas) ON DELETE CASCADE,
    id_planes INT NOT NULL REFERENCES planes(id_planes) ON DELETE RESTRICT,
    fecha_inicio DATE DEFAULT CURRENT_DATE,
    fecha_fin DATE NOT NULL,
    ofertas_publicadas INT NOT NULL DEFAULT 0,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA' CHECK (estado IN ('ACTIVA', 'VENCIDA', 'AGOTADA', 'PENDIENTE_PAGO'))
);

CREATE TABLE pagos (
    id_pagos SERIAL PRIMARY KEY,
    id_empresas INT NOT NULL REFERENCES empresas(id_empresas) ON DELETE CASCADE,
    id_suscripciones INT REFERENCES suscripciones(id_suscripciones) ON DELETE SET NULL,
    monto NUMERIC(10, 2) NOT NULL CHECK (monto >= 0),
    metodo_pago VARCHAR(30) NOT NULL DEFAULT 'QR' CHECK (metodo_pago IN ('QR', 'PAGO_FACIL_QR', 'TRANSFERENCIA', 'EFECTIVO')),
    referencia_pago VARCHAR(100),
    referencia_qr TEXT,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
    fecha_pago DATE DEFAULT CURRENT_DATE
);

CREATE TABLE facturas (
    id_facturas SERIAL PRIMARY KEY,
    id_pagos INT NOT NULL UNIQUE REFERENCES pagos(id_pagos) ON DELETE CASCADE,
    id_empresas INT NOT NULL REFERENCES empresas(id_empresas) ON DELETE CASCADE,
    numero_factura VARCHAR(50) NOT NULL UNIQUE,
    nit_emisor VARCHAR(30) NOT NULL DEFAULT '1029384756',
    razon_social_emisor VARCHAR(150) NOT NULL DEFAULT 'Bolsa de Trabajo UAGRM - FICCT',
    nit_cliente VARCHAR(50) NOT NULL,
    razon_social_cliente VARCHAR(150) NOT NULL,
    concepto TEXT NOT NULL,
    monto_total NUMERIC(10, 2) NOT NULL,
    codigo_autorizacion VARCHAR(100) NOT NULL,
    fecha_emision TIMESTAMP DEFAULT NOW()
);

CREATE TABLE ofertas (
    id_ofertas SERIAL PRIMARY KEY,
    id_empresas INT NOT NULL REFERENCES empresas(id_empresas) ON DELETE CASCADE,
    id_areas_laborales INT NOT NULL REFERENCES areas_laborales(id_areas_laborales) ON DELETE RESTRICT,
    titulo VARCHAR(150) NOT NULL,
    descripcion TEXT NOT NULL,
    modalidad VARCHAR(30) NOT NULL DEFAULT 'PRESENCIAL' CHECK (modalidad IN ('PRESENCIAL', 'REMOTO', 'HIBRIDO')),
    tipo_contrato VARCHAR(50) NOT NULL DEFAULT 'TIEMPO_COMPLETO' CHECK (tipo_contrato IN ('TIEMPO_COMPLETO', 'MEDIO_TIEMPO', 'PASANTIA', 'POR_PROYECTO')),
    ubicacion VARCHAR(150) DEFAULT 'Santa Cruz de la Sierra',
    salario NUMERIC(10, 2) DEFAULT 0.00,
    vacantes INT NOT NULL DEFAULT 1,
    fecha_publicacion DATE DEFAULT CURRENT_DATE,
    fecha_cierre DATE DEFAULT (CURRENT_DATE + INTERVAL '30 days'),
    estado VARCHAR(25) NOT NULL DEFAULT 'PUBLICADA' 
        CHECK (estado IN ('BORRADOR', 'PENDIENTE_MODERACION', 'PUBLICADA', 'CERRADA'))
);

CREATE TABLE oferta_habilidades (
    id_oferta_habilidades SERIAL PRIMARY KEY,
    id_ofertas INT NOT NULL REFERENCES ofertas(id_ofertas) ON DELETE CASCADE,
    id_habilidades INT NOT NULL REFERENCES habilidades(id_habilidades) ON DELETE RESTRICT,
    es_obligatoria BOOLEAN DEFAULT TRUE,
    nivel_minimo VARCHAR(20) NOT NULL DEFAULT 'INTERMEDIO' CHECK (nivel_minimo IN ('BASICO', 'INTERMEDIO', 'AVANZADO')),
    UNIQUE (id_ofertas, id_habilidades)
);

-- =============================================================================
-- CU5. GESTION DE BUSQUEDAS, CONSULTAS Y POSTULACIONES A OFERTAS LABORALES
-- =============================================================================

CREATE TABLE postulaciones (
    id_postulaciones SERIAL PRIMARY KEY,
    id_estudiantes INT NOT NULL REFERENCES estudiantes(id_estudiantes) ON DELETE CASCADE,
    id_ofertas INT NOT NULL REFERENCES ofertas(id_ofertas) ON DELETE CASCADE,
    fecha_postulacion DATE DEFAULT CURRENT_DATE,
    mensaje_presentacion TEXT,
    cv_adjunto_url TEXT,
    estado VARCHAR(30) NOT NULL DEFAULT 'POSTULADO' 
        CHECK (estado IN ('POSTULADO', 'EN_REVISION', 'PRESELECCIONADO', 'ENTREVISTA', 'CONTRATADO', 'RECHAZADO')),
    motivo_rechazo TEXT,
    UNIQUE (id_estudiantes, id_ofertas)
);

-- =============================================================================
-- CU6. GESTION DE CANDIDATOS, SELECCION Y CITAS DE ENTREVISTA
-- =============================================================================

CREATE TABLE seguimientos (
    id_seguimientos SERIAL PRIMARY KEY,
    id_postulaciones INT NOT NULL REFERENCES postulaciones(id_postulaciones) ON DELETE CASCADE,
    etapa VARCHAR(50) NOT NULL 
        CHECK (etapa IN ('REVISION_CV', 'PRUEBA_TECNICA', 'ENTREVISTA_RRHH', 'ENTREVISTA_TECNICA', 'CONTRATADO', 'DESCARTADO')),
    evaluador VARCHAR(100),
    calificacion NUMERIC(5, 2) CHECK (calificacion BETWEEN 0 AND 100),
    observaciones TEXT,
    fecha_registro DATE DEFAULT CURRENT_DATE
);

CREATE TABLE entrevistas (
    id_entrevistas SERIAL PRIMARY KEY,
    id_postulaciones INT NOT NULL REFERENCES postulaciones(id_postulaciones) ON DELETE CASCADE,
    fecha_hora TIMESTAMP NOT NULL,
    modalidad VARCHAR(20) NOT NULL DEFAULT 'VIRTUAL' CHECK (modalidad IN ('VIRTUAL', 'PRESENCIAL')),
    lugar_o_enlace TEXT NOT NULL,
    entrevistador VARCHAR(120) NOT NULL,
    telefono_contacto VARCHAR(30),
    mensaje_indicaciones TEXT,
    estado VARCHAR(20) DEFAULT 'PROGRAMADA' CHECK (estado IN ('PROGRAMADA', 'REALIZADA', 'CANCELADA')),
    fecha_registro TIMESTAMP DEFAULT NOW()
);

-- =============================================================================
-- CU7. GESTION DE PLATAFORMA, MODERACION Y REPORTES DE EMPLEABILIDAD
-- =============================================================================

CREATE TABLE moderaciones (
    id_moderaciones SERIAL PRIMARY KEY,
    id_usuarios INT NOT NULL REFERENCES usuarios(id_usuarios) ON DELETE RESTRICT,
    tipo_entidad VARCHAR(50) NOT NULL CHECK (tipo_entidad IN ('EMPRESA', 'OFERTA', 'USUARIO')),
    entidad_id INT NOT NULL,
    accion VARCHAR(50) NOT NULL CHECK (accion IN ('APROBAR', 'RECHAZAR', 'SUSPENDER')),
    motivo TEXT,
    fecha DATE DEFAULT CURRENT_DATE
);

-- =============================================================================
-- TRIGGERS DE AUTOMATIZACION
-- =============================================================================

-- 1. Asignar automaticamente Plan de Prueba Gratuita a toda nueva empresa
CREATE OR REPLACE FUNCTION fn_asignar_plan_bienvenida()
RETURNS TRIGGER AS $$
DECLARE
    v_plan_id INT;
    v_dias INT;
BEGIN
    SELECT id_planes, duracion_dias INTO v_plan_id, v_dias 
    FROM planes 
    WHERE es_gratuito = TRUE AND estado = 'ACTIVO' 
    LIMIT 1;

    IF v_plan_id IS NOT NULL THEN
        INSERT INTO suscripciones (id_empresas, id_planes, fecha_inicio, fecha_fin, estado)
        VALUES (NEW.id_empresas, v_plan_id, CURRENT_DATE, CURRENT_DATE + (v_dias || ' days')::INTERVAL, 'ACTIVA');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_empresa_plan_bienvenida ON empresas;
CREATE TRIGGER tr_empresa_plan_bienvenida
AFTER INSERT ON empresas
FOR EACH ROW
EXECUTE FUNCTION fn_asignar_plan_bienvenida();

-- 2. Procesar nueva oferta: Moderacion previa y consumo de cupo
CREATE OR REPLACE FUNCTION fn_procesar_nueva_oferta()
RETURNS TRIGGER AS $$
DECLARE
    v_suscripcion RECORD;
BEGIN
    SELECT s.id_suscripciones, s.ofertas_publicadas, p.limite_ofertas, p.requiere_moderacion
    INTO v_suscripcion
    FROM suscripciones s
    JOIN planes p ON s.id_planes = p.id_planes
    WHERE s.id_empresas = NEW.id_empresas AND s.estado = 'ACTIVA'
    LIMIT 1;

    IF v_suscripcion IS NULL THEN
        RAISE EXCEPTION 'La empresa no cuenta con una suscripción activa para publicar ofertas.';
    END IF;

    IF v_suscripcion.ofertas_publicadas >= v_suscripcion.limite_ofertas THEN
        RAISE EXCEPTION 'Ha alcanzado el límite de ofertas permitidas para su suscripción actual.';
    END IF;

    IF v_suscripcion.requiere_moderacion = TRUE THEN
        NEW.estado := 'PENDIENTE_MODERACION';
    ELSE
        NEW.estado := 'PUBLICADA';
    END IF;

    UPDATE suscripciones 
    SET ofertas_publicadas = ofertas_publicadas + 1
    WHERE id_suscripciones = v_suscripcion.id_suscripciones;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_procesar_nueva_oferta ON ofertas;
CREATE TRIGGER tr_procesar_nueva_oferta
BEFORE INSERT ON ofertas
FOR EACH ROW
EXECUTE FUNCTION fn_procesar_nueva_oferta();

-- 3. Sincronizar estado de postulacion al registrar seguimiento
CREATE OR REPLACE FUNCTION fn_sincronizar_estado_postulacion()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.etapa = 'CONTRATADO' THEN
        UPDATE postulaciones SET estado = 'CONTRATADO' WHERE id_postulaciones = NEW.id_postulaciones;
    ELSIF NEW.etapa = 'DESCARTADO' THEN
        UPDATE postulaciones 
        SET estado = 'RECHAZADO', 
            motivo_rechazo = COALESCE(NEW.observaciones, 'No cumple con el perfil requerido para la vacante.')
        WHERE id_postulaciones = NEW.id_postulaciones;
    ELSIF NEW.etapa = 'REVISION_CV' THEN
        UPDATE postulaciones SET estado = 'EN_REVISION' WHERE id_postulaciones = NEW.id_postulaciones;
    ELSIF NEW.etapa LIKE 'ENTREVISTA%' THEN
        UPDATE postulaciones SET estado = 'ENTREVISTA' WHERE id_postulaciones = NEW.id_postulaciones;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_sincronizar_estado_postulacion ON seguimientos;
CREATE TRIGGER tr_sincronizar_estado_postulacion
AFTER INSERT ON seguimientos
FOR EACH ROW
EXECUTE FUNCTION fn_sincronizar_estado_postulacion();

-- 4. Control de Vacantes Llenas
CREATE OR REPLACE FUNCTION fn_control_vacantes_oferta()
RETURNS TRIGGER AS $$
DECLARE
    v_oferta_id INT;
    v_vacantes INT;
    v_contratados INT;
BEGIN
    IF NEW.estado = 'CONTRATADO' AND (OLD.estado IS NULL OR OLD.estado <> 'CONTRATADO') THEN
        v_oferta_id := NEW.id_ofertas;
        
        SELECT vacantes INTO v_vacantes FROM ofertas WHERE id_ofertas = v_oferta_id;
        SELECT COUNT(*) INTO v_contratados FROM postulaciones WHERE id_ofertas = v_oferta_id AND estado = 'CONTRATADO';

        IF v_contratados >= v_vacantes THEN
            UPDATE ofertas SET estado = 'CERRADA' WHERE id_ofertas = v_oferta_id;

            UPDATE postulaciones 
            SET estado = 'RECHAZADO',
                motivo_rechazo = 'El proceso de selección para esta convocatoria ha finalizado. Las vacantes han sido cubiertas.'
            WHERE id_ofertas = v_oferta_id AND estado NOT IN ('CONTRATADO', 'RECHAZADO');
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_control_vacantes_oferta ON postulaciones;
CREATE TRIGGER tr_control_vacantes_oferta
AFTER UPDATE ON postulaciones
FOR EACH ROW
EXECUTE FUNCTION fn_control_vacantes_oferta();

-- 5. Generacion Automatica de Factura Digital
CREATE OR REPLACE FUNCTION fn_generar_factura_pago()
RETURNS TRIGGER AS $$
DECLARE
    v_empresa RECORD;
    v_plan_nombre VARCHAR(100);
    v_num_factura VARCHAR(50);
    v_auth_code VARCHAR(100);
BEGIN
    IF NEW.estado = 'APROBADO' AND (OLD.estado IS NULL OR OLD.estado <> 'APROBADO') THEN
        SELECT e.nit, e.razon_social INTO v_empresa FROM empresas e WHERE e.id_empresas = NEW.id_empresas;
        
        SELECT p.nombre INTO v_plan_nombre 
        FROM suscripciones s 
        JOIN planes p ON s.id_planes = p.id_planes 
        WHERE s.id_suscripciones = NEW.id_suscripciones;

        IF v_plan_nombre IS NULL THEN
            v_plan_nombre := 'Plan de Suscripción Empresarial';
        END IF;

        v_num_factura := CONCAT('FAC-2026-', LPAD(NEW.id_pagos::TEXT, 5, '0'));
        v_auth_code := MD5(CONCAT(NEW.id_pagos, v_num_factura, NEW.monto, NOW()::TEXT));

        INSERT INTO facturas (
            id_pagos, id_empresas, numero_factura, 
            nit_cliente, razon_social_cliente, 
            concepto, monto_total, codigo_autorizacion
        ) VALUES (
            NEW.id_pagos, NEW.id_empresas, v_num_factura,
            v_empresa.nit, v_empresa.razon_social,
            CONCAT('Suscripción ', v_plan_nombre, ' - Bolsa de Trabajo UAGRM'),
            NEW.monto, v_auth_code
        ) ON CONFLICT (id_pagos) DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_generar_factura_pago ON pagos;
CREATE TRIGGER tr_generar_factura_pago
AFTER UPDATE OR INSERT ON pagos
FOR EACH ROW
EXECUTE FUNCTION fn_generar_factura_pago();

-- =============================================================================
-- CU8. REPORTES Y VISTAS SQL
-- =============================================================================

-- Reporte 1: Catalogo de Ofertas Disponibles
CREATE OR REPLACE VIEW v_ofertas_disponibles AS
SELECT 
    o.id_ofertas AS oferta_id,
    o.id_ofertas,
    o.titulo,
    e.nombre_comercial AS empresa,
    CASE 
        WHEN e.verificada THEN 'Verificada' 
        ELSE 'No verificada' 
    END AS verificacion,
    a.nombre AS area,
    o.modalidad,
    o.tipo_contrato,
    o.vacantes,
    CASE WHEN o.salario > 0 THEN CONCAT('Bs. ', o.salario) ELSE 'A convenir' END AS salario,
    COALESCE(
        (SELECT STRING_AGG(h.nombre, ', ') 
         FROM oferta_habilidades oh 
         JOIN habilidades h ON oh.id_habilidades = h.id_habilidades 
         WHERE oh.id_ofertas = o.id_ofertas), 'Conocimientos generales'
    ) AS habilidades_requeridas,
    o.fecha_cierre,
    COUNT(p.id_postulaciones) AS total_postulantes
FROM ofertas o
JOIN empresas e ON o.id_empresas = e.id_empresas
JOIN areas_laborales a ON o.id_areas_laborales = a.id_areas_laborales
LEFT JOIN postulaciones p ON o.id_ofertas = p.id_ofertas
WHERE o.estado = 'PUBLICADA' AND (o.fecha_cierre IS NULL OR o.fecha_cierre >= CURRENT_DATE)
GROUP BY o.id_ofertas, o.titulo, e.nombre_comercial, e.verificada, a.nombre, o.modalidad, o.tipo_contrato, o.vacantes, o.salario, o.fecha_cierre;

-- Reporte 2: Bandeja de Ofertas Pendientes de Moderacion
CREATE OR REPLACE VIEW v_ofertas_pendientes_moderacion AS
SELECT 
    o.id_ofertas AS oferta_id,
    o.id_ofertas,
    o.titulo,
    e.nombre_comercial AS empresa,
    e.nit,
    CASE WHEN e.verificada THEN 'Empresa Verificada' ELSE 'Empresa No Verificada' END AS estado_empresa,
    a.nombre AS area,
    o.vacantes,
    o.fecha_publicacion,
    o.descripcion
FROM ofertas o
JOIN empresas e ON o.id_empresas = e.id_empresas
JOIN areas_laborales a ON o.id_areas_laborales = a.id_areas_laborales
WHERE o.estado = 'PENDIENTE_MODERACION';

-- Reporte 3: Seguimiento de Candidatos Postulados
CREATE OR REPLACE VIEW v_candidatos_oferta AS
SELECT 
    p.id_postulaciones AS postulacion_id,
    p.id_postulaciones,
    o.id_ofertas AS oferta_id,
    o.id_ofertas,
    o.titulo AS oferta,
    CONCAT(u.nombre, ' ', u.apellido) AS postulante,
    est.ru,
    est.carrera,
    est.semestre,
    u.telefono,
    u.email,
    p.estado,
    p.fecha_postulacion,
    COALESCE(ROUND(AVG(s.calificacion), 1), 0.0) AS calificacion_promedio,
    COALESCE(p.cv_adjunto_url, est.cv_url) AS cv_enlace
FROM postulaciones p
JOIN ofertas o ON p.id_ofertas = o.id_ofertas
JOIN estudiantes est ON p.id_estudiantes = est.id_estudiantes
JOIN usuarios u ON est.id_usuarios = u.id_usuarios
LEFT JOIN seguimientos s ON p.id_postulaciones = s.id_postulaciones
GROUP BY p.id_postulaciones, o.id_ofertas, o.titulo, u.nombre, u.apellido, est.ru, est.carrera, est.semestre, u.telefono, u.email, p.estado, p.fecha_postulacion, p.cv_adjunto_url, est.cv_url;

-- Reporte 4: Consulta de Postulaciones Propias del Estudiante
CREATE OR REPLACE VIEW v_mis_postulaciones AS
SELECT 
    p.id_postulaciones AS postulacion_id,
    p.id_postulaciones,
    p.id_estudiantes AS estudiante_id,
    p.id_estudiantes,
    u.email AS email_estudiante,
    CONCAT(u.nombre, ' ', u.apellido) AS postulante,
    est.id_usuarios,
    o.titulo AS oferta,
    e.nombre_comercial AS empresa,
    CASE WHEN e.verificada THEN 'Verificada' ELSE 'No verificada' END AS verificacion,
    p.fecha_postulacion,
    p.estado,
    p.motivo_rechazo,
    ent.fecha_hora AS entrevista_programada,
    ent.lugar_o_enlace AS enlace_entrevista
FROM postulaciones p
JOIN ofertas o ON p.id_ofertas = o.id_ofertas
JOIN empresas e ON o.id_empresas = e.id_empresas
JOIN estudiantes est ON p.id_estudiantes = est.id_estudiantes
JOIN usuarios u ON est.id_usuarios = u.id_usuarios
LEFT JOIN entrevistas ent ON p.id_postulaciones = ent.id_postulaciones AND ent.estado = 'PROGRAMADA';

-- Reporte 5: Citas de Entrevistas Programadas
CREATE OR REPLACE VIEW v_entrevistas_programadas AS
SELECT 
    ent.id_entrevistas AS entrevista_id,
    ent.id_entrevistas,
    ent.id_postulaciones AS postulacion_id,
    ent.id_postulaciones,
    o.titulo AS oferta,
    e.nombre_comercial AS empresa,
    CONCAT(u.nombre, ' ', u.apellido) AS postulante,
    COALESCE(ent.telefono_contacto, u.telefono) AS telefono_contacto,
    ent.modalidad,
    ent.fecha_hora,
    ent.lugar_o_enlace,
    ent.entrevistador,
    ent.estado
FROM entrevistas ent
JOIN postulaciones p ON ent.id_postulaciones = p.id_postulaciones
JOIN ofertas o ON p.id_ofertas = o.id_ofertas
JOIN empresas e ON o.id_empresas = e.id_empresas
JOIN estudiantes est ON p.id_estudiantes = est.id_estudiantes
JOIN usuarios u ON est.id_usuarios = u.id_usuarios;

-- Reporte 6: Reporte de Empleabilidad
CREATE OR REPLACE VIEW v_reporte_empleabilidad AS
SELECT 
    est.carrera,
    COUNT(DISTINCT est.id_estudiantes) AS total_estudiantes_activos,
    COUNT(DISTINCT p.id_postulaciones) AS total_postulaciones,
    COUNT(DISTINCT CASE WHEN p.estado = 'CONTRATADO' THEN p.id_estudiantes END) AS estudiantes_contratados,
    ROUND(
        (COUNT(DISTINCT CASE WHEN p.estado = 'CONTRATADO' THEN p.id_estudiantes END)::NUMERIC / 
        NULLIF(COUNT(DISTINCT est.id_estudiantes), 0)) * 100, 2
    ) AS porcentaje_empleabilidad
FROM estudiantes est
LEFT JOIN postulaciones p ON est.id_estudiantes = p.id_estudiantes
GROUP BY est.carrera;

-- Reporte 7: Recaudacion por Suscripciones y Facturas
CREATE OR REPLACE VIEW v_recaudacion_suscripciones AS
SELECT 
    p.nombre AS plan,
    COUNT(pag.id_pagos) AS total_pagos,
    COALESCE(SUM(pag.monto), 0.00) AS total_recaudado,
    COUNT(f.id_facturas) AS facturas_emitidas
FROM planes p
LEFT JOIN suscripciones s ON p.id_planes = s.id_planes
LEFT JOIN pagos pag ON s.id_suscripciones = pag.id_suscripciones AND pag.estado = 'APROBADO'
LEFT JOIN facturas f ON pag.id_pagos = f.id_pagos
GROUP BY p.id_planes, p.nombre;

-- Reporte 8: Ranking de Empresas
CREATE OR REPLACE VIEW v_ranking_empresas AS
SELECT 
    e.id_empresas AS empresa_id,
    e.id_empresas,
    e.nombre_comercial AS empresa,
    CASE WHEN e.verificada THEN 'Verificada' ELSE 'No verificada' END AS verificacion,
    e.sector_industrial,
    COUNT(DISTINCT o.id_ofertas) AS ofertas_publicadas,
    COUNT(DISTINCT p.id_postulaciones) AS total_postulantes_recibidos,
    COUNT(DISTINCT CASE WHEN p.estado = 'CONTRATADO' THEN p.id_postulaciones END) AS contrataciones_realizadas
FROM empresas e
LEFT JOIN ofertas o ON e.id_empresas = o.id_empresas
LEFT JOIN postulaciones p ON o.id_ofertas = p.id_ofertas
GROUP BY e.id_empresas, e.nombre_comercial, e.verificada, e.sector_industrial
ORDER BY contrataciones_realizadas DESC, ofertas_publicadas DESC;

-- Reporte 9: Listado de Facturas Emitidas
CREATE OR REPLACE VIEW v_facturas_emitidas AS
SELECT 
    f.id_facturas AS id,
    f.id_facturas,
    f.numero_factura,
    f.fecha_emision,
    f.razon_social_cliente AS empresa,
    f.nit_cliente AS nit,
    f.concepto,
    f.monto_total,
    f.codigo_autorizacion
FROM facturas f
ORDER BY f.fecha_emision DESC;

-- =============================================================================
-- VISTAS TRANSACCIONALES (INSTEAD OF INSERT)
-- =============================================================================

CREATE OR REPLACE VIEW v_registro_estudiante AS
SELECT 
    u.ci, u.nombre, u.apellido, u.email, u.password, u.telefono, u.foto_url,
    e.ru, e.carrera, e.semestre, e.perfil_profesional, e.cv_url
FROM usuarios u
JOIN estudiantes e ON u.id_usuarios = e.id_usuarios;

CREATE OR REPLACE FUNCTION tr_insertar_registro_estudiante()
RETURNS TRIGGER AS $$
DECLARE
    v_rol_id INT;
    v_user_id INT;
BEGIN
    SELECT id_roles INTO v_rol_id FROM roles WHERE nombre = 'ESTUDIANTE';
    
    INSERT INTO usuarios (id_roles, ci, nombre, apellido, email, password, telefono, foto_url)
    VALUES (v_rol_id, NEW.ci, NEW.nombre, NEW.apellido, NEW.email, NEW.password, NEW.telefono, 
            COALESCE(NEW.foto_url, 'https://ui-avatars.com/api/?name=' || NEW.nombre || '+' || NEW.apellido || '&background=random'))
    RETURNING id_usuarios INTO v_user_id;

    INSERT INTO estudiantes (id_usuarios, ru, carrera, semestre, perfil_profesional, cv_url)
    VALUES (v_user_id, NEW.ru, NEW.carrera, COALESCE(NEW.semestre, 1), NEW.perfil_profesional, NEW.cv_url);

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_registro_estudiante_instead ON v_registro_estudiante;
CREATE TRIGGER tr_registro_estudiante_instead
INSTEAD OF INSERT ON v_registro_estudiante
FOR EACH ROW
EXECUTE FUNCTION tr_insertar_registro_estudiante();

CREATE OR REPLACE VIEW v_registro_empresa AS
SELECT 
    u.ci, u.nombre, u.apellido, u.email, u.password, u.telefono,
    e.nit, e.razon_social, e.nombre_comercial, e.sector_industrial, 
    e.direccion, e.sitio_web, e.representante_legal, e.telefono_contacto, e.logo_url
FROM usuarios u
JOIN empresas e ON u.id_usuarios = e.id_usuarios;

CREATE OR REPLACE FUNCTION tr_insertar_registro_empresa()
RETURNS TRIGGER AS $$
DECLARE
    v_rol_id INT;
    v_user_id INT;
BEGIN
    SELECT id_roles INTO v_rol_id FROM roles WHERE nombre = 'EMPRESA';
    
    INSERT INTO usuarios (id_roles, ci, nombre, apellido, email, password, telefono, foto_url)
    VALUES (v_rol_id, NEW.ci, NEW.nombre, NEW.apellido, NEW.email, NEW.password, NEW.telefono,
            COALESCE(NEW.logo_url, 'https://ui-avatars.com/api/?name=' || NEW.nombre_comercial || '&background=0D8ABC&color=fff'))
    RETURNING id_usuarios INTO v_user_id;

    INSERT INTO empresas (
        id_usuarios, nit, razon_social, nombre_comercial, sector_industrial,
        direccion, sitio_web, representante_legal, telefono_contacto, logo_url, verificada
    ) VALUES (
        v_user_id, NEW.nit, NEW.razon_social, NEW.nombre_comercial, COALESCE(NEW.sector_industrial, 'Tecnologia'),
        NEW.direccion, NEW.sitio_web, NEW.representante_legal, NEW.telefono_contacto, NEW.logo_url, FALSE
    );

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_registro_empresa_instead ON v_registro_empresa;
CREATE TRIGGER tr_registro_empresa_instead
INSTEAD OF INSERT ON v_registro_empresa
FOR EACH ROW
EXECUTE FUNCTION tr_insertar_registro_empresa();

-- =============================================================================
-- POBLADO DE DATOS MAESTROS Y EJEMPLOS REALES
-- =============================================================================

-- 1. Roles
INSERT INTO roles (nombre, descripcion) VALUES
('DIRECTOR', 'Máxima autoridad académica. Verifica empresas y modera ofertas.'),
('SECRETARIA', 'Personal administrativo de soporte y revisión.'),
('ESTUDIANTE', 'Alumno de la UAGRM que busca ofertas laborales.'),
('EMPRESA', 'Entidad empleadora que publica ofertas y contrata talento.');

-- 2. Areas Laborales
INSERT INTO areas_laborales (nombre, descripcion) VALUES
('Desarrollo de Software', 'Frontend, Backend, Móvil, Fullstack y Arquitectura'),
('Redes y Telecomunicaciones', 'Infraestructura de redes, servidores, telefonía y enlaces'),
('Ciberseguridad', 'Auditoría, seguridad ofensiva/defensiva y normativas'),
('Ciencia de Datos e Inteligencia Artificial', 'Machine Learning, análisis de datos, BI y Big Data'),
('Soporte TI e Infraestructura', 'Helpdesk, mantenimiento de hardware y virtualización'),
('Gestión y Proyectos TI', 'Scrum Master, Product Owner y gestión de proyectos tecnológicos');

-- 3. Habilidades
INSERT INTO habilidades (nombre, categoria) VALUES
('Java', 'LENGUAJE'),
('Python', 'LENGUAJE'),
('JavaScript', 'LENGUAJE'),
('TypeScript', 'LENGUAJE'),
('Spring Boot', 'FRAMEWORK'),
('React', 'FRAMEWORK'),
('Angular', 'FRAMEWORK'),
('Docker', 'DEVOPS'),
('PostgreSQL', 'DATABASE'),
('MySQL', 'DATABASE'),
('Git', 'HERRAMIENTA'),
('Linux', 'HERRAMIENTA'),
('Redes CISCO', 'HERRAMIENTA'),
('Ciberseguridad', 'HERRAMIENTA'),
('Scrum', 'SOFT_SKILL'),
('Inglés B2', 'IDIOMA');

-- 4. Planes de Suscripcion
INSERT INTO planes (nombre, precio, duracion_dias, limite_ofertas, requiere_moderacion, es_gratuito, descripcion) VALUES
('PRUEBA GRATUITA', 0.00, 30, 1, TRUE, TRUE, 'Plan de bienvenida: 30 días, 1 oferta laboral sujeta a moderación por Dirección.'),
('PLAN BASICO', 70.00, 30, 3, FALSE, FALSE, 'Plan Starter: 30 días, 3 ofertas laborales con publicación directa.'),
('PLAN INTERMEDIO', 150.00, 30, 8, FALSE, FALSE, 'Plan Profesional: 30 días, 8 ofertas laborales, publicación directa y acceso a CVs destacados.'),
('PLAN EMPRESARIAL', 300.00, 30, 20, FALSE, FALSE, 'Plan Corporativo: 30 días, 20 ofertas laborales, soporte prioritario y máxima visibilidad.');

-- 5. Usuarios Maestros
INSERT INTO usuarios (id_roles, ci, nombre, apellido, email, password, telefono, foto_url) VALUES
(1, '1001', 'Roberto', 'Gomez', 'director.roberto@gmail.com', 'dir123', '70011223', 'https://ui-avatars.com/api/?name=Roberto+Gomez&background=555555&color=fff'),
(2, '1002', 'Carla', 'Suarez', 'carla.suarez@gmail.com', 'sec123', '70044556', 'https://ui-avatars.com/api/?name=Carla+Suarez&background=777777&color=fff');

-- 6. Estudiantes de Prueba
INSERT INTO usuarios (id_roles, ci, nombre, apellido, email, password, telefono, foto_url) VALUES
(3, '8923411', 'Mateo', 'Morales', 'mateo.morales@gmail.com', 'est123', '75012345', 'https://ui-avatars.com/api/?name=Mateo+Morales&background=555555&color=fff'),
(3, '9123842', 'Valentina', 'Condori', 'valentina.condori@gmail.com', 'est123', '76098765', 'https://ui-avatars.com/api/?name=Valentina+Condori&background=777777&color=fff');

INSERT INTO estudiantes (id_usuarios, ru, carrera, semestre, cv_url, perfil_profesional) VALUES
(3, '219012345', 'Ingeniería en Sistemas', 9, 'https://drive.google.com/cv-mateo', 'Desarrollador Backend Java y entusiasta de bases de datos.'),
(4, '219056789', 'Ingeniería Informática', 8, 'https://drive.google.com/cv-val', 'Desarrolladora Frontend React y Diseñadora UI/UX.');

-- Habilidades de estudiantes
INSERT INTO estudiante_habilidades (id_estudiantes, id_habilidades, nivel, anios_experiencia) VALUES
(1, 1, 'AVANZADO', 3),
(1, 5, 'AVANZADO', 2),
(1, 9, 'AVANZADO', 3),
(1, 11, 'INTERMEDIO', 2),
(2, 6, 'AVANZADO', 2),
(2, 3, 'AVANZADO', 3);

-- 7. Empresas de Prueba
INSERT INTO usuarios (id_roles, ci, nombre, apellido, email, password, telefono) VALUES
(4, '4019283', 'Carlos', 'Mendoza', 'rrhh@tigo.com.bo', 'emp123', '71000001'),
(4, '5192834', 'Mariela', 'Vargas', 'contacto@startupbo.com', 'emp123', '72000002');

INSERT INTO empresas (id_usuarios, nit, razon_social, nombre_comercial, sector_industrial, direccion, sitio_web, representante_legal, telefono_contacto, logo_url, verificada) VALUES
(5, '1020304050', 'TELECEL S.A.', 'Tigo Bolivia', 'Telecomunicaciones', 'Doble Vía a La Guardia Km 6', 'https://tigo.com.bo', 'Carlos Mendoza', '33445566', 'https://ui-avatars.com/api/?name=Tigo&background=00377B&color=fff', TRUE),
(6, '9080706050', 'STARTUP BOLIVIA S.R.L.', 'Startup Innova', 'Tecnología', 'Av. San Martín, Equipetrol', 'https://startupbo.com', 'Mariela Vargas', '72000002', 'https://ui-avatars.com/api/?name=Startup&background=FF5722&color=fff', FALSE);

UPDATE suscripciones 
SET id_planes = 3, ofertas_publicadas = 0, estado = 'ACTIVA' 
WHERE id_empresas = 1;

-- 8. Ofertas Laborales
INSERT INTO ofertas (id_empresas, id_areas_laborales, titulo, descripcion, modalidad, tipo_contrato, ubicacion, salario, vacantes, estado) VALUES
(1, 1, 'Desarrollador Java Spring Boot Junior', 'Buscamos estudiante de últimos semestres para backend bancario.', 'HIBRIDO', 'TIEMPO_COMPLETO', 'Santa Cruz - Equipetrol', 3500.00, 1, 'PUBLICADA'),
(2, 1, 'Pasante Frontend React', 'Pasantía remunerada para estudiantes de Sistemas o Informática.', 'REMOTO', 'PASANTIA', 'Santa Cruz', 1500.00, 2, 'PENDIENTE_MODERACION');

INSERT INTO oferta_habilidades (id_ofertas, id_habilidades, es_obligatoria, nivel_minimo) VALUES
(1, 1, TRUE, 'INTERMEDIO'),
(1, 5, TRUE, 'INTERMEDIO'),
(1, 9, FALSE, 'BASICO');

-- 9. Postulaciones de Prueba
INSERT INTO postulaciones (id_estudiantes, id_ofertas, mensaje_presentacion, estado) VALUES
(1, 1, 'Estimados, cumplo con todos los requerimientos de Java y Spring Boot de la materia.', 'POSTULADO'),
(2, 1, 'Hola, tengo experiencia práctica en desarrollo backend y bases de datos.', 'POSTULADO');

-- =============================================================================
-- FIN DEL SCRIPT
-- =============================================================================
