# PayFlow — Regras de Negócio

Este documento define o comportamento funcional do PayFlow. Cada regra tem um
identificador (RN-XX) para ser referenciada em testes, commits e mensagens de erro.

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
- **RN-05:** Um `USER` só pode consultar e movimentar as contas das quais é titular.
  Tentar operar a conta de outro usuário resulta em acesso negado, mesmo que a
  conta exista.
- **RN-06:** Um `ADMIN` pode consultar qualquer conta, realizar depósitos e
  alterar o status de contas. Um `ADMIN` não realiza transferências em nome de
  terceiros.
- **RN-07:** Todas as operações exigem autenticação, exceto cadastro de usuário e login.

---

## 4. A Conta

### 4.1. Estados

| Estado      | Envia transferência | Recebe transferência | Recebe depósito |
|-------------|:-------------------:|:--------------------:|:---------------:|
| `ATIVA`     | Sim                 | Sim                  | Sim             |
| `BLOQUEADA` | Não                 | Sim                  | Sim             |
| `ENCERRADA` | Não                 | Não                  | Não             |

- **RN-08:** Toda conta nasce com status `ATIVA` e saldo zero.
- **RN-09:** Somente um `ADMIN` pode alterar o status de uma conta.
- **RN-10:** `ENCERRADA` é um estado final; a conta não pode ser reativada.
- **RN-11:** Uma conta só pode ser encerrada se o saldo for exatamente zero.

### 4.2. Saldo e Limite Diário

- **RN-12:** O saldo nunca pode ficar menor que zero.
- **RN-13:** Toda conta possui um limite diário de envio. O valor padrão é
  R$ 5.000,00.
- **RN-14:** O "dia" é o dia corrente no fuso `America/Sao_Paulo`, de 00:00:00
  a 23:59:59. O limite é reiniciado à meia-noite.
- **RN-15:** Para o limite diário, contam apenas transferências **enviadas** e
  **concluídas** no dia. Depósitos e valores recebidos não contam.

---

## 5. Depósito

- **RN-16:** Depósitos são a única forma de dinheiro entrar no sistema.
- **RN-17:** Somente um `ADMIN` pode realizar depósitos.
- **RN-18:** O valor do depósito deve ser maior ou igual a R$ 0,01.
- **RN-19:** A conta de destino deve estar `ATIVA` ou `BLOQUEADA`.
- **RN-20:** Todo depósito gera um lançamento de crédito no extrato da conta.

---

## 6. Transferência

### 6.1. Validações

Uma transferência só é concluída se **todas** as condições forem verdadeiras:

- **RN-21:** A conta de origem pertence ao usuário autenticado.
- **RN-22:** A conta de origem está `ATIVA`.
- **RN-23:** A conta de destino existe e está `ATIVA` ou `BLOQUEADA`.
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

- **RN-35:** Toda requisição de transferência deve conter o header
  `Idempotency-Key` com um UUID gerado pelo cliente.
- **RN-36:** Se a mesma chave for reenviada pelo mesmo usuário com o mesmo corpo,
  o sistema não executa uma nova transferência; devolve a resposta original.
- **RN-37:** Se a mesma chave for reenviada com um corpo diferente, a requisição
  é rejeitada.
- **RN-38:** Chaves de idempotência são mantidas por no mínimo 24 horas.

> Idempotência resolve o "clique duplo". Concorrência (seção 7) resolve
> operações diferentes disputando o mesmo saldo. São problemas distintos.

---

## 9. Extrato

- **RN-39:** Cada transferência concluída gera dois lançamentos: um `DEBITO` na
  origem e um `CREDITO` no destino.
- **RN-40:** Cada lançamento registra: tipo, valor, data/hora, conta de
  contrapartida (quando houver), identificador da transação e saldo resultante
  após o lançamento.
- **RN-41:** Lançamentos são imutáveis: nunca são alterados nem excluídos.
- **RN-42:** O extrato é retornado do mais recente para o mais antigo, com paginação.

---

## 10. Notificações

- **RN-43:** Após uma transferência concluída, o destinatário recebe uma
  notificação (simulada via log).
- **RN-44:** A notificação só pode ser disparada se a transferência foi de fato
  confirmada. Uma transferência que sofreu rollback nunca gera notificação.
- **RN-45:** O envio da notificação não pode atrasar a resposta da transferência,
  e uma falha na notificação nunca desfaz a transferência.
- **RN-46:** Notificações que falham repetidamente são separadas para análise
  posterior, sem bloquear as demais.

---

## 11. Catálogo de Erros

| Código                        | HTTP | Regra       |
|-------------------------------|:----:|-------------|
| `VALOR_INVALIDO`              | 400  | RN-03, RN-25|
| `IDEMPOTENCY_KEY_AUSENTE`     | 400  | RN-35       |
| `NAO_AUTENTICADO`             | 401  | RN-07       |
| `ACESSO_NEGADO`               | 403  | RN-05, RN-21|
| `CONTA_NAO_ENCONTRADA`        | 404  | RN-23       |
| `CONFLITO_CONCORRENCIA`       | 409  | RN-34       |
| `CONTA_ORIGEM_INDISPONIVEL`   | 422  | RN-22       |
| `CONTA_DESTINO_INDISPONIVEL`  | 422  | RN-19, RN-23|
| `MESMA_CONTA`                 | 422  | RN-24       |
| `SALDO_INSUFICIENTE`          | 422  | RN-26       |
| `LIMITE_DIARIO_EXCEDIDO`      | 422  | RN-27       |
| `IDEMPOTENCY_KEY_REUTILIZADA` | 422  | RN-37       |
| `TRANSICAO_STATUS_INVALIDA`   | 422  | RN-10, RN-11|

Todos os erros seguem o formato ProblemDetail (RFC 9457).