param([switch]$RegenerateAssets, [string]$SourceProject)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$apiRoot = Join-Path $root 'third-party\build-api'
$m3gStubs = Join-Path $root 'third-party\m3g-stubs'
$proguard = Join-Path $root 'third-party\tools\proguard-base-5.3.3.jar'
$javac = $null
if ($env:JAVA_HOME) { $candidate = Join-Path $env:JAVA_HOME 'bin\javac.exe'; if (Test-Path $candidate) { $javac = Get-Item $candidate } }
if ($null -eq $javac) { $javac = Get-Command javac.exe -ErrorAction SilentlyContinue }
if ($null -eq $javac) { throw 'JDK 8 was not found. Install JDK 8 and set JAVA_HOME.' }
$javacPath = if ($javac.Source) { $javac.Source } else { $javac.FullName }
$javaVersion = (Get-Item $javacPath).VersionInfo.ProductVersion
if ($javaVersion -notmatch '^8\.') { throw "JDK 8 is required; found javac $javaVersion" }
if (-not (Test-Path $apiRoot)) { throw 'Bundled Java ME API stubs are missing.' }
if (-not (Test-Path $m3gStubs)) { throw 'Bundled M3G compile-time stubs are missing.' }
if (-not (Test-Path $proguard)) { throw 'Bundled ProGuard 5.3.3 is missing.' }
if ($RegenerateAssets) {
    if (-not $SourceProject) { throw 'Use -SourceProject when -RegenerateAssets is specified.' }
    & powershell -ExecutionPolicy Bypass -File (Join-Path $root 'tools\convert-assets.ps1') -ProjectRoot $SourceProject
    if ($LASTEXITCODE -ne 0) { throw 'Asset conversion failed.' }
}

$jdkBin = Split-Path $javacPath -Parent
$jar = Join-Path $jdkBin 'jar.exe'
$java = Join-Path $jdkBin 'java.exe'
$classes = Join-Path $root 'build\classes-m3g'
$dist = Join-Path $root 'dist'
Remove-Item -Recurse -Force $classes -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes, $dist | Out-Null

$apiSources = Get-ChildItem $apiRoot -Recurse -Filter '*.java' | Where-Object { $_.Name -ne 'Image.java' -and $_.Name -ne 'Graphics.java' -and $_.Name -ne 'Font.java' -and $_.Name -ne 'Canvas.java' } | ForEach-Object FullName
$localApiSources = Get-ChildItem (Join-Path $root 'src-api') -Recurse -Filter '*.java' | ForEach-Object FullName
$m3gSources = Get-ChildItem $m3gStubs -Filter '*.java' | Where-Object { $_.Name -ne 'Camera.java' -and $_.Name -ne 'Texture2D.java' } | ForEach-Object FullName
$localM3gSources = Get-ChildItem (Join-Path $root 'src-api-m3g') -Recurse -Filter '*.java' | ForEach-Object FullName
$meshSources = Get-ChildItem (Join-Path $root 'src') -Filter '*.java' | Where-Object { $_.Name -ne 'CrossyRoadS40Midlet.java' } | ForEach-Object FullName
$appSource = Join-Path $root 'src-m3g\CrossyRoadS40M3GMidlet.java'
& $javacPath '-source' '1.3' '-target' '1.1' '-d' $classes ($apiSources + $localApiSources + $m3gSources + $localM3gSources + $meshSources + $appSource)
if ($LASTEXITCODE -ne 0) { throw 'Crossy Road M3G compilation failed.' }

$raw = Join-Path $root 'build\CrossyRoadS40-M3G-raw.jar'
$entries = @('-C', $classes, 'CrossyRoadS40M3GMidlet.class', '-C', $classes, 'CrossyRoadS40M3GMidlet$SceneCanvas.class', '-C', $classes, 'CrossyRoadS40M3GMidlet$M3GMesh.class', '-C', $classes, 'AssetMeshes.class', '-C', $classes, 'TrainMesh.class', '-C', $classes, 'TrainFrontMesh.class', '-C', $classes, 'TrainBackMesh.class')
$generatedClasses = Get-ChildItem $classes -Filter '*.class' | Where-Object { $_.Name -like 'Mesh*.class' -or $_.Name -like 'Train*.class' }
foreach ($generatedClass in $generatedClasses) { $entries += @('-C', $classes, $generatedClass.Name) }
& $jar 'cfm' $raw (Join-Path $root 'manifest-m3g.mf') $entries
if ($LASTEXITCODE -ne 0) { throw 'M3G raw JAR creation failed.' }
& $jar 'uf' $raw '-C' (Join-Path $root 'resources') 'textures'
if ($LASTEXITCODE -ne 0) { throw 'M3G resource packaging failed.' }

Push-Location $root
try { & $java '-cp' $proguard 'proguard.ProGuard' '@build/proguard-m3g.pro' } finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { throw 'M3G preverification failed.' }
$jarPath = Join-Path $dist 'CrossyRoadS40-M3G.jar'
$size = (Get-Item $jarPath).Length
@('MIDlet-Name: Crossy Road','MIDlet-Version: 0.2.13','MIDlet-Vendor: WlaDiS','MIDlet-1: Crossy Road,,CrossyRoadS40M3GMidlet','MicroEdition-Profile: MIDP-2.0','MicroEdition-Configuration: CLDC-1.1','MIDlet-Jar-URL: CrossyRoadS40-M3G.jar',('MIDlet-Jar-Size: ' + $size),'') | Set-Content (Join-Path $dist 'CrossyRoadS40-M3G.jad') -Encoding ascii
Write-Host "Built $jarPath ($size bytes)"
