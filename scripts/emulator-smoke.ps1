[CmdletBinding()]
param(
    [string]$Serial = "emulator-5554",
    [string]$Avd = "Pixel_6_API_31_2",
    [string]$Package = "com.lumabeat.app",
    [string]$Activity = "com.lumabeat.app.MainActivity",
    [string]$Apk = "app/build/outputs/apk/debug/app-debug.apk"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Resolve-AndroidSdk {
    $candidates = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk")
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

    $sdk = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (-not $sdk) { throw "Android SDK was not found." }
    return $sdk
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory)] [string[]]$Arguments,
        [switch]$AllowFailure
    )

    $output = & $script:Adb -s $Serial @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    $text = ($output | ForEach-Object { $_.ToString() }) -join "`n"
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "adb $($Arguments -join ' ') failed ($exitCode): $text"
    }
    return $text.Trim()
}

function Wait-ForBoot {
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        $state = Invoke-Adb -Arguments @("get-state") -AllowFailure
        $booted = Invoke-Adb -Arguments @("shell", "getprop", "sys.boot_completed") -AllowFailure
        if ($state -eq "device" -and $booted -eq "1") { return }
        Start-Sleep -Seconds 1
    }
    throw "Emulator '$Serial' did not finish booting within 120 seconds."
}

function Get-UiHierarchy {
    $remotePath = "/sdcard/lumabeat-smoke.xml"
    Invoke-Adb -Arguments @("shell", "uiautomator", "dump", $remotePath) | Out-Null
    $raw = Invoke-Adb -Arguments @("exec-out", "cat", $remotePath)
    Invoke-Adb -Arguments @("shell", "rm", "-f", $remotePath) -AllowFailure | Out-Null
    return [xml]$raw
}

function Assert-UiText {
    param([xml]$Hierarchy, [string]$Text)
    $node = $Hierarchy.SelectSingleNode("//node[@text='$Text']")
    if (-not $node) { throw "Expected UI text '$Text' was not found." }
}

$sdk = Resolve-AndroidSdk
$script:Adb = Join-Path $sdk "platform-tools\adb.exe"
$emulator = Join-Path $sdk "emulator\emulator.exe"

if (-not (Test-Path -LiteralPath $script:Adb)) { throw "adb.exe was not found in '$sdk'." }
if (-not (Test-Path -LiteralPath $emulator)) { throw "emulator.exe was not found in '$sdk'." }

$state = Invoke-Adb -Arguments @("get-state") -AllowFailure
if ($state -ne "device") {
    Write-Host "Starting AVD $Avd..." -ForegroundColor Cyan
    Start-Process -FilePath $emulator -ArgumentList @("-avd", $Avd, "-no-snapshot-load") | Out-Null
}

Wait-ForBoot
Write-Host "Emulator ready: $Serial" -ForegroundColor Green

$resolvedApk = (Resolve-Path -LiteralPath $Apk).Path
Invoke-Adb -Arguments @("install", "-r", $resolvedApk) | Write-Host
Invoke-Adb -Arguments @("logcat", "-b", "all", "-c") | Out-Null
Invoke-Adb -Arguments @("shell", "am", "force-stop", $Package) | Out-Null
Invoke-Adb -Arguments @("shell", "am", "start", "-W", "-n", "$Package/$Activity") | Write-Host
Start-Sleep -Seconds 2

$hierarchy = Get-UiHierarchy
Assert-UiText -Hierarchy $hierarchy -Text "LumaBeat"
Assert-UiText -Hierarchy $hierarchy -Text "Start beat tracking"
Assert-UiText -Hierarchy $hierarchy -Text "Beat response"
Assert-UiText -Hierarchy $hierarchy -Text "WiZ lights"

$crashLog = Invoke-Adb -Arguments @("logcat", "-b", "crash", "-d", "-v", "brief") -AllowFailure
if ($crashLog -match [regex]::Escape($Package)) {
    throw "LumaBeat reported a runtime crash.`n$crashLog"
}

Write-Host "PASS: LumaBeat launched and exposed its primary TV controls." -ForegroundColor Green
Write-Host "The emulator cannot validate WiZ LAN discovery; use a physical phone for that check." -ForegroundColor Yellow
