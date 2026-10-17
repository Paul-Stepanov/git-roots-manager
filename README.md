# git-roots-manager

Плагин PhpStorm: переключение отслеживания git-корней монорепозитория
из статус-бара. Клик по чекбоксу применяется мгновенно, без кнопки Apply.

## Сборка

```bash
./gradlew buildPlugin
```

Результат: `build/distributions/git-roots-manager-0.1.0.zip`.

Требования: JDK 21 (toolchain). Сборка идёт против локально установленного
PhpStorm — путь в `build.gradle.kts` (`intellijPlatform { local(...) }`),
так как `download.jetbrains.com` для PhpStorm недоступен из региона.

## Установка

Settings → Plugins → ⚙ → **Install Plugin from Disk** → выбрать zip →
перезапустить IDE.

## Использование

- иконка в статус-баре (справа внизу) → диалог со списком git-корней проекта,
  включая вложенные;
- сняли чекбокс — репозиторий мгновенно исчезает из отслеживания (панель
  Commit и т.д.); поставили — мгновенно возвращается;
- выключенные корни не возвращаются после пере-детекции платформы и
  перезапуска IDE.

## Где хранится состояние

- включённые корни — `.idea/vcs.xml` (штатные directory mappings платформы);
- выключенные — `workspace.xml` (компонент `GitRootsManagerState`) и
  игнор-лист `VcsConfiguration` (workspace), чтобы детектор корней не
  предлагал их снова.
