# inspecta-core

Biblioteca Maven (jar) com toda a camada de banco de dados do **Inspecta** — plataforma de vistoria imobiliaria. Java 21 (build com JDK 25) + Spring Boot 3.

## O que este projeto faz

Nao e um servico executavel — e um **bundle de dominio** consumido como dependencia Maven por [inspecta-api](../inspecta-api), [inspecta-worker](../inspecta-worker) e [inspecta-messenger](../inspecta-messenger). Concentra:

- **Entities JPA e repositories** de todo o dominio (as 36 tabelas descritas em `backend-specs/dominio.md`).
- **Services de CRUD** (salvar/editar/buscar) usados in-process pelos apps.
- **Outbox pattern**: `OutboxService.publish()` roda na mesma transacao da mudanca de dados; `OutboxRelay` (SKIP LOCKED) publica no RabbitMQ e roda dentro do inspecta-api e do inspecta-worker.
- **Migrations Flyway** (`src/main/resources/db/migration`) — schema completo do banco. So o `inspecta-api` executa as migrations no boot; os demais sobem com `ddl-auto: validate` e `flyway.enabled=false`.
- **Aspect de tenant/RLS** para isolamento multi-tenant a nivel de linha.
- **ShedLock**: configuracao de lock distribuido para os crons que rodam em mais de uma replica.

Regra de dependencia: nenhum app declara entity ou repository proprio — tudo vive aqui. Comunicacao entre modulos de dominio diferentes acontece **apenas via Service**, nunca acessando o Repository de outro modulo.

## Como rodar

Este projeto nao sobe (nao tem `main` de aplicacao). Ele so precisa ser instalado no repositorio Maven local antes de buildar qualquer um dos outros tres servicos:

```
mvn install
```

Isso compila e publica o jar `br.com.vistoria:inspecta-core` em `~/.m2`, de onde `inspecta-api`, `inspecta-worker` e `inspecta-messenger` o consomem via `pom.xml`.

Sempre que alterar entities, repositories, services ou migrations aqui, rode `mvn install` de novo antes de rodar/buildar os outros repos — inclusive nos Dockerfiles deles, que fazem esse build em duas etapas (inspecta-core primeiro, depois o app). Pacote base: `br.com.vistoria.core`.

### Rodar os testes
```
mvn test
```

## Licenca

Software proprietario. Uso comercial sujeito a licenca — ver [LICENSE](./LICENSE).
