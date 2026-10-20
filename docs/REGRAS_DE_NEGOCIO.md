# PayFlow — Regras de Negócio

Este documento define o comportamento funcional do PayFlow. Cada regra tem um
identificador (RN-XX) para ser referenciada em testes, commits e mensagens de erro.
Os identificadores são estáveis: regras novas recebem o próximo número livre,
mesmo que apareçam no meio do documento.

---

## 1. Visão Geral

O PayFlow é um sistema de transferências instantâneas entre contas de usuários.
Seus objetivos são:

- nunca permitir saldo negativo;
- nunca perder histórico de movimentações;
- nunca gerar dinheiro duplicado ou "saldo fantasma", mesmo sob alta concorrência.

### 1.1. Fora do escopo desta versão

- Saques para fora do sistema
- Estornos
- Cheque especial / crédito
- Agendamento de transferências
- Múltiplas moedas
- Múltiplas contas por usuário

---

## 2. Valores Monetários

- **RN-01:** Todos os valores são em Real (BRL), com exatamente 2 casas decimais.
- **RN-02:** Valores monetários nunca são representados com ponto flutuante
  (`float`/`double`). Usar `BigDecimal` com escala 2.
- **RN-03:** Valores com mais de 2 casas decimais na entrada são rejeitados
  (não são arredondados silenciosamente).

---

## 3. Usuários e Permissões

- **RN-04:** Existem dois perfis: `USER` e `ADMIN`.
- **RN-47:** Cada usuário possui exatamente uma conta. Não é possível criar uma
  segunda conta para o mesmo usuário. *(Momento de criação da conta: ver
  Decisões Pendentes.)*
- **RN-05:** Um `USER` só pode consultar e movimentar a conta da qual é titular.
  Tentar operar a conta de outro usuário resulta em acesso negado, mesmo que a
  conta exista.
- **RN-06:** Um `ADMIN` pode consultar qualquer conta, realizar depósitos e
  alterar o status de contas. Um `ADMIN` não realiza transferências em nome de
  terceiros.
- **RN-07:** Todas as operações exigem autenticação, exceto cadastro de usuário e login.

---

## 4. A Conta

### 4.1. Estados

| Estado    | Envia transferência | Recebe transferência | Recebe depósito |
|-----------|:-------------------:|:--------------------:|:---------------:|
| `ACTIVE`  | Sim                 | Sim                  | Sim             |
| `BLOCKED` | Não                 | Sim                  | Sim             |
| `CLOSED`  | Não                 | Não                  | Não             |

- **RN-08:** Toda conta nasce com status `ACTIVE` e saldo zero.
- **RN-09:** Somente um `ADMIN` pode alterar o status de uma conta.
- **RN-10:** `CLOSED` é um estado final; a conta não pode ser reativada.
- **RN-11:** Uma conta só pode ser encerrada se o saldo for exatamente zero.

### 4.2. Saldo e Limite Diário

- **RN-12:** O saldo nunca pode ficar menor que zero.
- **RN-13:** Toda conta possui um limite diário de envio. O valor padrão é
  R$ 5.000,00.
- **RN-14:** O "dia" é o dia corrente no fuso `America/Sao_Paulo`, de 00:00:00
  a 23:59:59. O total enviado no dia volta a zero a cada novo dia.
- **RN-15:** Para o limite diário, contam apenas transferências **enviadas** e
  **concluídas** no dia. Depósitos e valores recebidos não contam.

---

## 5. Depósito

- **RN-16:** Depósitos são a única forma de dinheiro entrar no sistema.
- **RN-17:** Somente um `ADMIN` pode realizar depósitos.
- **RN-18:** O valor do depósito deve ser maior ou igual a R$ 0,01.
- **RN-19:** A conta de destino deve estar `ACTIVE` ou `BLOCKED`.
- **RN-20:** Todo depósito gera um lançamento `CREDIT` no extrato da conta.

---

## 6. Transferência

### 6.1. Validações

Uma transferência só é concluída se **todas** as condições forem verdadeiras:

- **RN-21:** A conta de origem pertence ao usuário autenticado.
- **RN-22:** A conta de origem está `ACTIVE`.
- **RN-23:** A conta de destino existe e está `ACTIVE` ou `BLOCKED`.
- **RN-24:** Origem e destino são contas diferentes.
- **RN-25:** O valor é maior ou igual a R$ 0,01.
- **RN-26:** O saldo da origem é maior ou igual ao valor.
- **RN-27:** O total enviado no dia, somado ao valor, não ultrapassa o limite
  diário da origem.

### 6.2. Atomicidade

- **RN-28:** O débito na origem, o crédito no destino e os lançamentos no extrato
  formam uma única operação: ou tudo acontece, ou nada acontece.
- **RN-29:** Não existe estado intermediário visível em que o dinheiro saiu da
  origem e não chegou ao destino.
- **RN-30:** Transferências rejeitadas não alteram nenhum saldo nem geram
  lançamentos no extrato.

---

## 7. Concorrência

- **RN-31:** Quando várias operações disputam o mesmo saldo ao mesmo tempo, as
  validações de saldo (RN-26) **e** de limite diário (RN-27) devem considerar o
  efeito das operações concorrentes. Não pode haver duas aprovações baseadas no
  mesmo saldo.
- **RN-32:** Se duas transferências simultâneas forem possíveis individualmente,
  mas não juntas, exatamente uma deve ser aprovada e a outra rejeitada por saldo
  insuficiente ou limite excedido.
- **RN-33:** Transferências cruzadas simultâneas (A→B e B→A) não podem travar o
  sistema (deadlock).
- **RN-34:** Se uma operação não conseguir obter acesso exclusivo às contas dentro
  de um tempo limite, ela é rejeitada com erro de conflito, sem efeitos colaterais.
  O cliente pode tentar novamente.

---

## 8. Idempotência

- **RN-35:** Toda requisição de transferência ou de depósito deve conter o header
  `Idempotency-Key` com um UUID gerado pelo cliente.
- **RN-36:** Se a mesma chave for reenviada pelo mesmo usuário com o mesmo corpo,
  o sistema não executa a operação novamente; devolve a resposta original.
  *(Tratamento de operações rejeitadas: ver Decisões Pendentes.)*
- **RN-37:** Se a mesma chave for reenviada com um corpo diferente, a requisição
  é rejeitada.
- **RN-38:** Chaves de idempotência são mantidas por no mínimo 24 horas.

> Idempotência resolve o "clique duplo". Concorrência (seção 7) resolve
> operações diferentes disputando o mesmo saldo. São problemas distintos.

---

## 9. Extrato

- **RN-39:** Cada transferência concluída gera dois lançamentos: um `DEBIT` na
  origem e um `CREDIT` no destino.
- **RN-40:** Cada lançamento registra: a conta a que pertence (`account_id`), a
  transação que o originou (`transaction_id`), o tipo (`DEBIT` ou `CREDIT`), o
  valor (`value`), o saldo da conta logo após o lançamento (`balance_after`) e a
  data/hora de criação (`createdAt`). A conta de contrapartida, quando houver,
  não é armazenada no lançamento: ela é obtida a partir da transação.
- **RN-41:** Lançamentos são imutáveis: nunca são alterados nem excluídos.
- **RN-42:** O extrato é retornado do mais recente para o mais antigo, com paginação.

---

## 10. Notificações

- **RN-43:** Após uma transferência concluída, o destinatário recebe uma
  notificação, persistida e consultável. *(Notificação de depósitos: ver
  Decisões Pendentes.)*
- **RN-44:** A notificação só pode ser disparada se a transferência foi de fato
  confirmada. Uma transferência que sofreu rollback nunca gera notificação.
- **RN-45:** O envio da notificação não pode atrasar a resposta da transferência,
  e uma falha na notificação nunca desfaz a transferência.
- **RN-46:** Notificações que falham repetidamente são separadas para análise
  posterior, sem bloquear as demais.
- **RN-48:** Um usuário só pode consultar e marcar como lidas as próprias
  notificações. A consulta retorna as mais recentes primeiro, com paginação.
- **RN-49:** Um mesmo evento nunca gera mais de uma notificação, mesmo que a
  mensagem seja entregue mais de uma vez.

---

## 11. Catálogo de Erros

| Código                            | HTTP | Regra        |
|-----------------------------------|:----:|--------------|
| `INVALID_AMOUNT`                  | 400  | RN-03, RN-25 |
| `IDEMPOTENCY_KEY_MISSING`         | 400  | RN-35        |
| `UNAUTHENTICATED`                 | 401  | RN-07        |
| `ACCESS_DENIED`                   | 403  | RN-05, RN-21, RN-48 |
| `ACCOUNT_NOT_FOUND`               | 404  | RN-23        |
| `NOTIFICATION_NOT_FOUND`          | 404  | RN-48        |
| `CONCURRENCY_CONFLICT`            | 409  | RN-34        |
| `ACCOUNT_ALREADY_EXISTS`          | 409  | RN-47        |
| `SOURCE_ACCOUNT_UNAVAILABLE`      | 422  | RN-22        |
| `DESTINATION_ACCOUNT_UNAVAILABLE` | 422  | RN-19, RN-23 |
| `SAME_ACCOUNT`                    | 422  | RN-24        |
| `INSUFFICIENT_BALANCE`            | 422  | RN-26        |
| `DAILY_LIMIT_EXCEEDED`            | 422  | RN-27        |
| `IDEMPOTENCY_KEY_REUSED`          | 422  | RN-37        |
| `INVALID_STATUS_TRANSITION`       | 422  | RN-10, RN-11 |

Todos os erros seguem o formato ProblemDetail (RFC 9457).

---

## 12. Decisões Pendentes

| Tema | Pergunta | Regras afetadas |
|------|----------|-----------------|
| Criação da conta | A conta é criada junto com o usuário, ou num passo separado? | RN-47 |
| Idempotência de rejeições | Se uma operação for rejeitada (ex.: saldo insuficiente), um reenvio com a mesma chave devolve o mesmo erro ou tenta executar de novo? | RN-36 |
| Notificação de depósito | Depósitos também geram notificação para o destinatário? | RN-43 |
