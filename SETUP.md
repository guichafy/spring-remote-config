> Volte para o [README](README.md) para visão geral do projeto.

# Reactive Configuration Refresh via Redis Pub/Sub

## Visão Geral

Este microservice carrega configurações do AWS SSM Parameter Store na inicialização e recarrega automaticamente quando notificado via Redis Pub/Sub, sem necessidade de restart dos pods.

```
Parameter Store ──► Trigger ──► Redis Pub/Sub ──► Pods ──► @RefreshScope beans atualizados
```

---

## Pré-requisitos

- Java 21+
- Docker (para Redis local)
- AWS CLI configurado (para SSM)
- Maven 3.9+ (ou use o wrapper `./mvnw`)

---

## 1. Configuração do AWS SSM Parameter Store

### 1.1 Criar parâmetros no SSM

Os parâmetros devem seguir o padrão de prefixo `/config/{app-name}/`. O nome da aplicação padrão é `spring-remote-config`.

```bash
# Criar um parâmetro simples (String)
aws ssm put-parameter \
  --name "/config/spring-remote-config/feature.flag.enabled" \
  --value "true" \
  --type "String" \
  --overwrite

# Criar um parâmetro com valor sensível (SecureString — criptografado com KMS)
aws ssm put-parameter \
  --name "/config/spring-remote-config/database.password" \
  --value "minha-senha-secreta" \
  --type "SecureString" \
  --overwrite

# Criar parâmetros com hierarquia (subpastas viram "." no Spring)
# /config/spring-remote-config/api/timeout  →  propriedade: api.timeout
aws ssm put-parameter \
  --name "/config/spring-remote-config/api/timeout" \
  --value "5000" \
  --type "String" \
  --overwrite

aws ssm put-parameter \
  --name "/config/spring-remote-config/api/endpoint" \
  --value "https://api.example.com/v2" \
  --type "String" \
  --overwrite
```

### 1.2 Verificar parâmetros criados

```bash
# Listar todos os parâmetros sob o prefixo
aws ssm get-parameters-by-path \
  --path "/config/spring-remote-config/" \
  --recursive \
  --with-decryption

# Consultar um parâmetro específico
aws ssm get-parameter \
  --name "/config/spring-remote-config/feature.flag.enabled"
```

### 1.3 Atualizar um parâmetro existente

```bash
aws ssm put-parameter \
  --name "/config/spring-remote-config/feature.flag.enabled" \
  --value "false" \
  --type "String" \
  --overwrite
```

### 1.4 Remover um parâmetro

```bash
aws ssm delete-parameter \
  --name "/config/spring-remote-config/feature.flag.enabled"
```

### 1.5 Conversão de nomes SSM para propriedades Spring

| Parâmetro SSM | Propriedade Spring |
|---|---|
| `/config/spring-remote-config/feature.flag.enabled` | `feature.flag.enabled` |
| `/config/spring-remote-config/api/timeout` | `api.timeout` |
| `/config/spring-remote-config/database/url` | `database.url` |

O prefixo `/config/spring-remote-config/` é removido e barras `/` restantes viram `.`.

---

## 2. Subir o Ambiente Local

### 2.1 Iniciar o Redis

```bash
docker compose up -d
```

Isso sobe:
- **Redis** na porta `6379`
- **Redis Insight** (UI) na porta `5540` — acesse em http://localhost:5540

### 2.2 Configurar credenciais AWS

A aplicação usa a cadeia padrão de credenciais do AWS SDK. Para desenvolvimento local, configure uma das opções:

**Opção A — AWS CLI (recomendado para dev):**
```bash
aws configure
# Informe Access Key, Secret Key, região (ex: us-east-1)
```

**Opção B — Variáveis de ambiente:**
```bash
export AWS_ACCESS_KEY_ID=sua-access-key
export AWS_SECRET_ACCESS_KEY=sua-secret-key
export AWS_REGION=us-east-1
```

**Opção C — Profile específico:**
```bash
export AWS_PROFILE=meu-perfil-dev
```

### 2.3 Iniciar a aplicação

```bash
./mvnw spring-boot:run
```

Se o SSM não estiver acessível (ex: sem credenciais AWS), a aplicação inicia normalmente com fallback local graças à configuração `app.config.fallback-to-local: true`.

**Com variáveis customizadas:**
```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--app.config.ssm-prefix=/config/meu-app/"
```

---

## 3. Usar o Refresh Reativo

### 3.1 Fluxo completo

1. Altere um parâmetro no SSM (veja seção 1.3)
2. Notifique os pods via endpoint REST ou diretamente pelo Redis
3. Todos os pods recarregam as configs do SSM automaticamente

### 3.2 Notificar via endpoint REST

```bash
# Notificação simples (recarrega todas as chaves)
curl -X POST http://localhost:8080/admin/config/notify

# Notificação com detalhes
curl -X POST http://localhost:8080/admin/config/notify \
  -H "Content-Type: application/json" \
  -d '{
    "changedKeys": ["/config/spring-remote-config/feature.flag.enabled"],
    "message": "Feature flag atualizada para produção"
  }'
```

### 3.3 Notificar via Redis CLI

```bash
# Conectar ao Redis
docker exec -it spring-remote-config-redis-1 redis-cli

# Publicar notificação no canal
PUBLISH "config:refresh:spring-remote-config" '{"timestamp":"2026-03-23T14:30:00Z","source":"manual","application":"spring-remote-config","message":"Refresh manual"}'
```

### 3.4 Formato da mensagem Redis

```json
{
  "timestamp": "2026-03-23T14:30:00Z",
  "source": "ci-cd | lambda | manual | admin-api",
  "application": "spring-remote-config",
  "changedKeys": [
    "/config/spring-remote-config/feature.flag.enabled",
    "/config/spring-remote-config/api.endpoint"
  ],
  "message": "Descrição opcional da mudança"
}
```

- `changedKeys` é opcional — quando ausente, recarrega TODAS as chaves
- `message` é opcional

---

## 4. Monitoramento

### 4.1 Health check

```bash
curl http://localhost:8080/actuator/health | jq '.components.configHealthIndicator'
```

Resposta:
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

### 4.2 Métricas

```bash
# Total de refreshes executados
curl http://localhost:8080/actuator/metrics/config.refresh.total

# Erros de refresh
curl http://localhost:8080/actuator/metrics/config.refresh.errors

# Duração do refresh
curl http://localhost:8080/actuator/metrics/config.refresh.duration

# Duração do carregamento SSM
curl http://localhost:8080/actuator/metrics/config.ssm.load.duration
```

---

## 5. Usar @RefreshScope nos Beans

Para que um bean recarregue seus valores após um refresh, anote-o com `@RefreshScope`:

```java
@Component
@RefreshScope
public class FeatureFlags {

    @Value("${feature.flag.enabled:false}")
    private boolean featureEnabled;

    @Value("${api.timeout:3000}")
    private int apiTimeout;

    // getters...
}
```

Após um refresh, o Spring recria o bean com os novos valores do SSM.

---

## 6. Deploy no EKS

### 6.1 Variáveis de ambiente no Deployment

```yaml
env:
  - name: REDIS_HOST
    value: "meu-cluster.cache.amazonaws.com"
  - name: REDIS_PORT
    value: "6379"
  - name: REDIS_SSL_ENABLED
    value: "true"
  - name: HOSTNAME
    valueFrom:
      fieldRef:
        fieldPath: metadata.name
```

### 6.2 IRSA (IAM Roles for Service Accounts)

A aplicação usa `DefaultCredentialsProvider` do AWS SDK, que automaticamente detecta credenciais IRSA no EKS. Garanta que a ServiceAccount do pod tenha a policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParametersByPath",
        "ssm:GetParameter",
        "ssm:GetParameters"
      ],
      "Resource": "arn:aws:ssm:*:*:parameter/config/spring-remote-config/*"
    }
  ]
}
```

---

## 7. Rodar os Testes

```bash
# Todos os testes (requer Docker para testes de integração)
./mvnw test

# Apenas testes unitários
./mvnw test -Dtest='SsmConfigPropertySourceTest,RedisRefreshListenerTest,ConfigChangeNotifierTest,ConfigHealthIndicatorTest'

# Apenas testes de integração
./mvnw test -Dtest='ConfigRefreshIntegrationTest'
```

---

## 8. Automação com CI/CD ou Lambda

Exemplo de script para atualizar config e notificar os pods:

```bash
#!/bin/bash
# update-config.sh — atualiza SSM e notifica via Redis

PARAM_NAME="/config/spring-remote-config/feature.flag.enabled"
NEW_VALUE="true"
REDIS_HOST="meu-cluster.cache.amazonaws.com"
REDIS_CHANNEL="config:refresh:spring-remote-config"

# 1. Atualizar no SSM
aws ssm put-parameter \
  --name "$PARAM_NAME" \
  --value "$NEW_VALUE" \
  --type "String" \
  --overwrite

# 2. Notificar pods via Redis
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
MESSAGE="{\"timestamp\":\"$TIMESTAMP\",\"source\":\"ci-cd\",\"application\":\"spring-remote-config\",\"changedKeys\":[\"$PARAM_NAME\"],\"message\":\"Deploy automation\"}"

redis-cli -h "$REDIS_HOST" PUBLISH "$REDIS_CHANNEL" "$MESSAGE"

echo "Config atualizada e pods notificados."
```
