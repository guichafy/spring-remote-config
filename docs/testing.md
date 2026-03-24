# Testes

> Volte para o [README](../README.md) para visão geral do projeto.

---

## Executando os Testes

```bash
# Todos os testes (requer Docker)
./mvnw test

# Apenas testes unitários (sem Docker)
./mvnw test -Dtest='SsmConfigPropertySourceTest,RedisRefreshListenerTest,ConfigChangeNotifierTest,ConfigHealthIndicatorTest'

# Apenas integração (requer Docker)
./mvnw test -Dtest='ConfigRefreshIntegrationTest'
```

---

## Cobertura — 20 testes

| Classe de Teste | Qtd | Escopo |
|---|---|---|
| `SsmConfigPropertySourceTest` | 5 | Carregamento, paginação, fallback, detecção de mudanças |
| `RedisRefreshListenerTest` | 6 | Debounce, JSON inválido, lock, métricas |
| `ConfigChangeNotifierTest` | 3 | Publicação, timestamps, null handling |
| `ConfigHealthIndicatorTest` | 3 | UP/DOWN, timestamps |
| `ConfigRefreshIntegrationTest` | 2 | Ciclo completo, sobrevive restart do Redis |
| `RemoteConfigApplicationTests` | 1 | Context load |
