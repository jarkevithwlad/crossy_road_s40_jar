param([string]$ProjectRoot)

$ErrorActionPreference = 'Stop'
$portRoot = Split-Path $PSScriptRoot -Parent
if (-not $ProjectRoot) { $ProjectRoot = Split-Path $portRoot -Parent }
$projectRoot = (Resolve-Path $ProjectRoot).Path
$out = Join-Path $portRoot 'src\AssetMeshes.java'

function Read-Mesh([string]$relativePath, [int]$maxFaces) {
    $path = Join-Path $projectRoot $relativePath
    $vertices = New-Object System.Collections.Generic.List[double[]]
    $uvs = New-Object System.Collections.Generic.List[double[]]
    $triangles = New-Object System.Collections.Generic.List[int[]]
    foreach ($line in [IO.File]::ReadAllLines($path)) {
        if ($line.StartsWith('v ')) {
            $p = $line.Split(' ', [StringSplitOptions]::RemoveEmptyEntries)
            [void]$vertices.Add(@([double]$p[1], [double]$p[2], [double]$p[3]))
        } elseif ($line.StartsWith('vt ')) {
            $p = $line.Split(' ', [StringSplitOptions]::RemoveEmptyEntries)
            [void]$uvs.Add(@([double]$p[1], [double]$p[2]))
        } elseif ($line.StartsWith('f ')) {
            $p = $line.Split(' ', [StringSplitOptions]::RemoveEmptyEntries)
            if ($p.Length -ge 4) {
                $ids = @()
                $faceUvs = @()
                for ($i = 1; $i -lt $p.Length; $i++) {
                    $parts = $p[$i].Split('/')
                    $ids += ([int]$parts[0] - 1)
                    $faceUvs += $(if ($parts.Length -gt 1 -and $parts[1] -ne '') { [int]$parts[1] - 1 } else { -1 })
                }
                for ($i = 1; $i -lt $ids.Length - 1; $i++) {
                    if ($maxFaces -gt 0 -and $triangles.Count -ge $maxFaces) { break }
                    [void]$triangles.Add(@($ids[0], $ids[$i], $ids[$i + 1], $faceUvs[0], $faceUvs[$i], $faceUvs[$i + 1]))
                }
            }
        }
        if ($maxFaces -gt 0 -and $triangles.Count -ge $maxFaces) { break }
    }
    for ($i = 1; $i -lt $triangles.Count; $i++) {
        $current = $triangles[$i]
        $currentDepth = 0.0
        foreach ($id in @($current[0], $current[1], $current[2])) { $currentDepth += $vertices[$id][0] + $vertices[$id][2] }
        $j = $i - 1
        while ($j -ge 0) {
            $previousDepth = 0.0
            foreach ($id in @($triangles[$j][0], $triangles[$j][1], $triangles[$j][2])) { $previousDepth += $vertices[$id][0] + $vertices[$id][2] }
            if ($previousDepth -le $currentDepth) { break }
            $triangles[$j + 1] = $triangles[$j]
            $j--
        }
        $triangles[$j + 1] = $current
    }
    $data = New-Object System.Collections.Generic.List[int]
    foreach ($face in $triangles) {
        for ($corner = 0; $corner -lt 3; $corner++) {
            $id = $face[$corner]
            $v = $vertices[$id]
            [void]$data.Add([int][Math]::Round($v[0] * 256.0))
            [void]$data.Add([int][Math]::Round($v[1] * 256.0))
            [void]$data.Add([int][Math]::Round($v[2] * 256.0))
            $uv = if ($face[3 + $corner] -ge 0 -and $face[3 + $corner] -lt $uvs.Count) { $uvs[$face[3 + $corner]] } else { @([double]0.5, [double]0.5) }
            $u = [int][Math]::Max(0, [Math]::Min(255, [Math]::Round($uv[0] * 255.0)))
            $vCoord = [int][Math]::Max(0, [Math]::Min(255, [Math]::Round($uv[1] * 255.0)))
            [void]$data.Add(($u -shl 8) -bor $vCoord)
        }
    }
    return $data.ToArray()
}

$meshes = [ordered]@{
    ROW = @{ path = 'assets/models/environment/grass/model.obj'; faces = 0 }
    ROAD = @{ path = 'assets/models/environment/road/model.obj'; faces = 0 }
    RIVER = @{ path = 'assets/models/environment/river/0.obj'; faces = 0 }
    RAIL = @{ path = 'assets/models/environment/railroad/0.obj'; faces = 0 }
    CAR = @{ path = 'assets/models/vehicles/blue_car/0.obj'; faces = 0 }
    LOG = @{ path = 'assets/models/environment/log/1/0.obj'; faces = 0 }
    LILY = @{ path = 'assets/models/environment/lily_pad/0.obj'; faces = 0 }
    TREE = @{ path = 'assets/models/environment/tree/2/0.obj'; faces = 0 }
    BOULDER = @{ path = 'assets/models/environment/boulder/0/0.obj'; faces = 0 }
    HERO = @{ path = 'assets/models/characters/chicken/0.obj'; faces = 0 }
    TRAIN_LIGHT = @{ path = 'assets/models/environment/train_light/inactive/0.obj'; faces = 0 }
    TRAIN_LIGHT_ON1 = @{ path = 'assets/models/environment/train_light/active/0/0.obj'; faces = 0 }
    TRAIN_LIGHT_ON2 = @{ path = 'assets/models/environment/train_light/active/1/0.obj'; faces = 0 }
}

function Write-MeshClass([string]$className, [int[]]$data, [string]$description) {
    $chunkInts = 64 * 12
    $chunkCount = [int][Math]::Ceiling($data.Length / [double]$chunkInts)
    for ($chunk = 0; $chunk -lt $chunkCount; $chunk++) {
        $start = $chunk * $chunkInts
        $end = [Math]::Min($data.Length - 1, $start + $chunkInts - 1)
        $chunkData = $data[$start..$end]
        $chunkValues = ($chunkData | ForEach-Object { [string]$_ }) -join ', '
        $chunkName = $className + 'Part' + $chunk
        $chunkLines = New-Object System.Collections.Generic.List[string]
        [void]$chunkLines.Add('/** Generated mesh chunk; kept below the Nokia S40 bytecode limit. */')
        [void]$chunkLines.Add('public final class ' + $chunkName + ' {')
        [void]$chunkLines.Add('    private ' + $chunkName + '() {}')
        [void]$chunkLines.Add('    public static final int[] DATA = new int[] {' + $chunkValues + '};')
        [void]$chunkLines.Add('}')
        [IO.File]::WriteAllLines((Join-Path $portRoot ('src\' + $chunkName + '.java')), $chunkLines, [Text.Encoding]::ASCII)
    }
    $references = (0..($chunkCount - 1) | ForEach-Object { $className + 'Part' + $_ + '.DATA' }) -join ', '
    $aggregateLines = New-Object System.Collections.Generic.List[string]
    [void]$aggregateLines.Add('/** Generated mesh chunk table. */')
    [void]$aggregateLines.Add('public final class ' + $className + ' {')
    [void]$aggregateLines.Add('    private ' + $className + '() {}')
    [void]$aggregateLines.Add('    public static final int[][] DATA = new int[][] {' + $references + '};')
    [void]$aggregateLines.Add('}')
    [IO.File]::WriteAllLines((Join-Path $portRoot ('src\' + $className + '.java')), $aggregateLines, [Text.Encoding]::ASCII)
}

$lines = New-Object System.Collections.Generic.List[string]
[void]$lines.Add('/** Generated from Expo OBJ assets. Do not edit by hand. */')
[void]$lines.Add('public final class AssetMeshes {')
[void]$lines.Add('    private AssetMeshes() {}')
foreach ($entry in $meshes.GetEnumerator()) {
    $data = Read-Mesh $entry.Value.path $entry.Value.faces
    $className = 'Mesh'
    foreach ($part in $entry.Key.Split('_')) { $className += $part.Substring(0, 1) + $part.Substring(1).ToLower() }
    Write-MeshClass $className $data 'Generated mesh'
    [void]$lines.Add('    public static final int[][] ' + $entry.Key + ' = ' + $className + '.DATA;')
}
[void]$lines.Add('}')
[IO.File]::WriteAllLines($out, $lines, [Text.Encoding]::ASCII)
$trainData = Read-Mesh 'assets/models/vehicles/train/middle/0.obj' 626
Write-MeshClass 'TrainMesh' $trainData 'Generated train middle mesh'
$trainParts = @(
    @{ name = 'TrainFrontMesh'; path = 'assets/models/vehicles/train/front/0.obj' },
    @{ name = 'TrainBackMesh'; path = 'assets/models/vehicles/train/back/0.obj' }
)
foreach ($part in $trainParts) {
    $partData = Read-Mesh $part.path 0
    Write-MeshClass $part.name $partData 'Generated train part mesh'
}
New-Item -ItemType Directory -Force -Path (Join-Path $portRoot 'resources\textures') | Out-Null
$textureFiles = @{
    'grass.png' = 'assets/models/environment/grass/light-grass.png'
    'road.png' = 'assets/models/environment/road/stripes-texture.png'
    'river.png' = 'assets/models/environment/river/0.png'
    'rail.png' = 'assets/models/environment/railroad/0.png'
    'car.png' = 'assets/models/vehicles/blue_car/0.png'
    'log.png' = 'assets/models/environment/log/1/0.png'
    'lily.png' = 'assets/models/environment/lily_pad/0.png'
    'tree.png' = 'assets/models/environment/tree/2/0.png'
    'boulder.png' = 'assets/models/environment/boulder/0/0.png'
    'hero.png' = 'assets/models/characters/chicken/0.png'
    'train.png' = 'assets/models/vehicles/train/middle/0.png'
    'train-front.png' = 'assets/models/vehicles/train/front/0.png'
    'train-back.png' = 'assets/models/vehicles/train/back/0.png'
    'train-light.png' = 'assets/models/environment/train_light/inactive/0.png'
    'train-light-on1.png' = 'assets/models/environment/train_light/active/0/0.png'
    'train-light-on2.png' = 'assets/models/environment/train_light/active/1/0.png'
    'train-light-off.png' = 'assets/models/environment/train_light/active/0/0.png'
}
Add-Type -AssemblyName System.Drawing
foreach ($name in $textureFiles.Keys) {
    $outputTexture = Join-Path $portRoot ('resources\textures\' + $name)
    if ($name -eq 'train-light-off.png' -and (Test-Path $outputTexture)) { continue }
    $source = [Drawing.Bitmap]::new((Join-Path $projectRoot $textureFiles[$name]))
    $small = [Drawing.Bitmap]::new(64, 64)
    $graphics = [Drawing.Graphics]::FromImage($small)
    $graphics.DrawImage($source, 0, 0, 64, 64)
    if ($name -eq 'train-light-off.png') {
        for ($y = 0; $y -lt 64; $y++) {
            for ($x = 0; $x -lt 64; $x++) {
                $pixel = $small.GetPixel($x, $y)
                if ($pixel.A -gt 0 -and $pixel.R -gt ($pixel.G * 1.25) -and $pixel.R -gt ($pixel.B * 1.25)) {
                    $small.SetPixel($x, $y, [Drawing.Color]::FromArgb($pixel.A, [int]($pixel.R * 0.18), [int]($pixel.G * 0.18), [int]($pixel.B * 0.18)))
                }
            }
        }
    }
    $small.Save($outputTexture, [Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose(); $small.Dispose(); $source.Dispose()
}
Write-Host "Generated $out"
