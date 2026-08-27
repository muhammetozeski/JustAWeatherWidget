<#
======================================================================
  SmallestApk - Sablon derleme betigi  (PowerShell / pwsh)
----------------------------------------------------------------------
  Ne yapar:
    1) API\ altindaki TASINABILIR JDK + Android SDK + Gradle ile derler
       (internet GEREKMEZ, --offline).
    2) zipalign + imza uygular (EC anahtar).
    3) Imzali APK'yi Publish\ icine "$APK_ADI" adiyla koyar.

  Tum yollar bu betigin bulundugu klasore goredir; klasoru bir butun
  olarak kopyalayip baska yere tasiyabilirsin (dis bagimlilik yok).

  Kullanim:
    pwsh -File .\Derle.ps1                -> targetSdk 29 + v1-only imza (VARSAYILAN, en kucuk)
    pwsh -File .\Derle.ps1 -TargetSdk 33  -> targetSdk 33 + v1+v2+v3 imza (otomatik)
    pwsh -File .\Derle.ps1 -V2            -> targetSdk 29 ama yine de v2+v3 imza

  IMZA / TARGETSDK  (olculmus, bkz. RAPOR.md > "Imza"):
    Android 11 (API 30), targetSdk >= 30 olan APK'yi v2 imza yoksa KURMAZ.
    * targetSdk 29 + v1-only  -> 4809 bayt, apksigner API 21-34 araliginda dogruluyor.
    * targetSdk 33 + v1+v2+v3 -> 12837 bayt (+8 KB; 588 bayti imza, gerisi
      apksigner'in 4096'ya hizalama dolgusu - kaldirilamiyor).
    Bu yuzden varsayilan targetSdk 29'dur; 33 secersen imza otomatik v2+v3 olur.
======================================================================
#>
param(
    # 30 ve uzeri secersen imza otomatik v2+v3'e cikar (Android 11+ zorunlulugu).
    [ValidateRange(11, 35)]
    [int]$TargetSdk = 29,

    # targetSdk 29 olsa bile v2+v3 imza istersen (APK ~8 KB buyur).
    [switch]$V2
)
$ErrorActionPreference = 'Stop'

# targetSdk 30+ ise v2 ZORUNLU; altindaysa yalnizca -V2 dendiyse acilir.
$useV2 = $V2.IsPresent -or ($TargetSdk -ge 30)
$v2flag = if ($useV2) { 'true' } else { 'false' }

# ======================================================================
#  API KLASORU YOLU  (portable ama esnek)
#  - Bu satir TANIMLIYSA: API klasoru bu yoldan kullanilir. Boylece 1 GB'lik
#    API'yi her projeye kopyalamana gerek kalmaz (tek merkezden paylasilir);
#    proje klasorunu kopyalarken "API HARIC" kopyalaman yeter.
#  - Bu satiri YORUMA alirsan: betigin yanindaki .\API kullanilir
#    (tam tasinabilir, klasoru oldugu gibi kopyalama yontemi).
$API_DIR = 'C:\E\Claude\SmallestApk\API'
# ======================================================================

$ROOT = $PSScriptRoot
if ($API_DIR) { $API = $API_DIR } else { $API = Join-Path $ROOT 'API' }
$PROJ = Join-Path $ROOT 'Proje'
$PUB  = Join-Path $ROOT 'Publish'

# ----------------------------------------------------------------------
#  AYARLAR  (kendi projene gore degistir)
# ----------------------------------------------------------------------
$APK_ADI      = 'JustAWeatherWidget.apk'  # cikti APK dosya adi
$ROOT_PROJECT = 'JustAWeatherWidget'      # Proje\settings.gradle icindeki rootProject.name ile AYNI olmali
# ----------------------------------------------------------------------

if (-not (Test-Path $API))  { throw "API klasoru yok: $API" }
if (-not (Test-Path $PROJ)) { throw "Proje klasoru yok: $PROJ" }

$env:JAVA_HOME        = Join-Path $API 'jdk'
$env:ANDROID_HOME     = Join-Path $API 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $API 'gradle-home'
$env:PATH = "$($env:JAVA_HOME)\bin;$($env:ANDROID_HOME)\platform-tools;$($env:PATH)"

$BT = Join-Path $API 'android-sdk\build-tools\30.0.3'
$KS = Join-Path $API 'keystore\release-ec.jks'
$GRADLE = (Get-ChildItem (Join-Path $API 'gradle-home\wrapper\dists') -Recurse -Filter 'gradle.bat' -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
if (-not $GRADLE) { throw "Gradle bulunamadi: API\gradle-home\wrapper\dists\..." }

New-Item -ItemType Directory -Force $PUB | Out-Null

# SDK yolunu bu makineye gore taze yaz (silme yok, ustune yazar)
Set-Content -Encoding ascii (Join-Path $PROJ 'local.properties') ("sdk.dir=" + ($env:ANDROID_HOME -replace '\\','/'))

Write-Host ("[1/4] Derleniyor (offline, targetSdk {0})...  (debuggable+release oldugu icin lintVitalRelease atlanir)" -f $TargetSdk) -ForegroundColor Cyan
& $GRADLE -p $PROJ clean assembleRelease -x lintVitalRelease --offline "-PtargetSdk=$TargetSdk"
if ($LASTEXITCODE -ne 0) { throw 'Gradle derleme HATASI (yukaridaki ciktiya bak)' }

$uns = Join-Path $PROJ ("build\outputs\apk\release\{0}-release-unsigned.apk" -f $ROOT_PROJECT)
if (-not (Test-Path $uns)) { throw "Imzasiz APK bulunamadi: $uns  (ROOT_PROJECT ayari settings.gradle ile ayni mi?)" }

# --- deflate post-process (RAPOR.md > 5.6): IMZADAN ONCE yeniden paketle.
#     Gradle'in deflate'i en iyisi degil; resources.arsc STORED kalir, gerisi en yuksek
#     deflate ile sikistirilir. AGP'nin ekledigi META-INF\...\app-metadata.properties
#     de bu adimda dusuyor (calisma zamaninda kullanilmiyor).
$SEVENZ = Join-Path $API 'tools\7z.exe'
$packed = $uns
if (Test-Path $SEVENZ) {
    $work = Join-Path $PROJ 'build\_opt'
    $opt  = Join-Path $PROJ 'build\opt.zip'
    if (Test-Path $work) { Remove-Item -Recurse -Force $work }
    if (Test-Path $opt)  { Remove-Item -Force $opt }
    & $SEVENZ x $uns "-o$work" -y | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '7z acma HATASI' }
    Remove-Item -Recurse -Force (Join-Path $work 'META-INF') -ErrorAction SilentlyContinue
    Push-Location $work
    try {
        & $SEVENZ a -tzip $opt 'resources.arsc' -mm=Copy | Out-Null
        if ($LASTEXITCODE -ne 0) { throw '7z (arsc) HATASI' }
        & $SEVENZ a -tzip $opt '*' '-x!resources.arsc' -mm=Deflate -mx=9 -mfb=258 -mpass=15 -r | Out-Null
        if ($LASTEXITCODE -ne 0) { throw '7z (deflate) HATASI' }
    } finally { Pop-Location }
    $packed = $opt
    $kazanc = (Get-Item $uns).Length - (Get-Item $opt).Length
    Write-Host ("[2/4] Yeniden paketleme (7z deflate mx=9): {0} bayt kazanc" -f $kazanc) -ForegroundColor Cyan
} else {
    Write-Host '[2/4] 7z bulunamadi, yeniden paketleme atlandi' -ForegroundColor DarkYellow
}

$aligned = Join-Path $PROJ 'build\aligned.apk'
Write-Host '[3/4] Hizalama (zipalign -p -f 4)...' -ForegroundColor Cyan
& "$BT\zipalign.exe" -p -f 4 $packed $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign HATASI' }

$imzaAdi = if ($useV2) { 'v1+v2+v3' } else { 'v1-only JAR' }
Write-Host ("[4/4] Imzalama ({0}, EC anahtar)..." -f $imzaAdi) -ForegroundColor Cyan
$out = Join-Path $PUB $APK_ADI
if (Test-Path $out) { Remove-Item -LiteralPath $out -Force }
& "$BT\apksigner.bat" sign --ks $KS --ks-key-alias ec --ks-pass pass:android123 --key-pass pass:android123 --min-sdk-version 18 --v1-signing-enabled true --v2-signing-enabled $v2flag --v3-signing-enabled $v2flag --v4-signing-enabled false --out $out $aligned
if (-not (Test-Path $out)) { throw 'Imzalama HATASI: cikti olusmadi' }

$sz = (Get-Item $out).Length
$kb = [math]::Round($sz / 1024, 1)
Write-Host ''
Write-Host '====================================================' -ForegroundColor Green
Write-Host (" APK hazir : {0}" -f $out)        -ForegroundColor Green
Write-Host (" APK boyutu: {0} byte (~{1} KB)" -f $sz, $kb) -ForegroundColor Green
Write-Host (" targetSdk : {0}   Imza: {1}" -f $TargetSdk, $imzaAdi) -ForegroundColor Green
if ($useV2) {
    Write-Host ' Her Android surumune kurulur.' -ForegroundColor Green
} else {
    Write-Host ' targetSdk < 30 + v1 imza -> Android 11+ dahil her surume kurulur.' -ForegroundColor Green
}
Write-Host '====================================================' -ForegroundColor Green
Write-Host ''
Write-Host 'Telefona kurmak (ADB bagli olmali):' -ForegroundColor Yellow
Write-Host ('  {0}\android-sdk\platform-tools\adb.exe install -r "{1}"' -f $API, $out)
Write-Host ''
Write-Host 'KURULU boyutu dogrulamak icin README.md > "Kurulu boyutu dogrulama" bolumune bak.' -ForegroundColor Yellow

