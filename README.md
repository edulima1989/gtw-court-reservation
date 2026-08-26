# Court Reservation — Despliegue con Docker Compose

Este repositorio contiene el **API Gateway** y el `docker-compose.yml` que levanta la plataforma completa:
4 microservicios, el gateway, la app shell, 3 microfrontends y una base de datos PostgreSQL.

---

## 1. Arquitectura

```
                       ┌──────────────────────────────────────────┐
Navegador ───────────► │ mf-shell (3000)                          │
                       │  ├─ mf-reservas (3001)   Module          │
                       │  ├─ mf-admin (3002)      Federation      │
                       │  └─ mf-reportes (3003)                   │
                       └────────────────────┬─────────────────────┘
                                            │ HTTP
                                            ▼
                         ┌────────────────────────────────────────┐
                         │              gateway (8080)            │
                         └──┬────┬──────────────────────┬─────┬───┘
                            │    │                      │     │
     ┌──────────────────────┘    │                      │     └──────────────────┐
     ▼                           ▼                      ▼                        ▼
msa-court (8081)   msa-authentication (8082)   msa-reservations (8083)   msa-reports (8084)
     │                           │                        │                    │
     │                           │                        │        consume vía HTTP
     │                           │                        │        canchas y reservas
     │                           │                        │◄───────────────────┘
     └────────────────────────┬──┴────────────────────────┘
                              │
                              ▼
                      postgres (5432)
                canchas_db · usuarios_db · reservas_db
```

| Servicio | Contenedor | Puerto host | Descripción |
|---|---|---|---|
| PostgreSQL | `court-postgres` | 5432 | Base de datos compartida (3 esquemas/DB) |
| Autenticación | `msa-authentication` | 8082 | Usuarios, login, validación de sesión |
| Canchas | `msa-court` | 8081 | Canchas y deportes |
| Reservas | `msa-reservations` | 8083 | Reservas y cancelaciones |
| Reportes | `msa-reports` | 8084 | Reportes agregados; sin base de datos propia, consume canchas y reservas por HTTP. Rutas restringidas a rol ADMIN |
| Gateway | `gtw-court-reservation` | 8080 | Enrutamiento, CORS y filtros de seguridad |
| Shell | `mf-shell` | 3000 | Aplicación contenedora (host de microfrontends) |
| Reservas MF | `mf-reservas` | 3001 | Microfrontend remoto |
| Administración MF | `mf-admin` | 3002 | Microfrontend remoto |
| Reportes MF | `mf-reportes` | 3003 | Microfrontend remoto |

---

## 2. Prerrequisitos

- **Docker Desktop 4.x** (o Docker Engine 24+) con **Docker Compose v2**.
- **Git**.
- Puertos libres en el host: `3000`, `3001`, `3002`, `3003`, `5432`, `8080`, `8081`, `8082`, `8083`, `8084`.
- ~8 GB de RAM disponibles y ~5 GB de disco para las imágenes.

> No se requiere instalar Java, Gradle ni Node en la máquina: todo se compila dentro de los contenedores.

---

## 3. Estructura de carpetas requerida

El `docker-compose.yml` construye cada imagen desde su repositorio **hermano**, por lo que todos los
proyectos deben estar clonados dentro de la misma carpeta padre:

```
<carpeta-padre>/
├── gtw-court-reservation/            <-- aquí está el docker-compose.yml
├── msa-court-reservation-authentication/
├── msa-court-reservation-court/
├── msa-court-reservation-reservations/
├── msa-court-reservation-reports/
├── master_microfrontend_shell/
├── master_microfrontend_reservas/
├── master_microfrontend_admin/
└── master_microfrontend_reportes/
```

Clonado desde cero:

```bash
mkdir court-reservation && cd court-reservation

git clone <url>/gtw-court-reservation.git
git clone <url>/msa-court-reservation-authentication.git
git clone <url>/msa-court-reservation-court.git
git clone <url>/msa-court-reservation-reservations.git
git clone <url>/msa-court-reservation-reports.git
git clone <url>/master_microfrontend_shell.git
git clone <url>/master_microfrontend_reservas.git
git clone <url>/master_microfrontend_admin.git
git clone <url>/master_microfrontend_reportes.git
```

---

## 4. Configuración de variables de entorno

Dentro de `gtw-court-reservation`, copia la plantilla y ajusta los valores:

```bash
cd gtw-court-reservation
cp .env.example .env        # En Windows PowerShell: Copy-Item .env.example .env
```

| Variable | Default | Descripción |
|---|---|---|
| `POSTGRES_USER` | `postgres` | Usuario de PostgreSQL |
| `POSTGRES_PASSWORD` | `mysecretpassword` | Contraseña de PostgreSQL |
| `JWT_SECRET` | valor de ejemplo | Secreto de firma JWT (mínimo 64 bytes) |
| `JWT_EXPIRATION` | `86400000` | Vigencia del token en milisegundos |
| `RESERVAS_ASSET_PREFIX` | `http://localhost:3001` | Origen público del microfrontend de reservas |
| `ADMIN_ASSET_PREFIX` | `http://localhost:3002` | Origen público del microfrontend de administración |
| `REPORTES_ASSET_PREFIX` | `http://localhost:3003` | Origen público del microfrontend de reportes |

> **Importante:** los `*_ASSET_PREFIX` se aplican en tiempo de build. Si publicas el sistema en un
> host distinto a `localhost`, cámbialos por el dominio real **antes** de construir las imágenes,
> de lo contrario el shell no podrá cargar los remotos de Module Federation.

> El archivo `.env` **no debe subirse al repositorio**; contiene credenciales.

---

## 5. Despliegue

Desde `gtw-court-reservation`:

```bash
docker compose up --build -d
```

Esto ejecuta, en orden:

1. **Build de los servicios Java** (multi-stage: Gradle + JDK 21 → JRE 21).
2. **Build de los microfrontends** (`npm ci` + `rsbuild build` → nginx).
3. **Arranque de PostgreSQL**, que crea `usuarios_db`, `canchas_db` y `reservas_db` mediante
   `docker/postgres/init-databases.sql`.
4. **Arranque de los microservicios** una vez que el healthcheck de PostgreSQL pasa a *healthy*.
   Hibernate crea las tablas automáticamente (`ddl-auto: update`).
5. **Arranque de `msa-reports`**, que no usa base de datos y espera a `msa-court` y `msa-reservations`.
6. **Arranque del gateway** y, por último, de los microfrontends y el shell.

La primera ejecución descarga dependencias de Gradle y npm, por lo que tarda considerablemente más
que las siguientes (las capas quedan en caché).

Seguimiento del arranque:

```bash
docker compose ps
docker compose logs -f
```

---

## 6. Verificación

Con todos los contenedores en estado `Up`:

| Qué | URL |
|---|---|
| Aplicación (shell) | http://localhost:3000 |
| Gateway | http://localhost:8080 |
| Swagger — Canchas | http://localhost:8081/courts/swagger-ui/index.html |
| Swagger — Usuarios | http://localhost:8082/users/swagger-ui/index.html |
| Swagger — Reservas | http://localhost:8083/reservations/swagger-ui/index.html |
| Swagger — Reportes | http://localhost:8084/reports/swagger-ui/index.html |

Comprobación rápida por consola:

```bash
# Estado de los contenedores
docker compose ps

# Bases de datos creadas
docker compose exec postgres psql -U postgres -c "\l"

# Registro de un usuario a través del gateway
curl -X POST http://localhost:8080/users/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"userName":"Demo","userMail":"demo@demo.com","userPassword":"Demo12345","userRole":"USUARIO_FINAL"}'
```

Luego abre http://localhost:3000, inicia sesión y navega entre los módulos de reservas,
administración y reportes.

---

## 7. Operación diaria

```bash
# Detener sin borrar datos
docker compose stop

# Reanudar
docker compose start

# Reconstruir un servicio puntual tras cambiar su código
docker compose up -d --build msa-court

# Reconstruir solo los microfrontends
docker compose up -d --build mf-shell mf-reservas mf-admin mf-reportes

# Logs de un servicio
docker compose logs -f gateway

# Bajar todo (conserva la base de datos)
docker compose down

# Bajar todo y ELIMINAR los datos de PostgreSQL
docker compose down -v
```

---

## 8. Solución de problemas

**`bind: address already in use`**
Otro proceso ocupa el puerto (por ejemplo un `npm run dev` o un PostgreSQL local). Detén el proceso
o cambia el mapeo en `docker-compose.yml` (`"5433:5432"`).

**`Failed to get remoteEntry exports` (#RUNTIME-001) en el navegador**
El shell está usando un manifest cacheado. Recarga forzada con `Ctrl+Shift+R`. Si persiste,
reconstruye los microfrontends y verifica que el `publicPath` sea absoluto:

```bash
curl -s http://localhost:3002/mf-manifest.json | grep publicPath
```

**Los microservicios no arrancan y los logs muestran `Connection refused` hacia PostgreSQL**
PostgreSQL aún no terminaba de inicializar. Compose ya espera al healthcheck; si ocurre,
`docker compose restart msa-court msa-authentication msa-reservations msa-reports`.

**`msa-reports` devuelve errores o listas vacías**
Este servicio no tiene base de datos: consume `msa-court` y `msa-reservations` por HTTP usando
`COURT_SERVICE_URI` y `RESERVATION_SERVICE_URI`. Verifica que ambos estén `Up` y revisa los logs con
`docker compose logs -f msa-reports`.

**`403` al consultar `/reports/**` desde el gateway**
Las rutas de reportes están protegidas con el filtro `AdminOnly`; usa un token de un usuario con rol
`ADMIN`.

**Las bases de datos no existen**
El script de inicialización solo se ejecuta cuando el volumen está vacío. Recrea el volumen con
`docker compose down -v && docker compose up -d`.

**Error CORS al llamar al gateway**
El gateway solo autoriza el origen definido en `SHELL_ORIGIN` (por defecto `http://localhost:3000`).
Ajústalo en `docker-compose.yml` si sirves el shell desde otro origen.

**El build de Gradle falla por permisos de `gradlew`**
Los `Dockerfile` ya normalizan los saltos de línea y aplican `chmod +x`; asegúrate de no haber
modificado esa capa.

---

## 9. Ejecución sin Docker (desarrollo)

Los valores por defecto de los `application.yaml` apuntan a `localhost`, por lo que el modo local
sigue funcionando: levanta PostgreSQL, luego cada microservicio con `./gradlew bootRun` y cada
frontend con `npm install && npm run dev`.
