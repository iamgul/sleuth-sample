# Sleuth → Micrometer Tracing Migration (Spring Boot 2.x → 3.x)

This README documents **everything that was discussed, missed initially, fixed later, and why those fixes are critical** when migrating from **Spring Cloud Sleuth (Spring Boot 2.x)** to **Micrometer Tracing (Spring Boot 3.x)**.

The goal of these projects is **only one thing**:

> ✅ Log `traceId` and `spanId` correctly in application logs and understand *why* it works.

---

## 1. Project Overview

### Project 1 — Sleuth Sample (Spring Boot 2.7.x)
- Uses **Spring Cloud Sleuth**
- Demonstrates automatic trace/span logging
- Compatible only with Spring Boot **2.x**

### Project 2 — Micrometer Sample (Spring Boot 3.5.x)
- Uses **Micrometer Tracing**
- Demonstrates the correct replacement for Sleuth
- Requires **Spring Boot 3.x** and **Java 17+**

---

## 2. Sleuth Project Configuration (Spring Boot 2.7.x)

### application.yml
```yaml
server:
  port: 9191

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} %-5level [%X{traceId}-%X{spanId}] %logger - %msg%n"

spring:
  sleuth:
    traceId128: true
```

### What does `spring.sleuth.traceId128: true` mean?

- Sleuth default trace IDs were **64-bit**
- Setting this to `true` enables **128-bit trace IDs**
- Required for:
  - W3C Trace Context
  - Large distributed systems
  - OpenTelemetry compatibility

> ⚠️ This property exists **only in Sleuth** and is **ignored completely** in Spring Boot 3.x.

---

### pom.xml (Sleuth Project)
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>sleuth-sample</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.12</version>
    </parent>

    <!-- CRITICAL: Spring Cloud BOM is mandatory -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>2021.0.8</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-sleuth</artifactId>
        </dependency>
    </dependencies>
</project>
```

### 🔴 Critical thing that was initially missed (Sleuth)

❌ **Missing Spring Cloud BOM** results in:
- Sleuth not instrumenting HTTP requests
- Logs showing `[-]` instead of trace/span IDs

✔ **Adding the BOM fixes tracing immediately**

---

## 3. Micrometer Project Configuration (Spring Boot 3.5.8)

### application.yml
```yaml
server:
  port: 9292

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} %-5level [%X{traceId}-%X{spanId}] %logger - %msg%n"
```

> Note: Micrometer **always uses 128-bit trace IDs by default**. No configuration is required.

---

### pom.xml (Micrometer Project)
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>micrometer-sample</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.8</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing</artifactId>
        </dependency>

        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-brave</artifactId>
        </dependency>

        <!-- CRITICAL: REQUIRED for tracing to work -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

## 4. 🔥 ROOT CAUSE (Micrometer project)

### Why traceId/spanId were showing `[-]`

Micrometer **does NOT auto-instrument HTTP requests** unless **Actuator is present**.

### How Micrometer tracing actually works

```
HTTP Request
   ↓
ServerHttpObservationFilter  (from Actuator)
   ↓
ObservationRegistry
   ↓
Micrometer Tracer
   ↓
Span created
   ↓
traceId/spanId added to MDC
   ↓
Logs show traceId/spanId
```

### Without Actuator
```
No Observation → No Span → MDC empty → [-]
```

> ✅ This is **by design** in Spring Boot 3.x

---

## 5. Why Actuator is MANDATORY in Spring Boot 3.x

| Aspect | Sleuth (2.x) | Micrometer (3.x) |
|------|-------------|-----------------|
| Tracing auto-enabled | Yes | ❌ No |
| Needs Actuator | ❌ No | ✅ Yes |
| Observability model | Sleuth | Observation API |
| OpenTelemetry-ready | ❌ | ✅ |

---

## 6. Can we replace `logback-classic` with Lombok `@Slf4j`?

### ❌ NO — they are NOT replacements

### Lombok `@Slf4j`
- Compile-time convenience
- Generates `Logger` field
- Does **not** log anything

### Logback
- Runtime logging engine
- Writes logs to console/files
- Required for logging to work

✔ They **work together**, not instead of each other.

---

## 7. Important Spring Boot Detail (Very Important)

### You usually do NOT need to declare `logback-classic` explicitly

Because:
```
spring-boot-starter-web
   └── spring-boot-starter-logging
         └── logback-classic
```

### Therefore this dependency is **redundant**
```xml
<dependency>
  <groupId>ch.qos.logback</groupId>
  <artifactId>logback-classic</artifactId>
</dependency>
```

### ✅ Best Practice
- Let Spring Boot manage logging
- Add Lombok only for code convenience

---

## 8. Final Migration Summary

✔ Sleuth works only with Spring Boot 2.x
✔ Micrometer is the **only supported tracing solution** in Spring Boot 3.x
✔ Actuator is **mandatory** for Micrometer tracing
✔ Trace IDs are always **128-bit** in Micrometer
✔ Logging pattern remains the same
✔ Lombok does not replace Logback

---

## 9. Common Pitfalls (Lessons Learned)

- ❌ Forgetting Spring Cloud BOM (Sleuth)
- ❌ Forgetting Actuator (Micrometer)
- ❌ Expecting Lombok to replace Logback
- ❌ Logging during startup and expecting trace IDs

---

## 10. How to Verify

```bash
curl http://localhost:9191/hello
curl http://localhost:9292/hello
```

Expected logs:
```
[traceId-spanId]
```

---

✅ This README is **copy-paste ready** and reflects **exactly what was learned during this migration**.

