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
| Persistência | DataStore Preferences/Proto? Não — **DataStore + JSON** (`JsonListSerializer`) |
| Serialização | kotlinx.serialization |
| minSdk / target / compile | 26 / 37 / 37 |
| Java | 17 |
| Versão atual | `versionName 3.33`, `versionCode 54` |
| Tamanho | ~78 arquivos Kotlin, ~16.500 linhas, 452 strings |

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
```

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
| `*Repository.kt` | Um por entidade; expõe `Flow<List<T>>` e funções de escrita |
| `*Stats.kt`, `SubtaskCascade.kt`, `TaskSort.kt` | Regras puras (sem Android) |
| `Connection*.kt` | Modelo, repositório, motor e sugestões de conexão |
| `BackupService.kt` | Exporta/importa as 10 tabelas JSON |

Cada entidade tem **um arquivo** em `files/datastore/`:

```
routines.json  tasks.json  subtasks.json  habits.json  habit_completions.json
completions.json  agenda_events.json  extra_blocks.json  block_overrides.json
connections.json  settings.json
```

Todos os stores usam `ReplaceFileCorruptionHandler { emptyList() }` (ou `AppSettings()`),
então **um arquivo corrompido vira lista vazia** em vez de derrubar o app.

### 4.2 `ui/` — telas

```
NexTasksApp.kt      hospeda a gaveta, roteia por AppDestination
AppDestination.kt   enum das seções (rótulo + ícone + addon dono)
AppIcon.kt          sincroniza o activity-alias com a cor de destaque
home/ calendar/ routines/ tasks/ habits/ connections/ settings/
components/         cards, campos de formulário, diálogo de nota
theme/              cores, tipografia, acentos
```

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
| Gamificação | Nada — só a página (é placeholder). Não esconder "Sequência" da Home |
| IA | Nada — só a página (placeholder) |

Quando a gamificação ganhar pontos/conquistas de verdade, é ali que se aplica o portão —
e **só** então.

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

### 7.3 Sugestões de conexão

Cruza nome de bloco com nome de entidade: iguais (nota 2) ou um contendo o outro com
**pelo menos 4 letras** (nota 1). Sem esse limite, "ir" casaria com "dormir".
Ignora pares já conectados e entidades concluídas.

### 7.4 Backup

Exporta as 10 tabelas JSON. **Não** exporta `settings.json` (tema, cor, addons, ordem) —
restaurar um backup não devolve preferências.

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

Não há testes automatizados úteis (só o esqueleto do JUnit). O teste é manual, no AVD.

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
responde"). Meça a faixa de texto/cor com ImageMagick e confirme qual elemento acendeu.

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

- **Gamificação e IA** são páginas placeholder — sem pontos, conquistas ou chat.
- **Conexões**: não avisa quando duas entidades disputam o mesmo bloco (ambas avançam);
  não estima "no seu ritmo, conclui em ~N dias".
- **Gate de conexões** com o addon desligado está implementado, mas **não foi verificado
  em execução** (o emulador quebrou na sessão). Teste manual: com Conexões off, confirmar
  um bloco não deve concluir a tarefa conectada.
- **Backup** não inclui preferências (`settings.json`).

---

## 13. Histórico (resumo)

v1.x calendário e rotinas · v2.x blocos completáveis, cores, Home, tarefas ·
v3.0–3.21 heatmap, sub-tarefas, eventos, hábitos (121 ícones), ordem das tarefas,
cor de destaque · v3.22 ícone segue a cor · v3.23 logo do ícone maior ·
v3.24–3.25 addons · v3.26–3.31 conexões (página, edição, filtros, sugestões, meta,
avisos) · v3.32 addon desligado desliga comportamento · v3.33 gamificação não bloqueia nada.

Detalhe completo em `.workbuddy-ai/memory/`.
