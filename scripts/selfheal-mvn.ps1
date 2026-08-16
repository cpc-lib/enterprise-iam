$ErrorActionPreference = "Continue"
Set-Location "d:\code\specs\enterprise-iam\backend"
$base = "https://maven.aliyun.com/repository/public"
$repo = "D:\repository"
$tmproot = "d:\code\specs\enterprise-iam\.m2fetch"
if (-not (Test-Path $tmproot)) { New-Item -ItemType Directory -Path $tmproot -Force | Out-Null }

# Purge .lastUpdated files
Get-ChildItem -Path $repo -Recurse -Filter "*.lastUpdated" -File -ErrorAction SilentlyContinue |
    ForEach-Object { Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue }

function Ensure-Dir([string]$path) {
    if (Test-Path $path -PathType Container) { return $false }
    New-Item -ItemType Directory -Path $path -Force | Out-Null
    return $true
}

$extRegex = '\.(jar|pom|sha1|sha256|sha512|md5|lastUpdated|xml|properties|module|zip|war|tar\.gz|class|txt)$'

function Is-FilePath([string]$path) {
    $leaf = Split-Path $path -Leaf
    return ($leaf -match $extRegex)
}

function Download-File([string]$repoPath) {
    $fileName = Split-Path $repoPath -Leaf
    $parent = Split-Path $repoPath -Parent
    Ensure-Dir $parent
    if (Test-Path $repoPath) { return $false }
    $url = $repoPath -replace [regex]::Escape($repo), $base -replace '\\', '/'
    $tmpFile = Join-Path $tmproot ($fileName + "_" + [guid]::NewGuid().ToString("N").Substring(0,8))
    & curl.exe -sL $url -o $tmpFile
    if ($LASTEXITCODE -eq 0 -and (Test-Path $tmpFile) -and (Get-Item $tmpFile).Length -gt 0) {
        Copy-Item $tmpFile $repoPath -Force
        Remove-Item $tmpFile -Force -ErrorAction SilentlyContinue
        Write-Host "  + $fileName"
        return $true
    }
    Remove-Item $tmpFile -Force -ErrorAction SilentlyContinue
    Write-Host "  FAILED: $url"
    return $false
}

function Parse-And-Fix([string]$output) {
    $fixed = 0
    $dirsToCreate = [System.Collections.Generic.HashSet[string]]::new()
    $filesToDownload = [System.Collections.Generic.HashSet[string]]::new()

    # Pattern A: AccessDeniedException: <path>
    foreach ($m in [regex]::Matches($output, 'AccessDeniedException: (D:\\repository\\[^\r\n]+)')) {
        $p = $m.Groups[1].Value.Trim()
        if (Is-FilePath $p) {
            [void]$filesToDownload.Add($p)
        } else {
            [void]$dirsToCreate.Add($p)
        }
    }

    # Pattern B: Not allow operate files: <path1>, <path2>
    foreach ($m in [regex]::Matches($output, 'Not allow operate files: ([^\r\n]+)')) {
        $paths = $m.Groups[1].Value -split ', ' |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ -like 'D:\repository\*' }
        foreach ($p in $paths) {
            $p = $p.TrimEnd('\')
            $leaf = Split-Path $p -Leaf
            # Skip .tmp files - derive real artifact from path
            if ($leaf -match '^(.+?)\.\d+\.tmp') {
                $realName = $Matches[1]
                $parentDir = Split-Path $p -Parent
                $realPath = Join-Path $parentDir $realName
                if (-not (Test-Path $realPath)) { [void]$filesToDownload.Add($realPath) }
            } elseif (Is-FilePath $p) {
                [void]$filesToDownload.Add($p)
            } else {
                [void]$dirsToCreate.Add($p)
            }
        }
    }

    # Pattern C: "the artifact groupId:artifactId:type:version has not been downloaded"
    foreach ($m in [regex]::Matches($output, 'the artifact ([^:]+):([^:]+):([^:]+):([^:\s]+) has not been downloaded')) {
        $gid = $m.Groups[1].Value; $aid = $m.Groups[2].Value; $type = $m.Groups[3].Value; $ver = $m.Groups[4].Value
        $groupPath = $gid -replace '\.', '/'
        $p = Join-Path $repo "$groupPath/$aid/$ver/$aid-$ver.$type"
        if (-not (Test-Path $p)) { [void]$filesToDownload.Add($p) }
    }

    # Pattern D: "Could not transfer artifact groupId:artifactId:type:version"
    foreach ($m in [regex]::Matches($output, 'Could not transfer artifact ([^:]+):([^:]+):([^:]+):([^:\s]+)')) {
        $gid = $m.Groups[1].Value; $aid = $m.Groups[2].Value; $type = $m.Groups[3].Value; $ver = $m.Groups[4].Value
        $groupPath = $gid -replace '\.', '/'
        $p = Join-Path $repo "$groupPath/$aid/$ver/$aid-$ver.$type"
        if (-not (Test-Path $p)) { [void]$filesToDownload.Add($p) }
    }

    # Create directories
    foreach ($d in $dirsToCreate) {
        if (Ensure-Dir $d) { Write-Host "  +DIR $d"; $fixed++ }
    }
    # Download files
    foreach ($f in $filesToDownload) {
        if (Download-File $f) { $fixed++ }
    }
    return $fixed
}

for ($i = 0; $i -lt 100; $i++) {
    Write-Host "`n=== ROUND $i ==="
    $out = & mvn -pl iam-auth-service -am test 2>&1 | Out-String
    if ($LASTEXITCODE -eq 0) {
        Write-Host "SUCCESS"
        Write-Host $out.Substring([Math]::Max(0, $out.Length - 2000))
        exit 0
    }
    # Purge .lastUpdated before each round
    Get-ChildItem -Path $repo -Recurse -Filter "*.lastUpdated" -File -ErrorAction SilentlyContinue |
        ForEach-Object { Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue }
    $fixed = Parse-And-Fix $out
    Write-Host "Round ${i}: fixed $fixed item(s)"
    if ($fixed -eq 0) {
        Write-Host "NO MORE FIXES - last 3000 chars:"
        Write-Host $out.Substring([Math]::Max(0, $out.Length - 3000))
        exit 1
    }
}
Write-Host "GAVE UP after 100 rounds"
exit 1
