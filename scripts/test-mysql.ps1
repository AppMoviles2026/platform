param([string[]]$MavenArguments = @('-q', 'test'))
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskNames = @('SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD',
    'SPRING_MAIL_HOST','SPRING_MAIL_PORT','COLLABPRO_TEST_MAILPIT','COLLABPRO_TEST_MAILPIT_URL')
$taskPrevious = @{}
foreach ($taskName in $taskNames) { $taskPrevious[$taskName] = [Environment]::GetEnvironmentVariable($taskName, 'Process') }
Push-Location $taskRoot
try {
    docker compose -f compose.test.yaml up -d --wait --wait-timeout 180
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron iniciar los servicios de prueba.' }
    $taskPort = if ($env:COLLABPRO_TEST_MYSQL_PORT) { $env:COLLABPRO_TEST_MYSQL_PORT } else { '3317' }
    $env:SPRING_DATASOURCE_URL = "jdbc:mysql://localhost:$taskPort/collabpro_test?connectionTimeZone=UTC"
    $env:SPRING_DATASOURCE_USERNAME = 'collabpro_test'
    $env:SPRING_DATASOURCE_PASSWORD = 'integration-test-only'
    $env:SPRING_MAIL_HOST = 'localhost'
    $env:SPRING_MAIL_PORT = '1026'
    $env:COLLABPRO_TEST_MAILPIT = 'true'
    $env:COLLABPRO_TEST_MAILPIT_URL = 'http://localhost:8026'
    & .\mvnw.cmd @MavenArguments
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas con MySQL/Mailpit. Revisa target/surefire-reports.' }
}
finally {
    foreach ($taskName in $taskNames) { [Environment]::SetEnvironmentVariable($taskName, $taskPrevious[$taskName], 'Process') }
    Pop-Location
}
