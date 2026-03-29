param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$firebaseJsonFiles = Get-ChildItem -Path (Join-Path $repoRoot "src/main/resources/firebase") -Filter *.json -File -ErrorAction SilentlyContinue
if ($firebaseJsonFiles) {
    throw "Firebase credential JSON files must not exist under src/main/resources/firebase. Use FIREBASE_CREDENTIALS_PATH or FIREBASE_CREDENTIALS_BASE64 at runtime instead."
}

if ($env:MVN_BIN) {
    $mvnBin = $env:MVN_BIN
} else {
    $bundledMaven = Join-Path $repoRoot "..\\.tools\\apache-maven-3.9.9\\bin\\mvn.cmd"
    if (Test-Path $bundledMaven) {
        $mvnBin = $bundledMaven
    } else {
        $mvnCommand = Get-Command mvn -ErrorAction SilentlyContinue
        if (-not $mvnCommand) {
            throw "No Maven executable found. Set MVN_BIN or install Maven."
        }
        $mvnBin = $mvnCommand.Source
    }
}

& $mvnBin clean verify @MavenArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
