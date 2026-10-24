param(
    [ValidateSet('Run','Test','Package','Smoke')][string]$Action = 'Run',
    [string]$Repository = ''
)
$ErrorActionPreference = 'Stop'
if ($Action -eq 'Run') {
    $forgeKeyFile = Join-Path $PSScriptRoot '.forge-gemini-key.xml'
    $forgeFreeTierFile = Join-Path $PSScriptRoot '.forge-ai-free-tier-confirmed'
    if ($env:FORGE_AI_FREE_TIER_CONFIRMED -eq 'true') {
        Set-Content -LiteralPath $forgeFreeTierFile -Value 'true' -NoNewline
    } else {
        if (Test-Path -LiteralPath $forgeFreeTierFile) {
            $env:FORGE_AI_FREE_TIER_CONFIRMED = 'true'
        } else {
            $freeProject = Read-Host 'Is your Google AI Studio project on the free tier with billing disabled? [y/N]'
            if ($freeProject -match '^(y|yes)$') {
                $env:FORGE_AI_FREE_TIER_CONFIRMED = 'true'
                Set-Content -LiteralPath $forgeFreeTierFile -Value 'true' -NoNewline
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:GEMINI_API_KEY) -and $env:FORGE_AI_FREE_TIER_CONFIRMED -eq 'true') {
        if (Test-Path -LiteralPath $forgeKeyFile) {
            try {
                $forgeSecret = Import-Clixml -LiteralPath $forgeKeyFile
                $env:GEMINI_API_KEY = [System.Net.NetworkCredential]::new('', $forgeSecret).Password
            } catch {
                Write-Warning 'The saved Gemini key could not be unlocked for this Windows account. Enter it again below.'
            }
        }
        if ([string]::IsNullOrWhiteSpace($env:GEMINI_API_KEY)) {
            $forgeSecret = Read-Host 'Paste your Gemini API key (input is hidden; it will be encrypted for this Windows account)' -AsSecureString
            if ($forgeSecret.Length -gt 0) {
                $env:GEMINI_API_KEY = [System.Net.NetworkCredential]::new('', $forgeSecret).Password
                try { $forgeSecret | Export-Clixml -LiteralPath $forgeKeyFile -Force }
                catch { Write-Warning 'Could not save the encrypted key. FORGE will use it for this run only.' }
            }
        }
    }
}
Push-Location $PSScriptRoot
try {
    if (!$Repository) { $Repository = Join-Path $PSScriptRoot '.tools\m2' }
    New-Item -ItemType Directory -Force -Path $Repository | Out-Null
    $jdk = $env:JAVA_HOME
    if (!$jdk -or !(Test-Path (Join-Path $jdk 'bin\javac.exe'))) {
        $jdk = Get-ChildItem "$env:ProgramFiles\Java\jdk*" -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName
    }
    if (!$jdk) { throw 'Install JDK 21 or newer and set JAVA_HOME to its folder.' }
    $javaExe = Join-Path $jdk 'bin\java.exe'
    $mavenRoot = $env:MAVEN_HOME
    if (!$mavenRoot) {
        $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
        if ($mavenCommand) { $mavenRoot = Split-Path (Split-Path $mavenCommand.Source) }
    }
    if (!$mavenRoot) {
        $mavenRoot = Get-ChildItem "$env:USERPROFILE\.vscode\extensions\oracle.oracle-java-*\nbcode\java\maven" -Directory -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    }
    if (!$mavenRoot -or !(Test-Path (Join-Path $mavenRoot 'bin\m2.conf'))) {
        throw 'Install Apache Maven, or set MAVEN_HOME to an existing Maven folder. You can also import pom.xml in your IDE.'
    }
    $env:JAVA_HOME = $jdk
    [string[]]$goals = switch ($Action) {
        'Run' { @('compile','javafx:run') }
        'Test' { @('test') }
        'Package' { @('package') }
        'Smoke' { @('test-compile') }
    }
    # JavaFX must use the selected JDK, including when Windows PATH has a broken java shortcut.
    & $javaExe "-Dmaven.home=$mavenRoot" "-Dclassworlds.conf=$mavenRoot\bin\m2.conf" "-Dmaven.multiModuleProjectDirectory=$PSScriptRoot" -cp "$mavenRoot\boot\*" org.codehaus.plexus.classworlds.launcher.Launcher "-Dmaven.repo.local=$Repository" "-Djavafx.executable=$javaExe" -B @goals
    if ($LASTEXITCODE -ne 0) { throw "Maven failed with exit code $LASTEXITCODE." }
    if ($Action -eq 'Smoke') {
        $fx = (Get-ChildItem "$Repository\org\openjfx" -Recurse -Filter '*-21.0.6-win.jar').FullName -join ';'
        $libraries = @(
            "$Repository\com\google\code\gson\gson\2.13.2\gson-2.13.2.jar",
            "$Repository\org\xerial\sqlite-jdbc\3.53.4.0\sqlite-jdbc-3.53.4.0.jar",
            "$Repository\org\slf4j\slf4j-api\2.0.17\slf4j-api-2.0.17.jar",
            "$Repository\org\slf4j\slf4j-nop\2.0.17\slf4j-nop-2.0.17.jar"
        ) -join ';'
        $smokeId = [Guid]::NewGuid().ToString('N')
        & $javaExe --enable-native-access=ALL-UNNAMED "-Djavafx.cachedir=$PSScriptRoot\.tools\javafx" -cp "target\classes;target\test-classes;$fx;$libraries" edu.course.shop.GuiSmoke 'docs\screenshots' "target\smoke-$smokeId.db"
        if ($LASTEXITCODE -ne 0) { throw "GUI smoke test failed with exit code $LASTEXITCODE." }
    }
} finally { Pop-Location }
