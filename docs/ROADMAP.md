# PayFlow — Roadmap

## Como usar este roadmap

Cada marco tem quatro partes:

1. **Antes de começar:** perguntas para responder por escrito, sem consultar
   nada. Se não souber, pesquise na documentação oficial e depois responda com
   suas palavras.
2. **Tarefas:** o que implementar.
3. **Pronto quando:** critérios objetivos de conclusão.
4. **Armadilhas:** erros comuns daquele tema.

Regras pessoais:

- Escrever o código antes de pedir ajuda. IA só para revisão ou para pedir uma
  pista, nunca a solução pronta.
- Fonte principal: documentação oficial (Spring, Hibernate, PostgreSQL).
- Guardar as respostas das perguntas em `docs/estudos/marco-X.md`.
- Testes fazem parte de cada marco, não de um marco separado.

---

## Marco 0 — Fundação

**Antes de começar**
- Qual a diferença entre `ddl-auto=update` e migrations versionadas? Por que
  `update` é perigoso em produção?
- Por que testar contra PostgreSQL real em vez de H2?

**Tarefas**
- [x] Projeto Spring Boot 4.x com Java 25 (Web, Data JPA, Validation, PostgreSQL, Flyway).
- [x] `docker-compose.yml` com PostgreSQL.
- [x] Flyway configurado; `ddl-auto=validate`.
- [ ] Testcontainers configurado com um teste que apenas sobe o contexto.
- [x] Log de SQL habilitado no perfil de desenvolvimento.

**Pronto quando**
- `docker compose up` + aplicação sobem sem erro.
- O teste de contexto passa usando Testcontainers.

**Armadilhas**
- Usar H2 "só por enquanto" e descobrir no Marco 3 que os locks se comportam diferente.

---

## Marco 1 — Domínio e Persistência

**Antes de começar**
- Por que `BigDecimal` e não `double` para dinheiro? Qual a diferença entre
  `new BigDecimal(0.1)` e `new BigDecimal("0.1")`?
- O que é um modelo de domínio rico vs. anêmico?
- Por que `@Data` do Lombok é problemático em entidades JPA?

**Tarefas**
- [ ] Migrations para `users`, `account`, `transaction`, `launch`, `notifications`.
- [ ] Entidade `Conta` com métodos de domínio: `debit` deve incluir reset preguiçoso do `amount_today` e verificar o `daily_limit`, `credit`, `block`,
      `finish`. As regras (RN-08 a RN-12) ficam dentro da entidade.
- [ ] Entidades `Transacao` e `Lancamento` (imutável, RN-41).
- [ ] Exceções de domínio próprias (ex.: `SaldoInsuficienteException`).
- [ ] Testes unitários puros (sem Spring) para as regras da `Conta`.

**Pronto quando**
- É impossível deixar uma `Conta` em estado inválido chamando seus métodos públicos.
- Os testes da `Conta` rodam em milissegundos, sem subir o Spring.

**Armadilhas**
- Setters públicos que permitem `conta.setSaldo(-50)`.
- `equals`/`hashCode` baseados em todos os campos ou em coleções lazy.

---

## Marco 2 — API REST, Validação, Erros e Documentação

**Antes de começar**
- Qual a diferença entre validar formato (Bean Validation) e validar regra de
  negócio (domínio)? Onde cada uma deve ficar?
- Por que não expor entidades JPA diretamente na API?
- O que é o problema N+1? Como identificá-lo no log de SQL?
- Qual a diferença entre a especificação OpenAPI e o Swagger UI?
- Por que documentação gerada a partir do código tende a ficar menos
  desatualizada do que documentação escrita à parte?

**Tarefas**
- [ ] DTOs como `record`, separados por requisição e resposta.
- [ ] Endpoints: criar conta (vai existir junto com criar usuário), consultar
      conta, depósito (RN-16 a RN-20), alterar status, extrato paginado (RN-42).
- [ ] Bean Validation nos DTOs de entrada.
- [ ] `@RestControllerAdvice` retornando `ProblemDetail` com os códigos do
      catálogo de erros.
- [ ] Testes de controller com `@WebMvcTest`.
- [ ] Verificar no log se o extrato gera N+1. Só corrigir se gerar.
- [ ] Adicionar o springdoc-openapi (conferir a versão compatível com a
      versão do Spring Boot do projeto).
- [ ] Descrever cada endpoint: o que faz, quais regras (RN-XX) aplica e
      exemplos de requisição e resposta.
- [ ] Documentar as respostas de erro de cada endpoint no formato
      `ProblemDetail`, com os códigos do catálogo que ele pode devolver.
- [ ] Decidir se o Swagger UI fica disponível em todos os ambientes ou só no
      perfil de desenvolvimento, e registrar a decisão.

**Pronto quando**
- Todo erro da API sai no formato ProblemDetail com o código correto.
- Nenhum endpoint retorna uma entidade JPA.
- Todos os endpoints aparecem no Swagger UI com descrição, exemplos e erros
  possíveis.
- Dá para executar o fluxo de depósito e consulta de extrato inteiro pelo
  Swagger UI.

**Armadilhas**
- Colocar regra de negócio em anotação de validação.
- `JOIN FETCH` com paginação (o Hibernate pagina em memória).
- Um schema no Swagger com o nome ou os campos de uma entidade JPA: é sinal de
  que a entidade está vazando pela API.
- Encher os controllers de anotações de documentação a ponto de esconder o
  código. Descrições longas podem ficar na configuração do OpenAPI.
---

## Marco 3 — Transferência e Concorrência ⭐

Este é o coração do projeto. Siga a ordem das tarefas.

**Antes de começar**
- O que acontece, passo a passo, quando duas threads leem saldo R$ 100 e cada
  uma debita R$ 80?
- Diferença entre lock pessimista e otimista. Quando cada um faz sentido?
- O que é deadlock? Como a ordem de aquisição de locks o evita?
- O que `@Transactional` faz por baixo? Por que chamar um método `@Transactional`
  da mesma classe não funciona?
- Quais exceções causam rollback por padrão no Spring?

**Tarefas**
- [ ] Implementar a transferência **sem nenhum lock**, só com `@Transactional`.
- [ ] Escrever o teste de concorrência (`ExecutorService` + `CountDownLatch`):
      N threads transferindo da mesma conta ao mesmo tempo, contra PostgreSQL.
- [ ] **Ver o teste falhar.** Anotar em `docs/estudos/marco-3.md` o que aconteceu.
- [ ] Implementar lock pessimista (`SELECT ... FOR UPDATE`).
- [ ] Travar as contas sempre na mesma ordem (ex.: menor ID primeiro) — RN-33.
- [ ] Teste de transferências cruzadas A→B e B→A simultâneas.
- [ ] Garantir que a validação do limite diário também está protegida, verificando `amount_today`  (RN-31).
- [ ] Configurar timeout de lock e mapear para `CONFLITO_CONCORRENCIA` (RN-34).
- [ ] Teste provando rollback: falha forçada após o débito não altera nenhum saldo.
- [ ] (Opcional) Implementar a versão com lock otimista (`@Version`) e comparar.

**Pronto quando**
- Depois da transferência o `balance_after` do `launch` tem que ser igual ao valor de `amount` em `account`
- O teste com 50+ threads passa repetidamente; a soma total dos saldos do
  sistema não muda.
- O teste de transferências cruzadas passa sem deadlock.

**Armadilhas**
- Teste de concorrência que passa por acaso porque as threads não começam ao
  mesmo tempo.
- Ler a conta sem lock, validar, e só depois travar.
- Usar `synchronized` no Java (não funciona com múltiplas instâncias da aplicação).

---

## Marco 4 — Idempotência

**Antes de começar**
- Por que lock não resolve o clique duplo?
- O que acontece se duas requisições com a mesma chave chegarem ao mesmo tempo?

**Tarefas**
- [ ] Tabela de chaves de idempotência com constraint única (usuário + chave).
- [ ] Armazenar hash do corpo e a resposta original (RN-36, RN-37).
- [ ] Teste: mesma chave enviada 2x em sequência gera uma única transferência.
- [ ] Teste: mesma chave enviada 2x em paralelo gera uma única transferência.
- [ ] Rotina de limpeza de chaves antigas (RN-38).
- [ ] Documentar o header `Idempotency-Key` no Swagger UI, nos endpoints de
      transferência e depósito, com os erros `IDEMPOTENCY_KEY_MISSING` e
      `IDEMPOTENCY_KEY_REUSED`.

**Pronto quando**
- Nenhum cenário de reenvio gera transferência duplicada.

**Armadilhas**
- Verificar "a chave existe?" e depois inserir, sem constraint única (race condition).

---

## Marco 5 — Segurança

**Antes de começar**
- Qual a diferença entre autenticação e autorização?
- O que tem dentro de um JWT? Ele é criptografado ou apenas assinado?
- Por que roles não bastam para impedir que um usuário opere a conta de outro?

**Tarefas**
- [ ] Cadastro de usuário com senha em BCrypt.
- [ ] Endpoint de login que emite JWT.
- [ ] `SecurityFilterChain` configurada com lambdas.
- [ ] Validação do JWT. Primeiro com um `OncePerRequestFilter` próprio, para
      entender o mecanismo; depois migrar para `oauth2ResourceServer().jwt()`.
- [ ] `@PreAuthorize` para regras de perfil (RN-06).
- [ ] Verificação de titularidade da conta (RN-05, RN-21).
- [ ] Testes: usuário A tentando operar a conta de B recebe 403.
- [ ] Configurar o esquema de autenticação Bearer (JWT) no OpenAPI, para que
      o Swagger UI permita informar o token e chamar endpoints protegidos.

**Pronto quando**
- Nenhum endpoint protegido responde sem token válido.
- Nenhum usuário consegue ler ou movimentar a conta de outro.

**Armadilhas**
- Pegar o ID do usuário do corpo da requisição em vez do token.
- Retornar 404 vs. 403 de forma que revele a existência de contas alheias
  (decidir e documentar a escolha).

---

## Marco 6 — Notificações Assíncronas

Decisão de arquitetura: o consumidor de notificações roda dentro da própria
aplicação PayFlow, em um pacote separado (`notification`), e a tabela
`notifications` fica no mesmo banco. A extração para um serviço independente
fica como evolução futura (ver ADR).

**Antes de começar**
- O que é o problema do *dual write*?
- Por que publicar um evento dentro do `@Transactional` é perigoso?
- O que é o padrão Transactional Outbox?
- O que significa entrega "at-least-once"? Por que o RabbitMQ pode entregar a
  mesma mensagem mais de uma vez?
- O que é uma DLQ e quando uma mensagem vai para ela?
- Qual a diferença entre ack automático e manual no consumidor? Em qual deles
  uma falha no meio do processamento faz a mensagem se perder?

**Tarefas**

*Parte 1 — Evento em memória*
- [ ] Migration da tabela `notifications`.
- [ ] Publicar um evento de domínio ao concluir uma transferência.
- [ ] Listener com `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`
      que grava a notificação do destinatário.
- [ ] Anotar em `docs/estudos/marco-6.md`: o que se perde se a aplicação cair
      entre o commit e a gravação da notificação?

*Parte 2 — Outbox + RabbitMQ*
- [ ] Subir RabbitMQ no `docker-compose.yml`.
- [ ] Migration da tabela `outbox`.
- [ ] Gravar o evento no `outbox` na mesma transação da transferência.
- [ ] Job que lê eventos pendentes do `outbox`, publica no broker e marca como
      publicados.
- [ ] Consumidor que recebe o evento e grava a notificação (RN-43).
- [ ] Consumidor idempotente: constraint única na tabela `notifications` que
      impeça duas notificações para o mesmo evento (decidir qual coluna).
- [ ] Retry com backoff e DLQ (RN-46).
- [ ] Remover a Parte 1, deixando só o fluxo via outbox.

*Parte 3 — API de notificações*
- [ ] Endpoint para listar as notificações do usuário autenticado (paginado,
      mais recentes primeiro).
- [ ] Endpoint para marcar uma notificação como lida.
- [ ] Verificação de titularidade: um usuário só vê e altera as próprias
      notificações.

*Testes*
- [ ] Transferência com rollback não gera evento no outbox nem notificação (RN-44).
- [ ] Consumidor falhando não afeta a transferência (RN-45).
- [ ] A mesma mensagem entregue duas vezes gera uma única notificação.
- [ ] Mensagem que falha repetidamente vai para a DLQ sem bloquear as demais (RN-46).
- [ ] Usuário A não consegue ler nem marcar como lida a notificação de B.

**Pronto quando**
- Nenhuma transferência confirmada fica sem notificação, e nenhuma notificação
  existe para transferência não confirmada.
- Reprocessar mensagens nunca gera notificações duplicadas.
- Derrubar o RabbitMQ não impede transferências; quando ele volta, as
  notificações pendentes são entregues.

**Armadilhas**
- Publicar no broker de dentro do `@Transactional` da transferência.
- Consumidor não idempotente (o job do outbox pode publicar o mesmo evento
  duas vezes se cair entre publicar e marcar como publicado).
- Ack automático: a mensagem é confirmada antes de a notificação ser gravada.
- Pegar o `user_id` do corpo da requisição nos endpoints de notificação, em vez
  do token.
- Retry infinito sem DLQ: uma mensagem com problema trava a fila.

**Decisões em aberto**
- Depósitos também geram notificação para o destinatário?
- A notificação é gravada com o texto pronto (`title`, `body_message`) ou com
  dados estruturados, montando o texto na hora de exibir?

---

## Marco 7 — Consolidação

**Tarefas**
- [ ] Testes de integração ponta a ponta do fluxo principal: cadastro → login →
      depósito → transferência → extrato → notificação.
- [ ] Revisar se cada regra RN-XX tem pelo menos um teste. Criar uma tabela
      regra → teste.
- [ ] README explicando as decisões técnicas (por que lock pessimista, por que
      outbox, etc.).

**Pronto quando**
- Você consegue explicar cada decisão do projeto sem olhar o código.
