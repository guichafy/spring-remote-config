# Deploy

> Volte para o [README](../README.md) para visão geral do projeto.

---

## EKS

### Variáveis de ambiente

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

### IRSA (IAM Roles for Service Accounts)

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

## Automação CI/CD

```bash
#!/bin/bash
PARAM="/config/spring-remote-config/feature.flag.enabled"
REDIS_HOST="meu-cluster.cache.amazonaws.com"
CHANNEL="config:refresh:spring-remote-config"

# 1. Atualizar no SSM
aws ssm put-parameter --name "$PARAM" --value "true" --type "String" --overwrite

# 2. Notificar pods
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
MSG="{\"timestamp\":\"$TIMESTAMP\",\"source\":\"ci-cd\",\"application\":\"spring-remote-config\"}"
redis-cli -h "$REDIS_HOST" PUBLISH "$CHANNEL" "$MSG"
```
