# Spring Remote Config

Microservice Spring Boot que demonstra um mecanismo de **atualização de configurações remotas em tempo de execução**, sem necessidade de restart. Parâmetros são carregados do **AWS SSM Parameter Store** e recarregados automaticamente via notificação **Redis Pub/Sub** (ElastiCache).

```
SSM Parameter Store        (source of truth)
        │
        │ (1) Config alterada
        ▼
Trigger (Lambda/CI-CD/CLI/REST)
        │
        │ (2) PUBLISH no Redis
        ▼
Redis Pub/Sub (ElastiCache)
    ┌───┼───┐
    ▼   ▼   ▼
  Pod1 Pod2 PodN           (3) Cada pod:
    │   │   │                   a. Recebe notificação
    │   │   │                   b. Chama GetParametersByPath
    │   │   │                   c. Atualiza PropertySource
    ▼   ▼   ▼                   d. contextRefresher.refresh()
@RefreshScope beans atualizados
```

---

## Tech Stack

| Componente | Tecnologia |
|---|---|
| Framework | Spring Boot 3.5.12, Spring Cloud 2025.0.1 |
| Linguagem | Java 21 |
| Config Store | AWS SSM Parameter Store |
| Notification Bus | ElastiCache Redis (Pub/Sub) |
| Observability | Micrometer (DataDog) |
| Testes | JUnit 5, Testcontainers (Redis), Mockito |
| Build | Maven |
| Runtime | AWS EKS (pods com IRSA) |

---

## Estrutura do Projeto

```
src/
├── main/java/guichafy/remote_config/
│   ├── RemoteConfigApplication.java          # Entry point
│   ├── config/
│   │   ├── ConfigRefreshProperties.java      # Properties tipadas (app.config.*)
│   │   ├── SsmConfigPropertySource.java      # Carrega/recarrega SSM → PropertySource
│   │   ├── RedisSubscriberConfig.java        # Wiring do listener container + bean SsmClient
│   │   ├── RedisRefreshListener.java         # Core: recebe Pub/Sub, debounce, refresh
│   │   ├── ConfigChangeNotifier.java         # REST endpoint + publisher Redis
│   │   └── FeatureFlagController.java        # Endpoint para visualizar feature flags
│   ├── event/
│   │   └── ConfigRefreshEventListener.java   # Extension point pós-refresh
│   └── health/
│       └── ConfigHealthIndicator.java        # Health check do subsistema
├── main/resources/
│   └── application.yaml                      # Configuração principal
└── test/java/guichafy/remote_config/
    ├── RemoteConfigApplicationTests.java     # Context load test
    ├── config/
    │   ├── SsmConfigPropertySourceTest.java  # 5 unit tests
    │   ├── RedisRefreshListenerTest.java     # 6 unit tests
    │   ├── ConfigChangeNotifierTest.java     # 3 unit tests
    │   └── ConfigRefreshIntegrationTest.java # 2 integration tests
    └── health/
        └── ConfigHealthIndicatorTest.java    # 3 unit tests
```

---

## Endpoints

| Método | Path | Descrição |
|---|---|---|
| `POST` | `/admin/config/notify` | Publica notificação de refresh no Redis |
| `GET` | `/feature-flags` | Lista todas as feature flags carregadas |
| `GET` | `/actuator/health` | Health check com status do config refresh |
| `GET` | `/actuator/metrics/{name}` | Métricas Micrometer |
| `POST` | `/actuator/refresh` | Trigger manual do Spring Cloud refresh |

---

## Documentação

| Documento | Conteúdo |
|---|---|
| [Quick Start & Uso](SETUP.md) | Ambiente local, passo a passo, uso do refresh |
| [Arquitetura & Componentes](docs/architecture.md) | Detalhes dos componentes, formato Redis, resiliência |
| [Configuração](docs/configuration.md) | application.yaml, propriedades, conversão SSM → Spring |
| [Observabilidade](docs/observability.md) | Métricas Micrometer, health checks |
| [Deploy](docs/deployment.md) | EKS, IRSA, automação CI/CD |
| [Testes](docs/testing.md) | Estratégia, cobertura, comandos |
| [Decisões de Design](docs/design-decisions.md) | Trade-offs e justificativas |
