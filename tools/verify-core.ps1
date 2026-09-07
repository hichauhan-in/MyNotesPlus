param(
    [string]$ToolchainRoot = (Join-Path $env:TEMP 'mynotes-validation-2.2.10'),
    [switch]$InstallToolchain
)

$ErrorActionPreference = 'Stop'
if ($InstallToolchain) {
    [System.IO.Directory]::CreateDirectory($ToolchainRoot) | Out-Null
    if (-not (Test-Path (Join-Path $ToolchainRoot 'java'))) {
        $asset = (Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse')[0]
        $archive = Join-Path $ToolchainRoot 'jdk.zip'
        Invoke-WebRequest $asset.binary.package.link -OutFile $archive
        if ((Get-FileHash $archive -Algorithm SHA256).Hash -ne $asset.binary.package.checksum) { throw 'JDK checksum mismatch' }
        Expand-Archive $archive -DestinationPath (Join-Path $ToolchainRoot 'java') -Force
    }
    if (-not (Test-Path (Join-Path $ToolchainRoot 'kotlinc'))) {
        $archive = Join-Path $ToolchainRoot 'kotlin.zip'
        $release = 'https://github.com/JetBrains/kotlin/releases/download/v2.2.10/kotlin-compiler-2.2.10.zip'
        Invoke-WebRequest $release -OutFile $archive
        $checksum = (Invoke-RestMethod "$release.sha256").Trim().Split(' ')[0]
        if ((Get-FileHash $archive -Algorithm SHA256).Hash -ne $checksum) { throw 'Kotlin checksum mismatch' }
        Expand-Archive $archive -DestinationPath $ToolchainRoot -Force
    }
    $dependencies = @{
        'junit.jar' = 'junit/junit/4.13.2/junit-4.13.2.jar'
        'hamcrest.jar' = 'org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar'
        'json.jar' = 'org/json/json/20250517/json-20250517.jar'
    }
    foreach ($name in $dependencies.Keys) {
        if (-not (Test-Path (Join-Path $ToolchainRoot $name))) {
            Invoke-WebRequest ("https://repo.maven.apache.org/maven2/" + $dependencies[$name]) -OutFile (Join-Path $ToolchainRoot $name)
        }
    }
}

$java = Get-ChildItem (Join-Path $ToolchainRoot 'java') -Filter java.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
if (-not $java) { throw 'Run with -InstallToolchain to prepare a temporary Java/Kotlin test environment.' }
$classpath = "$ToolchainRoot\kotlinc\lib\kotlin-stdlib.jar;$ToolchainRoot\junit.jar;$ToolchainRoot\hamcrest.jar;$ToolchainRoot\json.jar"
$sources = @(
    'app/src/main/java/com/example/domain/model/ExpenseLedger.kt',
    'app/src/main/java/com/example/data/local/ExpenseCodec.kt',
    'app/src/main/java/com/example/data/export/ExpenseReport.kt',
    'app/src/main/java/com/example/domain/model/AttachmentMarkup.kt',
    'app/src/main/java/com/example/data/share/ShareImportPolicy.kt',
    'app/src/test/java/com/example/domain/model/ExpenseLedgerTest.kt',
    'app/src/test/java/com/example/data/local/ExpenseCodecTest.kt',
    'app/src/test/java/com/example/data/export/ExpenseReportTest.kt',
    'app/src/test/java/com/example/data/share/ShareImportPolicyTest.kt'
)
$suites = @(
    'com.example.domain.model.ExpenseLedgerTest',
    'com.example.data.local.ExpenseCodecTest',
    'com.example.data.export.ExpenseReportTest',
    'com.example.data.share.ShareImportPolicyTest'
)

Push-Location (Join-Path $PSScriptRoot '..')
try {
    & $java -cp "$ToolchainRoot\kotlinc\lib\*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath $classpath -d "$ToolchainRoot\tests" @sources
    if ($LASTEXITCODE -ne 0) { throw 'Core Kotlin compilation failed' }
    & $java -cp "$ToolchainRoot\tests;$classpath" org.junit.runner.JUnitCore @suites
    if ($LASTEXITCODE -ne 0) { throw 'Core regression tests failed' }

    & $java -cp "$ToolchainRoot\kotlinc\lib\*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$ToolchainRoot\kotlinc\lib\kotlin-compiler.jar;$ToolchainRoot\kotlinc\lib\kotlin-stdlib.jar" -d "$ToolchainRoot\syntax" 'tools/KotlinSyntaxCheck.kt'
    if ($LASTEXITCODE -ne 0) { throw 'Syntax checker compilation failed' }
    $kotlinFiles = @(Get-ChildItem 'app/src' -Filter *.kt -Recurse | Select-Object -ExpandProperty FullName)
    $kotlinFiles += @('build.gradle.kts', 'settings.gradle.kts', 'app/build.gradle.kts')
    & $java -cp "$ToolchainRoot\syntax;$ToolchainRoot\kotlinc\lib\*" KotlinSyntaxCheckKt @kotlinFiles
    if ($LASTEXITCODE -ne 0) { throw 'Kotlin syntax validation failed' }

    [xml]$manifest = Get-Content -Raw 'app/src/main/AndroidManifest.xml'
    $androidNamespace = 'http://schemas.android.com/apk/res/android'
    if ($manifest.manifest.application.GetAttribute('allowBackup', $androidNamespace) -ne 'false') { throw 'OS backup guard missing' }
    if ($manifest.manifest.application.GetAttribute('usesCleartextTraffic', $androidNamespace) -ne 'false') { throw 'Cleartext transport guard missing' }
    if ((Get-Content -Raw 'app/src/main/java/com/example/di/AppContainer.kt').Contains('fallbackToDestructiveMigration')) { throw 'Destructive migration fallback present' }
    if ((Get-Content -Raw 'app/src/main/java/com/example/ui/lock/AppLock.kt').Contains('rememberSaveable')) { throw 'Unlock state must not be saved across process death' }
    if ((Get-Content -Raw 'docs/index.html').Contains('your-email@example.com')) { throw 'Placeholder privacy contact remains' }
    'Security configuration checks passed. Android compilation, Compose tests, and device checks are still required.'
} finally {
    Pop-Location
}