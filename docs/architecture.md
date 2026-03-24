# Arquitetura & Componentes

> Volte para o [README](../README.md) para visão geral do projeto.

---

## Componentes

### SsmConfigPropertySource

Carrega todas as chaves do SSM Parameter Store sob o prefixo configurado e as registra como PropertySource de alta prioridade no Spring Environment.

- **`@PostConstruct`** carrega configuração inicial
- **`reloadFromSsm()`** recarrega com paginação, decryption e detecção de mudanças
- Converte paths SSM para property keys: `/config/app/feature/flag` → `feature.flag`
- Fallback configurável: se SSM indisponível, app inicia com config local

### RedisRefreshListener

Recebe mensagens do Redis Pub/Sub e orquestra o ciclo de refresh.

- Implementa `MessageListener` diretamente (sem adapter)
- **Debounce**: múltiplas mensagens dentro da janela (default 2s) resultam em UM refresh
- **Lock**: `ReentrantLock` previne refreshes concorrentes
- **Resiliência**: erros não derrubam o listener — ele continua operando

### ConfigChangeNotifier

Publisher de notificações + endpoint REST para triggers manuais.

```
POST /admin/config/notify
Content-Type: application/json

{
  "changedKeys": ["/config/spring-remote-config/feature.flag.enabled"],
  "message": "Descrição opcional"
}
```

### ConfigHealthIndicator

Health check em `/actuator/health` com detalhes do subsistema:

```json
{
  "status": "UP",
  "details": {
    "redisSubscription": "active",
    "lastRefreshTimestamp": "2026-03-23T14:30:05Z",
    "totalRefreshes": 3,
    "totalErrors": 0,
    "channel": "config:refresh:spring-remote-config"
  }
}
```

### ConfigRefreshEventListener

Listener de `RefreshScopeRefreshedEvent` — ponto de extensão para lógica pós-refresh:
- Invalidação de cache
- Reconexão de connection pools
- Reset de circuit breakers

---

## Formato da Mensagem Redis

```json
{
  "timestamp": "2026-03-23T14:30:00Z",
  "source": "ci-cd | lambda | manual | admin-api",
  "application": "spring-remote-config",
  "changedKeys": [
    "/config/spring-remote-config/feature.flag.enabled"
  ],
  "message": "Descrição opcional"
}
```

| Campo | Obrigatório | Descrição |
|---|---|---|
| `timestamp` | Sim | ISO-8601 UTC |
| `source` | Sim | Quem disparou a mudança |
| `application` | Sim | Nome da aplicação |
| `changedKeys` | Não | Lista de chaves alteradas (informativo) |
| `message` | Não | Descrição legível |

Se o JSON for inválido, o listener loga WARNING e ignora (sem crash).

---

## Resiliência

| Cenário | Comportamento |
|---|---|
| SSM indisponível no startup | App inicia com fallback local (se `fallback-to-local: true`) |
| SSM indisponível no refresh | Mantém valores anteriores, loga ERROR, incrementa counter |
| Redis indisponível no startup | App inicia normalmente, tenta reconectar em background |
| Redis desconecta durante operação | App continua com última config, reconecta automaticamente |
| Mensagem JSON inválida no Redis | Log WARNING, ignora (sem crash, sem refresh) |
| Múltiplas notificações simultâneas | Debounce consolida em UM refresh |
| Refresh concorrente | Lock previne execução simultânea |
