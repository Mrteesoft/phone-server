param(
    [string]$OutputRoot = "app/src/main/assets/runtime/proot",
    [string]$WorkingRoot = "tmp-proot-vendor"
)

$ErrorActionPreference = "Stop"

$repoBase = "https://packages.termux.dev/apt/termux-main"
$architectures = @(
    @{
        AndroidAbi = "arm64-v8a"
        TermuxArch = "aarch64"
        ProotFile = "pool/main/p/proot/proot_5.1.107-70_aarch64.deb"
        TallocFile = "pool/main/libt/libtalloc/libtalloc_2.4.3_aarch64.deb"
    },
    @{
        AndroidAbi = "armeabi-v7a"
        TermuxArch = "arm"
        ProotFile = "pool/main/p/proot/proot_5.1.107-70_arm.deb"
        TallocFile = "pool/main/libt/libtalloc/libtalloc_2.4.3_arm.deb"
    },
    @{
        AndroidAbi = "x86_64"
        TermuxArch = "x86_64"
        ProotFile = "pool/main/p/proot/proot_5.1.107-70_x86_64.deb"
        TallocFile = "pool/main/libt/libtalloc/libtalloc_2.4.3_x86_64.deb"
    }
)

function New-CleanDirectory {
    param([string]$Path)

    if (Test-Path -LiteralPath $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force
    }
    New-Item -ItemType Directory -Path $Path | Out-Null
}

function Expand-DebPackage {
    param(
        [string]$DebPath,
        [string]$Destination
    )

    New-CleanDirectory -Path $Destination
    $bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $DebPath))

    if ($bytes.Length -lt 8) {
        throw "Invalid deb archive: $DebPath"
    }

    $magic = [System.Text.Encoding]::ASCII.GetString($bytes, 0, 8)
    if ($magic -ne "!<arch>`n") {
        throw "Unexpected ar header in $DebPath"
    }

    $offset = 8
    while ($offset + 60 -le $bytes.Length) {
        $header = [System.Text.Encoding]::ASCII.GetString($bytes, $offset, 60)
        $name = $header.Substring(0, 16).Trim()
        $sizeText = $header.Substring(48, 10).Trim()
        $fileSize = [int]$sizeText
        $dataOffset = $offset + 60

        if ($dataOffset + $fileSize -gt $bytes.Length) {
            throw "Corrupt ar entry in $DebPath"
        }

        $entryName = $name.TrimEnd("/")
        $entryPath = Join-Path $Destination $entryName
        [System.IO.File]::WriteAllBytes($entryPath, $bytes[$dataOffset..($dataOffset + $fileSize - 1)])

        $offset = $dataOffset + $fileSize
        if ($offset % 2 -ne 0) {
            $offset += 1
        }
    }

    $dataArchive = Get-ChildItem -LiteralPath $Destination -Filter "data.tar.*" | Select-Object -First 1
    if (-not $dataArchive) {
        throw "No data archive found in $DebPath"
    }

    return $dataArchive.FullName
}

function Expand-TarMembers {
    param(
        [string]$ArchivePath,
        [string]$Destination,
        [string[]]$Members
    )

    New-CleanDirectory -Path $Destination
    foreach ($member in $Members) {
        tar -xf $ArchivePath -C $Destination $member
    }
}

function Copy-BinaryFile {
    param(
        [string]$Source,
        [string]$Destination
    )

    $destinationDir = Split-Path -Parent $Destination
    if (-not (Test-Path -LiteralPath $destinationDir)) {
        New-Item -ItemType Directory -Path $destinationDir | Out-Null
    }

    Copy-Item -LiteralPath $Source -Destination $Destination -Force
}

New-CleanDirectory -Path $WorkingRoot
if (-not (Test-Path -LiteralPath $OutputRoot)) {
    New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null
}

foreach ($entry in $architectures) {
    $abiRoot = Join-Path $OutputRoot $entry.AndroidAbi
    New-CleanDirectory -Path $abiRoot
    New-Item -ItemType Directory -Path (Join-Path $abiRoot "lib") | Out-Null

    $downloadsRoot = Join-Path $WorkingRoot $entry.TermuxArch
    New-CleanDirectory -Path $downloadsRoot

    $prootDeb = Join-Path $downloadsRoot ([System.IO.Path]::GetFileName($entry.ProotFile))
    $tallocDeb = Join-Path $downloadsRoot ([System.IO.Path]::GetFileName($entry.TallocFile))

    Invoke-WebRequest -Uri "$repoBase/$($entry.ProotFile)" -OutFile $prootDeb
    Invoke-WebRequest -Uri "$repoBase/$($entry.TallocFile)" -OutFile $tallocDeb

    $prootArchive = Expand-DebPackage -DebPath $prootDeb -Destination (Join-Path $downloadsRoot "proot")
    $tallocArchive = Expand-DebPackage -DebPath $tallocDeb -Destination (Join-Path $downloadsRoot "libtalloc")

    $prootRoot = Join-Path $downloadsRoot "proot-root"
    $prootMembers = @(
        "./data/data/com.termux/files/usr/bin/proot",
        "./data/data/com.termux/files/usr/libexec/proot/loader"
    )
    if ($entry.TermuxArch -ne "arm") {
        $prootMembers += "./data/data/com.termux/files/usr/libexec/proot/loader32"
    }
    Expand-TarMembers -ArchivePath $prootArchive -Destination $prootRoot -Members $prootMembers

    $tallocRoot = Join-Path $downloadsRoot "libtalloc-root"
    Expand-TarMembers `
        -ArchivePath $tallocArchive `
        -Destination $tallocRoot `
        -Members @("./data/data/com.termux/files/usr/lib/libtalloc.so.2.4.3")

    Copy-BinaryFile `
        -Source (Join-Path $prootRoot "data/data/com.termux/files/usr/bin/proot") `
        -Destination (Join-Path $abiRoot "proot")

    Copy-BinaryFile `
        -Source (Join-Path $prootRoot "data/data/com.termux/files/usr/libexec/proot/loader") `
        -Destination (Join-Path $abiRoot "loader")

    $loader32Path = Join-Path $prootRoot "data/data/com.termux/files/usr/libexec/proot/loader32"
    if (Test-Path -LiteralPath $loader32Path) {
        Copy-BinaryFile `
            -Source $loader32Path `
            -Destination (Join-Path $abiRoot "loader32")
    }

    Copy-BinaryFile `
        -Source (Join-Path $tallocRoot "data/data/com.termux/files/usr/lib/libtalloc.so.2.4.3") `
        -Destination (Join-Path $abiRoot "lib/libtalloc.so.2")

    $metadata = @"
{
  "androidAbi": "$($entry.AndroidAbi)",
  "termuxArchitecture": "$($entry.TermuxArch)",
  "prootPackage": "$($entry.ProotFile)",
  "libtallocPackage": "$($entry.TallocFile)"
}
"@
    Set-Content -LiteralPath (Join-Path $abiRoot "metadata.json") -Value $metadata -Encoding ASCII
}
