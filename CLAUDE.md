# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Visão Geral

Microservice Spring Boot para **atualização de configurações em tempo de execução** sem restart. Parâmetros são carregados do **AWS SSM Parameter Store** e recarregados via notificação **Redis Pub/Sub** (ElastiCache). Roda em AWS EKS com IRSA.

## Stack

Java 21, Spring Boot 3.5.12, Spring Cloud 2025.0.1, AWS SDK v2 (SSM/STS), Redis (Lettuce), Maven wrapper, Testcontainers + JUnit 5 + Mockito.

## Comandos

```bash
# Build (sem testes)
./mvnw clean package -DskipTests

# Todos os testes (requer Docker para Testcontainers)
./mvnw test

# Teste unitário específico (classe ou método)
./mvnw test -Dtest=SsmConfigPropertySourceTest
./mvnw test -Dtest=SsmConfigPropertySourceTest#shouldLoadParametersFromSsm

# Testes de integração
./mvnw test -Dtest=ConfigRefreshIntegrationTest

# Redis local para desenvolvimento
docker compose up -d redis
```

## Arquitetura

Fluxo de refresh: **SSM (source of truth) → trigger externo → Redis Pub/Sub → todos os pods recarregam SSM → Spring context refresh**.

Componentes principais em `guichafy.remote_config.config`:

- **`SsmConfigPropertySource`** — Carrega parâmetros SSM via `GetParametersByPath`, converte paths SSM para property keys Spring (`/config/app/feature.enabled` → `feature.enabled`), registra como `MapPropertySource` com prioridade máxima. Detecta keys alteradas no reload.
- **`RedisRefreshListener`** — `MessageListener` no Redis Pub/Sub. Implementa **debounce** (janela configurável) e **lock** para coalescer notificações rápidas. Chama `reloadFromSsm()` e depois `ContextRefresher.refresh()`.
- **`ConfigChangeNotifier`** — REST controller (`POST /admin/config/notify`) que publica notificações de refresh no Redis. Ponto de entrada do trigger.
- **`RedisSubscriberConfig`** — Wiring do `RedisMessageListenerContainer` e bean `SsmClient` (`@ConditionalOnMissingBean` para override em testes).
- **`ConfigRefreshProperties`** — `@ConfigurationProperties(prefix = "app.config")`: `ssmPrefix`, `redisChannel`, `fallbackToLocal`, `debounceWindowMs`.

Componentes de suporte:
- **`FeatureFlagController`** (`GET /feature-flags`) — Exibe feature flags carregadas.
- **`ConfigRefreshEventListener`** — Extension point pós-refresh.
- **`ConfigHealthIndicator`** — Health check com status da subscription, timestamp do último refresh e contadores de erro.

## Configuração

Properties sob `app.config.*` em `application.yaml`. Variáveis de ambiente para deploy: `REDIS_HOST`, `REDIS_PORT`, `REDIS_SSL_ENABLED`.

Perfil de teste (`application-test.yml`) reduz `debounce-window-ms` para 500ms e usa `fallback-to-local: true`.

## Testes

- **Unitários** — Mockam `SsmClient`, usam `SimpleMeterRegistry`. Sem dependências externas.
- **Integração** (`ConfigRefreshIntegrationTest`) — `@Testcontainers` com Redis real + `@MockitoBean` para `SsmClient`. Testam ciclo completo de refresh e resiliência a restart do Redis.
- Perfil de teste ativado via `@ActiveProfiles("test")`.
