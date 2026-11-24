-- Copia del esquema que crean las migraciones de Django en SQLite (main del 24/11/2026, con HU-12),
-- solo con las tablas que lee Spring. Las pruebas no tocan la base real.
DROP TABLE IF EXISTS prestadores_prestador_rubros;
DROP TABLE IF EXISTS prestadores_prestador;
DROP TABLE IF EXISTS prestadores_rubro;
DROP TABLE IF EXISTS auth_user;
CREATE TABLE "auth_user" ("id" integer NOT NULL PRIMARY KEY AUTOINCREMENT, "username" varchar(150) NOT NULL UNIQUE);
CREATE TABLE "prestadores_rubro" ("id" integer NOT NULL PRIMARY KEY AUTOINCREMENT, "nombre" varchar(60) NOT NULL UNIQUE);
CREATE TABLE "prestadores_prestador" ("id" integer NOT NULL PRIMARY KEY AUTOINCREMENT, "nombres" varchar(100) NOT NULL, "apellidos" varchar(100) NOT NULL, "dni" varchar(8) NOT NULL UNIQUE, "telefono" varchar(20) NOT NULL, "latitud" decimal NOT NULL, "longitud" decimal NOT NULL, "radio_cobertura_km" smallint unsigned NOT NULL CHECK ("radio_cobertura_km" >= 0), "disponible" bool NOT NULL, "estado" varchar(12) NOT NULL, "creado" datetime NOT NULL, "usuario_id" integer NOT NULL UNIQUE REFERENCES "auth_user" ("id") DEFERRABLE INITIALLY DEFERRED, "calificacion_promedio" decimal NULL, "calificaciones_total" integer unsigned NOT NULL CHECK ("calificaciones_total" >= 0));
CREATE TABLE "prestadores_prestador_rubros" ("id" integer NOT NULL PRIMARY KEY AUTOINCREMENT, "prestador_id" bigint NOT NULL REFERENCES "prestadores_prestador" ("id") DEFERRABLE INITIALLY DEFERRED, "rubro_id" bigint NOT NULL REFERENCES "prestadores_rubro" ("id") DEFERRABLE INITIALLY DEFERRED);
