# Pedidos360 (Desarrollo Cloud Native I)

Frontend (Angular + MSAL) y backend (Spring Boot) del sistema Pedidos360: login con Azure AD
validado de extremo a extremo, CRUD de pedidos y catálogo, y mensajería asíncrona con un
clúster RabbitMQ de 2 nodos (productores, consumidores, DLQ y microservicio administrador).

## Servicios

| Componente | Puerto | Responsabilidad |
|---|---|---|
| `frontend-pedidos360` | 4200 | Angular 18 + MSAL: login, guards por rol, consumo del BFF |
| `ms-pedidos360-bff` | 8080 | Valida el JWT de Azure AD y enruta hacia orders/catalog |
| `ms-pedidos360-orders` | 8081 | CRUD de pedidos y máquina de estados |
| `ms-pedidos360-catalog` | 8082 | CRUD de productos y control de stock |
| `ms-pedidos360-notify` | — (sin puerto) | Consume las colas de RabbitMQ (email, cocina, boleta) y las DLQ |
| `ms-pedidos360-mqadmin` | 8083 | Crea y elimina colas, exchanges y bindings de RabbitMQ (solo rol Admin) |

Los tres microservicios validan el JWT de forma independiente (issuer, audience, firma y
expiración) y aplican autorización por rol (`Admin`, `Operador`, `Cliente`, `Auditor`) vía
`@PreAuthorize`. El frontend lee esos mismos roles desde el claim `roles` del ID token para
mostrar/ocultar pantallas con guards.

## 1. Base de datos (MySQL + phpMyAdmin)

```bash
cd infra/db
docker compose up -d
```

- MySQL queda en `localhost:3306` (usuario `pedidos360` / password `pedidos360`, bases
  `pedidos360_orders` y `pedidos360_catalog` creadas automáticamente).
- phpMyAdmin queda en [http://localhost:8090](http://localhost:8090) (usuario `root`,
  password `pedidos360_root`, o los valores que definas en `infra/db/.env`).

## 1b. RabbitMQ (clúster de 2 nodos)

```bash
cd infra/mq
cp .env.example .env          # editar RABBITMQ_ERLANG_COOKIE
docker compose up -d
```

Levanta `rabbit1` y `rabbit2` formando el clúster `pedidos360`, y un contenedor de una sola
ejecución que aplica las políticas de retención. Panel de gestión: nodo 1
[http://localhost:15672](http://localhost:15672), nodo 2 [http://localhost:15673](http://localhost:15673)
(usuario y clave del `.env`). Detalle, comandos de verificación y despliegue en EC2 en
[infra/mq/README.md](infra/mq/README.md).

Cómo viaja un mensaje: al cambiar un pedido de estado, `orders` guarda en la base y, **después
del commit**, publica en `cmd.direct`. `notify` lo consume con ACK manual; si falla va a la DLQ
de esa cola y un listener de DLQ deja el detalle en el log. La checklist contra la guía de
evaluación está en [docs/EA2-checklist.md](docs/EA2-checklist.md).

## 2. Azure AD — qué crear en el portal

Hoy el código apunta a placeholders (`CHANGE_ME_...`). Para dejarlo funcionando de verdad
hacen falta **dos** App Registrations en el mismo tenant:

### 2.1 App Registration de la API ("Pedidos360-API")

1. Azure Portal → Entra ID → App registrations → New registration → sin redirect URI.
2. **Expose an API** → Add a scope: `access_as_user` (Admin y usuarios pueden consentir).
   Esto genera el Application ID URI `api://<API_CLIENT_ID>`.
3. **App roles** → crea 4 roles (Allowed member types: *Users/Groups*), con estos `Value`
   exactos (el código los compara literalmente):
   - `Admin`
   - `Operador`
   - `Cliente`
   - `Auditor`
4. En **Enterprise applications → Pedidos360-API → Users and groups**, asigna cada usuario
   de prueba a uno de esos roles.
5. Anota el **Tenant ID** y el **Client ID** de esta app: los usa el backend.

### 2.2 App Registration del SPA ("Pedidos360-SPA")

1. New registration → **Platform: Single-page application** → redirect URI
   `http://localhost:4200/auth/callback`.
2. **API permissions** → Add a permission → APIs de mi organización → `Pedidos360-API` →
   selecciona el scope `access_as_user` → Grant admin consent.
3. Anota el **Client ID** de este segundo registro: lo usa el frontend.

### 2.3 Configurar el código

Backend — variables de entorno antes de levantar los servicios (la audiencia es el Client ID de la
API, el GUID **sin** `api://`, que es lo que trae el claim `aud` de los tokens v2.0):

```bash
export AZURE_TENANT_ID=<tenant-id>
export AZURE_API_AUDIENCE=<API_CLIENT_ID>
```

Frontend — editar `frontend-pedidos360/src/environments/environment.development.ts`
(y `environment.ts` para build de producción):

```ts
azureAd: {
  clientId: '<SPA_CLIENT_ID>',
  tenantId: '<tenant-id>',
  redirectUri: 'http://localhost:4200/auth/callback',
  postLogoutRedirectUri: 'http://localhost:4200/login',
  apiScope: 'api://<API_CLIENT_ID>/access_as_user',
}
```

Sin estos valores reales, tanto el backend como el frontend igual **arrancan y compilan**
(el `JwtDecoder` del backend es perezoso; el login del frontend simplemente redirige a un
tenant inexistente y Azure AD responde con un error controlado, lo que ya probé).

## 3. Compilar y probar

```bash
# Backend
cd backend
mvn clean verify

# Frontend
cd frontend-pedidos360
npm install
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
```

Los módulos de backend corren sus tests con una base H2 en memoria (no requiere
Docker/MySQL para compilar ni testear).

## 4. Ejecutar localmente

Primero la infraestructura (secciones 1 y 1b: MySQL y RabbitMQ). Luego, una terminal por servicio:

```bash
cd backend
mvn -pl ms-pedidos360-catalog spring-boot:run   # 8082
mvn -pl ms-pedidos360-orders  spring-boot:run   # 8081 (declara exchanges, colas y bindings)
mvn -pl ms-pedidos360-notify  spring-boot:run   # consume las colas y las DLQ
mvn -pl ms-pedidos360-mqadmin spring-boot:run   # 8083
mvn -pl ms-pedidos360-bff     spring-boot:run   # 8080
npm start --prefix ../frontend-pedidos360       # 4200
```

Variables útiles: `RABBITMQ_ADDRESSES=host1:5672,host2:5673` (los dos nodos del clúster; si no se
define se usa `RABBITMQ_HOST`/`RABBITMQ_PORT`), `RABBITMQ_USER`, `RABBITMQ_PASS`,
`MQADMIN_SERVICE_URL` (para el BFF).

- Frontend: [http://localhost:4200](http://localhost:4200)
- Swagger de cada microservicio: `http://localhost:<puerto>/swagger-ui.html`

## Estructura

```
frontend-pedidos360/       Angular 18 + MSAL
  src/app/core/            Config de MSAL, AuthService, guards, interceptores
  src/app/layout/          Shell (header + nav por rol)
  src/app/features/        Login, dashboard, pedidos, catálogo
  src/app/shared/          Modelos y servicios HTTP hacia el BFF
backend/
  ms-pedidos360-bff/       Resource server + proxy con autorización por rol
  ms-pedidos360-orders/    Pedidos: CREADO -> ACEPTADO -> EN_PREPARACION -> DESPACHADO -> ENTREGADO / CANCELADO
  ms-pedidos360-catalog/   Productos y stock
  ms-pedidos360-notify/    Consumidores RabbitMQ (ACK manual, reintento, DLQ) y listener de DLQ
  ms-pedidos360-mqadmin/   Administrador de RabbitMQ (RabbitAdminService, validación, solo Admin)
infra/
  db/                      docker-compose de MySQL + phpMyAdmin
  mq/                      docker-compose del clúster RabbitMQ (rabbit1 + rabbit2) y políticas
docs/                      Diagrama de flujo y checklist contra la guía EA2
```

## Pendiente

- Kafka (auditoría/reportería) y despliegue completo en AWS (EC2 + API Gateway); ver
  [docs/EA2-checklist.md](docs/EA2-checklist.md) para lo que falta de la guía actual.
