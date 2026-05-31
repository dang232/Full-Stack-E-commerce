# Monitoring Service & Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a NestJS monitoring service with dynamic service discovery, health polling, TimescaleDB metrics, real-time WebSocket alerts, and a static HTML dashboard with Keycloak auth.

**Architecture:** NestJS app on port 8096 discovers services from the API Gateway's Actuator endpoint, polls their health every 10s, stores metrics in TimescaleDB, pushes real-time updates via Socket.io, and serves a vanilla HTML/JS dashboard protected by Keycloak PKCE auth.

**Tech Stack:** NestJS 11, TypeORM + TimescaleDB (pg16), Socket.io, passport-jwt + jwks-rsa, vanilla HTML/CSS/JS

---

## Task 1: Project Scaffold & Config

**Files:**
- Create: `services/monitoring-service-v2/package.json`
- Create: `services/monitoring-service-v2/tsconfig.json`
- Create: `services/monitoring-service-v2/nest-cli.json`
- Create: `services/monitoring-service-v2/.env.example`
- Create: `services/monitoring-service-v2/src/main.ts`
- Create: `services/monitoring-service-v2/src/app.module.ts`
- Create: `services/monitoring-service-v2/src/config/app.config.ts`
- Create: `services/monitoring-service-v2/src/config/database.config.ts`

- [ ] **Step 1: Create package.json**

```json
{
  "name": "monitoring-service-v2",
  "version": "0.0.1",
  "private": true,
  "scripts": {
    "build": "nest build",
    "start": "nest start",
    "start:dev": "nest start --watch",
    "start:debug": "nest start --debug --watch",
    "start:prod": "node dist/main",
    "test": "jest",
    "test:watch": "jest --watch",
    "test:cov": "jest --coverage"
  },
  "dependencies": {
    "@nestjs/common": "^11.1.21",
    "@nestjs/config": "^4.0.4",
    "@nestjs/core": "^11.1.21",
    "@nestjs/passport": "^11.0.5",
    "@nestjs/platform-express": "^11.1.21",
    "@nestjs/platform-socket.io": "^11.1.24",
    "@nestjs/schedule": "^5.0.1",
    "@nestjs/serve-static": "^5.0.1",
    "@nestjs/typeorm": "^11.0.0",
    "@nestjs/websockets": "^11.1.24",
    "axios": "^1.7.9",
    "jwks-rsa": "^3.2.0",
    "passport": "^0.7.0",
    "passport-jwt": "^4.0.1",
    "pg": "^8.13.1",
    "reflect-metadata": "^0.2.2",
    "rxjs": "^7.8.2",
    "socket.io": "^4.8.3",
    "typeorm": "^0.3.20"
  },
  "devDependencies": {
    "@nestjs/cli": "^11.0.21",
    "@nestjs/schematics": "^11.1.0",
    "@nestjs/testing": "^11.1.21",
    "@types/express": "^5.0.6",
    "@types/jest": "^30.0.0",
    "@types/node": "^24.12.4",
    "@types/passport-jwt": "^4.0.1",
    "jest": "^30.4.2",
    "source-map-support": "^0.5.21",
    "ts-jest": "^29.4.9",
    "ts-node": "^10.9.2",
    "tsconfig-paths": "^4.2.0",
    "typescript": "^5.7.3"
  },
  "jest": {
    "moduleFileExtensions": ["js", "json", "ts"],
    "rootDir": "src",
    "testRegex": ".*\\.spec\\.ts$",
    "transform": { "^.+\\.(t|j)s$": "ts-jest" },
    "coverageDirectory": "../coverage",
    "testEnvironment": "node"
  }
}
```

- [ ] **Step 2: Create tsconfig.json**

```json
{
  "compilerOptions": {
    "module": "nodenext",
    "moduleResolution": "nodenext",
    "resolvePackageJsonExports": true,
    "esModuleInterop": true,
    "isolatedModules": true,
    "declaration": true,
    "removeComments": true,
    "emitDecoratorMetadata": true,
    "experimentalDecorators": true,
    "allowSyntheticDefaultImports": true,
    "target": "ES2023",
    "sourceMap": true,
    "outDir": "./dist",
    "baseUrl": "./",
    "incremental": true,
    "skipLibCheck": true,
    "strictNullChecks": true,
    "forceConsistentCasingInFileNames": true,
    "noImplicitAny": true,
    "strictBindCallApply": true,
    "noFallthroughCasesInSwitch": true
  }
}
```

- [ ] **Step 3: Create nest-cli.json**

```json
{
  "$schema": "https://json.schemastore.org/nest-cli",
  "collection": "@nestjs/schematics",
  "sourceRoot": "src",
  "compilerOptions": {
    "deleteOutDir": true
  }
}
```

- [ ] **Step 4: Create .env.example**

```env
PORT=8096
GATEWAY_URL=http://localhost:8080
GATEWAY_ACTUATOR_URL=http://localhost:8080/actuator/gateway/routes
DISCOVERY_INTERVAL_MS=300000
HEALTH_POLL_INTERVAL_MS=10000
TIMESCALE_HOST=localhost
TIMESCALE_PORT=5440
TIMESCALE_DB=monitoring
TIMESCALE_USER=monitoring
TIMESCALE_PASSWORD=monitoring
KEYCLOAK_ISSUER_URI=http://localhost:9090/realms/vnshop
KEYCLOAK_JWK_SET_URI=http://keycloak:8080/realms/vnshop/protocol/openid-connect/certs
KEYCLOAK_ADMIN_ROLE=admin
```

- [ ] **Step 5: Create src/config/app.config.ts**

```typescript
import { registerAs } from '@nestjs/config';

export const appConfig = registerAs('app', () => ({
  port: parseInt(process.env.PORT ?? '8096', 10),
  gatewayUrl: process.env.GATEWAY_URL ?? 'http://localhost:8080',
  gatewayActuatorUrl:
    process.env.GATEWAY_ACTUATOR_URL ??
    'http://localhost:8080/actuator/gateway/routes',
  discoveryIntervalMs: parseInt(process.env.DISCOVERY_INTERVAL_MS ?? '300000', 10),
  healthPollIntervalMs: parseInt(process.env.HEALTH_POLL_INTERVAL_MS ?? '10000', 10),
  keycloak: {
    issuerUri: process.env.KEYCLOAK_ISSUER_URI ?? 'http://localhost:9090/realms/vnshop',
    jwkSetUri:
      process.env.KEYCLOAK_JWK_SET_URI ??
      'http://keycloak:8080/realms/vnshop/protocol/openid-connect/certs',
    adminRole: process.env.KEYCLOAK_ADMIN_ROLE ?? 'admin',
  },
}));
```

- [ ] **Step 6: Create src/config/database.config.ts**

```typescript
import { registerAs } from '@nestjs/config';

export const databaseConfig = registerAs('database', () => ({
  host: process.env.TIMESCALE_HOST ?? 'localhost',
  port: parseInt(process.env.TIMESCALE_PORT ?? '5440', 10),
  database: process.env.TIMESCALE_DB ?? 'monitoring',
  username: process.env.TIMESCALE_USER ?? 'monitoring',
  password: process.env.TIMESCALE_PASSWORD ?? 'monitoring',
}));
```

- [ ] **Step 7: Create src/app.module.ts**

```typescript
import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ScheduleModule } from '@nestjs/schedule';
import { ServeStaticModule } from '@nestjs/serve-static';
import { join } from 'path';
import { appConfig } from './config/app.config.js';
import { databaseConfig } from './config/database.config.js';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true, load: [appConfig, databaseConfig] }),
    ScheduleModule.forRoot(),
    TypeOrmModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        type: 'postgres',
        host: config.get('database.host'),
        port: config.get('database.port'),
        database: config.get('database.database'),
        username: config.get('database.username'),
        password: config.get('database.password'),
        autoLoadEntities: true,
        synchronize: false,
      }),
    }),
    ServeStaticModule.forRoot({ rootPath: join(__dirname, '..', 'public') }),
  ],
})
export class AppModule {}
```

- [ ] **Step 8: Create src/main.ts**

```typescript
import { NestFactory } from '@nestjs/core';
import { IoAdapter } from '@nestjs/platform-socket.io';
import { AppModule } from './app.module.js';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  app.useWebSocketAdapter(new IoAdapter(app));
  app.enableCors({
    origin: (process.env.CORS_ORIGINS ?? 'http://localhost:3000,http://localhost:5173').split(','),
    credentials: true,
  });

  const port = process.env.PORT ?? 8096;
  await app.listen(port);
  console.log(`Monitoring service running on port ${port}`);
}

void bootstrap();
```

- [ ] **Step 9: Install dependencies and verify build**

Run: `cd services/monitoring-service-v2 && npm install && npm run build`
Expected: Compiles with no errors

- [ ] **Step 10: Commit**

```bash
git add services/monitoring-service-v2/
git commit -m "feat(monitoring): scaffold NestJS project with config and static serving"
```

---

## Task 2: Infrastructure — TimescaleDB & Docker

**Files:**
- Modify: `docker-compose.yml`
- Create: `infra/timescaledb/init.sql`

- [ ] **Step 1: Add TimescaleDB container to docker-compose.yml**

Add after the last postgres service block:

```yaml
  timescaledb:
    profiles: ["apps"]
    image: timescale/timescaledb:latest-pg16
    container_name: vnshop-timescaledb
    environment:
      POSTGRES_DB: monitoring
      POSTGRES_USER: monitoring
      POSTGRES_PASSWORD: monitoring
    ports:
      - "5440:5432"
    volumes:
      - timescaledb-data:/var/lib/postgresql/data
      - ./infra/timescaledb/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U monitoring -d monitoring"]
      interval: 10s
      timeout: 5s
      retries: 5
```

Add `timescaledb-data:` to the `volumes:` section at the bottom.

- [ ] **Step 2: Create infra/timescaledb/init.sql**

```sql
-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Health metrics hypertable
CREATE TABLE health_metrics (
  time        TIMESTAMPTZ NOT NULL,
  service_id  TEXT NOT NULL,
  status      TEXT NOT NULL,
  response_ms INTEGER,
  details     JSONB
);

SELECT create_hypertable('health_metrics', 'time');

-- Retention: auto-drop after 30 days
SELECT add_retention_policy('health_metrics', INTERVAL '30 days');

-- Continuous aggregate for hourly rollups
CREATE MATERIALIZED VIEW health_hourly
WITH (timescaledb.continuous) AS
SELECT
  time_bucket('1 hour', time) AS bucket,
  service_id,
  avg(response_ms)::INTEGER AS avg_ms,
  percentile_cont(0.95) WITHIN GROUP (ORDER BY response_ms)::INTEGER AS p95_ms,
  count(*) FILTER (WHERE status = 'up') * 100.0 / GREATEST(count(*), 1) AS uptime_pct
FROM health_metrics
GROUP BY bucket, service_id
WITH NO DATA;

SELECT add_continuous_aggregate_policy('health_hourly',
  start_offset => INTERVAL '2 hours',
  end_offset => INTERVAL '1 hour',
  schedule_interval => INTERVAL '1 hour');

-- Alerts table
CREATE TABLE alerts (
  id          SERIAL PRIMARY KEY,
  service_id  TEXT NOT NULL,
  type        TEXT NOT NULL,
  message     TEXT,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_alerts_service_id ON alerts(service_id);
CREATE INDEX idx_alerts_created_at ON alerts(created_at DESC);
CREATE INDEX idx_health_metrics_service_time ON health_metrics(service_id, time DESC);
```

- [ ] **Step 3: Verify TimescaleDB starts**

Run: `docker compose up timescaledb -d && docker compose logs timescaledb --tail 20`
Expected: Container starts, init.sql runs, "database system is ready to accept connections"

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml infra/timescaledb/
git commit -m "feat(monitoring): add TimescaleDB container with schema init"
```

---

## Task 3: Auth Module — JWT + Roles Guard

**Files:**
- Create: `services/monitoring-service-v2/src/auth/auth.module.ts`
- Create: `services/monitoring-service-v2/src/auth/jwt.strategy.ts`
- Create: `services/monitoring-service-v2/src/auth/auth.guard.ts`
- Create: `services/monitoring-service-v2/src/auth/roles.guard.ts`
- Create: `services/monitoring-service-v2/src/auth/roles.decorator.ts`
- Test: `services/monitoring-service-v2/src/auth/roles.guard.spec.ts`

- [ ] **Step 1: Create roles.decorator.ts**

```typescript
import { SetMetadata } from '@nestjs/common';

export const ROLES_KEY = 'roles';
export const Roles = (...roles: string[]) => SetMetadata(ROLES_KEY, roles);
```

- [ ] **Step 2: Create jwt.strategy.ts**

```typescript
import { Injectable, Logger } from '@nestjs/common';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy, StrategyOptions } from 'passport-jwt';
import { passportJwtSecret } from 'jwks-rsa';

export interface JwtPayload {
  sub: string;
  realm_access?: { roles?: string[] };
  [claim: string]: unknown;
}

@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy, 'jwt') {
  private static readonly logger = new Logger(JwtStrategy.name);

  constructor() {
    const issuerUri =
      process.env.KEYCLOAK_ISSUER_URI ?? 'http://localhost:9090/realms/vnshop';
    const jwksUri =
      process.env.KEYCLOAK_JWK_SET_URI ??
      'http://keycloak:8080/realms/vnshop/protocol/openid-connect/certs';

    const options: StrategyOptions = {
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      ignoreExpiration: false,
      issuer: issuerUri,
      algorithms: ['RS256'],
      secretOrKeyProvider: passportJwtSecret({
        cache: true,
        rateLimit: true,
        jwksRequestsPerMinute: 10,
        jwksUri,
      }),
    };

    super(options);
    JwtStrategy.logger.log(
      `JWT strategy initialised (issuer=${issuerUri}, jwks=${jwksUri})`,
    );
  }

  validate(payload: JwtPayload): JwtPayload {
    return payload;
  }
}
```

- [ ] **Step 3: Create auth.guard.ts**

```typescript
import { Injectable } from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';

@Injectable()
export class JwtAuthGuard extends AuthGuard('jwt') {}
```

- [ ] **Step 4: Create roles.guard.ts**

```typescript
import { Injectable, CanActivate, ExecutionContext } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { ROLES_KEY } from './roles.decorator.js';
import { JwtPayload } from './jwt.strategy.js';

@Injectable()
export class RolesGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const requiredRoles = this.reflector.getAllAndOverride<string[]>(ROLES_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (!requiredRoles || requiredRoles.length === 0) {
      return true;
    }

    const request = context.switchToHttp().getRequest();
    const user = request.user as JwtPayload | undefined;
    if (!user) return false;

    const userRoles = user.realm_access?.roles ?? [];
    return requiredRoles.some((role) => userRoles.includes(role));
  }
}
```

- [ ] **Step 5: Write test for RolesGuard**

```typescript
import { RolesGuard } from './roles.guard.js';
import { Reflector } from '@nestjs/core';
import { ExecutionContext } from '@nestjs/common';

describe('RolesGuard', () => {
  let guard: RolesGuard;
  let reflector: Reflector;

  beforeEach(() => {
    reflector = new Reflector();
    guard = new RolesGuard(reflector);
  });

  function mockContext(user: unknown): ExecutionContext {
    return {
      getHandler: () => ({}),
      getClass: () => ({}),
      switchToHttp: () => ({ getRequest: () => ({ user }) }),
    } as unknown as ExecutionContext;
  }

  it('allows access when no roles required', () => {
    jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue(undefined);
    expect(guard.canActivate(mockContext({}))).toBe(true);
  });

  it('denies access when user has no matching role', () => {
    jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue(['admin']);
    const user = { sub: '1', realm_access: { roles: ['buyer'] } };
    expect(guard.canActivate(mockContext(user))).toBe(false);
  });

  it('allows access when user has admin role', () => {
    jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue(['admin']);
    const user = { sub: '1', realm_access: { roles: ['admin', 'buyer'] } };
    expect(guard.canActivate(mockContext(user))).toBe(true);
  });

  it('denies access when no user on request', () => {
    jest.spyOn(reflector, 'getAllAndOverride').mockReturnValue(['admin']);
    expect(guard.canActivate(mockContext(undefined))).toBe(false);
  });
});
```

- [ ] **Step 6: Run test**

Run: `cd services/monitoring-service-v2 && npx jest src/auth/roles.guard.spec.ts --no-coverage`
Expected: 4 tests pass

- [ ] **Step 7: Create auth.module.ts**

```typescript
import { Module } from '@nestjs/common';
import { PassportModule } from '@nestjs/passport';
import { JwtStrategy } from './jwt.strategy.js';
import { JwtAuthGuard } from './auth.guard.js';
import { RolesGuard } from './roles.guard.js';

@Module({
  imports: [PassportModule.register({ defaultStrategy: 'jwt' })],
  providers: [JwtStrategy, JwtAuthGuard, RolesGuard],
  exports: [JwtAuthGuard, RolesGuard],
})
export class AuthModule {}
```

- [ ] **Step 8: Register AuthModule in AppModule and apply global guards**

Update `src/app.module.ts` imports to include `AuthModule`, and add `APP_GUARD` providers:

```typescript
import { Module } from '@nestjs/common';
import { APP_GUARD } from '@nestjs/core';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ScheduleModule } from '@nestjs/schedule';
import { ServeStaticModule } from '@nestjs/serve-static';
import { join } from 'path';
import { appConfig } from './config/app.config.js';
import { databaseConfig } from './config/database.config.js';
import { AuthModule } from './auth/auth.module.js';
import { JwtAuthGuard } from './auth/auth.guard.js';
import { RolesGuard } from './auth/roles.guard.js';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true, load: [appConfig, databaseConfig] }),
    ScheduleModule.forRoot(),
    TypeOrmModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        type: 'postgres',
        host: config.get('database.host'),
        port: config.get('database.port'),
        database: config.get('database.database'),
        username: config.get('database.username'),
        password: config.get('database.password'),
        autoLoadEntities: true,
        synchronize: false,
      }),
    }),
    ServeStaticModule.forRoot({ rootPath: join(__dirname, '..', 'public') }),
    AuthModule,
  ],
  providers: [
    { provide: APP_GUARD, useClass: JwtAuthGuard },
    { provide: APP_GUARD, useClass: RolesGuard },
  ],
})
export class AppModule {}
```

- [ ] **Step 9: Verify build**

Run: `cd services/monitoring-service-v2 && npm run build`
Expected: Compiles with no errors

- [ ] **Step 10: Commit**

```bash
git add services/monitoring-service-v2/src/auth/
git commit -m "feat(monitoring): auth module with JWT validation and admin role guard"
```

---

## Task 4: Metrics Module — Entities & Repository

**Files:**
- Create: `services/monitoring-service-v2/src/metrics/entities/health-metric.entity.ts`
- Create: `services/monitoring-service-v2/src/metrics/entities/alert.entity.ts`
- Create: `services/monitoring-service-v2/src/metrics/metrics.service.ts`
- Create: `services/monitoring-service-v2/src/metrics/metrics.module.ts`
- Test: `services/monitoring-service-v2/src/metrics/metrics.service.spec.ts`

- [ ] **Step 1: Create health-metric.entity.ts**

```typescript
import { Entity, Column, PrimaryGeneratedColumn, Index } from 'typeorm';

@Entity('health_metrics')
@Index(['service_id', 'time'])
export class HealthMetric {
  @PrimaryGeneratedColumn('increment')
  id!: number;

  @Column({ type: 'timestamptz' })
  time!: Date;

  @Column({ name: 'service_id' })
  serviceId!: string;

  @Column()
  status!: string;

  @Column({ name: 'response_ms', nullable: true })
  responseMs!: number | null;

  @Column({ type: 'jsonb', nullable: true })
  details!: Record<string, unknown> | null;
}
```

- [ ] **Step 2: Create alert.entity.ts**

```typescript
import { Entity, Column, PrimaryGeneratedColumn, Index } from 'typeorm';

@Entity('alerts')
@Index(['service_id'])
@Index(['created_at'])
export class Alert {
  @PrimaryGeneratedColumn()
  id!: number;

  @Column({ name: 'service_id' })
  serviceId!: string;

  @Column()
  type!: string;

  @Column({ nullable: true })
  message!: string | null;

  @Column({ name: 'created_at', type: 'timestamptz', default: () => 'NOW()' })
  createdAt!: Date;

  @Column({ name: 'resolved_at', type: 'timestamptz', nullable: true })
  resolvedAt!: Date | null;
}
```

- [ ] **Step 3: Create metrics.service.ts**

```typescript
import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, MoreThan, IsNull } from 'typeorm';
import { HealthMetric } from './entities/health-metric.entity.js';
import { Alert } from './entities/alert.entity.js';

export interface MetricsSummary {
  avgMs: number;
  p95Ms: number;
  uptimePct: number;
}

@Injectable()
export class MetricsService {
  constructor(
    @InjectRepository(HealthMetric)
    private readonly metricRepo: Repository<HealthMetric>,
    @InjectRepository(Alert)
    private readonly alertRepo: Repository<Alert>,
  ) {}

  async recordMetric(
    serviceId: string,
    status: string,
    responseMs: number | null,
    details: Record<string, unknown> | null,
  ): Promise<void> {
    await this.metricRepo.insert({
      time: new Date(),
      serviceId,
      status,
      responseMs,
      details,
    });
  }

  async getHistory(
    serviceId: string,
    period: '1h' | '24h' | '7d',
  ): Promise<HealthMetric[]> {
    const since = new Date();
    const hours = period === '1h' ? 1 : period === '24h' ? 24 : 168;
    since.setHours(since.getHours() - hours);

    return this.metricRepo.find({
      where: { serviceId, time: MoreThan(since) },
      order: { time: 'ASC' },
    });
  }

  async getSummary(serviceId: string, period: '1h' | '24h' | '7d'): Promise<MetricsSummary> {
    const since = new Date();
    const hours = period === '1h' ? 1 : period === '24h' ? 24 : 168;
    since.setHours(since.getHours() - hours);

    const result = await this.metricRepo
      .createQueryBuilder('m')
      .select('AVG(m.response_ms)::INTEGER', 'avgMs')
      .addSelect(
        "percentile_cont(0.95) WITHIN GROUP (ORDER BY m.response_ms)::INTEGER",
        'p95Ms',
      )
      .addSelect(
        "count(*) FILTER (WHERE m.status = 'up') * 100.0 / GREATEST(count(*), 1)",
        'uptimePct',
      )
      .where('m.service_id = :serviceId', { serviceId })
      .andWhere('m.time > :since', { since })
      .getRawOne();

    return {
      avgMs: result?.avgMs ?? 0,
      p95Ms: result?.p95Ms ?? 0,
      uptimePct: parseFloat(result?.uptimePct ?? '0'),
    };
  }

  async createAlert(serviceId: string, type: string, message: string): Promise<Alert> {
    const alert = this.alertRepo.create({ serviceId, type, message });
    return this.alertRepo.save(alert);
  }

  async resolveAlerts(serviceId: string): Promise<void> {
    await this.alertRepo.update(
      { serviceId, resolvedAt: IsNull() },
      { resolvedAt: new Date() },
    );
  }

  async getActiveAlerts(): Promise<Alert[]> {
    return this.alertRepo.find({
      where: { resolvedAt: IsNull() },
      order: { createdAt: 'DESC' },
    });
  }

  async getAlertHistory(days: number = 7): Promise<Alert[]> {
    const since = new Date();
    since.setDate(since.getDate() - days);
    return this.alertRepo.find({
      where: { createdAt: MoreThan(since) },
      order: { createdAt: 'DESC' },
    });
  }
}
```

- [ ] **Step 4: Write test for MetricsService**

```typescript
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { MetricsService } from './metrics.service.js';
import { HealthMetric } from './entities/health-metric.entity.js';
import { Alert } from './entities/alert.entity.js';

describe('MetricsService', () => {
  let service: MetricsService;
  const mockMetricRepo = {
    insert: jest.fn(),
    find: jest.fn(),
    createQueryBuilder: jest.fn(),
  };
  const mockAlertRepo = {
    create: jest.fn(),
    save: jest.fn(),
    update: jest.fn(),
    find: jest.fn(),
  };

  beforeEach(async () => {
    const module = await Test.createTestingModule({
      providers: [
        MetricsService,
        { provide: getRepositoryToken(HealthMetric), useValue: mockMetricRepo },
        { provide: getRepositoryToken(Alert), useValue: mockAlertRepo },
      ],
    }).compile();

    service = module.get(MetricsService);
    jest.clearAllMocks();
  });

  it('records a metric', async () => {
    await service.recordMetric('product-service', 'up', 42, null);
    expect(mockMetricRepo.insert).toHaveBeenCalledWith(
      expect.objectContaining({ serviceId: 'product-service', status: 'up', responseMs: 42 }),
    );
  });

  it('creates an alert', async () => {
    const alert = { id: 1, serviceId: 'cart-service', type: 'down', message: 'unreachable' };
    mockAlertRepo.create.mockReturnValue(alert);
    mockAlertRepo.save.mockResolvedValue(alert);

    const result = await service.createAlert('cart-service', 'down', 'unreachable');
    expect(result).toEqual(alert);
  });

  it('resolves active alerts for a service', async () => {
    await service.resolveAlerts('cart-service');
    expect(mockAlertRepo.update).toHaveBeenCalledWith(
      { serviceId: 'cart-service', resolvedAt: expect.anything() },
      { resolvedAt: expect.any(Date) },
    );
  });
});
```

- [ ] **Step 5: Run tests**

Run: `cd services/monitoring-service-v2 && npx jest src/metrics/metrics.service.spec.ts --no-coverage`
Expected: 3 tests pass

- [ ] **Step 6: Create metrics.module.ts**

```typescript
import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { HealthMetric } from './entities/health-metric.entity.js';
import { Alert } from './entities/alert.entity.js';
import { MetricsService } from './metrics.service.js';

@Module({
  imports: [TypeOrmModule.forFeature([HealthMetric, Alert])],
  providers: [MetricsService],
  exports: [MetricsService],
})
export class MetricsModule {}
```

- [ ] **Step 7: Register MetricsModule in AppModule**

Add `MetricsModule` to the `imports` array in `src/app.module.ts`.

- [ ] **Step 8: Verify build**

Run: `cd services/monitoring-service-v2 && npm run build`
Expected: Compiles with no errors

- [ ] **Step 9: Commit**

```bash
git add services/monitoring-service-v2/src/metrics/
git commit -m "feat(monitoring): metrics module with TimescaleDB entities and service"
```

---

## Task 5: Discovery Module — Gateway Client & OpenAPI Fetcher

**Files:**
- Create: `services/monitoring-service-v2/src/discovery/discovery.types.ts`
- Create: `services/monitoring-service-v2/src/discovery/gateway-client.ts`
- Create: `services/monitoring-service-v2/src/discovery/openapi-fetcher.ts`
- Create: `services/monitoring-service-v2/src/discovery/discovery.service.ts`
- Create: `services/monitoring-service-v2/src/discovery/discovery.controller.ts`
- Create: `services/monitoring-service-v2/src/discovery/discovery.module.ts`
- Test: `services/monitoring-service-v2/src/discovery/gateway-client.spec.ts`
- Test: `services/monitoring-service-v2/src/discovery/discovery.service.spec.ts`

- [ ] **Step 1: Create discovery.types.ts**

```typescript
export interface GatewayRoute {
  route_id: string;
  predicates: Array<{ name: string; args: Record<string, string> }>;
  filters: unknown[];
  uri: string;
  order: number;
}

export interface DiscoveredService {
  id: string;
  name: string;
  url: string;
  healthPath: string;
  routes: string[];
}

export interface DiscoveredEndpoint {
  id: string;
  serviceId: string;
  method: string;
  path: string;
  summary?: string;
  schema?: Record<string, unknown>;
}
```

- [ ] **Step 2: Create gateway-client.ts**

```typescript
import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import axios from 'axios';
import { GatewayRoute, DiscoveredService } from './discovery.types.js';

@Injectable()
export class GatewayClient {
  private readonly logger = new Logger(GatewayClient.name);
  private readonly actuatorUrl: string;

  constructor(private readonly config: ConfigService) {
    this.actuatorUrl = this.config.get<string>(
      'app.gatewayActuatorUrl',
      'http://localhost:8080/actuator/gateway/routes',
    );
  }

  async fetchRoutes(): Promise<GatewayRoute[]> {
    try {
      const response = await axios.get<GatewayRoute[]>(this.actuatorUrl, {
        timeout: 5000,
      });
      return response.data;
    } catch (error) {
      this.logger.warn(`Failed to fetch gateway routes: ${(error as Error).message}`);
      return [];
    }
  }

  parseServices(routes: GatewayRoute[]): DiscoveredService[] {
    const serviceMap = new Map<string, DiscoveredService>();

    for (const route of routes) {
      const url = route.uri;
      const hostname = new URL(url).hostname;
      const id = hostname;
      const name = hostname
        .split('-')
        .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
        .join(' ');

      const pathPredicates = route.predicates
        .filter((p) => p.name === 'Path')
        .flatMap((p) => Object.values(p.args));

      if (serviceMap.has(id)) {
        serviceMap.get(id)!.routes.push(...pathPredicates);
      } else {
        serviceMap.set(id, {
          id,
          name,
          url,
          healthPath: '/actuator/health',
          routes: [...pathPredicates],
        });
      }
    }

    return Array.from(serviceMap.values());
  }
}
```

- [ ] **Step 3: Write test for GatewayClient.parseServices**

```typescript
import { GatewayClient } from './gateway-client.js';
import { ConfigService } from '@nestjs/config';
import { GatewayRoute } from './discovery.types.js';

describe('GatewayClient', () => {
  let client: GatewayClient;

  beforeEach(() => {
    const config = { get: () => 'http://localhost:8080/actuator/gateway/routes' } as unknown as ConfigService;
    client = new GatewayClient(config);
  });

  it('parses routes into deduplicated services', () => {
    const routes: GatewayRoute[] = [
      {
        route_id: 'products',
        predicates: [{ name: 'Path', args: { _genkey_0: '/products/**' } }],
        filters: [],
        uri: 'http://product-service:8082',
        order: 0,
      },
      {
        route_id: 'categories',
        predicates: [{ name: 'Path', args: { _genkey_0: '/categories/**' } }],
        filters: [],
        uri: 'http://product-service:8082',
        order: 1,
      },
      {
        route_id: 'cart',
        predicates: [{ name: 'Path', args: { _genkey_0: '/cart/**' } }],
        filters: [],
        uri: 'http://cart-service:8084',
        order: 2,
      },
    ];

    const services = client.parseServices(routes);

    expect(services).toHaveLength(2);
    const productSvc = services.find((s) => s.id === 'product-service');
    expect(productSvc).toBeDefined();
    expect(productSvc!.routes).toEqual(['/products/**', '/categories/**']);
    expect(productSvc!.name).toBe('Product Service');

    const cartSvc = services.find((s) => s.id === 'cart-service');
    expect(cartSvc).toBeDefined();
    expect(cartSvc!.routes).toEqual(['/cart/**']);
  });

  it('returns empty array for empty routes', () => {
    expect(client.parseServices([])).toEqual([]);
  });
});
```

- [ ] **Step 4: Run test**

Run: `cd services/monitoring-service-v2 && npx jest src/discovery/gateway-client.spec.ts --no-coverage`
Expected: 2 tests pass

- [ ] **Step 5: Create openapi-fetcher.ts**

```typescript
import { Injectable, Logger } from '@nestjs/common';
import axios from 'axios';
import { DiscoveredEndpoint } from './discovery.types.js';

@Injectable()
export class OpenApiFetcher {
  private readonly logger = new Logger(OpenApiFetcher.name);

  async fetchSchema(serviceUrl: string, serviceId: string): Promise<DiscoveredEndpoint[]> {
    const spec = await this.tryFetchSpec(serviceUrl);
    if (!spec) return [];
    return this.parseSpec(spec, serviceId);
  }

  private async tryFetchSpec(serviceUrl: string): Promise<Record<string, unknown> | null> {
    // Try Spring Boot OpenAPI first
    try {
      const res = await axios.get(`${serviceUrl}/v3/api-docs`, { timeout: 3000 });
      if (res.data?.paths) return res.data;
    } catch { /* fall through */ }

    // Try NestJS Swagger
    try {
      const res = await axios.get(`${serviceUrl}/api-json`, { timeout: 3000 });
      if (res.data?.paths) return res.data;
    } catch { /* fall through */ }

    return null;
  }

  private parseSpec(spec: Record<string, unknown>, serviceId: string): DiscoveredEndpoint[] {
    const endpoints: DiscoveredEndpoint[] = [];
    const paths = spec.paths as Record<string, Record<string, unknown>> | undefined;
    if (!paths) return endpoints;

    for (const [path, methods] of Object.entries(paths)) {
      for (const [method, details] of Object.entries(methods)) {
        if (['get', 'post', 'put', 'patch', 'delete'].includes(method)) {
          const op = details as Record<string, unknown>;
          endpoints.push({
            id: `${serviceId}:${method.toUpperCase()}:${path}`,
            serviceId,
            method: method.toUpperCase(),
            path,
            summary: (op.summary as string) ?? undefined,
            schema: op.requestBody ? (op.requestBody as Record<string, unknown>) : undefined,
          });
        }
      }
    }

    return endpoints;
  }
}
```

- [ ] **Step 6: Create discovery.service.ts**

```typescript
import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Interval } from '@nestjs/schedule';
import { GatewayClient } from './gateway-client.js';
import { OpenApiFetcher } from './openapi-fetcher.js';
import { DiscoveredService, DiscoveredEndpoint } from './discovery.types.js';

@Injectable()
export class DiscoveryService implements OnModuleInit {
  private readonly logger = new Logger(DiscoveryService.name);
  private services: DiscoveredService[] = [];
  private endpoints: DiscoveredEndpoint[] = [];
  private readonly intervalMs: number;

  constructor(
    private readonly gatewayClient: GatewayClient,
    private readonly openApiFetcher: OpenApiFetcher,
    private readonly config: ConfigService,
  ) {
    this.intervalMs = this.config.get<number>('app.discoveryIntervalMs', 300000);
  }

  async onModuleInit(): Promise<void> {
    await this.refresh();
  }

  @Interval(300000)
  async refresh(): Promise<void> {
    this.logger.log('Refreshing service discovery...');
    const routes = await this.gatewayClient.fetchRoutes();

    if (routes.length > 0) {
      this.services = this.gatewayClient.parseServices(routes);
      await this.detectHealthPaths();
      await this.refreshEndpoints();
      this.logger.log(`Discovered ${this.services.length} services, ${this.endpoints.length} endpoints`);
    } else if (this.services.length === 0) {
      this.logger.warn('No routes from gateway and no cached services — will retry');
    }
  }

  private async detectHealthPaths(): Promise<void> {
    for (const svc of this.services) {
      try {
        await axios.get(`${svc.url}/actuator/health`, { timeout: 2000 });
        svc.healthPath = '/actuator/health';
      } catch {
        svc.healthPath = '/health';
      }
    }
  }

  private async refreshEndpoints(): Promise<void> {
    const allEndpoints: DiscoveredEndpoint[] = [];
    for (const svc of this.services) {
      const eps = await this.openApiFetcher.fetchSchema(svc.url, svc.id);
      allEndpoints.push(...eps);
    }
    this.endpoints = allEndpoints;
  }

  getServices(): DiscoveredService[] {
    return this.services;
  }

  getEndpoints(): DiscoveredEndpoint[] {
    return this.endpoints;
  }

  getEndpointById(id: string): DiscoveredEndpoint | undefined {
    return this.endpoints.find((e) => e.id === id);
  }

  getServiceById(id: string): DiscoveredService | undefined {
    return this.services.find((s) => s.id === id);
  }
}
```

Note: Add `import axios from 'axios';` at the top of discovery.service.ts (used in `detectHealthPaths`).

- [ ] **Step 7: Write test for DiscoveryService**

```typescript
import { Test } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { DiscoveryService } from './discovery.service.js';
import { GatewayClient } from './gateway-client.js';
import { OpenApiFetcher } from './openapi-fetcher.js';

describe('DiscoveryService', () => {
  let service: DiscoveryService;
  const mockGatewayClient = {
    fetchRoutes: jest.fn(),
    parseServices: jest.fn(),
  };
  const mockOpenApiFetcher = { fetchSchema: jest.fn() };
  const mockConfig = { get: jest.fn().mockReturnValue(300000) };

  beforeEach(async () => {
    const module = await Test.createTestingModule({
      providers: [
        DiscoveryService,
        { provide: GatewayClient, useValue: mockGatewayClient },
        { provide: OpenApiFetcher, useValue: mockOpenApiFetcher },
        { provide: ConfigService, useValue: mockConfig },
      ],
    }).compile();

    service = module.get(DiscoveryService);
    jest.clearAllMocks();
  });

  it('populates services on refresh', async () => {
    const routes = [{ route_id: 'cart', uri: 'http://cart-service:8084', predicates: [], filters: [], order: 0 }];
    const services = [{ id: 'cart-service', name: 'Cart Service', url: 'http://cart-service:8084', healthPath: '/health', routes: ['/cart/**'] }];

    mockGatewayClient.fetchRoutes.mockResolvedValue(routes);
    mockGatewayClient.parseServices.mockReturnValue(services);
    mockOpenApiFetcher.fetchSchema.mockResolvedValue([]);

    await service.refresh();

    expect(service.getServices()).toHaveLength(1);
    expect(service.getServices()[0].id).toBe('cart-service');
  });

  it('keeps cached services when gateway is unreachable', async () => {
    // First successful refresh
    mockGatewayClient.fetchRoutes.mockResolvedValue([{ route_id: 'x', uri: 'http://x:1', predicates: [], filters: [], order: 0 }]);
    mockGatewayClient.parseServices.mockReturnValue([{ id: 'x', name: 'X', url: 'http://x:1', healthPath: '/health', routes: [] }]);
    mockOpenApiFetcher.fetchSchema.mockResolvedValue([]);
    await service.refresh();

    // Second refresh fails
    mockGatewayClient.fetchRoutes.mockResolvedValue([]);
    await service.refresh();

    expect(service.getServices()).toHaveLength(1);
  });
});
```

- [ ] **Step 8: Run tests**

Run: `cd services/monitoring-service-v2 && npx jest src/discovery/ --no-coverage`
Expected: All tests pass

- [ ] **Step 9: Create discovery.controller.ts**

```typescript
import { Controller, Get, Param, NotFoundException } from '@nestjs/common';
import { Roles } from '../auth/roles.decorator.js';
import { DiscoveryService } from './discovery.service.js';

@Controller('monitoring')
@Roles('admin')
export class DiscoveryController {
  constructor(private readonly discoveryService: DiscoveryService) {}

  @Get('endpoints')
  getEndpoints() {
    const endpoints = this.discoveryService.getEndpoints();
    const services = this.discoveryService.getServices();

    const grouped = services.map((svc) => ({
      service: svc,
      endpoints: endpoints.filter((e) => e.serviceId === svc.id),
    }));

    return grouped;
  }

  @Get('endpoints/:id/schema')
  getEndpointSchema(@Param('id') id: string) {
    const endpoint = this.discoveryService.getEndpointById(id);
    if (!endpoint) throw new NotFoundException('Endpoint not found');
    return endpoint;
  }
}
```

- [ ] **Step 10: Create discovery.module.ts**

```typescript
import { Module } from '@nestjs/common';
import { GatewayClient } from './gateway-client.js';
import { OpenApiFetcher } from './openapi-fetcher.js';
import { DiscoveryService } from './discovery.service.js';
import { DiscoveryController } from './discovery.controller.js';

@Module({
  controllers: [DiscoveryController],
  providers: [GatewayClient, OpenApiFetcher, DiscoveryService],
  exports: [DiscoveryService],
})
export class DiscoveryModule {}
```

- [ ] **Step 11: Register DiscoveryModule in AppModule**

Add `DiscoveryModule` to the `imports` array in `src/app.module.ts`.

- [ ] **Step 12: Verify build**

Run: `cd services/monitoring-service-v2 && npm run build`
Expected: Compiles with no errors

- [ ] **Step 13: Commit**

```bash
git add services/monitoring-service-v2/src/discovery/
git commit -m "feat(monitoring): discovery module with gateway client and OpenAPI fetcher"
```

---

## Task 6: Health Module — Polling & State Machine

**Files:**
- Create: `services/monitoring-service-v2/src/health/health-checker.ts`
- Create: `services/monitoring-service-v2/src/health/health.service.ts`
- Create: `services/monitoring-service-v2/src/health/health.controller.ts`
- Create: `services/monitoring-service-v2/src/health/health.module.ts`
- Test: `services/monitoring-service-v2/src/health/health.service.spec.ts`

- [ ] **Step 1: Create health-checker.ts**

```typescript
import { Injectable, Logger } from '@nestjs/common';
import axios from 'axios';

export interface HealthCheckResult {
  serviceId: string;
  status: 'up' | 'down' | 'degraded';
  responseMs: number;
  dependencies: Record<string, { status: string }>;
}

@Injectable()
export class HealthChecker {
  private readonly logger = new Logger(HealthChecker.name);
  private static readonly DEGRADED_THRESHOLD_MS = 2000;

  async check(serviceId: string, url: string, healthPath: string): Promise<HealthCheckResult> {
    const start = Date.now();
    try {
      const response = await axios.get(`${url}${healthPath}`, { timeout: 5000 });
      const responseMs = Date.now() - start;
      const data = response.data as Record<string, unknown>;

      const dependencies = this.parseDependencies(data);
      const anyDepDown = Object.values(dependencies).some((d) => d.status !== 'UP');
      const slow = responseMs > HealthChecker.DEGRADED_THRESHOLD_MS;

      let status: 'up' | 'down' | 'degraded' = 'up';
      if (anyDepDown || slow) status = 'degraded';

      return { serviceId, status, responseMs, dependencies };
    } catch {
      return {
        serviceId,
        status: 'down',
        responseMs: Date.now() - start,
        dependencies: {},
      };
    }
  }

  private parseDependencies(data: Record<string, unknown>): Record<string, { status: string }> {
    const deps: Record<string, { status: string }> = {};
    const components = data.components as Record<string, { status?: string }> | undefined;
    if (!components) return deps;

    for (const [name, detail] of Object.entries(components)) {
      deps[name] = { status: detail.status ?? 'UNKNOWN' };
    }
    return deps;
  }
}
```

- [ ] **Step 2: Create health.service.ts**

```typescript
import { Injectable, Logger } from '@nestjs/common';
import { Interval } from '@nestjs/schedule';
import { DiscoveryService } from '../discovery/discovery.service.js';
import { MetricsService } from '../metrics/metrics.service.js';
import { HealthChecker, HealthCheckResult } from './health-checker.js';
import { EventEmitter2 } from '@nestjs/event-emitter';

export interface ServiceStatus {
  serviceId: string;
  name: string;
  status: 'up' | 'down' | 'degraded';
  responseMs: number;
  uptimePct: number;
  dependencies: Record<string, { status: string }>;
  lastChecked: Date;
}

@Injectable()
export class HealthService {
  private readonly logger = new Logger(HealthService.name);
  private readonly statuses = new Map<string, ServiceStatus>();
  private readonly failureCounts = new Map<string, number>();
  private readonly recoveryCounts = new Map<string, number>();
  private static readonly FAILURE_THRESHOLD = 3;
  private static readonly RECOVERY_THRESHOLD = 3;

  constructor(
    private readonly discoveryService: DiscoveryService,
    private readonly metricsService: MetricsService,
    private readonly healthChecker: HealthChecker,
    private readonly eventEmitter: EventEmitter2,
  ) {}

  @Interval(10000)
  async pollAll(): Promise<void> {
    const services = this.discoveryService.getServices();
    if (services.length === 0) return;

    const results = await Promise.allSettled(
      services.map((svc) => this.healthChecker.check(svc.id, svc.url, svc.healthPath)),
    );

    for (let i = 0; i < services.length; i++) {
      const result = results[i];
      if (result.status === 'fulfilled') {
        await this.processResult(services[i].name, result.value);
      }
    }
  }

  private async processResult(name: string, result: HealthCheckResult): Promise<void> {
    const { serviceId, status, responseMs, dependencies } = result;
    const previous = this.statuses.get(serviceId);
    const previousStatus = previous?.status ?? 'up';

    // Track consecutive failures/recoveries
    if (status === 'down') {
      this.failureCounts.set(serviceId, (this.failureCounts.get(serviceId) ?? 0) + 1);
      this.recoveryCounts.set(serviceId, 0);
    } else {
      this.recoveryCounts.set(serviceId, (this.recoveryCounts.get(serviceId) ?? 0) + 1);
      this.failureCounts.set(serviceId, 0);
    }

    // Determine effective status based on thresholds
    let effectiveStatus = status;
    if (status === 'down' && (this.failureCounts.get(serviceId) ?? 0) < HealthService.FAILURE_THRESHOLD) {
      effectiveStatus = previousStatus; // Don't flip to down until threshold met
    }
    if (previousStatus === 'down' && status === 'up' && (this.recoveryCounts.get(serviceId) ?? 0) < HealthService.RECOVERY_THRESHOLD) {
      effectiveStatus = 'down'; // Don't flip to up until recovery threshold met
    }

    // Record metric
    await this.metricsService.recordMetric(serviceId, effectiveStatus, responseMs, dependencies as unknown as Record<string, unknown>);

    // Get uptime summary
    const summary = await this.metricsService.getSummary(serviceId, '24h');

    // Update status map
    this.statuses.set(serviceId, {
      serviceId,
      name,
      status: effectiveStatus,
      responseMs,
      uptimePct: summary.uptimePct,
      dependencies,
      lastChecked: new Date(),
    });

    // Emit events on state transitions
    if (previousStatus !== effectiveStatus) {
      if (effectiveStatus === 'down') {
        const alert = await this.metricsService.createAlert(serviceId, 'down', `${name} is unreachable`);
        this.eventEmitter.emit('service.alert', { serviceId, type: 'down', message: alert.message, timestamp: new Date() });
      } else if (effectiveStatus === 'degraded') {
        await this.metricsService.createAlert(serviceId, 'degraded', `${name} is degraded`);
        this.eventEmitter.emit('service.alert', { serviceId, type: 'degraded', message: `${name} is degraded`, timestamp: new Date() });
      } else if (previousStatus === 'down' && effectiveStatus === 'up') {
        await this.metricsService.resolveAlerts(serviceId);
        this.eventEmitter.emit('service.alert', { serviceId, type: 'recovered', message: `${name} recovered`, timestamp: new Date() });
      }
    }

    // Always emit status update
    this.eventEmitter.emit('service.status', { serviceId, status: effectiveStatus, responseMs, timestamp: new Date() });
  }

  getAllStatuses(): ServiceStatus[] {
    return Array.from(this.statuses.values());
  }

  getStatus(serviceId: string): ServiceStatus | undefined {
    return this.statuses.get(serviceId);
  }
}
```

- [ ] **Step 3: Write test for HealthService**

```typescript
import { Test } from '@nestjs/testing';
import { EventEmitter2 } from '@nestjs/event-emitter';
import { HealthService } from './health.service.js';
import { HealthChecker } from './health-checker.js';
import { DiscoveryService } from '../discovery/discovery.service.js';
import { MetricsService } from '../metrics/metrics.service.js';

describe('HealthService', () => {
  let service: HealthService;
  const mockDiscovery = { getServices: jest.fn() };
  const mockMetrics = {
    recordMetric: jest.fn(),
    getSummary: jest.fn().mockResolvedValue({ avgMs: 50, p95Ms: 100, uptimePct: 99.5 }),
    createAlert: jest.fn().mockResolvedValue({ id: 1, message: 'test' }),
    resolveAlerts: jest.fn(),
  };
  const mockChecker = { check: jest.fn() };
  const mockEmitter = { emit: jest.fn() };

  beforeEach(async () => {
    const module = await Test.createTestingModule({
      providers: [
        HealthService,
        { provide: DiscoveryService, useValue: mockDiscovery },
        { provide: MetricsService, useValue: mockMetrics },
        { provide: HealthChecker, useValue: mockChecker },
        { provide: EventEmitter2, useValue: mockEmitter },
      ],
    }).compile();

    service = module.get(HealthService);
    jest.clearAllMocks();
  });

  it('polls all discovered services and records metrics', async () => {
    mockDiscovery.getServices.mockReturnValue([
      { id: 'cart-service', name: 'Cart Service', url: 'http://cart-service:8084', healthPath: '/health', routes: [] },
    ]);
    mockChecker.check.mockResolvedValue({
      serviceId: 'cart-service',
      status: 'up',
      responseMs: 45,
      dependencies: {},
    });

    await service.pollAll();

    expect(mockMetrics.recordMetric).toHaveBeenCalledWith('cart-service', 'up', 45, {});
    expect(service.getAllStatuses()).toHaveLength(1);
    expect(service.getAllStatuses()[0].status).toBe('up');
  });

  it('emits alert after 3 consecutive failures', async () => {
    mockDiscovery.getServices.mockReturnValue([
      { id: 'x', name: 'X', url: 'http://x:1', healthPath: '/health', routes: [] },
    ]);
    mockChecker.check.mockResolvedValue({ serviceId: 'x', status: 'down', responseMs: 5000, dependencies: {} });

    await service.pollAll(); // failure 1
    await service.pollAll(); // failure 2
    await service.pollAll(); // failure 3 — triggers alert

    expect(mockMetrics.createAlert).toHaveBeenCalledWith('x', 'down', 'X is unreachable');
  });

  it('skips polling when no services discovered', async () => {
    mockDiscovery.getServices.mockReturnValue([]);
    await service.pollAll();
    expect(mockChecker.check).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 4: Run tests**

Run: `cd services/monitoring-service-v2 && npx jest src/health/health.service.spec.ts --no-coverage`
Expected: 3 tests pass

- [ ] **Step 5: Create health.controller.ts**

```typescript
import { Controller, Get, Param, Query, NotFoundException } from '@nestjs/common';
import { Roles } from '../auth/roles.decorator.js';
import { HealthService } from './health.service.js';
import { MetricsService } from '../metrics/metrics.service.js';
import { DiscoveryService } from '../discovery/discovery.service.js';

@Controller('monitoring')
@Roles('admin')
export class HealthController {
  constructor(
    private readonly healthService: HealthService,
    private readonly metricsService: MetricsService,
    private readonly discoveryService: DiscoveryService,
  ) {}

  @Get('services')
  getServices() {
    return this.healthService.getAllStatuses();
  }

  @Get('services/:id/history')
  async getHistory(
    @Param('id') id: string,
    @Query('period') period: '1h' | '24h' | '7d' = '24h',
  ) {
    const svc = this.discoveryService.getServiceById(id);
    if (!svc) throw new NotFoundException('Service not found');
    return this.metricsService.getHistory(id, period);
  }

  @Get('services/:id/dependencies')
  getDependencies(@Param('id') id: string) {
    const status = this.healthService.getStatus(id);
    if (!status) throw new NotFoundException('Service not found');
    return { serviceId: id, dependencies: status.dependencies };
  }

  @Get('alerts')
  getAlerts() {
    return this.metricsService.getActiveAlerts();
  }

  @Get('alerts/history')
  getAlertHistory() {
    return this.metricsService.getAlertHistory(7);
  }
}
```

- [ ] **Step 6: Create health.module.ts**

```typescript
import { Module } from '@nestjs/common';
import { HealthChecker } from './health-checker.js';
import { HealthService } from './health.service.js';
import { HealthController } from './health.controller.js';
import { DiscoveryModule } from '../discovery/discovery.module.js';
import { MetricsModule } from '../metrics/metrics.module.js';

@Module({
  imports: [DiscoveryModule, MetricsModule],
  controllers: [HealthController],
  providers: [HealthChecker, HealthService],
  exports: [HealthService],
})
export class HealthModule {}
```

- [ ] **Step 7: Add EventEmitterModule and HealthModule to AppModule**

Add `@nestjs/event-emitter` to imports in `app.module.ts`:

```typescript
import { EventEmitterModule } from '@nestjs/event-emitter';
// ... in imports array:
EventEmitterModule.forRoot(),
HealthModule,
```

Also add `"@nestjs/event-emitter": "^3.1.0"` to `package.json` dependencies and run `npm install`.

- [ ] **Step 8: Verify build**

Run: `cd services/monitoring-service-v2 && npm run build`
Expected: Compiles with no errors

- [ ] **Step 9: Commit**

```bash
git add services/monitoring-service-v2/src/health/ services/monitoring-service-v2/package.json services/monitoring-service-v2/package-lock.json
git commit -m "feat(monitoring): health module with polling, state machine, and alerts"
```

---

## Task 7: WebSocket Gateway — Real-time Push

**Files:**
- Create: `services/monitoring-service-v2/src/gateway/monitoring.gateway.ts`
- Create: `services/monitoring-service-v2/src/gateway/gateway.module.ts`
- Test: `services/monitoring-service-v2/src/gateway/monitoring.gateway.spec.ts`

- [ ] **Step 1: Create monitoring.gateway.ts**

```typescript
import {
  WebSocketGateway,
  WebSocketServer,
  OnGatewayConnection,
  OnGatewayDisconnect,
} from '@nestjs/websockets';
import { Server, Socket } from 'socket.io';
import { Logger } from '@nestjs/common';
import { OnEvent } from '@nestjs/event-emitter';
import * as jwt from 'jsonwebtoken';
import * as jwksRsa from 'jwks-rsa';
import { ConfigService } from '@nestjs/config';

@WebSocketGateway({
  namespace: '/ws/monitoring',
  cors: {
    origin: (process.env.CORS_ORIGINS ?? 'http://localhost:8096,http://localhost:3000').split(','),
    credentials: true,
  },
})
export class MonitoringGateway implements OnGatewayConnection, OnGatewayDisconnect {
  @WebSocketServer()
  server!: Server;

  private readonly logger = new Logger(MonitoringGateway.name);
  private readonly jwksClient: jwksRsa.JwksClient;
  private readonly adminRole: string;

  constructor(private readonly config: ConfigService) {
    const jwkSetUri = this.config.get<string>(
      'app.keycloak.jwkSetUri',
      'http://keycloak:8080/realms/vnshop/protocol/openid-connect/certs',
    );
    this.adminRole = this.config.get<string>('app.keycloak.adminRole', 'admin');

    const factory = (jwksRsa as unknown as { default?: typeof jwksRsa }).default ?? jwksRsa;
    this.jwksClient = (factory as typeof jwksRsa)({
      jwksUri: jwkSetUri,
      cache: true,
      rateLimit: true,
    });
  }

  async handleConnection(client: Socket): Promise<void> {
    try {
      const token = (client.handshake.auth?.token ?? client.handshake.query['token']) as string | undefined;
      if (!token) {
        client.disconnect(true);
        return;
      }

      const payload = await this.verifyToken(token);
      if (!payload) {
        client.disconnect(true);
        return;
      }

      const roles = (payload as Record<string, unknown>).realm_access as { roles?: string[] } | undefined;
      if (!roles?.roles?.includes(this.adminRole)) {
        client.disconnect(true);
        return;
      }

      this.logger.log(`Client connected: ${client.id}`);
    } catch {
      client.disconnect(true);
    }
  }

  handleDisconnect(client: Socket): void {
    this.logger.log(`Client disconnected: ${client.id}`);
  }

  @OnEvent('service.status')
  handleServiceStatus(payload: { serviceId: string; status: string; responseMs: number; timestamp: Date }) {
    this.server?.emit('service:status', payload);
  }

  @OnEvent('service.alert')
  handleServiceAlert(payload: { serviceId: string; type: string; message: string; timestamp: Date }) {
    this.server?.emit('service:alert', payload);
  }

  private async verifyToken(token: string): Promise<Record<string, unknown> | null> {
    return new Promise((resolve) => {
      const getKey = (header: jwt.JwtHeader, callback: jwt.SigningKeyCallback) => {
        this.jwksClient.getSigningKey(header.kid, (err, key) => {
          if (err || !key) return callback(err ?? new Error('No key'));
          callback(null, key.getPublicKey());
        });
      };

      jwt.verify(token, getKey, { algorithms: ['RS256'] }, (err, decoded) => {
        if (err) return resolve(null);
        resolve(decoded as Record<string, unknown>);
      });
    });
  }
}
```

- [ ] **Step 2: Write test for MonitoringGateway**

```typescript
import { MonitoringGateway } from './monitoring.gateway.js';
import { ConfigService } from '@nestjs/config';

jest.mock('jwks-rsa', () => () => ({
  getSigningKey: jest.fn(),
}));

describe('MonitoringGateway', () => {
  let gateway: MonitoringGateway;

  beforeEach(() => {
    const config = {
      get: (key: string, def: string) => def,
    } as unknown as ConfigService;
    gateway = new MonitoringGateway(config);
    gateway.server = { emit: jest.fn() } as unknown as any;
  });

  it('emits service:status on event', () => {
    const payload = { serviceId: 'x', status: 'up', responseMs: 50, timestamp: new Date() };
    gateway.handleServiceStatus(payload);
    expect(gateway.server.emit).toHaveBeenCalledWith('service:status', payload);
  });

  it('emits service:alert on event', () => {
    const payload = { serviceId: 'x', type: 'down', message: 'down', timestamp: new Date() };
    gateway.handleServiceAlert(payload);
    expect(gateway.server.emit).toHaveBeenCalledWith('service:alert', payload);
  });

  it('disconnects client with no token', async () => {
    const client = {
      handshake: { auth: {}, query: {} },
      disconnect: jest.fn(),
      id: 'test',
    } as unknown as any;

    await gateway.handleConnection(client);
    expect(client.disconnect).toHaveBeenCalledWith(true);
  });
});
```

- [ ] **Step 3: Run tests**

Run: `cd services/monitoring-service-v2 && npx jest src/gateway/monitoring.gateway.spec.ts --no-coverage`
Expected: 3 tests pass

- [ ] **Step 4: Create gateway.module.ts**

```typescript
import { Module } from '@nestjs/common';
import { MonitoringGateway } from './monitoring.gateway.js';

@Module({
  providers: [MonitoringGateway],
  exports: [MonitoringGateway],
})
export class MonitoringGatewayModule {}
```

- [ ] **Step 5: Register MonitoringGatewayModule in AppModule**

Add `MonitoringGatewayModule` to the `imports` array in `src/app.module.ts`.

Also add `"jsonwebtoken": "^9.0.2"` and `"@types/jsonwebtoken": "^9.0.7"` to package.json and run `npm install`.

- [ ] **Step 6: Verify build**

Run: `cd services/monitoring-service-v2 && npm run build`
Expected: Compiles with no errors

- [ ] **Step 7: Commit**

```bash
git add services/monitoring-service-v2/src/gateway/
git commit -m "feat(monitoring): WebSocket gateway with JWT auth and real-time event push"
```

---

## Task 8: Playground Module — Proxy Test Requests

**Files:**
- Create: `services/monitoring-service-v2/src/playground/playground.service.ts`
- Create: `services/monitoring-service-v2/src/playground/playground.controller.ts`
- Create: `services/monitoring-service-v2/src/playground/playground.module.ts`
- Test: `services/monitoring-service-v2/src/playground/playground.service.spec.ts`

- [ ] **Step 1: Create playground.service.ts**

```typescript
import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import axios, { AxiosResponse, Method } from 'axios';

export interface TestRequest {
  method: string;
  path: string;
  headers?: Record<string, string>;
  body?: unknown;
  queryParams?: Record<string, string>;
}

export interface TestResponse {
  status: number;
  statusText: string;
  headers: Record<string, string>;
  body: unknown;
  timeMs: number;
}

@Injectable()
export class PlaygroundService {
  private readonly logger = new Logger(PlaygroundService.name);
  private readonly gatewayUrl: string;

  constructor(private readonly config: ConfigService) {
    this.gatewayUrl = this.config.get<string>('app.gatewayUrl', 'http://localhost:8080');
  }

  async executeRequest(req: TestRequest, authToken?: string): Promise<TestResponse> {
    const url = `${this.gatewayUrl}${req.path}`;
    const headers: Record<string, string> = { ...req.headers };
    if (authToken) {
      headers['Authorization'] = `Bearer ${authToken}`;
    }

    const start = Date.now();
    try {
      const response: AxiosResponse = await axios({
        method: req.method.toLowerCase() as Method,
        url,
        headers,
        data: req.body,
        params: req.queryParams,
        timeout: 30000,
        validateStatus: () => true,
      });

      return {
        status: response.status,
        statusText: response.statusText,
        headers: response.headers as Record<string, string>,
        body: response.data,
        timeMs: Date.now() - start,
      };
    } catch (error) {
      return {
        status: 0,
        statusText: (error as Error).message,
        headers: {},
        body: null,
        timeMs: Date.now() - start,
      };
    }
  }
}
```

- [ ] **Step 2: Write test for PlaygroundService**

```typescript
import { Test } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { PlaygroundService } from './playground.service.js';
import axios from 'axios';

jest.mock('axios');
const mockedAxios = axios as jest.MockedFunction<typeof axios>;

describe('PlaygroundService', () => {
  let service: PlaygroundService;

  beforeEach(async () => {
    const module = await Test.createTestingModule({
      providers: [
        PlaygroundService,
        { provide: ConfigService, useValue: { get: () => 'http://localhost:8080' } },
      ],
    }).compile();

    service = module.get(PlaygroundService);
    jest.clearAllMocks();
  });

  it('proxies request to gateway and returns response', async () => {
    mockedAxios.mockResolvedValue({
      status: 200,
      statusText: 'OK',
      headers: { 'content-type': 'application/json' },
      data: { items: [] },
    });

    const result = await service.executeRequest({
      method: 'GET',
      path: '/products',
      queryParams: { page: '1' },
    }, 'test-token');

    expect(mockedAxios).toHaveBeenCalledWith(expect.objectContaining({
      method: 'get',
      url: 'http://localhost:8080/products',
      headers: expect.objectContaining({ Authorization: 'Bearer test-token' }),
      params: { page: '1' },
    }));
    expect(result.status).toBe(200);
    expect(result.body).toEqual({ items: [] });
    expect(result.timeMs).toBeGreaterThanOrEqual(0);
  });

  it('returns error response on network failure', async () => {
    mockedAxios.mockRejectedValue(new Error('ECONNREFUSED'));

    const result = await service.executeRequest({ method: 'GET', path: '/cart' });

    expect(result.status).toBe(0);
    expect(result.statusText).toBe('ECONNREFUSED');
  });
});
```

- [ ] **Step 3: Run tests**

Run: `cd services/monitoring-service-v2 && npx jest src/playground/playground.service.spec.ts --no-coverage`
Expected: 2 tests pass

- [ ] **Step 4: Create playground.controller.ts**

```typescript
import { Controller, Post, Param, Body, Req, NotFoundException } from '@nestjs/common';
import { Request } from 'express';
import { Roles } from '../auth/roles.decorator.js';
import { PlaygroundService, TestRequest } from './playground.service.js';
import { DiscoveryService } from '../discovery/discovery.service.js';

@Controller('monitoring')
@Roles('admin')
export class PlaygroundController {
  constructor(
    private readonly playgroundService: PlaygroundService,
    private readonly discoveryService: DiscoveryService,
  ) {}

  @Post('endpoints/:id/test')
  async testEndpoint(
    @Param('id') id: string,
    @Body() body: TestRequest,
    @Req() req: Request,
  ) {
    const endpoint = this.discoveryService.getEndpointById(id);
    if (!endpoint) throw new NotFoundException('Endpoint not found');

    const authHeader = req.headers.authorization;
    const token = authHeader?.replace('Bearer ', '');

    return this.playgroundService.executeRequest(body, token);
  }
}
```

- [ ] **Step 5: Create playground.module.ts**

```typescript
import { Module } from '@nestjs/common';
import { PlaygroundService } from './playground.service.js';
import { PlaygroundController } from './playground.controller.js';
import { DiscoveryModule } from '../discovery/discovery.module.js';

@Module({
  imports: [DiscoveryModule],
  controllers: [PlaygroundController],
  providers: [PlaygroundService],
})
export class PlaygroundModule {}
```

- [ ] **Step 6: Register PlaygroundModule in AppModule**

Add `PlaygroundModule` to the `imports` array in `src/app.module.ts`.

- [ ] **Step 7: Verify build**

Run: `cd services/monitoring-service-v2 && npm run build`
Expected: Compiles with no errors

- [ ] **Step 8: Commit**

```bash
git add services/monitoring-service-v2/src/playground/
git commit -m "feat(monitoring): playground module for proxying test requests"
```

---

## Task 9: Static Dashboard — Auth & App Shell

**Files:**
- Create: `services/monitoring-service-v2/public/index.html`
- Create: `services/monitoring-service-v2/public/css/styles.css`
- Create: `services/monitoring-service-v2/public/js/auth.js`
- Create: `services/monitoring-service-v2/public/js/app.js`

- [ ] **Step 1: Create index.html**

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>VNShop Monitoring</title>
  <link rel="stylesheet" href="/css/styles.css">
  <script src="https://cdn.socket.io/4.8.3/socket.io.min.js"></script>
</head>
<body>
  <div id="app">
    <header>
      <h1>VNShop Monitoring</h1>
      <nav>
        <button class="tab active" data-view="health">Health</button>
        <button class="tab" data-view="playground">API Playground</button>
      </nav>
      <button id="logout-btn" class="btn-logout">Logout</button>
    </header>

    <div id="alert-banner" class="alert-banner hidden"></div>

    <main>
      <section id="view-health" class="view active"></section>
      <section id="view-playground" class="view"></section>
    </main>
  </div>

  <div id="login-screen" class="login-screen hidden">
    <p>Redirecting to login...</p>
  </div>

  <script src="/js/auth.js"></script>
  <script src="/js/charts.js"></script>
  <script src="/js/health.js"></script>
  <script src="/js/playground.js"></script>
  <script src="/js/app.js"></script>
</body>
</html>
```

- [ ] **Step 2: Create css/styles.css**

```css
:root {
  --bg: #1a1a2e;
  --surface: #16213e;
  --surface-hover: #1a2744;
  --text: #e0e0e0;
  --text-muted: #8892a4;
  --accent: #4fc3f7;
  --success: #66bb6a;
  --warning: #ffa726;
  --danger: #ef5350;
  --border: #2a3a5e;
  --radius: 8px;
}

* { margin: 0; padding: 0; box-sizing: border-box; }

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: var(--bg);
  color: var(--text);
  min-height: 100vh;
}

header {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem 2rem;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
}

header h1 { font-size: 1.2rem; color: var(--accent); }
header nav { flex: 1; display: flex; gap: 0.5rem; }

.tab {
  padding: 0.5rem 1rem;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  border-radius: var(--radius);
  font-size: 0.9rem;
}
.tab.active { background: var(--bg); color: var(--accent); }

.btn-logout {
  padding: 0.4rem 0.8rem;
  border: 1px solid var(--danger);
  background: transparent;
  color: var(--danger);
  border-radius: var(--radius);
  cursor: pointer;
  font-size: 0.8rem;
}

main { padding: 2rem; }

.view { display: none; }
.view.active { display: block; }

.alert-banner {
  padding: 0.8rem 2rem;
  background: var(--danger);
  color: white;
  font-weight: 500;
  animation: slideDown 0.3s ease;
}
.alert-banner.hidden { display: none; }

@keyframes slideDown { from { transform: translateY(-100%); } to { transform: translateY(0); } }

/* Service cards grid */
.services-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1rem;
}

.service-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 1.2rem;
  cursor: pointer;
  transition: border-color 0.2s;
}
.service-card:hover { border-color: var(--accent); }

.service-card .status-dot {
  width: 10px; height: 10px;
  border-radius: 50%;
  display: inline-block;
  margin-right: 0.5rem;
}
.status-dot.up { background: var(--success); }
.status-dot.degraded { background: var(--warning); }
.status-dot.down { background: var(--danger); }

.service-card .name { font-weight: 600; font-size: 1rem; }
.service-card .meta { color: var(--text-muted); font-size: 0.85rem; margin-top: 0.5rem; }

/* Detail panel */
.detail-panel {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 1.5rem;
  margin-top: 1rem;
}

/* Playground */
.playground-layout { display: grid; grid-template-columns: 280px 1fr; gap: 1rem; }

.endpoint-list {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 1rem;
  max-height: 80vh;
  overflow-y: auto;
}

.endpoint-item {
  padding: 0.5rem;
  cursor: pointer;
  border-radius: 4px;
  font-size: 0.85rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.endpoint-item:hover { background: var(--surface-hover); }

.method-badge {
  font-size: 0.7rem;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 3px;
  text-transform: uppercase;
}
.method-badge.get { background: #1b5e20; color: #a5d6a7; }
.method-badge.post { background: #e65100; color: #ffcc80; }
.method-badge.put { background: #1565c0; color: #90caf9; }
.method-badge.delete { background: #b71c1c; color: #ef9a9a; }

.request-panel {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 1.5rem;
}

.request-panel textarea {
  width: 100%;
  min-height: 150px;
  background: var(--bg);
  color: var(--text);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 0.8rem;
  font-family: 'Fira Code', monospace;
  font-size: 0.85rem;
  resize: vertical;
}

.btn-send {
  margin-top: 1rem;
  padding: 0.6rem 1.5rem;
  background: var(--accent);
  color: #000;
  border: none;
  border-radius: var(--radius);
  font-weight: 600;
  cursor: pointer;
}

.response-panel {
  margin-top: 1rem;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 1rem;
}

.response-panel pre {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 0.85rem;
  font-family: 'Fira Code', monospace;
}

.login-screen {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  color: var(--text-muted);
}
.login-screen.hidden { display: none; }

.hidden { display: none !important; }
```

- [ ] **Step 3: Create js/auth.js**

```javascript
const Auth = (() => {
  const KEYCLOAK_URL = window.location.hostname === 'localhost'
    ? 'http://localhost:9090'
    : 'http://keycloak:8085';
  const REALM = 'vnshop';
  const CLIENT_ID = 'monitoring-dashboard';
  const REDIRECT_URI = window.location.origin + '/';
  const TOKEN_KEY = 'monitoring_token';
  const REFRESH_KEY = 'monitoring_refresh';

  function getToken() {
    return sessionStorage.getItem(TOKEN_KEY);
  }

  function setTokens(access, refresh) {
    sessionStorage.setItem(TOKEN_KEY, access);
    if (refresh) sessionStorage.setItem(REFRESH_KEY, refresh);
  }

  function clearTokens() {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(REFRESH_KEY);
  }

  function parseJwt(token) {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(base64));
  }

  function isExpired(token) {
    try {
      const payload = parseJwt(token);
      return Date.now() >= payload.exp * 1000;
    } catch { return true; }
  }

  function hasAdminRole(token) {
    try {
      const payload = parseJwt(token);
      const roles = payload.realm_access?.roles ?? [];
      return roles.includes('admin');
    } catch { return false; }
  }

  function generateCodeVerifier() {
    const array = new Uint8Array(32);
    crypto.getRandomValues(array);
    return btoa(String.fromCharCode(...array)).replace(/[+/=]/g, '').slice(0, 43);
  }

  async function generateCodeChallenge(verifier) {
    const encoder = new TextEncoder();
    const data = encoder.encode(verifier);
    const digest = await crypto.subtle.digest('SHA-256', data);
    return btoa(String.fromCharCode(...new Uint8Array(digest)))
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }

  async function login() {
    const verifier = generateCodeVerifier();
    sessionStorage.setItem('pkce_verifier', verifier);
    const challenge = await generateCodeChallenge(verifier);

    const authUrl = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/auth?` +
      `client_id=${CLIENT_ID}&response_type=code&scope=openid&` +
      `redirect_uri=${encodeURIComponent(REDIRECT_URI)}&` +
      `code_challenge=${challenge}&code_challenge_method=S256`;

    window.location.href = authUrl;
  }

  async function handleCallback() {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    if (!code) return false;

    const verifier = sessionStorage.getItem('pkce_verifier');
    if (!verifier) return false;

    const tokenUrl = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`;
    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: CLIENT_ID,
      code,
      redirect_uri: REDIRECT_URI,
      code_verifier: verifier,
    });

    try {
      const res = await fetch(tokenUrl, { method: 'POST', body });
      const data = await res.json();
      if (data.access_token) {
        setTokens(data.access_token, data.refresh_token);
        sessionStorage.removeItem('pkce_verifier');
        window.history.replaceState({}, '', '/');
        return true;
      }
    } catch (e) { console.error('Token exchange failed:', e); }
    return false;
  }

  async function refreshToken() {
    const refresh = sessionStorage.getItem(REFRESH_KEY);
    if (!refresh) return false;

    const tokenUrl = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`;
    const body = new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: CLIENT_ID,
      refresh_token: refresh,
    });

    try {
      const res = await fetch(tokenUrl, { method: 'POST', body });
      const data = await res.json();
      if (data.access_token) {
        setTokens(data.access_token, data.refresh_token);
        return true;
      }
    } catch { /* fall through */ }
    return false;
  }

  function logout() {
    clearTokens();
    const logoutUrl = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/logout?` +
      `client_id=${CLIENT_ID}&post_logout_redirect_uri=${encodeURIComponent(REDIRECT_URI)}`;
    window.location.href = logoutUrl;
  }

  async function init() {
    // Check for callback code
    if (window.location.search.includes('code=')) {
      const ok = await handleCallback();
      if (ok) return true;
    }

    const token = getToken();
    if (!token || isExpired(token)) {
      const refreshed = await refreshToken();
      if (!refreshed) {
        login();
        return false;
      }
    }

    if (!hasAdminRole(getToken())) {
      alert('Access denied: admin role required');
      clearTokens();
      login();
      return false;
    }

    // Schedule refresh before expiry
    const payload = parseJwt(getToken());
    const expiresIn = (payload.exp * 1000) - Date.now() - 30000;
    if (expiresIn > 0) {
      setTimeout(() => refreshToken(), expiresIn);
    }

    return true;
  }

  return { init, getToken, logout };
})();
```

- [ ] **Step 4: Create js/app.js**

```javascript
(async () => {
  const authenticated = await Auth.init();
  if (!authenticated) {
    document.getElementById('login-screen').classList.remove('hidden');
    document.getElementById('app').classList.add('hidden');
    return;
  }

  document.getElementById('app').classList.remove('hidden');
  document.getElementById('login-screen').classList.add('hidden');

  // Tab navigation
  document.querySelectorAll('.tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.tab').forEach((t) => t.classList.remove('active'));
      document.querySelectorAll('.view').forEach((v) => v.classList.remove('active'));
      tab.classList.add('active');
      document.getElementById(`view-${tab.dataset.view}`).classList.add('active');
    });
  });

  // Logout
  document.getElementById('logout-btn').addEventListener('click', () => Auth.logout());

  // WebSocket connection
  const socket = io('/ws/monitoring', {
    auth: { token: Auth.getToken() },
    transports: ['websocket'],
  });

  socket.on('service:status', (data) => Health.updateService(data));
  socket.on('service:alert', (data) => showAlert(data));
  socket.on('disconnect', () => console.warn('WebSocket disconnected'));

  // Alert banner
  function showAlert(data) {
    const banner = document.getElementById('alert-banner');
    banner.textContent = `⚠ ${data.message}`;
    banner.classList.remove('hidden');
    if (data.type === 'recovered') {
      banner.style.background = 'var(--success)';
      setTimeout(() => banner.classList.add('hidden'), 5000);
    } else {
      banner.style.background = 'var(--danger)';
    }
  }

  // Initialize views
  await Health.init();
  Playground.init();
})();
```

- [ ] **Step 5: Commit**

```bash
git add services/monitoring-service-v2/public/
git commit -m "feat(monitoring): static dashboard shell with auth, styles, and app entry"
```

---

## Task 10: Dashboard — Health View & Charts

**Files:**
- Create: `services/monitoring-service-v2/public/js/charts.js`
- Create: `services/monitoring-service-v2/public/js/health.js`

- [ ] **Step 1: Create js/charts.js**

```javascript
const Charts = (() => {
  function sparkline(data, width = 200, height = 40) {
    if (!data || data.length === 0) return '';
    const max = Math.max(...data, 1);
    const min = Math.min(...data, 0);
    const range = max - min || 1;
    const step = width / Math.max(data.length - 1, 1);

    const points = data.map((val, i) => {
      const x = i * step;
      const y = height - ((val - min) / range) * height;
      return `${x},${y}`;
    }).join(' ');

    return `<svg width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" xmlns="http://www.w3.org/2000/svg">
      <polyline fill="none" stroke="var(--accent)" stroke-width="1.5" points="${points}"/>
    </svg>`;
  }

  function uptimeBar(pct) {
    const color = pct >= 99 ? 'var(--success)' : pct >= 95 ? 'var(--warning)' : 'var(--danger)';
    return `<div style="background:var(--border);border-radius:4px;height:6px;width:100%;margin-top:4px;">
      <div style="background:${color};border-radius:4px;height:100%;width:${Math.min(pct, 100)}%;"></div>
    </div>`;
  }

  return { sparkline, uptimeBar };
})();
```

- [ ] **Step 2: Create js/health.js**

```javascript
const Health = (() => {
  let services = [];
  let selectedService = null;

  async function init() {
    await fetchServices();
    render();
  }

  async function fetchServices() {
    try {
      const res = await fetch('/monitoring/services', {
        headers: { Authorization: `Bearer ${Auth.getToken()}` },
      });
      if (res.ok) services = await res.json();
    } catch (e) { console.error('Failed to fetch services:', e); }
  }

  function updateService(data) {
    const idx = services.findIndex((s) => s.serviceId === data.serviceId);
    if (idx >= 0) {
      services[idx] = { ...services[idx], ...data, lastChecked: new Date() };
    } else {
      services.push({ ...data, name: data.serviceId, lastChecked: new Date() });
    }
    render();
  }

  function render() {
    const container = document.getElementById('view-health');
    container.innerHTML = `
      <div class="services-grid">${services.map(renderCard).join('')}</div>
      ${selectedService ? renderDetail() : ''}
    `;

    container.querySelectorAll('.service-card').forEach((card) => {
      card.addEventListener('click', () => {
        selectedService = card.dataset.id;
        render();
      });
    });
  }

  function renderCard(svc) {
    return `<div class="service-card" data-id="${svc.serviceId}">
      <div><span class="status-dot ${svc.status}"></span><span class="name">${svc.name || svc.serviceId}</span></div>
      <div class="meta">
        ${svc.responseMs != null ? `${svc.responseMs}ms` : '—'}
        &nbsp;·&nbsp;
        ${svc.uptimePct != null ? `${svc.uptimePct.toFixed(1)}% uptime` : '—'}
      </div>
      ${svc.uptimePct != null ? Charts.uptimeBar(svc.uptimePct) : ''}
    </div>`;
  }

  function renderDetail() {
    const svc = services.find((s) => s.serviceId === selectedService);
    if (!svc) return '';

    const deps = svc.dependencies
      ? Object.entries(svc.dependencies).map(([name, d]) =>
          `<li><span class="status-dot ${d.status === 'UP' ? 'up' : 'down'}"></span>${name}: ${d.status}</li>`
        ).join('')
      : '<li>No dependency info</li>';

    return `<div class="detail-panel">
      <h3>${svc.name || svc.serviceId}</h3>
      <p>Status: <strong>${svc.status}</strong> · Response: ${svc.responseMs ?? '—'}ms</p>
      <h4>Dependencies</h4>
      <ul>${deps}</ul>
      <h4>Response Time (last 24h)</h4>
      <div id="history-chart">Loading...</div>
      <button onclick="Health.closeDetail()" style="margin-top:1rem;" class="tab">Close</button>
    </div>`;
  }

  async function loadHistory(serviceId) {
    try {
      const res = await fetch(`/monitoring/services/${serviceId}/history?period=24h`, {
        headers: { Authorization: `Bearer ${Auth.getToken()}` },
      });
      if (res.ok) {
        const data = await res.json();
        const times = data.map((m) => m.responseMs ?? 0);
        const chartEl = document.getElementById('history-chart');
        if (chartEl) chartEl.innerHTML = Charts.sparkline(times, 600, 80);
      }
    } catch { /* ignore */ }
  }

  function closeDetail() {
    selectedService = null;
    render();
  }

  return { init, updateService, closeDetail };
})();
```

- [ ] **Step 3: Commit**

```bash
git add services/monitoring-service-v2/public/js/charts.js services/monitoring-service-v2/public/js/health.js
git commit -m "feat(monitoring): health dashboard view with service cards and sparklines"
```

---

## Task 11: Dashboard — API Playground View

**Files:**
- Create: `services/monitoring-service-v2/public/js/playground.js`

- [ ] **Step 1: Create js/playground.js**

```javascript
const Playground = (() => {
  let endpoints = [];
  let selectedEndpoint = null;
  let history = JSON.parse(localStorage.getItem('playground_history') || '[]');

  async function init() {
    await fetchEndpoints();
    render();
  }

  async function fetchEndpoints() {
    try {
      const res = await fetch('/monitoring/endpoints', {
        headers: { Authorization: `Bearer ${Auth.getToken()}` },
      });
      if (res.ok) endpoints = await res.json();
    } catch (e) { console.error('Failed to fetch endpoints:', e); }
  }

  function render() {
    const container = document.getElementById('view-playground');
    container.innerHTML = `<div class="playground-layout">
      <div class="endpoint-list">${renderSidebar()}</div>
      <div class="request-panel">${renderRequestPanel()}</div>
    </div>`;

    bindEvents(container);
  }

  function renderSidebar() {
    if (endpoints.length === 0) return '<p style="color:var(--text-muted)">No endpoints discovered</p>';

    return endpoints.map((group) => `
      <div class="endpoint-group">
        <h4 style="color:var(--accent);margin:0.5rem 0;font-size:0.8rem;">${group.service.name}</h4>
        ${group.endpoints.map((ep) => `
          <div class="endpoint-item" data-id="${ep.id}">
            <span class="method-badge ${ep.method.toLowerCase()}">${ep.method}</span>
            <span>${ep.path}</span>
          </div>
        `).join('')}
      </div>
    `).join('');
  }

  function renderRequestPanel() {
    if (!selectedEndpoint) {
      return '<p style="color:var(--text-muted)">Select an endpoint from the sidebar</p>';
    }

    const ep = findEndpoint(selectedEndpoint);
    const schemaHint = ep?.schema ? JSON.stringify(ep.schema, null, 2) : '{}';

    return `
      <h3><span class="method-badge ${ep.method.toLowerCase()}">${ep.method}</span> ${ep.path}</h3>
      ${ep.summary ? `<p style="color:var(--text-muted);margin:0.5rem 0;">${ep.summary}</p>` : ''}
      <label style="display:block;margin-top:1rem;font-size:0.85rem;color:var(--text-muted);">Request Body (JSON)</label>
      <textarea id="req-body" placeholder='${schemaHint}'></textarea>
      <label style="display:block;margin-top:0.5rem;font-size:0.85rem;color:var(--text-muted);">Query Params (key=value, one per line)</label>
      <textarea id="req-params" style="min-height:60px;" placeholder="page=1&#10;limit=10"></textarea>
      <button class="btn-send" id="btn-send">Send Request</button>
      <div id="response-area"></div>
      ${renderHistory()}
    `;
  }

  function renderHistory() {
    if (history.length === 0) return '';
    return `<h4 style="margin-top:1.5rem;color:var(--text-muted);">Recent Requests</h4>
      <div>${history.slice(0, 10).map((h, i) => `
        <div class="endpoint-item" data-history="${i}">
          <span class="method-badge ${h.method.toLowerCase()}">${h.method}</span>
          <span>${h.path}</span>
          <span style="margin-left:auto;color:var(--text-muted);font-size:0.75rem;">${h.status}</span>
        </div>
      `).join('')}</div>`;
  }

  function findEndpoint(id) {
    for (const group of endpoints) {
      const ep = group.endpoints.find((e) => e.id === id);
      if (ep) return ep;
    }
    return null;
  }

  function parseQueryParams(text) {
    const params = {};
    text.split('\n').filter(Boolean).forEach((line) => {
      const [key, ...rest] = line.split('=');
      if (key) params[key.trim()] = rest.join('=').trim();
    });
    return params;
  }

  async function sendRequest() {
    const ep = findEndpoint(selectedEndpoint);
    if (!ep) return;

    const bodyText = document.getElementById('req-body').value.trim();
    const paramsText = document.getElementById('req-params').value.trim();

    let body = undefined;
    if (bodyText) {
      try { body = JSON.parse(bodyText); }
      catch { alert('Invalid JSON in request body'); return; }
    }

    const queryParams = paramsText ? parseQueryParams(paramsText) : undefined;

    const responseArea = document.getElementById('response-area');
    responseArea.innerHTML = '<p style="color:var(--text-muted)">Sending...</p>';

    try {
      const res = await fetch(`/monitoring/endpoints/${encodeURIComponent(ep.id)}/test`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${Auth.getToken()}`,
        },
        body: JSON.stringify({ method: ep.method, path: ep.path, body, queryParams }),
      });

      const data = await res.json();
      responseArea.innerHTML = `<div class="response-panel">
        <p><strong>${data.status}</strong> ${data.statusText} · ${data.timeMs}ms</p>
        <pre>${JSON.stringify(data.body, null, 2)}</pre>
      </div>`;

      // Save to history
      history.unshift({ method: ep.method, path: ep.path, status: data.status, time: Date.now() });
      history = history.slice(0, 20);
      localStorage.setItem('playground_history', JSON.stringify(history));
    } catch (e) {
      responseArea.innerHTML = `<div class="response-panel"><p style="color:var(--danger)">Error: ${e.message}</p></div>`;
    }
  }

  function bindEvents(container) {
    container.querySelectorAll('.endpoint-item[data-id]').forEach((el) => {
      el.addEventListener('click', () => {
        selectedEndpoint = el.dataset.id;
        render();
      });
    });

    const sendBtn = container.querySelector('#btn-send');
    if (sendBtn) sendBtn.addEventListener('click', sendRequest);
  }

  return { init };
})();
```

- [ ] **Step 2: Commit**

```bash
git add services/monitoring-service-v2/public/js/playground.js
git commit -m "feat(monitoring): API playground view with endpoint browser and request proxy"
```

---

## Task 12: Gateway Integration — Route & Actuator Config

**Files:**
- Modify: `services/api-gateway/src/main/resources/application.yml`
- Modify: `services/api-gateway/src/main/java/com/vnshop/apigateway/infrastructure/route/RouteConfig.java`

- [ ] **Step 1: Update application.yml to expose gateway actuator**

In `services/api-gateway/src/main/resources/application.yml`, change:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

To:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,gateway
```

- [ ] **Step 2: Add monitoring route to RouteConfig.java**

Add a new constructor parameter:

```java
@Value("${vnshop.routes.monitoring-service:http://monitoring-service-v2:8096}") String monitoringServiceUri
```

Add the route before the admin catch-all route:

```java
.route("monitoring", route -> route.path("/monitoring/**")
    .filters(filters -> resilient(filters, "monitoring-service"))
    .uri(monitoringServiceUri))
```

- [ ] **Step 3: Verify gateway compiles**

Run: `cd services/api-gateway && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add services/api-gateway/
git commit -m "feat(gateway): expose actuator gateway endpoint and add monitoring route"
```

---

## Task 13: Docker Compose — Monitoring Service Container

**Files:**
- Create: `services/monitoring-service-v2/Dockerfile`
- Modify: `docker-compose.yml`

- [ ] **Step 1: Create Dockerfile**

```dockerfile
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:22-alpine
WORKDIR /app
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/public ./public
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./

ENV PORT=8096
EXPOSE 8096
CMD ["node", "dist/main.js"]
```

- [ ] **Step 2: Add monitoring-service-v2 to docker-compose.yml**

Add after the messaging-service block (or at the end of services):

```yaml
  monitoring-service-v2:
    profiles: ["apps"]
    build: ./services/monitoring-service-v2
    container_name: vnshop-monitoring
    ports:
      - "8096:8096"
    environment:
      PORT: 8096
      GATEWAY_URL: http://api-gateway:8080
      GATEWAY_ACTUATOR_URL: http://api-gateway:8080/actuator/gateway/routes
      TIMESCALE_HOST: timescaledb
      TIMESCALE_PORT: 5432
      TIMESCALE_DB: monitoring
      TIMESCALE_USER: monitoring
      TIMESCALE_PASSWORD: monitoring
      KEYCLOAK_ISSUER_URI: http://keycloak:8080/realms/vnshop
      KEYCLOAK_JWK_SET_URI: http://keycloak:8080/realms/vnshop/protocol/openid-connect/certs
      CORS_ORIGINS: http://localhost:8096,http://localhost:3000,http://localhost:5173
    depends_on:
      timescaledb:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8096/monitoring/services || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3
```

- [ ] **Step 3: Commit**

```bash
git add services/monitoring-service-v2/Dockerfile docker-compose.yml
git commit -m "feat(monitoring): Dockerfile and docker-compose integration"
```

---

## Task 14: End-to-End Verification

- [ ] **Step 1: Run all unit tests**

Run: `cd services/monitoring-service-v2 && npm test`
Expected: All tests pass (roles.guard, metrics.service, gateway-client, discovery.service, health.service, playground.service, monitoring.gateway)

- [ ] **Step 2: Build the service**

Run: `cd services/monitoring-service-v2 && npm run build`
Expected: Compiles with no errors

- [ ] **Step 3: Start infrastructure locally**

Run: `docker compose up timescaledb -d`
Expected: TimescaleDB healthy on port 5440

- [ ] **Step 4: Start the monitoring service**

Run: `cd services/monitoring-service-v2 && npm run start:dev`
Expected: Service starts on port 8096, logs "Monitoring service running on port 8096"

- [ ] **Step 5: Verify static dashboard is served**

Open: `http://localhost:8096` in browser
Expected: Redirects to Keycloak login (or shows dashboard if already authenticated)

- [ ] **Step 6: Verify discovery (with gateway running)**

If gateway is running, check: `GET http://localhost:8096/monitoring/services` (with admin JWT)
Expected: Returns list of discovered services with health status

- [ ] **Step 7: Final commit**

```bash
git add -A
git commit -m "feat(monitoring): complete monitoring service with dashboard, health polling, and API playground"
```
