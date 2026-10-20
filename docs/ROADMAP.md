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
- [ ] Projeto Spring Boot 3.x com Java 21 (Web, Data JPA, Validation, PostgreSQL, Flyway).
- [ ] `docker-compose.yml` com PostgreSQL.
- [ ] Flyway configurado; `ddl-auto=validate`.
- [ ] Testcontainers configurado com um teste que apenas sobe o contexto.
- [ ] Log de SQL habilitado no perfil de desenvolvimento.

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
- [ ] Migrations para `usuario`, `conta`, `transacao`, `lancamento`.
- [ ] Entidade `Conta` com métodos de domínio: `debitar`, `creditar`, `bloquear`,
      `encerrar`. As regras (RN-08 a RN-12) ficam dentro da entidade.
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

## Marco 2 — API REST, Validação e Erros

**Antes de começar**
- Qual a diferença entre validar formato (Bean Validation) e validar regra de
  negócio (domínio)? Onde cada uma deve ficar?
- Por que não expor entidades JPA diretamente na API?
- O que é o problema N+1? Como identificá-lo no log de SQL?

**Tarefas**
- [ ] DTOs como `record`, separados por requisição e resposta.
- [ ] Endpoints: criar conta, consultar conta, depósito (RN-16 a RN-20),
      alterar status, extrato paginado (RN-42).
- [ ] Bean Validation nos DTOs de entrada.
- [ ] `@RestControllerAdvice` retornando `ProblemDetail` com os códigos do
      catálogo de erros.
- [ ] Testes de controller com `@WebMvcTest`.
- [ ] Verificar no log se o extrato gera N+1. Só corrigir se gerar.

**Pronto quando**
- Todo erro da API sai no formato ProblemDetail com o código correto.
- Nenhum endpoint retorna uma entidade JPA.

**Armadilhas**
- Colocar regra de negócio em anotação de validação.
- `JOIN FETCH` com paginação (o Hibernate pagina em memória).

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
- [ ] Garantir que a validação do limite diário também está protegida (RN-31).
- [ ] Configurar timeout de lock e mapear para `CONFLITO_CONCORRENCIA` (RN-34).
- [ ] Teste provando rollback: falha forçada após o débito não altera nenhum saldo.
- [ ] (Opcional) Implementar a versão com lock otimista (`@Version`) e comparar.

**Pronto quando**
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

**Pronto quando**
- Nenhum endpoint protegido responde sem token válido.
- Nenhum usuário consegue ler ou movimentar a conta de outro.

**Armadilhas**
- Pegar o ID do usuário do corpo da requisição em vez do token.
- Retornar 404 vs. 403 de forma que revele a existência de contas alheias
  (decidir e documentar a escolha).

---

## Marco 6 — Notificações Assíncronas

**Antes de começar**
- O que é o problema do *dual write*?
- Por que publicar um evento dentro do `@Transactional` é perigoso?
- O que é o padrão Transactional Outbox?
- O que é uma DLQ e quando uma mensagem vai para ela?

**Tarefas**
- [ ] Versão 1: `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`.
      Entender o que se perde se a aplicação cair logo após o commit.
- [ ] Subir RabbitMQ no `docker-compose.yml`.
- [ ] Versão 2: tabela `outbox` gravada na mesma transação da transferência
      + job que publica no broker.
- [ ] Consumidor que simula a notificação via log (RN-43).
- [ ] Retry com backoff e DLQ (RN-46).
- [ ] Teste: transferência com rollback não gera mensagem (RN-44).
- [ ] Teste: consumidor falhando não afeta a transferência (RN-45).

**Pronto quando**
- Nenhuma transferência confirmada fica sem notificação, e nenhuma notificação
  existe para transferência não confirmada.

**Armadilhas**
- Consumidor não idempotente (a mesma mensagem pode chegar mais de uma vez).

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