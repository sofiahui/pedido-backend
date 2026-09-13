# Pedidos360 — Backend (EP1, Desarrollo Cloud Native I)

Backend del sistema Pedidos360: 3 microservicios Spring Boot que implementan lo exigido
por la EP1 (autenticación Azure AD/MSAL validada por el backend) más el CRUD funcional
de pedidos y catálogo descrito en el caso semestral.

## Servicios

| Servicio | Puerto | Responsabilidad |
|---|---|---|
| `ms-pedidos360-bff` | 8080 | Valida el JWT de Azure AD y enruta hacia orders/catalog |
| `ms-pedidos360-orders` | 8081 | CRUD de pedidos y máquina de estados |
| `ms-pedidos360-catalog` | 8082 | CRUD de productos y control de stock |

Los tres validan el JWT de forma independiente (issuer, audience, firma y expiración) y
aplican autorización por rol (`Admin`, `Operador`, `Cliente`) vía `@PreAuthorize`.

## 1. Base de datos (MySQL + phpMyAdmin)

```bash
cd infra/db
docker compose up -d
```

- MySQL queda en `localhost:3306` (usuario `pedidos360` / password `pedidos360`, bases
  `pedidos360_orders` y `pedidos360_catalog` creadas automáticamente).
- phpMyAdmin queda en [http://localhost:8090](http://localhost:8090) (usuario `root`,
  password `pedidos360_root`, o los valores que definas en `infra/db/.env`).

## 2. Azure AD (App Registration "Pedidos360")

Todavía no está configurado con un tenant real. Cuando lo crees en Azure Portal necesitas:

1. Un **App Registration** para la API (expón un scope, ej. `api://<API_CLIENT_ID>`) y
   asigna **App Roles**: `Admin`, `Operador`, `Cliente`, `Auditor`.
2. El **Tenant ID** y el **Client ID** de esa API.
3. Configúralos como variables de entorno antes de levantar los servicios:

```bash
export AZURE_TENANT_ID=<tu-tenant-id>
export AZURE_API_AUDIENCE=api://<tu-api-client-id>
```

Sin estas variables, los servicios arrancan igual (con valores placeholder) porque el
`JwtDecoder` se construye de forma perezosa — solo falla al llegar una petición real con
token, no al iniciar la aplicación.

## 3. Compilar y probar

```bash
cd backend
mvn clean verify
```

Cada módulo corre sus tests con una base H2 en memoria (no requiere Docker/MySQL para
compilar ni testear).

## 4. Ejecutar localmente

En 3 terminales distintas:

```bash
mvn -pl ms-pedidos360-catalog spring-boot:run
mvn -pl ms-pedidos360-orders spring-boot:run
mvn -pl ms-pedidos360-bff spring-boot:run
```

Swagger de cada servicio: `http://localhost:<puerto>/swagger-ui.html`.

## Estructura

```
backend/
  ms-pedidos360-bff/       Resource server + proxy con autorización por rol
  ms-pedidos360-orders/    Pedidos: CREADO -> ACEPTADO -> EN_PREPARACION -> DESPACHADO -> ENTREGADO / CANCELADO
  ms-pedidos360-catalog/   Productos y stock
infra/
  db/                      docker-compose de MySQL + phpMyAdmin
```

## Pendiente (fuera del alcance de esta entrega)

- Frontend Angular + MSAL.
- RabbitMQ (notificaciones), Kafka (auditoría/reportería), despliegue en EC2 — a cargo
  del resto del equipo según el caso semestral completo.
