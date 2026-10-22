# E0 Khởi tạo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dựng khung monorepo chạy được: Postgres + MinIO bằng docker-compose, Spring Boot (health + format lỗi chung), React+Vite+Antd (trang trống + proxy), CI.

**Architecture:** `backend/` Gradle (Groovy DSL, có wrapper) + Spring Boot 3.3, `frontend/` Vite React TS, `docker-compose.yml` ở gốc. FE proxy `/api` sang BE `:8080`.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Gradle 8.10, Flyway, Testcontainers, React 18, TypeScript 5, Vite 5, Vitest, Ant Design 5, TanStack Query 5.

**Spec:** `docs/superpowers/specs/2026-10-14-gia-pha-design.md`

## Global Constraints

- Java 21; Spring Boot 3.3.x; PostgreSQL 16; package gốc `vn.giapha`.
- Tiền tố API `/api/v1`; lỗi dạng `{code,message,details}`.
- Giao diện tiếng Việt; code/tên bảng tiếng Anh.
- FE chạy Chrome, Safari, màn hình 375 px.

## Review Focus

- Request tới `/api/v1/khong-ton-tai` phải trả JSON `{code:"NOT_FOUND"}` chứ không phải trang HTML lỗi.
- Body JSON sai cú pháp phải trả 400 `VALIDATION_FAILED`, không 500.
- `docker compose up` lần hai (volume đã có) không lỗi migration.

---

### Task 1: docker-compose và .gitignore

**Files:**
- Create: `docker-compose.yml`, `.gitignore`, `.env.example`

- [ ] **Step 1: Tạo `docker-compose.yml`**

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: giapha
      POSTGRES_USER: giapha
      POSTGRES_PASSWORD: giapha
    ports: ["5432:5432"]
    volumes: ["pgdata:/var/lib/postgresql/data"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U giapha"]
      interval: 5s
      retries: 10
  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports: ["9000:9000", "9001:9001"]
    volumes: ["miniodata:/data"]
volumes:
  pgdata:
  miniodata:
```

- [ ] **Step 2: Tạo `.gitignore`**

```
build/
.gradle/
node_modules/
dist/
.env
.idea/
.vscode/
.DS_Store
*.log
```

- [ ] **Step 3: Tạo `.env.example`**

```
DB_URL=jdbc:postgresql://localhost:5432/giapha
DB_USER=giapha
DB_PASSWORD=giapha
JWT_SECRET=change-me-change-me-change-me-change-me-32b
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=giapha
```

- [ ] **Step 4: Chạy thử**

Run (ở `src/Root`): `docker compose up -d && docker compose ps`
Expected: `postgres` trạng thái `healthy`, `minio` `running`.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml .gitignore .env.example
git commit -m "chore(E0): docker-compose postgres + minio"
```

---

### Task 2: Backend skeleton + lỗi chung

**Files:**
- Create: `backend/build.gradle`, `backend/settings.gradle`, `backend/gradlew`, `backend/gradlew.bat`, `backend/gradle/wrapper/gradle-wrapper.jar`, `backend/gradle/wrapper/gradle-wrapper.properties`
- Create: `backend/src/main/java/vn/giapha/GiaphaApplication.java`
- Create: `backend/src/main/java/vn/giapha/common/ApiError.java`
- Create: `backend/src/main/java/vn/giapha/common/ApiException.java`
- Create: `backend/src/main/java/vn/giapha/common/GlobalExceptionHandler.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/db/migration/V1__enable_extensions.sql`
- Create: `backend/src/test/java/vn/giapha/PostgresTestConfig.java`
- Create: `backend/src/test/java/vn/giapha/common/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces: `ApiException(HttpStatus status, String code, String message, Object details)`; `ApiError(String code, String message, Object details)` record; `PostgresTestConfig` (Testcontainers `@ServiceConnection`).

- [ ] **Step 1: Tạo `backend/settings.gradle` và `backend/build.gradle`**

`backend/settings.gradle`:
```groovy
rootProject.name = 'giapha'
```
`backend/build.gradle`:
```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'vn.giapha'
version = '0.0.1-SNAPSHOT'

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'
    runtimeOnly 'org.postgresql:postgresql'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
    implementation 'software.amazon.awssdk:s3:2.28.11'
    implementation 'org.apache.poi:poi-ooxml:5.3.0'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('jar') {
    enabled = false          // chỉ giữ bootJar: build/libs/giapha-0.0.1-SNAPSHOT.jar
}

tasks.named('test') {
    useJUnitPlatform()
    testLogging {
        events 'failed'
        exceptionFormat 'full'
    }
}
```
Tạo **gradle wrapper** (phiên bản cố định 8.10.2) rồi kiểm tra: (ở `backend`)
```bash
# cách 1: máy đã cài Gradle
gradle wrapper --gradle-version 8.10.2
# cách 2: không cài Gradle, dùng Docker
docker run --rm -v "$PWD":/w -w /w gradle:8.10.2-jdk21 gradle wrapper --gradle-version 8.10.2
chmod +x gradlew
./gradlew --version
```
Expected: in ra `Gradle 8.10.2`. Từ đây **mọi lệnh BE dùng `./gradlew`** (không cần cài Gradle). Lần chạy đầu tải phụ thuộc nên chậm.

- [ ] **Step 2: Tạo `GiaphaApplication.java`**

```java
package vn.giapha;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GiaphaApplication {
    public static void main(String[] args) {
        SpringApplication.run(GiaphaApplication.class, args);
    }
}
```

- [ ] **Step 3: Tạo `application.yml`**

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/giapha}
    username: ${DB_USER:giapha}
    password: ${DB_PASSWORD:giapha}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.jdbc.time_zone: UTC
  flyway:
    enabled: true
  jackson:
    default-property-inclusion: always
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 12MB
server:
  compression:
    enabled: true
    mime-types: application/json
    min-response-size: 1024
  forward-headers-strategy: framework
  error:
    include-message: never
app:
  jwt:
    secret: ${JWT_SECRET:change-me-change-me-change-me-change-me-32b}
    access-minutes: 15
    refresh-days: 14
  storage:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY:minioadmin}
    secret-key: ${MINIO_SECRET_KEY:minioadmin}
    bucket: ${MINIO_BUCKET:giapha}
management:
  endpoints.web.exposure.include: health
```

- [ ] **Step 4: Tạo `V1__enable_extensions.sql`**

```sql
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

- [ ] **Step 5: Viết test lỗi chung (failing)**

`PostgresTestConfig.java`:
```java
package vn.giapha;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));
    }
}
```

`GlobalExceptionHandlerTest.java`:
```java
package vn.giapha.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import vn.giapha.PostgresTestConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class GlobalExceptionHandlerTest {
    @Autowired MockMvc mvc;

    @Test
    @WithMockUser
    void unknownPathReturnsJsonNotFound() throws Exception {
        mvc.perform(get("/api/v1/khong-ton-tai"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser
    void malformedJsonReturnsValidationFailed() throws Exception {
        mvc.perform(post("/api/v1/khong-ton-tai").contentType(MediaType.APPLICATION_JSON).content("{bad"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
```

- [ ] **Step 6: Chạy test, thấy FAIL**

Run (ở `backend`): `./gradlew test --tests '*GlobalExceptionHandlerTest'`
Expected: FAIL (trả về trang lỗi mặc định, không có `$.code`). Cần Docker đang chạy cho Testcontainers.

- [ ] **Step 7: Viết triển khai**

`ApiError.java`:
```java
package vn.giapha.common;

public record ApiError(String code, String message, Object details) {}
```

`ApiException.java`:
```java
package vn.giapha.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Object details;

    public ApiException(HttpStatus status, String code, String message, Object details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Object details() { return details; }
}
```

`GlobalExceptionHandler.java`:
```java
package vn.giapha.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(new ApiError(e.code(), e.getMessage(), e.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "Dữ liệu không hợp lệ", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "Nội dung gửi lên không đọc được", null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", "Không tìm thấy", null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("FORBIDDEN", "Không đủ quyền", null));
    }
}
```

Spring Security mặc định cấu hình ở Step này sẽ chặn mọi request (401) — thêm tạm `SecurityConfig` sẽ được thay ở E3:

`backend/src/main/java/vn/giapha/common/TempSecurityConfig.java`:
```java
package vn.giapha.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Tạm thời cho phép tất cả; E3 Task 5 XOÁ file này và thay bằng SecurityConfig thật. */
@Configuration
class TempSecurityConfig {
    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
```

- [ ] **Step 8: Chạy test, thấy PASS**

Run: `./gradlew test --tests '*GlobalExceptionHandlerTest'`
Expected: PASS (2 tests).

- [ ] **Step 9: Commit**

```bash
git add backend
git commit -m "feat(E0): backend skeleton (Gradle), error handler, testcontainers config"
```

---

### Task 3: Frontend skeleton

**Files:**
- Create: `frontend/package.json`, `frontend/index.html`, `frontend/tsconfig.json`, `frontend/vite.config.ts`
- Create: `frontend/src/main.tsx`, `frontend/src/App.tsx`, `frontend/src/App.test.tsx`, `frontend/src/test-setup.ts`
- Create: `frontend/src/api/http.ts`

**Interfaces:**
- Produces: `http<T>(path: string, init?: RequestInit): Promise<T>` ném `ApiErrorBody & {status:number}` khi lỗi; `ApiErrorBody = {code:string;message:string;details:unknown}`.

- [ ] **Step 1: `package.json`**

```json
{
  "name": "giapha-frontend",
  "private": true,
  "version": "0.0.1",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc --noEmit && vite build",
    "test": "vitest run",
    "preview": "vite preview"
  },
  "dependencies": {
    "@tanstack/react-query": "^5.59.0",
    "antd": "^5.21.0",
    "idb-keyval": "^6.2.1",
    "pixi-viewport": "^5.0.3",
    "pixi.js": "^7.4.2",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.27.0"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.6.0",
    "@testing-library/react": "^16.0.1",
    "@testing-library/user-event": "^14.5.2",
    "@types/react": "^18.3.11",
    "@types/react-dom": "^18.3.1",
    "@vitejs/plugin-react": "^4.3.3",
    "jsdom": "^25.0.1",
    "typescript": "^5.6.3",
    "vite": "^5.4.9",
    "vitest": "^2.1.3"
  }
}
```

- [ ] **Step 2: `tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2022", "DOM", "DOM.Iterable", "WebWorker"],
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "skipLibCheck": true,
    "isolatedModules": true,
    "types": ["vitest/globals", "@testing-library/jest-dom"]
  },
  "include": ["src"]
}
```

- [ ] **Step 3: `vite.config.ts`**

```ts
/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: { proxy: { '/api': 'http://localhost:8080' } },
  worker: { format: 'es' },
  test: { environment: 'jsdom', globals: true, setupFiles: ['./src/test-setup.ts'] },
})
```

- [ ] **Step 4: `index.html`, `src/test-setup.ts`**

`index.html`:
```html
<!doctype html>
<html lang="vi">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Gia phả</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```
`src/test-setup.ts`:
```ts
import '@testing-library/jest-dom/vitest'
```

- [ ] **Step 5: Viết test (failing)** `src/App.test.tsx`

```tsx
import { render, screen } from '@testing-library/react'
import App from './App'

test('hiển thị tên ứng dụng', () => {
  render(<App />)
  expect(screen.getByText('Gia phả')).toBeInTheDocument()
})
```

- [ ] **Step 6: Cài đặt và chạy test, thấy FAIL**

Run (ở `frontend`): `npm install && npm test`
Expected: FAIL — không tìm thấy `./App`.

- [ ] **Step 7: Triển khai**

`src/App.tsx`:
```tsx
export default function App() {
  return <h1>Gia phả</h1>
}
```
`src/main.tsx`:
```tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import viVN from 'antd/locale/vi_VN'
import App from './App'

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } } })

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider locale={viVN}>
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    </ConfigProvider>
  </React.StrictMode>,
)
```
`src/api/http.ts`:
```ts
export type ApiErrorBody = { code: string; message: string; details: unknown }
export type ApiFailure = ApiErrorBody & { status: number }

let accessToken: string | null = null
export const setAccessToken = (t: string | null) => { accessToken = t }
export const getAccessToken = () => accessToken

export async function http<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  const res = await fetch(`/api/v1${path}`, { ...init, headers, credentials: 'include' })
  if (!res.ok) {
    let body: ApiErrorBody = { code: 'UNKNOWN', message: res.statusText, details: null }
    try { body = await res.json() } catch { /* giữ mặc định */ }
    throw { ...body, status: res.status } satisfies ApiFailure
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
```

- [ ] **Step 8: Chạy test, thấy PASS**

Run: `npm test && npm run build`
Expected: 1 test PASS; build thành công.

- [ ] **Step 9: Commit**

```bash
git add frontend
git commit -m "feat(E0): frontend skeleton (vite, antd, vitest, http client)"
```

---

### Task 4: CI

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Tạo workflow**

```yaml
name: ci
on: [push, pull_request]
jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew --no-daemon build
        working-directory: backend
  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20', cache: npm, cache-dependency-path: frontend/package-lock.json }
      - run: npm ci && npm test && npm run build
        working-directory: frontend
```

- [ ] **Step 2: Kiểm tra cú pháp YAML**

Run: `python3 -c "import yaml,sys;yaml.safe_load(open('.github/workflows/ci.yml'))"` (ở `src/Root`)
Expected: không lỗi.

- [ ] **Step 3: Commit**

```bash
git add .github
git commit -m "chore(E0): CI for backend and frontend"
```

---

## Self-review

- Spec coverage: repo layout, docker-compose, error format, health — đủ. 
- `TempSecurityConfig` bị xoá ở E3 Task 5 (đã ghi trong comment).
