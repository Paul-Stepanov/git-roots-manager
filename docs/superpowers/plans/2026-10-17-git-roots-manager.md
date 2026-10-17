# Git Roots Manager — план реализации

> **Для исполнителя:** план выполняется в этой сессии (native, по правилам
> пользователя сабагенты не создаются). Шаги с чекбоксами (`- [ ]`).
> Коммиты выполняет ТОЛЬКО пользователь: исполнитель лишь выводит текст
> коммита. Строку `Co-Authored-By` никогда не добавлять.

**Goal:** Плагин для PhpStorm 2026.2.3: иконка в статус-баре открывает модалку
со списком git-корней проекта; клик по чекбоксу мгновенно включает/выключает
отслеживание (directory mapping), выключенные корни не возвращаются.

**Architecture:** Чистая логика (списки маппингов, diff) — платформенно-независимые
Kotlin-объекты с юнит-тестами. Всё обращение к API платформы изолировано в
адаптер `VcsMappingService` (реализация по результатам рекогносцировки 262).
Выключенные корни — собственное персистентное состояние плагина в workspace-области,
при старте проекта пере-применяется. ФС на предмет `.git` плагин не сканирует.

**Tech Stack:** Kotlin, Gradle + IntelliJ Platform Gradle Plugin 2.x, JDK 21
(toolchain), PhpStorm 2026.2.3 (build 262), JUnit 4 для чистой логики.

**Spec:** `docs/superpowers/specs/2026-10-17-git-roots-manager-design.md`

## Global Constraints

- Язык Kotlin; `kotlin { jvmToolchain(21) }`; целевая платформа 262, `sinceBuild = "262"`.
- id и пакет плагина: `dev.stepanov.gitrootsmanager`, group `dev.stepanov`, version `0.1.0`.
- Отступы 4 пробела, длина строки ≤ 120; комментарии в коде —
  только необходимый KDoc («почему»), без комментарного мусора.
- Список корней = маппинги платформы + собственный список выключенных; скан ФС запрещён.
- Чекбокс применяет изменение мгновенно; без кнопки Apply; без профилей.
- `untilBuild` не задавать (плагин для личного использования на 262+).
- Версии плагинов Gradle/Kotlin исполнитель закрепляет актуальными стабильными
  с plugins.gradle.org / plugin portal (шаг проверки — в Task 1), не «по памяти».
- Текст каждого коммита предлагает исполнитель, коммитит пользователь.
- Ответы пользователю и KDoc — на русском; идентификаторы — английские.

## Review Focus

1. Выключенный корень возвращается после рескана/детекции платформы или
   перезапуска IDE → ожидание: остаётся выключенным. Пин: `StartupReapplier`
   и слушатель изменения маппингов (Task 8) + ручной смоук (Task 9, шаги 3–4).
2. Повторное включение восстанавливает маппинг не с той VCS (потеряно имя
   `"Git"`) → ожидание: возвращается исходный vcs-имя. Пин: тест
   `uniteKeepsDisabledVcsName` (Task 2, уровень модели) + ручной смоук
   (Task 9, шаг 4, сквозное восстановление через сервис).
3. Несовпадение путей (`$PROJECT_DIR$/sub` в vcs.xml vs абсолютный путь в
   состоянии) ломает дедупликацию → ожидание: одна сущность на корень. Пин:
   тесты `normalize*` (Task 2) + конвертация макроса в адаптере (Task 4,
   рекогносцировка семантики `getDirectory()`).
4. Зависание UI/нарушение EDT при переключении на большом списке → ожидание:
   отклик мгновенный, без фризов. Пин: ручной смоук (Task 9, шаг 2), вся
   платформенная работа — через сервис на EDT.
5. Диалог в проекте без git или в момент закрытия проекта → ожидание: виджет
   скрыт/диалог корректен, без исключений. Пин: проверка `isAvailable`
   (Task 6) + guard `project.isDisposed` (Task 7), ручной смоук (Task 9, шаг 5).

---

### Task 1: Скелет Gradle-проекта + рекогносцировка API 262

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`
- Create: `src/main/resources/META-INF/plugin.xml`
- Create: `src/main/resources/icons/gitRootsManager16.svg`, `src/main/resources/icons/gitRootsManager16_dark.svg`
- Create: `docs/superpowers/plans/262-api-notes.md`

**Interfaces:**
- Produces: собирающийся скелет (`./gradlew buildPlugin` →
  `build/distributions/git-roots-manager-0.1.0.zip`); файл заметок
  `262-api-notes.md`, на который опираются Tasks 4, 6, 8.

- [ ] **Step 1: Создать `settings.gradle.kts`**

```kotlin
rootProject.name = "git-roots-manager"
```

- [ ] **Step 2: Создать `build.gradle.kts`**

Версии `org.jetbrains.kotlin.jvm` и `org.jetbrains.intellij.platform` —
актуальные стабильные: kotlin с https://plugins.jetbrains.com/plugin/69531-kotlin,
IntelliJ Platform Gradle Plugin 2.x с https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform.
Закрепить выбранные версии в этом же файле и вписать их в `262-api-notes.md`.

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm") version "<PIN>"
    id("org.jetbrains.intellij.platform") version "<PIN>"
}

group = "dev.stepanov"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        phpstorm("2026.2.3")
    }
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
        }
    }
}

tasks {
    test {
        useJUnit()
    }
}
```

Если у 2.x нет хелпера `phpstorm(...)` — заменить на `create("PS", "2026.2.3")`;
зафиксировать фактический вариант в `262-api-notes.md`.

- [ ] **Step 3: Создать `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2g
kotlin.code.style=official
```

- [ ] **Step 4: Создать `.gitignore`**

```
.gradle/
build/
.idea/
```

- [ ] **Step 5: Создать минимальный `plugin.xml`**

```xml
<idea-plugin>
    <id>dev.stepanov.gitrootsmanager</id>
    <name>Git Roots Manager</name>
    <vendor>stepanov</vendor>
    <description>Переключение отслеживания git-репозиториев монорепозитория из статус-бара.</description>
    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.modules.vcs</depends>
</idea-plugin>
```

- [ ] **Step 6: Создать иконку 16×16 (light и dark)**

`gitRootsManager16.svg`:

```svg
<svg width="16" height="16" viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg">
  <g stroke="#6E6E6E" stroke-width="1.4" fill="none">
    <path d="M4.5 6v4"/>
    <path d="M6.5 4h3.5c1.2 0 2 .8 2 2v2"/>
  </g>
  <circle cx="4.5" cy="4" r="1.8" fill="#6E6E6E"/>
  <circle cx="4.5" cy="12" r="1.8" fill="#6E6E6E"/>
  <circle cx="12" cy="10" r="1.8" fill="#6E6E6E"/>
</svg>
```

`gitRootsManager16_dark.svg` — то же с `#CED0D6`.

- [ ] **Step 7: Рекогносцировка API платформы 262**

По исходникам https://github.com/JetBrains/intellij-community (ветка `262`;
поиск — GitHub code search или MCP `zread`) проверить и записать в
`docs/superpowers/plans/262-api-notes.md` фактические сигнатуры:

- `platform/vcs-api/.../ProjectLevelVcsManager` — `getDirectoryMappings()`,
  `setDirectoryMappings(...)`, семантика `VcsDirectoryMapping.getDirectory()`
  (возвращает ли `$PROJECT_DIR$`-макрос или раскрытый абсолютный путь).
- Игнор-лист детектора корней: кандидаты `platform/vcs-impl/.../roots/VcsRootProblemNotifier`
  (методы игнорирования корня), хранилище игнорируемых корней (workspace.xml,
  компонент `VcsManagerConfiguration`). Зафиксировать точный вызов или записать
  «API нет → используется только fallback StartupReapplier».
- API повторного рескана корней: кандидаты `VcsRootScanner`/`VcsRootProblemNotifier.rescanAndNotify*`.
- `platform/platform-api/.../wm/StatusBarWidget` — презентация иконки с
  обработчиком клика (кандидат `IconPresentation.getClickConsumer()`),
  и `StatusBarWidgetFactory` — состав методов в 262.
- EP-имена: `com.intellij.statusBarWidgetFactory`, `com.intellij.postStartupActivity`
  (тип `ProjectActivity`).
- `ProjectLevelVcsManager.addVcsListener` / `VcsListener.directoryMappingChanged()` —
  сигнатуры и корректный `Disposable` для снятия слушателя.
- API «обнаруженных, но ещё не зарегистрированных» корней (кандидаты:
  `VcsRootDetector`, данные `VcsRootProblemNotifier`) — решить, показывать ли
  в диалоге корни, ожидающие подтверждения детекции; если дешёвого API нет —
  фиксируем известное ограничение MVP (спека, «Модель корней»).

Каждый пункт — с путём к файлу-источнику и цитатой сигнатуры. Если ветка 262
недоступна — брать ближайшую (`master`) и пометить это в заметках.

- [ ] **Step 8: Сборка скелета**

Run: `./gradlew buildPlugin`
Expected: `BUILD SUCCESSFUL`, в `build/distributions/` лежит zip.

- [ ] **Step 9: Предложить текст коммита (коммитит пользователь)**

```
feat: scaffold gradle project and record platform 262 api notes
```

---

### Task 2: Чистое ядро — модель маппингов (TDD)

**Files:**
- Create: `src/main/kotlin/dev/stepanov/gitrootsmanager/model/Mapping.kt`
- Create: `src/main/kotlin/dev/stepanov/gitrootsmanager/model/DisabledRoot.kt`
- Create: `src/main/kotlin/dev/stepanov/gitrootsmanager/model/ManagedRoot.kt`
- Create: `src/main/kotlin/dev/stepanov/gitrootsmanager/model/MappingLogic.kt`
- Create: `src/main/kotlin/dev/stepanov/gitrootsmanager/model/RootsModel.kt`
- Test: `src/test/kotlin/dev/stepanov/gitrootsmanager/model/MappingLogicTest.kt`
- Test: `src/test/kotlin/dev/stepanov/gitrootsmanager/model/RootsModelTest.kt`

**Interfaces:**
- Produces (используют Tasks 4, 5, 7):

```kotlin
data class Mapping(val directory: String, val vcs: String)
data class DisabledRoot(val directory: String = "", val vcs: String = "")
data class ManagedRoot(val directory: String, val vcs: String, val tracked: Boolean)

object MappingLogic {
    fun normalizeDirectory(directory: String): String
    fun removeMapping(mappings: List<Mapping>, directory: String): List<Mapping>
    fun addMapping(mappings: List<Mapping>, directory: String, vcs: String): List<Mapping>
}

object RootsModel {
    fun unite(mappings: List<Mapping>, disabled: List<DisabledRoot>): List<ManagedRoot>
}
```

Контракты: `normalizeDirectory` — trim, срез хвостового `/` (кроме корня `"/"`),
пустая строка → пустая; `directory` для сравнения везде нормализован.
`removeMapping` — удаляет только точное совпадение `directory`, порядок сохраняется,
отсутствующий путь — no-op. `addMapping` — заменяет существующую запись с тем же
`directory` (сохраняя позицию) или добавляет в конец. `unite` — все маппинги
(`tracked = true`) в исходном порядке, затем выключенные, отсутствующие в
маппингах; сортировка по `directory` выполняется на стороне UI.

- [ ] **Step 1: Написать падающие тесты `MappingLogicTest`**

Имена и утверждения: `normalizeTrimsTrailingSlash` (`"/a/b/"` → `"/a/b"`),
`normalizeKeepsRoot` (`"/"` → `"/"`), `normalizeTrimsWhitespace`
(`" /a/b "` → `"/a/b"`), `normalizeEmptyStaysEmpty` (`""` → `""`),
`removeDeletesOnlyTarget`
(`[a, b, c]` минус `b` → `[a, c]`, порядок стабилен), `removeMissingIsNoOp`,
`addReplacesSameDirectoryKeepingPosition` (`b` → заменяется новым vcs),
`addAppendsNew`.

- [ ] **Step 2: Написать падающие тесты `RootsModelTest`**

`uniteMarksMappingsTracked`, `uniteAppendsDisabledNotInMappings`,
`uniteKeepsDisabledVcsName` (выключенный корень с vcs `"Git"` приходит в
`ManagedRoot.vcs` как `"Git"`), `uniteDropsDisabledPresentInMappings`.

- [ ] **Step 3: Прогнать тесты — падение**

Run: `./gradlew test`
Expected: FAIL — классы модели не существуют.

- [ ] **Step 4: Реализовать 5 файлов модели по сигнатурам и контрактам выше**

Тела тривиальны из контрактов; сравнение `directory` — только после
`normalizeDirectory`.

- [ ] **Step 5: Прогнать тесты — зелено**

Run: `./gradlew test`
Expected: PASS, все тесты.

- [ ] **Step 6: Предложить текст коммита**

```
feat: pure mapping logic for root toggling with unit tests
```

---

### Task 3: Персистентное состояние выключенных корней

**Files:**
- Create: `src/main/kotlin/dev/stepanov/gitrootsmanager/state/DisabledRootsState.kt`

**Interfaces:**
- Consumes: `DisabledRoot` (Task 2).
- Produces (используют Tasks 5, 8):

```kotlin
@Service(Service.Level.PROJECT)
@State(name = "GitRootsManagerState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class DisabledRootsState : PersistentStateComponent<DisabledRootsState.State> {
    class State { var disabled: MutableList<DisabledRoot> = mutableListOf() }
    fun getDisabled(): List<DisabledRoot>
    fun setDisabled(roots: List<DisabledRoot>)
    fun add(root: DisabledRoot)
    fun remove(directory: String)
    fun find(directory: String): DisabledRoot?
}
```

Хранение в workspace.xml — локально на машину, не коммитится (соответствует
решению дизайн-документа). `directory` хранится нормализованным.

- [ ] **Step 1: Реализовать `DisabledRootsState`**

XML-сериализация платформы требует bean-семантику: у `State` и элементов —
public-поля/бины с конструктором по умолчанию (`DisabledRoot` уже такой).

- [ ] **Step 2: Сборка**

Run: `./gradlew buildPlugin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Предложить текст коммита**

```
feat: persistent disabled-roots state stored in workspace
```

---

### Task 4: Адаптер к API платформы — `VcsMappingService`

**Files:**
- Create: `src/main/kotlin/dev/stepanov/gitrootsmanager/platform/VcsMappingService.kt`
- Create: `src/main/kotlin/dev/stepanov/gitrootsmanager/platform/PlatformVcsMappingService.kt`
- Modify: `docs/superpowers/plans/262-api-notes.md` (пометить использованное)

**Interfaces:**
- Consumes: `Mapping`, `MappingLogic` (Task 2); заметки 262 (Task 1).
- Produces (использует Task 5):

```kotlin
interface VcsMappingService {
    fun currentMappings(): List<Mapping>
    fun applyMappings(mappings: List<Mapping>)
    fun ignoreRoot(directory: String)
    fun requestRescan()
}
```

`PlatformVcsMappingService` — `@Service(Service.Level.PROJECT)` (light service,
в `plugin.xml` не регистрируется) + companion `fun get(project: Project): VcsMappingService`.
Контракт потоков: методы вызываются строго на EDT; переключение на EDT —
обязанность вызывающего (диалог и виджет уже на EDT; `StartupReapplier` —
`withContext(Dispatchers.EDT)`); `currentMappings` конвертирует `$PROJECT_DIR$`-макрос в
абсолютный путь (семантика — по заметкам 262) и нормализует через
`MappingLogic.normalizeDirectory`; `applyMappings` конвертирует обратно.
`ignoreRoot` и `requestRescan` — по сигнатурам из заметок; если API игнора
в 262 отсутствует — метод пустой с KDoc «покрывается StartupReapplier».

- [ ] **Step 1: Реализовать интерфейс и платформенную реализацию**

Использовать только сигнатуры, подтверждённые заметками 262; отклонения от
ожиданий — сначала правка заметок, потом код.

- [ ] **Step 2: Сборка**

Run: `./gradlew buildPlugin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Предложить текст коммита**

```
feat: platform adapter for vcs directory mappings
```

---

### Task 5: Оркестрация — `GitRootsService`

**Files:**
- Create: `src/main/kotlin/dev/stepanov/gitrootsmanager/core/GitRootsService.kt`

**Interfaces:**
- Consumes: `RootsModel`, `Mapping`, `ManagedRoot`, `DisabledRoot` (Task 2),
  `DisabledRootsState` (Task 3), `VcsMappingService` (Task 4).
- Produces (используют Tasks 6, 7, 8):

```kotlin
@Service(Service.Level.PROJECT)
class GitRootsService(private val project: Project) {
    fun listRoots(): List<ManagedRoot>
    fun setTracked(root: ManagedRoot, tracked: Boolean)
    companion object { fun get(project: Project): GitRootsService }
}
```

`listRoots` = `RootsModel.unite(VcsMappingService.currentMappings(), state.getDisabled())`.
`setTracked(false)`: убрать маппинг через `MappingLogic.removeMapping` →
`applyMappings`; `state.add(DisabledRoot(dir, vcs))`; `ignoreRoot(dir)`;
`requestRescan()`. `setTracked(true)`: `state.remove(dir)`; прочитать vcs-имя
из состояния ДО удаления; `MappingLogic.addMapping` → `applyMappings`.

- [ ] **Step 1: Реализовать сервис** (light service через `@Service`, без
  регистрации в `plugin.xml`)

- [ ] **Step 2: Сборка**

Run: `./gradlew buildPlugin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Предложить текст коммита**

```
feat: root listing and instant toggle orchestration service
```

---

### Task 6: Виджет статус-бара

**Files:**
- Create: `src/main/kotlin/dev/stepanov/gitrootsmanager/ui/GitRootsWidgetFactory.kt`
- Create: `src/main/kotlin/dev/stepanov/gitrootsmanager/ui/GitRootsWidget.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

**Interfaces:**
- Consumes: `GitRootsService` (Task 5), `GitRootsDialog` (Task 7 — на этот
  момент диалога ещё нет; клик в этом таске открывает заглушку-вызов, в Task 7
  заменяется на `GitRootsDialog(project).show()`).
- Produces: EP-виджет, видимый в проектах с git.

Состав методов `StatusBarWidgetFactory`/`StatusBarWidget` — по заметкам 262
(Task 1), не по памяти. `isAvailable(project)`: проект не disposed и есть
хотя бы один маппинг или одна запись в выключенных; иначе виджет скрыт.
Иконка: `gitRootsManager16.svg` (light/dark подхватываются суффиксом `_dark`).
Тултип: «Git Roots Manager». Клик — открытие диалога.

- [ ] **Step 1: Реализовать фабрику и виджет**

- [ ] **Step 2: Зарегистрировать в `plugin.xml`**

```xml
<statusBarWidgetFactory id="GitRootsManagerWidget" implementation="dev.stepanov.gitrootsmanager.ui.GitRootsWidgetFactory"/>
```

- [ ] **Step 3: Смоук в песочнице**

Run: `./gradlew runIde`
Expected: в «песочном» IDE на проекте с git видна иконка в статус-баре; в
проекте без git — скрыта. Закрыть песочницу.

- [ ] **Step 4: Предложить текст коммита**

```
feat: status bar widget opening roots manager dialog
```

---

### Task 7: Диалог со списком корней

**Files:**
- Create: `src/main/kotlin/dev/stepanov/gitrootsmanager/ui/GitRootsDialog.kt`
- Modify: `src/main/kotlin/dev/stepanov/gitrootsmanager/ui/GitRootsWidget.kt` (заглушка → `GitRootsDialog(project).show()`)

**Interfaces:**
- Consumes: `GitRootsService` (Task 5), `ManagedRoot` (Task 2).
- Produces: модальный `DialogWrapper` с мгновенным применением.

```kotlin
class GitRootsDialog(project: Project) : DialogWrapper(project, true)
```

Заголовок «Git Roots Manager». Панель: по строке на корень —
`JBCheckBox(displayPath, tracked)`; `displayPath` — путь относительно
`project.basePath`, при невозможности — абсолютный; сортировка строк по
`directory`. При открытии диалога — `VcsMappingService.requestRescan()`
(если API подтверждён заметками 262), после чего список читается заново.
Обработчик клика чекбокса: `GitRootsService.setTracked(root, isSelected)`,
затем состояние строки перечитывается из `listRoots()` (источник истины —
модель, не визуальное состояние). Внизу — только стандартная Close-кнопка.
Guard: если `project.isDisposed` — диалог не строится. Если список пуст —
сообщение «VCS roots not found».

- [ ] **Step 1: Реализовать диалог, заменить заглушку клика виджета**

- [ ] **Step 2: Смоук в песочнице**

Run: `./gradlew runIde`
Expected: клик по иконке открывает модалку со списком корней; клик чекбокса
мгновенно изменяет список в Settings → Version Control → Directory Mappings
(проверить открытой на фоне настройкой); «выключенный» корень исчезает из
панели Commit; Esc/Close закрывают.

- [ ] **Step 3: Предложить текст коммита**

```
feat: roots dialog with instant toggle on checkbox click
```

---

### Task 8: Пере-применение выключенных корней (старт проекта + изменения маппингов)

**Files:**
- Create: `src/main/kotlin/dev/stepanov/gitrootsmanager/startup/StartupReapplier.kt`
- Create: `src/main/kotlin/dev/stepanov/gitrootsmanager/startup/DisabledRootsGuard.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

**Interfaces:**
- Consumes: `GitRootsService` (Task 5), `DisabledRootsState` (Task 3),
  `VcsMappingService` (Task 4).
- Produces: гарантия анти-цели «выключенное не возвращается» — и при открытии
  проекта, и при изменении маппингов в течение сессии.

```kotlin
class StartupReapplier : ProjectActivity {
    override suspend fun execute(project: Project)
}

@Service(Service.Level.PROJECT)
class DisabledRootsGuard(private val project: Project)
```

Логика (общая для обоих триггеров, идемпотентная): вычислить пересечение
«выключенные из `DisabledRootsState` ∩ текущие маппинги»; если пусто — ничего
не делать (это же гасит петлю: собственная правка маппингов снова вызовет
слушатель, но увидит пустое пересечение); иначе убрать эти корни из маппингов,
не меняя состояние. Триггеры:

- `StartupReapplier` — при открытии проекта; корутина переключается на EDT
  (`withContext(Dispatchers.EDT)`) перед вызовом сервисов; там же
  `project.getService(DisabledRootsGuard::class.java)` ставит слушателя.
- `DisabledRootsGuard` — проектный light service; в конструкторе
  `ProjectLevelVcsManager.addVcsListener` (снятие — по `Disposable` проекта):
  любое изменение directory mappings платформой (пере-детекция, автодобавление)
  запускает повторную сверку. Сигнатуры `VcsListener`/`addVcsListener` —
  по заметкам 262.

Не выполняет ФС-операций, не блокирует открытие проекта (лёгкое сравнение
списков).

- [ ] **Step 1: Реализовать активность и guard-службу**

- [ ] **Step 2: Зарегистрировать в `plugin.xml`**

```xml
<postStartupActivity implementation="dev.stepanov.gitrootsmanager.startup.StartupReapplier"/>
```

- [ ] **Step 3: Сборка**

Run: `./gradlew buildPlugin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Предложить текст коммита**

```
feat: re-apply disabled roots on open and on mapping changes
```

---

### Task 9: Финальная сборка, установка и ручной смоук

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: всё выше. Результат — установленный в PhpStorm 2026.2.3 плагин.

- [ ] **Step 1: Написать `README.md`**

Кратко: сборка (`./gradlew buildPlugin`), установка zip через
Settings → Plugins → ⚙ → Install Plugin from Disk, как пользоваться
(иконка в статус-баре → чекбоксы), где лежит состояние (workspace.xml).

- [ ] **Step 2: Финальная сборка**

Run: `./gradlew clean buildPlugin test`
Expected: `BUILD SUCCESSFUL`, zip пересобран.

- [ ] **Step 3: Установить в PhpStorm и прогнать смоук по критериям приёмки спеки**

Чек-лист (из спеки, раздел «Критерии приёмки»):

1. Монорепо `logikamoloka-dev-env`: виджет виден, диалог показывает все
   git-корни, включая вложенные.
2. Снятие чекбокса — репозиторий мгновенно исчезает из отслеживания, в т.ч.
   после VCS-обновления (Fetch/refresh).
3. Перезапуск PhpStorm — выключенные остаются выключенными, уведомлений о
   «незарегистрированных корнях» нет (работа `StartupReapplier` + игнора).
4. Включение чекбокса — мгновенно возвращается с правильным vcs.
5. Проект без вложенных репозиториев — диалог показывает корневой маппинг,
   ошибок нет; в проекте вовсе без git виджет скрыт.
6. Ни одного зависания UI при переключении (списки из десятков корней).

Результат прогонa зафиксировать в ответе пользователю: что прошло, что нет.

- [ ] **Step 4: Предложить текст коммита**

```
docs: readme with build and install instructions
```

---

## Хвост зависимостей между задачами

Task 1 → 2 → (3, 4) → 5 → (6 → 7), 8 → 9. Задача 6 зависит от 7 только
заглушкой клика, поэтому допустим порядок 5 → 6 → 7 → 8 → 9.
