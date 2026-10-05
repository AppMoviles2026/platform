$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
$previousLocation = Get-Location
$names = @('SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD', 'MAVEN_USER_HOME', 'COLLABPRO_TEST_MAILPIT')
$previousValues = @{}
foreach ($name in $names) { $previousValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    Set-Location -LiteralPath $repoPath
    docker compose up -d --wait
    if ($LASTEXITCODE -ne 0) { throw 'Docker MySQL did not start' }
    docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "CREATE DATABASE IF NOT EXISTS collabpro_test; GRANT ALL PRIVILEGES ON collabpro_test.* TO collabpro;"'
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the isolated test database' }
    $mysqlPort = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { '3307' }
    $env:SPRING_DATASOURCE_URL = "jdbc:mysql://localhost:${mysqlPort}/collabpro_test?connectionTimeZone=UTC"
    $env:SPRING_DATASOURCE_USERNAME = 'collabpro'
    $env:SPRING_DATASOURCE_PASSWORD = if ($env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD } else { 'collabpro-local-only' }
    $env:COLLABPRO_TEST_MAILPIT = 'true'
    $env:MAVEN_USER_HOME = Join-Path ([System.IO.Path]::GetTempPath()) 'collabpro-scaffold-maven-cache'
    & .\mvnw.cmd -B clean test
    if ($LASTEXITCODE -ne 0) { throw 'MySQL backend Identity / Campaign / SMTP tests failed' }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previousValues[$name], 'Process') }
    Set-Location -LiteralPath $previousLocation.Path
}
