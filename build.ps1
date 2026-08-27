param([switch]$RegenerateAssets, [string]$SourceProject)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$apiRoot = Join-Path $root 'third-party\build-api'
$proguard = Join-Path $root 'third-party\tools\proguard-base-5.3.3.jar'
$javac = $null
if ($env:JAVA_HOME) { $candidate = Join-Path $env:JAVA_HOME 'bin\javac.exe'; if (Test-Path $candidate) { $javac = Get-Item $candidate } }
if ($null -eq $javac) { $javac = Get-Command javac.exe -ErrorAction SilentlyContinue }
if ($null -eq $javac) { throw 'JDK 8 was not found. Install JDK 8 and set JAVA_HOME.' }
$javacPath = if ($javac.Source) { $javac.Source } else { $javac.FullName }
$javaVersion = (Get-Item $javacPath).VersionInfo.ProductVersion
if ($javaVersion -notmatch '^8\.') { throw "JDK 8 is required; found javac $javaVersion" }
if (-not (Test-Path $apiRoot)) { throw 'Bundled Java ME API stubs are missing.' }
if (-not (Test-Path $proguard)) { throw 'Bundled ProGuard 5.3.3 is missing.' }
if ($RegenerateAssets) {
    if (-not $SourceProject) { throw 'Use -SourceProject when -RegenerateAssets is specified.' }
    & powershell -ExecutionPolicy Bypass -File (Join-Path $root 'tools\convert-assets.ps1') -ProjectRoot $SourceProject
    if ($LASTEXITCODE -ne 0) { throw 'Asset conversion failed.' }
}

$jdkBin = Split-Path $javacPath -Parent
$jar = Join-Path $jdkBin 'jar.exe'
$java = Join-Path $jdkBin 'java.exe'
$build = Join-Path $root 'build'
$classes = Join-Path $build 'classes'
$dist = Join-Path $root 'dist'
Remove-Item -Recurse -Force $classes -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes, $dist | Out-Null
$apiSources = Get-ChildItem $apiRoot -Recurse -Filter '*.java' | Where-Object { $_.Name -ne 'Image.java' -and $_.Name -ne 'Font.java' } | ForEach-Object FullName
$localApiSources = Get-ChildItem (Join-Path $root 'src-api') -Recurse -Filter '*.java' | ForEach-Object FullName
$sources = Get-ChildItem (Join-Path $root 'src') -Recurse -Filter '*.java' | ForEach-Object FullName
& $javacPath '-source' '1.3' '-target' '1.1' '-d' $classes ($apiSources + $localApiSources + $sources)
if ($LASTEXITCODE -ne 0) { throw 'Crossy Road compilation failed.' }

$raw = Join-Path $build 'CrossyRoadS40-raw.jar'
$jarEntries = @('-C', $classes, 'CrossyRoadS40Midlet.class', '-C', $classes, 'CrossyRoadS40Midlet$SceneCanvas.class', '-C', $classes, 'CrossyRoadS40Midlet$SceneCanvas$Texture.class', '-C', $classes, 'AssetMeshes.class', '-C', $classes, 'TrainMesh.class', '-C', $classes, 'TrainFrontMesh.class', '-C', $classes, 'TrainBackMesh.class')
$generatedClasses = Get-ChildItem $classes -Filter '*.class' | Where-Object { $_.Name -like 'Mesh*.class' -or $_.Name -like 'Train*.class' }
foreach ($generatedClass in $generatedClasses) { $jarEntries += @('-C', $classes, $generatedClass.Name) }
& $jar 'cfm' $raw (Join-Path $root 'manifest.mf') $jarEntries
if ($LASTEXITCODE -ne 0) { throw 'Raw JAR creation failed.' }
if (Test-Path (Join-Path $root 'resources')) { & $jar 'uf' $raw '-C' (Join-Path $root 'resources') '.' }
if ($LASTEXITCODE -ne 0) { throw 'Raw JAR creation failed.' }
$proguardConfig = Join-Path $build 'proguard.pro'
@("-injars $raw", "-outjars $(Join-Path $dist 'CrossyRoadS40.jar')", "-libraryjars $apiRoot", '-dontshrink', '-dontoptimize', '-dontobfuscate', '-dontwarn', '-dontnote', '-microedition', '-keep public class CrossyRoadS40Midlet extends javax.microedition.midlet.MIDlet { public protected *; }', '-keepclassmembers class CrossyRoadS40Midlet { protected void startApp(); protected void pauseApp(); protected void destroyApp(boolean); }', '-keep class CrossyRoadS40Midlet$SceneCanvas { *; }', '-keep class CrossyRoadS40Midlet$SceneCanvas$Texture { *; }', '-keep public class AssetMeshes { public *; }', '-keep public class TrainMesh { public *; }', '-keep public class TrainFrontMesh { public *; }', '-keep public class TrainBackMesh { public *; }') | Set-Content -Encoding ascii $proguardConfig
Push-Location $root
try { & $java '-cp' $proguard 'proguard.ProGuard' ('@' + $proguardConfig) } finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { throw 'ProGuard/preverification failed.' }
$jarPath = Join-Path $dist 'CrossyRoadS40.jar'
$size = (Get-Item $jarPath).Length
@('MIDlet-Name: Crossy Road S40 Visual Spike','MIDlet-Version: 0.1.6','MIDlet-Vendor: WlaDiS','MIDlet-1: Crossy Road S40,,CrossyRoadS40Midlet','MicroEdition-Profile: MIDP-2.0','MicroEdition-Configuration: CLDC-1.1','MIDlet-Jar-URL: CrossyRoadS40.jar',('MIDlet-Jar-Size: ' + $size),'') | Set-Content (Join-Path $dist 'CrossyRoadS40.jad') -Encoding ascii
Write-Host "Built $jarPath ($size bytes)"
