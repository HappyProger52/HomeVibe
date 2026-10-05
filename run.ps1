param([switch]$AskOwnerPassword, [switch]$Test, [string]$AdminLogin)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    throw 'Укажите JAVA_HOME на установленный JDK 21. Инструкция в README.md.'
}
if ($AskOwnerPassword) {
    if (-not $AdminLogin) { $AdminLogin = Read-Host 'Логин администратора (латиница, цифры, от 3 символов)' }
    $env:HOMEVIBE_OWNER_LOGIN = $AdminLogin.Trim().ToLowerInvariant()
    $secret = Read-Host 'Пароль владельца (от 12 символов)' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try { $env:HOMEVIBE_OWNER_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
try {
    if ($Test) { & "$PSScriptRoot\mvnw.cmd" test }
    else { & "$PSScriptRoot\mvnw.cmd" spring-boot:run }
    if ($LASTEXITCODE -ne 0) { throw "Maven завершился с кодом $LASTEXITCODE" }
} finally {
    if ($AskOwnerPassword) { Remove-Item Env:HOMEVIBE_OWNER_PASSWORD -ErrorAction SilentlyContinue }
}
