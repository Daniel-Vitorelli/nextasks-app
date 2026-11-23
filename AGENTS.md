# AGENTS.md — NexTasks (Android)

Guia para quem (humano ou agente) for mexer neste repositório. Descreve o que o app é,
como está organizado, quais regras de negócio não podem ser quebradas e as armadilhas
que já custaram tempo.

---

## 1. O que é

App Android **nativo** que reimplementa o NexTasks — um app web em Next.js que fica em
`C:/Users/Daniel/programacao/nextasks/frontend`.

O **web é a referência** de modelo de dados e regras de negócio. O Android não copia a
interface: adapta para mobile (Material 3, gestos, telas pequenas). Divergências
conscientes estão registradas em `.workbuddy-ai/memory/YYYY-MM-DD.md`.

Idioma do produto e das respostas: **português**.

---

## 2. Stack

| Item | Valor |
|---|---|
| Linguagem | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Persistência | **DataStore + JSON** (`JsonListSerializer`), um arquivo por entidade |
| Serialização | kotlinx.serialization |
| minSdk / target / compile | 26 / 37 / 37 |
| Java | 17 |
| Versão atual | `versionName 3.41`, `versionCode 62` |
| Tamanho | ~89 arquivos Kotlin, ~19.050 linhas, 599 strings |

Dependências: `core-ktx`, `lifecycle-runtime-ktx`, `activity-compose`, `datastore`,
`kotlinx-serialization-json`, Compose BOM, `animation`, `material3`, `ui-tooling`.

Sem Hilt, sem Room, sem Navigation-Compose: a navegação é um `enum` + `when`.

---

## 3. Comandos

```bash
export JAVA_HOME="/c/Users/Daniel/.jdks/temurin-21"
export ANDROID_HOME="/c/Users/Daniel/Android/Sdk"

cd /c/Users/Daniel/programacao/app
./gradlew assembleDebug          # gera app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # regras puras em JVM (sem emulador)
./gradlew connectedDebugAndroidTest   # gestos, com emulador ligado
```

Os testes ficam em `app/src/test/` (regras, 108 casos) e `app/src/androidTest/` (toque
de verdade, hoje só a pinça da grade). Rode antes de fechar qualquer versão.

Abrir no emulador (atalho da área de trabalho aponta para cá):

```bat
tools\abrir-no-emulador.cmd
```

Ele checa espaço em disco, liga o AVD `NexTasks_API36` com `-no-snapshot`, espera o boot,
instala o APK mais recente da raiz e abre o app pelo **alias ativo**.

---

## 4. Arquitetura

Duas camadas, sem intermediários:

```
data/    modelos @Serializable + repositórios (DataStore) + regras puras
ui/      telas Compose (uma por seção) + componentes + tema
```

### 4.1 `data/` — persistência e regras

| Arquivo | Papel |
|---|---|
| `JsonListSerializer.kt` | Serializer genérico de `List<T>` em JSON |
| `Dates.kt` | Parse de data/hora tolerante (`parseIsoDate`, `parseIsoTime`) |
| `*Repository.kt` | Um por entidade; expõe `Flow<List<T>>` e funções de escrita |
| `*Stats.kt`, `SubtaskCascade.kt`, `TaskSort.kt`, `RoutineSchedule.kt` | Regras puras (sem Android) |
| `TaskCompletion.kt` | Conclusão de tarefa **com** a cascata das sub-tarefas |
| `Connection*.kt` | Modelo, repositório, motor e sugestões de conexão |
| `Gamification*.kt` | XP, níveis, ranks, catálogo de conquistas e retrato de estatísticas |
| `GamificationRepository.kt` | Histórico de XP, conquistas e o motor que reconcilia |
| `BackupService.kt` | Exporta/importa as 12 tabelas JSON |

Cada entidade tem **um arquivo** em `files/datastore/`:

```
routines.json  tasks.json  subtasks.json  habits.json  habit_completions.json
completions.json  agenda_events.json  extra_blocks.json  block_overrides.json
connections.json  settings.json  xp_events.json  achievements.json
```

Todos os stores usam `ReplaceFileCorruptionHandler { emptyList() }` (ou `AppSettings()`),
então **um arquivo corrompido vira lista vazia** em vez de derrubar o app.

### 4.2 `ui/` — telas

```
NexTasksApp.kt      hospeda a gaveta, roteia por AppDestination
AppDestination.kt   enum das seções (rótulo + ícone + addon dono)
AppIcon.kt          sincroniza o activity-alias com a cor de destaque
home/ calendar/ routines/ tasks/ habits/ connections/ gamification/ settings/
components/         cards, campos de formulário, diálogo de nota
theme/              cores, tipografia, acentos
```

No calendário, Dia / 3 dias / Semana compartilham `CalendarTimeGrid`: eixo de horas,
blocos posicionados pelo horário e **pinça de dois dedos para zoom** (`pinchToZoom`,
0,6x–2,5x). O zoom mora em `CalendarScreen` (`gridZoom`) para sobreviver à troca de dia
e de visualização; a grade só recebe `zoom`/`onZoom` e mantém o horário sob os dedos
(`zoomedScrollOffset`). O `horizontalSwipe` ignora gestos com dois dedos — sem isso a
pinça trocaria de dia.

`NexTasksApp` cria **todos** os repositórios uma vez (`remember`) e os passa para as telas.
Não há ViewModel: estado de tela é `remember`/`rememberSaveable` e as escritas vão direto
para o repositório via `scope.launch`.

---

## 5. Modelo de domínio

### Routine / RoutineBlock

```kotlin
Routine(id, name, description, frequency, duration, endDate, isActive, createdAt, blocks)
  frequency: Daily | Weekly
  duration:  Indefinite | Until
RoutineBlock(id, title, description, startTime, endTime, isAllDay, color, weekday,
             confirmation, scoreMax)
  confirmation: None | Checklist | Score
```

- **Só uma rotina ativa por vez** (`isActive`). Ativar uma desativa as outras — e a primeira
  rotina criada quando não há nenhuma ativa já nasce ativa.
- `startTime`/`endTime` são `"HH:MM"`. `weekday` (0 = domingo) só importa em `Weekly`.
- Blocos de rotina semanal só ocorrem no próprio dia da semana.

### Completion

```kotlin
Completion(blockId, periodStart, value)
```

- Chave: **(bloco, início do período)**. Período = **dia** em rotina diária,
  **início da semana** em semanal (`CalendarDates.startOfWeek()`).
- `value`: `"true"`/`"false"` (checklist) ou a nota `"1".."5"` (score).
- **Não dá para confirmar antes do horário de início** — o checkbox só aparece depois.

### Task / Subtask

```kotlin
Task(id, title, description, startDate, dueDate, priority, done, createdAt)
Subtask(id, taskId, parentId, title, description, done, createdAt)
```

- `startDate`/`dueDate` são `"YYYY-MM-DD"` opcionais.
- Sub-tarefa tem `parentId` → árvore de profundidade livre.
- **Cascata**: concluir a tarefa conclui todas as sub-tarefas; concluir todas as
  sub-tarefas conclui a tarefa (`SubtaskCascade`).

### Habit / HabitCompletion

```kotlin
Habit(id, name, description, icon, color, kind, frequency, daysOfWeek, targetCount,
      allowExceed, createdAt)
  kind: Good | Bad      // Bad = a marcação vira recaída
  frequency: Daily | Weekly
HabitCompletion(habitId, date, count)
```

- `targetCount` nulo = hábito sem meta (só conta ocorrências).
- Bom satura na meta; ruim acumula recaídas. `allowExceed` deixa passar da meta.
- `daysOfWeek` só vale para hábito **bom diário**; os demais valem todo dia.

### AgendaEvent / BlockOverride / ExtraBlock

```kotlin
AgendaEvent(id, title, description, startDate, endDate, startTime, endTime, color, suspension)
  suspension: None | WholeDays | WithinHours
BlockOverride(routineId, blockId, date, deleted, block?)   // exceção de um dia
ExtraBlock(id, date, routineId?, title, description, startTime, endTime, isAllDay, color,
           confirmation, scoreMax)                          // bloco avulso de um dia
```

- `AgendaEvent` é multi-dia e pode **suspender** a rotina no período.
- `BlockOverride` edita ou apaga um bloco **sem tocar na rotina**.
- `ExtraBlock` existe só naquele dia.

### Connection

```kotlin
Connection(id, routineId, blockId, taskId?, subtaskId?, habitId?, requiredCount, dayFilter, createdOn)
  dayFilter: "all" | "weekday:0..6" | "date:YYYY-MM-DD"
  requiredCount: 1..99
```

Liga **uma** entidade (tarefa, sub-tarefa ou hábito) a **um** bloco. Ver §7.2.

### XpEvent / AchievementUnlock

```kotlin
XpEvent(id, kind, refKey, amount, createdAt)
AchievementUnlock(achievementId, unlockedOn)
```

- `refKey` é a chave de idempotência (`block:<id>:<período>`, `task:<id>:done`…): a mesma
  ação nunca pontua duas vezes.
- O total de XP é a **soma do histórico** — não existe contador paralelo.
- Conquista desbloqueada é permanente; o XP do tier entra uma vez.

### AppSettings

```kotlin
AppSettings(themeMode, userName, taskSort, accent, enabledAddons)
  themeMode: System | Light | Dark
  accent: Green | Blue | Purple | Pink | Orange | Amber | Cyan
```

---

## 6. Navegação e addons

`AppDestination` tem 9 seções: Início, Calendário, Rotinas, Tarefas, Hábitos, **Conexões**,
**Assistente IA**, **Gamificação**, Configurações.

Três são **addons** (`Addon.Connections`, `Addon.Ai`, `Addon.Gamification`): nascem
desligados, não aparecem na gaveta e só existem depois de ativados em Configurações → Addons.

**Regra de ouro**: addon desligado desliga o que ele **faz**, não só a tela.

| Addon | Comportamento quando desligado |
|---|---|
| Conexões | O motor não roda (não conclui tarefa, não confirma bloco) e a poda de órfãs não roda |
| Gamificação | O motor não **escreve**: `enabled()` barra `reconcile`, `onTaskChanged`, `onSubtaskChanged`, `onHabitChanged` e `refreshAchievements`. Nada é apagado — `xp_events.json` e `achievements.json` ficam intactos |
| IA | Nada — só a página (placeholder) |

O portão da gamificação é de **escrita**, não de leitura. Enquanto está desligada, concluir
tarefa, marcar hábito ou confirmar bloco não lança XP nem desbloqueia conquista — mas os
dados que geram XP continuam sendo gravados. Ao religar, o primeiro `reconcile` (abrir a
tela, confirmar bloco ou excluir tarefa) refaz o XP de domínio a partir do estado atual e
credita o período retroativo; conquistas já desbloqueadas são permanentes e ficam fora
dessa varredura.

Não confundir com a **Sequência** da Home: ela é da rotina (`routineStats`), não da
gamificação, e continua aparecendo com o addon desligado.

---

## 7. Regras de negócio que não podem quebrar

### 7.1 Períodos

Rotina diária confirma por **dia**; semanal, por **semana** (início = domingo).
Errar isso faz o progresso contar duas vezes ou nunca.

### 7.2 Conexões — motor nos dois sentidos

- **Bloco → entidade**: confirmar o bloco conta para a conexão; ao atingir
  `requiredCount`, conclui a tarefa/sub-tarefa ou fecha a meta do hábito.
- **Entidade → bloco**: concluir a tarefa/sub-tarefa/hábito confirma o bloco no período
  atual (semanal usa o início da semana), se ainda não estiver confirmado.
- Confirmações **anteriores à criação** da conexão não contam (`createdOn`).
- `dayFilter` restringe quais períodos contam; `date:` só admite meta 1.
- Em rotina **semanal** não existe filtro de dia: a confirmação é gravada no início da
  semana, então `weekday:`/`date:` comparariam sempre com o domingo e a conexão nunca
  contaria. `dayFilterFor` força `all` nesse caso e o editor esconde os chips.
- **Desmarcar o bloco desfaz** o que a conexão concluiu: tarefa e sub-tarefa voltam a
  aberta quando a contagem cai abaixo da meta — a menos que outra conexão da mesma
  entidade continue satisfeita. Hábito **não** volta (a marcação é do usuário).
- **Bloco → hábito usa o dia do bloco**, não "hoje": em rotina semanal o período é o
  domingo e o bloco acontece `weekday` dias depois. A marcação é feita com
  `HabitRepository.ensureCount` (garante a meta sem somar de novo), então confirmar,
  desmarcar e remarcar não infla a contagem.
- **Recaída de hábito ruim não confirma bloco**: `onHabitDone` só age em hábito bom.
- **Várias entidades no mesmo bloco** avançam juntas; a tela avisa ("também alimenta")
  no cartão e no editor. Não é bloqueio: um bloco pode legitimamente alimentar duas.
- `ConnectionStatus.kt` responde duas perguntas do cartão: por que a conexão nunca vai
  disparar (`blockBlocker`: rotina inativa/terminada/não começou, horário inválido) e
  quantos dias faltam no ritmo real (`paceEstimate`).

### 7.3 Conclusão de tarefa

Sempre pelo `TaskCompletion`, nunca com `TaskRepository.setDone` direto: concluir a
tarefa **tem** que concluir todas as sub-tarefas. Home, lista de Tarefas e motor de
Conexões chamam o mesmo ponto — antes cada tela repetia (ou esquecia) a cascata.

### 7.4 Quando a rotina existe

`Routine.coversDay` (`RoutineSchedule.kt`) é a única resposta para "esta rotina acontece
neste dia?": inativa, antes do `createdAt` ou depois do `endDate` não acontece. O motor
de ocorrências usa essa função, então calendário, Home, gráfico e heatmap concordam —
antes o gráfico cortava pelo `createdAt` e o calendário mostrava blocos no passado.

### 7.5 Sugestões de conexão

Cruza nome de bloco com nome de entidade: iguais (nota 2) ou um contendo o outro com
**pelo menos 4 letras** (nota 1). Sem esse limite, "ir" casaria com "dormir".
Ignora pares já conectados e entidades concluídas.

### 7.6 Gamificação

- **XP é ledger, não contador**: `xp_events.json` guarda cada lançamento e o nível é
  `levelFromXp(soma)`. Curva `75·(L−1)·L/2`, ranks por faixa de nível.
- **Valor negativo é lançamento de verdade** (`habit.slip` = −15). Só `0` significa
  "não pontua / desfazer"; tratar `amount <= 0` como remoção engolia a recaída.
- Cada ação **reconcilia** o próprio lançamento (`onTaskChanged`, `onHabitChanged`…):
  desfazer remove, mudar a nota atualiza, repetir não duplica.
- `reconcile()` refaz todo o XP de domínio a partir dos dados. Roda ao confirmar bloco e
  ao abrir a tela — cobre o que pontua fora das telas (conexão que conclui tarefa,
  backup restaurado). Conquistas ficam fora da varredura (são permanentes).
- Conquistas são avaliadas em laço: o XP de uma pode subir de nível e destravar outra.
- **Divergência do web**: lá a nota cheia é `10`; aqui a nota é `1..scoreMax` por bloco,
  então "nota cheia" é `valor == scoreMax`.

### 7.7 Backup

Exporta as 12 tabelas JSON. **Não** exporta `settings.json` (tema, cor, addons, ordem) —
restaurar um backup não devolve preferências. Depois de restaurar, as conexões são
podadas: o arquivo pode trazer vínculos de blocos que não vieram junto.

### 7.8 Dado ruim não derruba tela

Data/hora gravada como texto pode estar inválida (backup remendado). Use
`parseIsoDate`/`parseIsoTime` (`Dates.kt`) e trate `null` como "registro ignorado".
`LocalDate.parse` cru só onde o valor acabou de sair de um seletor do próprio app.

### 7.9 Exclusão limpa o que dependia

Excluir rotina, bloco, tarefa, sub-tarefa ou hábito também apaga o que apontava para
ele: conclusões, exceções do dia e conexões. Sem isso o `connections.json` acumula
órfãs que só somem no próximo start.

---

## 8. Design system

- Cores de evento espelham os tokens `--event-*` do web (`EventColor`), com variante clara
  e escura. Sempre usar `EventColor.accent()` / `.container()`, nunca cores soltas.
- Cartões: `RoundedCornerShape(12.dp)`, fundo `surface`, borda 1dp `outlineVariant`.
- Títulos de seção: `labelSmall` maiúsculo, `SemiBold`, `onSurfaceVariant`.
- Cor de destaque do app: `AccentColor` → `MaterialTheme.colorScheme.primary`.
- O ícone do launcher segue a cor: `AppIcon.sync` liga/desliga os activity-alias.

---

## 9. Convenções de trabalho

1. **Responder em português.**
2. **Toda mudança vira versão**: incrementar `versionCode` e `versionName` em
   `app/build.gradle.kts`, gerar o APK e copiar para a raiz como
   `NexTasks-<versão>-debug.apk`.
3. **Rodar `tools/limpar-versao.sh` logo depois de copiar o APK.** Ele deixa só o APK mais
   recente, apaga capturas de teste antigas, apaga snapshots do AVD e esvazia a Lixeira.
4. **Registrar no log diário** (`.workbuddy-ai/memory/YYYY-MM-DD.md`) o que mudou e por quê,
   incluindo bugs encontrados e decisões.
5. Comentários e strings de UI em português, sem acento em nomes de arquivo.

---

## 10. Playbook de teste no emulador

As regras puras têm teste JVM (`./gradlew testDebugUnitTest`). Gesto tem teste
instrumentado (`./gradlew connectedDebugAndroidTest`, hoje a pinça da grade). O que
depende de dados e navegação continua manual, no AVD.

**Cuidado**: `connectedAndroidTest` desinstala os APKs ao terminar, e desinstalar apaga
os dados do app no emulador. `gradle.properties` já traz
`android.injected.androidTest.leaveApksInstalledAfterRun=true` para evitar isso — não
remover essa linha.

```bash
# semear dados (o app lê de files/datastore/*.json)
cat seed.json | adb shell "run-as com.daniel.nextasks sh -c 'cat > files/datastore/tasks.json'"

# abrir o app (o alias depende da cor salva!)
COMP=$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER com.daniel.nextasks | grep "com.daniel.nextasks/" | tail -1)
adb shell am start -n "$COMP"
```

- Prints: `adb exec-out screencap -p > arquivo.png`.
- O emulador fica em **UTC**; o app usa a data do aparelho (diferença de 1 dia perto da
  meia-noite).

### Coordenadas de toque

**Nunca estime pela imagem.** O fator entre o que se vê e o pixel real muda com o scroll e
já fez toques errarem por uma linha inteira (conclusão falsa de "o componente não
responde"). O caminho confiável é `uiautomator dump` e tocar no centro dos `bounds` do nó:

```bash
adb shell uiautomator dump /sdcard/ui.xml
adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -oE 'text="Tarefas"[^/]*bounds="[^"]*"'
```

O print (`screencap`) serve para **conferir** o resultado, não para medir o toque.

---

## 11. Armadilhas conhecidas

| Armadilha | O que acontece | Como evitar |
|---|---|---|
| `rm` é função de shell | Manda para a Lixeira e **não libera espaço** | Usar `/usr/bin/rm` |
| Snapshot do AVD | ~2,5 GB por sessão com `-no-snapshot-load`; o antigo vai para a Lixeira | Usar `-no-snapshot` |
| Disco cheio | Emulador morre com erro de GPU ("ColorBufferGl") que **parece** gráfico | Checar espaço antes de culpar o emulador |
| `.MainActivity` | Desde a 3.22 não é exportada nem é o entry point | Resolver o activity-alias ativo |
| `collectAsState(initial = emptyList())` | Entrega lista vazia antes do primeiro valor real | Ler com `.first()` quando a ação for destrutiva |
| `LaunchedEffect(Unit)` com preferências | Roda no primeiro frame, quando ainda é o padrão | Chavear em `settings.enabledAddons` |
| Chips em `horizontalScroll` | Opção do fim fica invisível e "não existe" | `FlowRow` |
| Checkbox de bloco | Só aparece **depois** do horário de início | Testar com bloco já iniciado |
| Trava do AVD (`*.lock`) | Emulador não abre: "AVD já em uso" | Conferir o `pid` e remover |

---

## 12. Pendências

- **Assistente IA** é página placeholder — não há chat.
- **Conexões**: desmarcar o bloco reabre tarefa/sub-tarefa, mas **não** desfaz hábito —
  a conexão não sabe separar o que foi dela do que o usuário marcou à mão. Só dá para
  resolver registrando a origem de cada marcação.
- **Gate de conexões** verificado em execução na v3.35: com o addon desligado, confirmar
  o bloco grava a conclusão mas **não** conclui a tarefa conectada, e a seção some da
  gaveta.
- **Backup** não inclui preferências (`settings.json`).
- **Restauração parcial** é avisada: se o arquivo trouxer menos tabelas que as 12, a
  mensagem diz quantas foram aplicadas em vez de anunciar sucesso completo.
- **Débito conhecido**: cartão de tarefa reimplementado em três telas, formatters de
  data duplicados e seleção por cor sem rótulo para TalkBack. Não é bug de regra;
  é refatoração pendente.

---

## 13. Git

Repositório local desde a v3.34 (commit inicial), branch `main`, **sem remoto**.

**Regra**: **toda versão nova do app vira um commit.** O passo vem depois da limpeza:

```bash
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk NexTasks-3.35-debug.apk
./tools/limpar-versao.sh
git add -A
git commit -m "v3.35 — resumo curto do que mudou"
```

- **Mensagem**: `vX.Y — resumo` na primeira linha; o porquê nos parágrafos seguintes,
  em português. Uma versão por commit (sem juntar duas).
- **`.gitattributes` importa**: nesta máquina `core.autocrlf = true`, e CRLF quebra o
  bash. O arquivo força **LF** nos `.sh` e **CRLF** nos `.cmd`.
- **Fora do controle de versão**: `build/`, `.gradle/`, `local.properties`, `*.apk`.
  O APK é artefato de build — a raiz guarda o mais recente para instalar, mas ele não
  entra no repositório.
- `.workbuddy-ai/` **entra** no repositório: é a memória do projeto e explica decisões.

---

## 14. Histórico (resumo)

v1.x calendário e rotinas · v2.x blocos completáveis, cores, Home, tarefas ·
v3.0–3.21 heatmap, sub-tarefas, eventos, hábitos (121 ícones), ordem das tarefas,
cor de destaque · v3.22 ícone segue a cor · v3.23 logo do ícone maior ·
v3.24–3.25 addons · v3.26–3.31 conexões (página, edição, filtros, sugestões, meta,
avisos) · v3.32 addon desligado desliga comportamento · v3.33 gamificação não bloqueia nada ·
v3.34–3.40 conexões, zoom, testes, gamificação · v3.41 correções de regra (período da
conexão, XP negativo, meta semanal, cascata avisando conexões, agenda, heatmap).

Detalhe completo em `.workbuddy-ai/memory/`.
