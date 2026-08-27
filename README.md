[Русская версия / Russian version](README.ru.md)

# Crossy Road S40

Source code for a Crossy Road port to Java ME / Nokia Series 40.

The repository already contains generated Java meshes and reduced PNG textures, so the original Expo project is not required for a regular build.

## Requirements

- Windows PowerShell;
- JDK 8 (set it through the `JAVA_HOME` environment variable or add `javac` to `PATH`).

The Java ME API, M3G compile-time stubs, and ProGuard 5.3.3 are included in `third-party/` and referenced relative to the project root.

## Build

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
powershell -ExecutionPolicy Bypass -File .\build-m3g.ps1
```

To convert models again from a separate Expo project, use the optional parameters:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-m3g.ps1 -RegenerateAssets -SourceProject ..\expo-project
```

The regular build uses only files from this directory and does not rely on absolute paths.

## Controls

- `↑/↓/←/→` or `2/8/4/6` — move the character;
- `#` — switch orientation: Portrait, Rotate left, Rotate right, Native landscape;
- `*` — switch the 3D renderer scale: `100%`, `90%`, …, `20%`;
- press any key after Game Over to start a new game.

## Asset source

This project is ported from the original [EvanBacon/Expo-Crossy-Road](https://github.com/EvanBacon/Expo-Crossy-Road) project.
The models and their related source assets were taken from that project.
