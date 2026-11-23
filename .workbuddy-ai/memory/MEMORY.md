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
- **A cada versão nova, commitar.** O projeto é um repositório git local (branch `main`, sem
  remoto) desde a v3.34. Mensagem: `vX.Y — resumo do que mudou`, em português, uma versão por
  commit. Ordem: build → copiar APK → limpar → **commit**. Sem commit, a versão não terminou.
  `*.apk` e `build/` ficam fora do repositório (`.gitignore`).
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
- Conquista desbloqueada é celebrada por um **overlay global** (canal no `GamificationEngine`
  lido pelo `NexTasksApp`), não por tela.
- Build: `JAVA_HOME=/c/Users/Daniel/.jdks/temurin-21`,
  `ANDROID_HOME=/c/Users/Daniel/Android/Sdk`, `./gradlew assembleDebug`.
- **Testes**: `./gradlew testDebugUnitTest` — **112 casos JVM** das regras puras (cascata,
  hábitos, conexões, ocorrências, ordenação, horário de bloco, gamificação). Sem emulador, ~30 s.
- Emulador: AVD `NexTasks_API36`; abrir com `tools/abrir-no-emulador.cmd` (usa `-no-snapshot`).
  Se o script terminar, o processo do emulador morre junto — para deixá-lo de pé, rodar
  `emulator.exe -avd NexTasks_API36 -no-snapshot` direto.

## Regras de negócio que já causaram bug

- Período de confirmação: rotina **diária = dia**, **semanal = início da semana**.
- Bloco→hábito usa o **dia do bloco** (semanal: período + `weekday`), não "hoje"; a marcação
  passa por `ensureCount` e não soma de novo.
- **XP negativo é lançamento** (recaída −15); só `0` remove.
- **Rank exige nível + conquistas** (`rankFor`); XP sozinho não sobe a escada.
- Só uma rotina ativa por vez; bloco só é confirmável **depois** do horário de início.
- Conexões funcionam nos **dois sentidos** (bloco→entidade e entidade→bloco); confirmações
  anteriores à criação não contam.
- **Concluir tarefa sempre pelo `TaskCompletion`**, nunca `setDone` direto: a cascata das
  sub-tarefas depende disso. Home e motor de conexões já esqueceram.
- **`Routine.coversDay`** é a única resposta para "a rotina acontece neste dia?" (inativa,
  antes do `createdAt`, depois do `endDate`). Calendário e gráfico divergiam.
- Sequência de hábito conta **dias agendados**, não dias de calendário.
- Addon desligado desliga **comportamento**, não só a tela — mas só quando existe comportamento
  real (hoje: apenas Conexões).

## Armadilhas do ambiente

- Disco cheio faz o emulador morrer com erro de **GPU** enganoso.
- `-no-snapshot-load` **grava** snapshot (~2,5 GB) e o antigo vai para a Lixeira.
- `.MainActivity` não é o entry point desde a 3.22: resolver o activity-alias ativo.
- `collectAsState(initial = emptyList())` entrega vazio antes do primeiro valor real — nunca
  podar/apagar dados com base nele (usar `.first()`).
- `LaunchedEffect(Unit)` roda antes das preferências carregarem (ainda são o padrão).
- **Toque no emulador não se mede pelo print**: o `screencap` é 1080x2400 e o visualizador
  reescala. Usar `uiautomator dump` e tocar no centro dos `bounds`.

## Pendências

- **Assistente IA** é placeholder (sem chat). Gamificação já tem XP, níveis e conquistas.
- Backup não inclui preferências (`settings.json`).
- Débito de UI: cartão de tarefa em 3 telas, formatters duplicados, seleção por cor sem
  rótulo no TalkBack.

## Histórico

Detalhe de cada versão em `.workbuddy-ai/memory/YYYY-MM-DD.md`.
