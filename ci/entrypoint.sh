#!/bin/bash
# Build-runner do inspecta-core. set -e: se mvn verify falhar, nunca publica no
# Reposilite (inspecta-api/inspecta-worker/inspecta-messenger continuam resolvendo a versão anterior).
#
# Dois modos, decididos pela presença de REPOSILITE_TOKEN_ID no ambiente:
#   - sem token  -> só `mvn verify` (job Jenkins "test", MODULE=inspecta-core).
#   - com token  -> `mvn verify` + `mvn deploy` (job Jenkins "upload/upload-inspecta-core").
set -euo pipefail

echo "== [1/2] mvn verify (testes existentes) =="
mvn -B -f pom.xml verify

if [ -z "${REPOSILITE_TOKEN_ID:-}" ]; then
  echo "== [2/2] sem REPOSILITE_TOKEN_ID: pulando deploy (rodada de teste) =="
  exit 0
fi

VER=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -1)

case "$VER" in
  *-SNAPSHOT)
    # SNAPSHOT e mutavel: o repo `snapshots` do Reposilite aceita redeploy. Todo
    # build verde republica, e inspecta-api/inspecta-worker/inspecta-messenger (updatePolicy=always)
    # pegam a versao nova no proximo build. Nao ha "ja existe, pula" -- o ponto do
    # SNAPSHOT e justamente nao depender de bump de versao. O Maven roteia pro
    # <snapshotRepository> do pom automaticamente por causa do sufixo -SNAPSHOT.
    echo "== [2/2] SNAPSHOT: (re)publicando inspecta-core:${VER} no Reposilite (/snapshots) =="
    ;;
  *)
    # Release e imutavel (o repo `releases` recusa redeploy). Publicar so faz
    # sentido quando a versao ainda nao existe la; se o codigo mudou sem bump, o
    # build dos consumidores quebra alto contra o jar antigo, forcando o bump.
    ART_URL="https://maven.inspecta.viniciusscarabelli.tech/releases/com/inspecta/inspecta-core/${VER}/inspecta-core-${VER}.pom"
    if curl -sf -o /dev/null "$ART_URL"; then
      echo "== [2/2] inspecta-core:${VER} ja esta no Reposilite -- nada a publicar =="
      exit 0
    fi
    echo "== [2/2] tudo verde: publicando inspecta-core:${VER} no Reposilite (/releases) =="
    ;;
esac

# Credenciais do deploy. O <snapshotRepository> e o <repository> do pom usam o
# mesmo id `inspecta-reposilite`, entao um <server> so cobre release e SNAPSHOT.
cat > settings-deploy.xml <<XML
<settings>
  <servers>
    <server>
      <id>inspecta-reposilite</id>
      <username>${REPOSILITE_TOKEN_ID}</username>
      <password>${REPOSILITE_TOKEN_SECRET}</password>
    </server>
  </servers>
</settings>
XML
mvn -B -s settings-deploy.xml -f pom.xml deploy -DskipTests
rm -f settings-deploy.xml

echo "== inspecta-core:${VER} publicado no Reposilite =="
