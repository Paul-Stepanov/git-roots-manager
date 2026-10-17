# 262 API notes — рекогносцировка платформы PhpStorm 2026.2 (build 262)

Источник: https://github.com/JetBrains/intellij-community, ветка `262`
(файлы скачаны и проверены 2026-10-17, пути указаны у каждого пункта).

## 1. Directory mappings — ProjectLevelVcsManager

`platform/vcs-api/src/com/intellij/openapi/vcs/ProjectLevelVcsManager.kt`

- `abstract fun getDirectoryMappings(): List<VcsDirectoryMapping>`
- `abstract fun setDirectoryMappings(mappings: List<VcsDirectoryMapping>)`
- `abstract fun getAllVcsRoots(): Array<VcsRoot>`
- Подписка на изменения маппингов — messageBus-топики компаньона:
  - `ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED: Topic<VcsMappingListener>` (project-level)
  - `VCS_CONFIGURATION_CHANGED_IN_PLUGIN: Topic<PluginVcsMappingListener>`
  - Метод `addVcsListener` у менеджера в 262 отсутствует — слушатель
  регистрируется через `project.messageBus.connect(disposable).subscribe(...)`.

## 2. VcsDirectoryMapping

`platform/vcs-api/src/com/intellij/openapi/vcs/VcsDirectoryMapping.java`

- `public final class VcsDirectoryMapping`
- Конструкторы: `(directory: String, vcs: String?)`, `(directory, vcs, rootSettings)`
- `getDirectory(): @SystemIndependent String` — нормализовано через
  `FileUtil.normalize` (прямые слеши); в памяти — раскрытый абсолютный путь:
  макросы `$PROJECT_DIR$` в `ProjectLevelVcsManagerImpl` не обрабатываются
  (grep по `macro|PROJECT_DIR` пуст), свёртка в макрос происходит только на
  уровне сериализации в `vcs.xml` (`@State(name="VcsDirectoryMappings",
  storages=[Storage("vcs.xml")])`, `ProjectLevelVcsManagerImpl.kt:84`).
- `getVcs(): String` (не null, пустая строка = `<none>`)
- **`DEFAULT_MAPPING_DIR = ""`** — маппинг `<Project>` кодируется пустой
  строкой; проверки `isDefaultMapping()`, `isNoneMapping()`.
- Следствие для адаптера: сравнение путей — по нормализованному абсолютному
  пути; корень проекта — отдельная сущность с пустым directory.

## 3. Игнор-лист незарегистрированных корней — VcsConfiguration

`platform/vcs-api/shared/src/com/intellij/openapi/vcs/VcsConfiguration.java`
(модульный source root — `shared/src`)

- `VcsConfiguration.getInstance(project)`
- `addIgnoredUnregisteredRoots(roots: Collection<String>)` (:251)
- `removeFromIgnoredUnregisteredRoots(roots: Collection<String>)` (:261)
- `isIgnoredUnregisteredRoot(root: String): Boolean` (:267)
- Хранение: `@State(..., storages = @Storage(StoragePathMacros.WORKSPACE_FILE))`
  (:35) — workspace.xml, переживает перезапуск.
- `VcsRootProblemNotifier` в 262 больше не существует; преемник —
  `platform/vcs-impl/src/com/intellij/openapi/vcs/roots/VcsRootErrorsHandler.java`,
  который и вызывает этот API (`mySettings.addIgnoredUnregisteredRoots(...)`, :148).
- Путь в игнор-листе — та же строка, что `mapping.getDirectory()`.

## 4. Рескан корней — VcsRootScanner

`platform/vcs-impl/src/com/intellij/openapi/vcs/roots/VcsRootScanner.kt`

- `fun getInstance(project: Project): VcsRootScanner = project.service<VcsRootScanner>()` (:88)
- `fun scheduleScan()` (:203) — публичный, запускает обнаружение корней.
  Использовать для запроса рескана при открытии диалога.

## 5. Виджет статус-бара

`platform/ide-core/src/com/intellij/openapi/wm/StatusBarWidget.kt`

```kotlin
interface StatusBarWidget : Disposable {
  fun ID(): String
  fun getPresentation(): WidgetPresentation?
  fun install(statusBar: StatusBar) {}
  override fun dispose() {}
  interface WidgetPresentation {
    fun getTooltipText(): String?
    fun getShortcutText(): String? = null
    fun getClickConsumer(): Consumer<MouseEvent>? = null   // клик!
  }
  interface IconPresentation : WidgetPresentation {
    fun getIcon(): Icon?
  }
}
```

Образец: `platform/platform-impl/src/com/intellij/openapi/wm/impl/status/PowerSaveStatusWidgetFactory.java`
— виджет реализует `StatusBarWidget, StatusBarWidget.IconPresentation`,
`getClickConsumer(): Consumer<MouseEvent>`.

`platform/platform-api/src/com/intellij/openapi/wm/StatusBarWidgetFactory.java`

- EP: `com.intellij.statusBarWidgetFactory` (подтверждён и в
  `platform/platform-resources/src/META-INF/PlatformExtensionPoints.xml:267`, `dynamic="true"`)
- `getId()`, `getDisplayName()`, `default boolean isAvailable(Project)` (по
  умолчанию true; смена доступности требует явного
  `StatusBarWidgetsManager.updateWidget(factory)`), 
  `createWidget(Project, CoroutineScope)` (default → `createWidget(Project)`),
  `disposeWidget(widget)`.
- Скрытие/показ виджета по `isAvailable` — через
  `com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager`.

## 6. Стартовая активность

`platform/core-api/src/com/intellij/openapi/startup/StartupActivity.kt`

- EP `com.intellij.postStartupActivity` — `POST_STARTUP_ACTIVITY:
  ExtensionPointName<Any>` (:23); реализация — `interface ProjectActivity {
  suspend fun execute(project: Project) }` (:60).

## 7. Версии сборки (закреплены)

- IntelliJ Platform Gradle Plugin: **2.19.0** (plugins.gradle.org, latest)
- Kotlin JVM: **2.4.21** (последняя стабильная без суффиксов)
- Gradle: **9.8.1** (services.gradle.org/versions/current)
- Целевая IDE: `phpstorm("2026.2.3")`, `sinceBuild = "262"`

## Итог для дизайна

- API игнора СУЩЕСТВУЕТ и публичен → `VcsMappingService.ignoreRoot` — реальный
  вызов `VcsConfiguration.addIgnoredUnregisteredRoots`, fallback не нужен
  (но `StartupReapplier`/Guard остаются страховкой).
- Слушатель изменений — messageBus-топик `VCS_CONFIGURATION_CHANGED`, не
  `addVcsListener`.
- `<Project>`-маппинг — пустая строка directory: диалог отображает его
  как корень проекта, toggle для него не нужен (его не выключаем).

## Использовано
- Task 4 (уточнение): `VcsRootScanner` (impl-модуль) недоступен compile-time —
  `requestRescan()` использует публичный `VcsRootDetector.getInstance(project).getOrDetect()`
  (`platform/vcs-api/src/com/intellij/openapi/vcs/roots/VcsRootDetector.java`).
- Task 4: ProjectLevelVcsManager (get/setDirectoryMappings), VcsDirectoryMapping(directory, vcs), VcsConfiguration.getInstance: addIgnoredUnregisteredRoots/removeFromIgnoredUnregisteredRoots, VcsRootScanner.getInstance().scheduleScan(). Макроконвертация не нужна (см. п.2) — применяется только MappingLogic.normalizeDirectory.
