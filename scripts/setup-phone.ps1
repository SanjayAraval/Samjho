<#
.SYNOPSIS
  One-shot setup of the Samjho demo phone: models -> build -> install -> Gemma.

.DESCRIPTION
  Samjho keeps its large third-party models out of git. This script puts them where the app
  expects them, builds the APK locally, installs it, and pushes the Gemma model to the phone.
  It is safe to re-run: models already in place are not copied again, and a Gemma file already
  on the phone with the right size is not pushed again.

  The Vosk models are bundled into the APK (app/src/main/assets). Gemma is NOT bundled; it is
  pushed to /data/local/tmp/llm/ on the phone. The app works fully without Gemma.

.PARAMETER ModelsPath
  A folder on this computer that holds the Vosk models. Each may be an unzipped folder named
  model-hi / model-en-in, an unzipped original (vosk-model-small-hi-0.22 /
  vosk-model-small-en-in-0.4), or the downloaded .zip. Only needed if the models are not already
  in app/src/main/assets.

.PARAMETER GemmaPath
  Path to Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task. Omit it to skip Gemma.

.PARAMETER Serial
  adb serial number, when more than one device is connected.

.PARAMETER SkipBuild
  Reuse the APK that is already built.

.EXAMPLE
  .\scripts\setup-phone.ps1 -ModelsPath C:\Downloads\vosk -GemmaPath C:\Downloads\Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task
#>
param(
    [string]$ModelsPath,
    [string]$GemmaPath,
    [string]$Serial,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$assets = Join-Path $repo 'app\src\main\assets'
$apk = Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk'
$gemmaName = 'Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task'
$gemmaDir = '/data/local/tmp/llm'

function Step($text) { Write-Host ""; Write-Host "== $text" -ForegroundColor Cyan }
function Ok($text) { Write-Host "   ok: $text" -ForegroundColor Green }
function Warn($text) { Write-Host "   warning: $text" -ForegroundColor Yellow }
function Fail($text) { Write-Host "   FAILED: $text" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------- tools

Step 'Finding the Android tools'
$sdk = $null
foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))) {
    if ($candidate -and (Test-Path (Join-Path $candidate 'platform-tools\adb.exe'))) { $sdk = $candidate; break }
}
$adb = $null
$onPath = Get-Command adb -ErrorAction SilentlyContinue
if ($onPath) { $adb = $onPath.Source } elseif ($sdk) { $adb = Join-Path $sdk 'platform-tools\adb.exe' }
if (-not $adb) { Fail 'adb not found. Install Android platform-tools, or set ANDROID_HOME.' }
Ok "adb: $adb"
if ($sdk -and -not $env:ANDROID_HOME) { $env:ANDROID_HOME = $sdk }
if (-not $env:ANDROID_HOME -and -not (Test-Path (Join-Path $repo 'local.properties'))) {
    Fail 'Android SDK not found. Set ANDROID_HOME or create local.properties with sdk.dir=...'
}

# ---------------------------------------------------------------- vosk models

Step 'Vosk speech models (bundled into the APK)'
$models = @(
    @{ Name = 'model-hi';    Original = 'vosk-model-small-hi-0.22' },
    @{ Name = 'model-en-in'; Original = 'vosk-model-small-en-in-0.4' }
)

function Find-ModelRoot([string]$dir) {
    if (Test-Path (Join-Path $dir 'am')) { return $dir }
    $nested = Get-ChildItem $dir -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path (Join-Path $_.FullName 'am') } | Select-Object -First 1
    if ($nested) { return $nested.FullName }
    return $null
}

$missing = @()
foreach ($m in $models) {
    $target = Join-Path $assets $m.Name
    if ((Test-Path $target) -and (Find-ModelRoot $target)) { Ok "$($m.Name) already in assets"; continue }

    if (-not $ModelsPath) { $missing += $m; continue }
    if (-not (Test-Path $ModelsPath)) { Fail "ModelsPath not found: $ModelsPath" }

    $source = $null
    foreach ($name in @($m.Name, $m.Original)) {
        $folder = Join-Path $ModelsPath $name
        if (Test-Path $folder -PathType Container) { $source = Find-ModelRoot $folder; if ($source) { break } }
    }
    if (-not $source) {
        $zip = Join-Path $ModelsPath ($m.Original + '.zip')
        if (Test-Path $zip) {
            $temp = Join-Path ([IO.Path]::GetTempPath()) ("samjho-" + $m.Original + "-" + [guid]::NewGuid().ToString('N'))
            Expand-Archive -Path $zip -DestinationPath $temp -Force
            $source = Find-ModelRoot $temp
        }
    }
    if (-not $source) { $missing += $m; continue }

    New-Item -ItemType Directory -Force $assets | Out-Null
    Copy-Item $source $target -Recurse -Force
    foreach ($part in 'am', 'conf', 'graph') {
        if (-not (Test-Path (Join-Path $target $part))) { Fail "$($m.Name) copied but '$part' is missing inside it" }
    }
    Ok "$($m.Name) copied from $source"
}
if ($missing.Count -gt 0) {
    Warn ("Vosk model(s) not found: " + (($missing | ForEach-Object { $_.Name }) -join ', '))
    Warn 'Continuing without them: the Android recogniser and the baked-in demos still work, but'
    Warn 'Vosk recording (and any Hindi recording) will be unavailable. See the README for downloads.'
}

# ---------------------------------------------------------------- build

if ($SkipBuild) {
    Step 'Build skipped'
} else {
    Step 'Building the debug APK (first build downloads dependencies)'
    Push-Location $repo
    try {
        & (Join-Path $repo 'gradlew.bat') ':app:assembleDebug' '--console=plain'
        if ($LASTEXITCODE -ne 0) { Fail "Gradle build failed (exit $LASTEXITCODE)" }
    } finally { Pop-Location }
}
if (-not (Test-Path $apk)) { Fail "APK not found at $apk" }
Ok ("APK: $apk (" + [math]::Round((Get-Item $apk).Length / 1MB, 1) + ' MB)')

# ---------------------------------------------------------------- device

Step 'Finding the phone'
$adbArgs = @()
if ($Serial) { $adbArgs = @('-s', $Serial) }
else {
    $attached = & $adb devices | Select-String -Pattern '\tdevice$' | ForEach-Object { ($_ -split '\t')[0] }
    if (-not $attached) { Fail 'No phone found. Plug it in, enable USB debugging, and accept the prompt on the phone.' }
    if (@($attached).Count -gt 1) { Fail ('More than one device: ' + ($attached -join ', ') + '. Pass -Serial.') }
    $adbArgs = @('-s', @($attached)[0])
}
$model = (& $adb @adbArgs shell getprop ro.product.model).Trim()
Ok "device: $($adbArgs[1]) ($model)"

Step 'Installing'
& $adb @adbArgs install -r $apk
if ($LASTEXITCODE -ne 0) { Fail 'adb install failed' }
Ok 'installed com.packetloss.samjho'

# ---------------------------------------------------------------- gemma

Step 'Gemma language model (pushed to the phone, not bundled)'
if (-not $GemmaPath) {
    Warn 'No -GemmaPath given, so Gemma is skipped. The app works fully without it.'
} elseif (-not (Test-Path $GemmaPath)) {
    Warn "GemmaPath not found: $GemmaPath. Skipping Gemma; the app works fully without it."
} else {
    $localSize = (Get-Item $GemmaPath).Length
    $remote = "$gemmaDir/$gemmaName"
    $remoteSize = (& $adb @adbArgs shell "stat -c %s $remote 2>/dev/null").Trim()
    if ($remoteSize -eq "$localSize") {
        Ok "already on the phone with the right size ($localSize bytes); not pushing again"
    } else {
        & $adb @adbArgs shell "mkdir -p $gemmaDir" | Out-Null
        Write-Host '   pushing about 530 MB, this takes a minute or two...'
        & $adb @adbArgs push $GemmaPath $remote
        if ($LASTEXITCODE -ne 0) {
            # Some phones reject the direct push with "remote fchown() failed"; go through /sdcard instead.
            Warn 'direct push failed; retrying through /sdcard/Download'
            & $adb @adbArgs push $GemmaPath "/sdcard/Download/$gemmaName"
            if ($LASTEXITCODE -ne 0) { Fail 'could not push the Gemma file' }
            & $adb @adbArgs shell "cp /sdcard/Download/$gemmaName $remote && chmod 644 $remote && rm /sdcard/Download/$gemmaName"
        }
        $after = (& $adb @adbArgs shell "stat -c %s $remote 2>/dev/null").Trim()
        if ($after -ne "$localSize") { Fail "Gemma size on the phone ($after) does not match the file ($localSize)" }
        Ok "Gemma is on the phone at $remote ($after bytes)"
    }
}

Step 'Done'
Write-Host '   Open Samjho on the phone. To check it offline, turn on airplane mode and switch Wi-Fi off.'
