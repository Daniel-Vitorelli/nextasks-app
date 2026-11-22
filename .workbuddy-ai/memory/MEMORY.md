# NexTasks — notas permanentes do projeto

**O guia completo está em `AGENTS.md` (raiz).** Este arquivo guarda o que precisa ser
lembrado de imediato, sem abrir o repositório.

## Regras de trabalho pedidas pelo usuário

- **Responder em português.**
- **Toda mudança vira versão**: subir `versionCode`/`versionName`, gerar o APK e copiar para a
  raiz como `NexTasks-<versão>-debug.apk`. Na raiz deve ficar **apenas o mais recente**.
- **A cada versão nova, rodar `tools/limpar-versao.sh`** logo depois de copiar o APK. Ele deixa
  só o APK mais recente, apaga capturas de teste antigas, apaga snapshots do AVD e **esvazia a
  Lixeira**. A limpeza acontece **na criação da versão**, não numa faxina semanal.
- **Neste ambiente `rm` manda para a Lixeira** (função de shell → shim de "safe delete"). Para
  apagar de verdade e liberar espaço: **`/usr/bin/rm`**.
- **Manter o disco limpo**: lixo pode ser removido sem perguntar a cada vez, inclusive a
  Lixeira. Fora do escopo: documentos, Downloads, `app/build`, caches do Gradle, `.workbuddy-ai`.

## Projeto

- App Android **nativo** (Kotlin + Compose + Material 3) que reimplementa o app web Next.js em
  `C:/Users/Daniel/programacao/nextasks/frontend`. **O web é a referência** de modelo de dados e
  regras; divergências conscientes ficam nos logs diários.
- Persistência: DataStore + JSON, um arquivo por entidade em `files/datastore/`. Sem Room, sem
  Hilt, sem Navigation-Compose (navegação é `enum AppDestination` + `when`).
- Build: `JAVA_HOME=/c/Users/Daniel/.jdks/temurin-21`,
  `ANDROID_HOME=/c/Users/Daniel/Android/Sdk`, `./gradlew assembleDebug`.
- Emulador: AVD `NexTasks_API36`; abrir com `tools/abrir-no-emulador.cmd` (usa `-no-snapshot`).

## Regras de negócio que já causaram bug

- Período de confirmação: rotina **diária = dia**, **semanal = início da semana**.
- Só uma rotina ativa por vez; bloco só é confirmável **depois** do horário de início.
- Conexões funcionam nos **dois sentidos** (bloco→entidade e entidade→bloco); confirmações
  anteriores à criação não contam.
- Addon desligado desliga **comportamento**, não só a tela — mas só quando existe comportamento
  real (hoje: apenas Conexões).

## Armadilhas do ambiente

- Disco cheio faz o emulador morrer com erro de **GPU** enganoso.
- `-no-snapshot-load` **grava** snapshot (~2,5 GB) e o antigo vai para a Lixeira.
- `.MainActivity` não é o entry point desde a 3.22: resolver o activity-alias ativo.
- `collectAsState(initial = emptyList())` entrega vazio antes do primeiro valor real — nunca
  podar/apagar dados com base nele (usar `.first()`).
- `LaunchedEffect(Unit)` roda antes das preferências carregarem (ainda são o padrão).

## Pendências

- Gamificação e IA são placeholders (sem pontos, conquistas ou chat).
- Gate de conexões com addon desligado: implementado, **não verificado em execução**.
- Backup não inclui preferências (`settings.json`).

## Histórico

Detalhe de cada versão em `.workbuddy-ai/memory/YYYY-MM-DD.md`.
