# Crossy Road S40

Исходники порта Crossy Road для Java ME / Nokia Series 40.

В репозитории уже находятся сгенерированные Java-меши и уменьшенные PNG-текстуры, поэтому для обычной сборки исходный Expo-проект не требуется.

## Требования

- Windows PowerShell;
- JDK 8 (укажите его через переменную среды `JAVA_HOME` или добавьте `javac` в `PATH`).

Java ME API, M3G compile-time stubs и ProGuard 5.3.3 находятся в `third-party/` и подключаются относительно корня проекта.

## Сборка

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
powershell -ExecutionPolicy Bypass -File .\build-m3g.ps1
```

Чтобы заново конвертировать модели из отдельного Expo-проекта, используйте необязательные параметры:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-m3g.ps1 -RegenerateAssets -SourceProject ..\expo-project
```

Обычная сборка использует только файлы этого каталога и не обращается к абсолютным путям.
