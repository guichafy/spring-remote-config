# Decisões de Design

> Volte para o [README](../README.md) para visão geral do projeto.

---

## Redis Pub/Sub (fire-and-forget)

**Contexto:** Precisamos notificar N pods simultaneamente quando uma configuração muda no SSM.

**Decisão:** Usar Redis Pub/Sub como canal de notificação, que é fire-and-forget.

**Consequência:** Se um pod não estiver conectado ao Redis no momento da publicação, ele perde a mensagem. Mitigado porque todo pod carrega config fresca do SSM no startup e na reconexão.

---

## Sem polling do SSM

**Contexto:** O SSM Parameter Store tem quota de 40 TPS (transactions per second) compartilhada por conta AWS.

**Decisão:** Não fazer polling periódico do SSM. Recarregar apenas quando notificado via Redis.

**Consequência:** Evita exaustão da quota. A troca é depender do Redis para saber que algo mudou.

---

## Debounce

**Contexto:** Em deploys ou scripts de CI/CD, múltiplas chaves podem ser atualizadas em sequência rápida, gerando várias notificações Redis em milissegundos.

**Decisão:** Implementar debounce de 2 segundos (configurável) no listener Redis.

**Consequência:** Múltiplas notificações dentro da janela de debounce resultam em UM único refresh, evitando refresh storms.

---

## @RefreshScope proxy

**Contexto:** O Spring Cloud `@RefreshScope` usa proxy para permitir recriação de beans em runtime.

**Decisão:** Usar `@RefreshScope` nos beans que dependem de configurações remotas.

**Consequência:** Requests in-flight completam com valores antigos; novos requests usam valores atualizados. Sem downtime durante refresh.

---

## MessageListener direto

**Contexto:** O `MessageListenerAdapter` do Spring Data Redis 3.5.x apresenta bug de `MethodInvoker` null em cenários específicos.

**Decisão:** Implementar `MessageListener` diretamente em vez de usar `MessageListenerAdapter`.

**Consequência:** Código levemente mais verboso, mas evita o bug e dá controle total sobre o processamento da mensagem.
