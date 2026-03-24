# Configuração

> Volte para o [README](../README.md) para visão geral do projeto.

---

## application.yaml

```yaml
spring:
  application:
    name: spring-remote-config
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      ssl:
        enabled: ${REDIS_SSL_ENABLED:false}

app:
  config:
    ssm-prefix: /config/${spring.application.name}/
    redis-channel: config:refresh:${spring.application.name}
    fallback-to-local: true
    debounce-window-ms: 2000
```

## Propriedades

| Propriedade | Descrição | Default |
|---|---|---|
| `app.config.ssm-prefix` | Prefixo do path no SSM | `/config/{app-name}/` |
| `app.config.redis-channel` | Canal Redis Pub/Sub | `config:refresh:{app-name}` |
| `app.config.fallback-to-local` | Permitir startup sem SSM | `true` |
| `app.config.debounce-window-ms` | Janela de debounce (ms) | `2000` |

---

## Conversão de Nomes SSM → Spring

| Parâmetro SSM | Propriedade Spring |
|---|---|
| `/config/spring-remote-config/feature.flag.enabled` | `feature.flag.enabled` |
| `/config/spring-remote-config/api/timeout` | `api.timeout` |
| `/config/spring-remote-config/database/url` | `database.url` |

O prefixo é removido e barras `/` restantes viram `.`.
