# 001 — Tabela Outbox + RabbitMQ para notificações

**Status:** Aceita
**Data:** 2026-10-20

## Contexto
Para a geração da notificação acontece sobre dois passos lógicos. Primeiro salvar a transação no banco (ou qualquer outra ação), depois gerar a notificação.
Suponha que a transação é escrita no banco com sucesso, pórem durante a inserção da notificação falha e ai a notificação se perde, e também precisa garantir que transferência não se perda ficando em acordo com RN-43, RN-44 e RN-45. Para isso existem 3 soluções e vou dizer por que escolhi a da utilização da tabela Outbox com RabbitMQ.

## Alternativas
```
1. Utilizar `@Transactional` com `@EventListener` e fazer a inserção da notificação no mesmo serviço:
-> Para o próposito dessa aplicação não faz sentido porque se a transferência é concluida com sucesso e a notificaçao falha, o JPA faria rollback de toda a transação no banco comprometendo a resiliência das transferências.
```

```
2. `@TransactionalEventListener + `@Async`
-> Aqui o listener só roda depois do commit, em outra thread. A transferência não espera e não é afetada por falhas na notificação. Pórem esse evento vive em memória e se o servidor cair no instante de criação da notificação, por algum motivo, esse dado se perde e a notificação se perde pra sempre.
```

A opção 1 viola a RN-45; a opção 2 seria aceitável, mas tem um gap de perda.

## Decisão escolhida

Por essa razão, optei pela 3 opção que seria:

```
3. Outbox + RabbitMQ
-> Aqui quando a linha do `Outbox` é gerada a notificação ainda não existe, mas grava o evento em si com os dados que vão ser necessários para geração da notificação. O Evento é gravado no banco de dados junto com a transferência, sobrevivendo a possiveis quedas. Um job separado lê periodicamente os eventos pendentes da tabela Outbox, publica cada um no RabbitMQ e os marca como publicados; se o broker estiver indisponível, os eventos aguardam na tabela até a próxima execução. Além disso o broker oferece retries, backoff e DLQ. Assim nenhuma notificação se perde, e nenhuma falha afeta a transferência.
```

## Consequências
Fica melhor a resiliência da arquitetura por trás da geração das notificações e redução de danos colaterais, oferecendo retries para eventuais falhas, backoff e DLQ para tratamento adequado. O tradeoff disso vai ser a complexidade maior que vai exigir, como configuração do broker (rabbitmq) e do listener e a exigência de uma nova tabela no banco de dados com novas colunas. E uma coisa que esse modelo se adequa é que pelo menos uma vez o evento vai ser publicado, podendo acontecer mais de uma vez por isso se utilizasse de idempotência para corrigir essa lacuna garantindo a constraint unique (RN-49). Assim a notificação deixa de ser imediata (modelo assíncrono) e a tabela Outbox cresce indefinidamente, exigindo jobs de limpeza