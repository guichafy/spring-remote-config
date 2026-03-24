# Observabilidade

> Volte para o [README](../README.md) para visão geral do projeto.

---

## Métricas

| Métrica | Tipo | Descrição |
|---|---|---|
| `config.refresh.total` | Counter | Refreshes executados com sucesso |
| `config.refresh.errors` | Counter | Tentativas de refresh que falharam |
| `config.refresh.duration` | Timer | Latência end-to-end do refresh |
| `config.refresh.keys.changed` | Gauge | Chaves alteradas no último refresh |
| `config.redis.subscription.active` | Gauge | 1 se inscrito, 0 se desconectado |
| `config.ssm.load.duration` | Timer | Duração da chamada GetParametersByPath |
| `config.ssm.load.errors` | Counter | Falhas na chamada ao SSM |

Tags: `app`, `pod` (lido de `HOSTNAME`).

### Consultando métricas

```bash
curl http://localhost:8080/actuator/metrics/config.refresh.total
curl http://localhost:8080/actuator/metrics/config.refresh.errors
curl http://localhost:8080/actuator/metrics/config.refresh.duration
curl http://localhost:8080/actuator/metrics/config.ssm.load.duration
```

---

## Health Check

Endpoint: `GET /actuator/health`

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

| Campo | Descrição |
|---|---|
| `redisSubscription` | Status da inscrição no Pub/Sub (`active` / `inactive`) |
| `lastRefreshTimestamp` | Timestamp do último refresh bem-sucedido |
| `totalRefreshes` | Quantidade total de refreshes executados |
| `totalErrors` | Quantidade total de erros durante refresh |
| `channel` | Canal Redis sendo monitorado |
