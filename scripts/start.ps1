param(
    [ValidateSet("all", "backend", "frontend")]
    [string]$Target = "all",
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 5173,
    [string]$AdminUsername = $env:API_CONVERT_ADMIN_USERNAME,
    [string]$AdminPassword = $env:API_CONVERT_ADMIN_PASSWORD,
    [string]$DbType = $env:API_CONVERT_DB_TYPE,
    [string]$DatasourceUrl = $env:SPRING_DATASOURCE_URL,
    [string]$DatasourceUsername = $env:SPRING_DATASOURCE_USERNAME,
    [string]$DatasourcePassword = $env:SPRING_DATASOURCE_PASSWORD
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Set-Java25 {
    param([string]$RequestedJavaHome)

    if (-not [string]::IsNullOrWhiteSpace($RequestedJavaHome)) {
        $env:JAVA_HOME = $RequestedJavaHome
        $env:PATH = (Join-Path $RequestedJavaHome "bin") + [IO.Path]::PathSeparator + $env:PATH
    }
}

function Enable-CompactObjectHeaders {
    param([string]$JvmArgs)

    $updatedArgs = if ([string]::IsNullOrWhiteSpace($JvmArgs)) { "" } else { $JvmArgs.Trim() }
    if ($updatedArgs -notmatch '(^|\s)-XX:\+UseCompactObjectHeaders(\s|$)') {
        if ($updatedArgs -notmatch '(^|\s)-XX:\+UnlockExperimentalVMOptions(\s|$)') {
            $updatedArgs = ($updatedArgs + " -XX:+UnlockExperimentalVMOptions").Trim()
        }
        $updatedArgs = ($updatedArgs + " -XX:+UseCompactObjectHeaders").Trim()
    }
    return $updatedArgs
}

function Set-AdminCredentials {
    param(
        [string]$Username,
        [string]$Password
    )

    if (-not [string]::IsNullOrWhiteSpace($Username)) {
        $env:API_CONVERT_ADMIN_USERNAME = $Username
    }
    if (-not [string]::IsNullOrWhiteSpace($Password)) {
        $env:API_CONVERT_ADMIN_PASSWORD = $Password
    }
}

function Set-DatabaseConfig {
    param(
        [string]$Type,
        [string]$Url,
        [string]$Username,
        [string]$Password
    )

    if (-not [string]::IsNullOrWhiteSpace($Type)) {
        $env:API_CONVERT_DB_TYPE = $Type
    }
    if (-not [string]::IsNullOrWhiteSpace($Url)) {
        $env:SPRING_DATASOURCE_URL = $Url
    }
    if (-not [string]::IsNullOrWhiteSpace($Username)) {
        $env:SPRING_DATASOURCE_USERNAME = $Username
    }
    if (-not [string]::IsNullOrWhiteSpace($Password)) {
        $env:SPRING_DATASOURCE_PASSWORD = $Password
    }
}

function Start-Backend {
    Set-Location $Root
    Set-Java25 $JavaHome
    Set-AdminCredentials $AdminUsername $AdminPassword
    Set-DatabaseConfig $DbType $DatasourceUrl $DatasourceUsername $DatasourcePassword
    $env:SERVER_PORT = "$BackendPort"
    $env:JAVA_OPTS = Enable-CompactObjectHeaders $env:JAVA_OPTS
    & "$Root\mvnw.cmd" spring-boot:run "-Dspring-boot.run.jvmArguments=$env:JAVA_OPTS"
}

function Start-Frontend {
    Set-Location (Join-Path $Root "frontend")
    if (-not (Test-Path "node_modules")) {
        npm install
    }
    npm run dev -- --host 0.0.0.0 --port $FrontendPort
}

if ($Target -eq "backend") {
    Start-Backend
    exit $LASTEXITCODE
}

if ($Target -eq "frontend") {
    Start-Frontend
    exit $LASTEXITCODE
}

$backendJob = Start-Job -Name "api-convert-backend" -ScriptBlock {
    param($Root, $JavaHome, $BackendPort, $AdminUsername, $AdminPassword, $DbType, $DatasourceUrl, $DatasourceUsername, $DatasourcePassword)
    Set-Location $Root
    if (-not [string]::IsNullOrWhiteSpace($JavaHome)) {
        $env:JAVA_HOME = $JavaHome
        $env:PATH = (Join-Path $JavaHome "bin") + [IO.Path]::PathSeparator + $env:PATH
    }
    if (-not [string]::IsNullOrWhiteSpace($AdminUsername)) {
        $env:API_CONVERT_ADMIN_USERNAME = $AdminUsername
    }
    if (-not [string]::IsNullOrWhiteSpace($AdminPassword)) {
        $env:API_CONVERT_ADMIN_PASSWORD = $AdminPassword
    }
    if (-not [string]::IsNullOrWhiteSpace($DbType)) {
        $env:API_CONVERT_DB_TYPE = $DbType
    }
    if (-not [string]::IsNullOrWhiteSpace($DatasourceUrl)) {
        $env:SPRING_DATASOURCE_URL = $DatasourceUrl
    }
    if (-not [string]::IsNullOrWhiteSpace($DatasourceUsername)) {
        $env:SPRING_DATASOURCE_USERNAME = $DatasourceUsername
    }
    if (-not [string]::IsNullOrWhiteSpace($DatasourcePassword)) {
        $env:SPRING_DATASOURCE_PASSWORD = $DatasourcePassword
    }
    $env:SERVER_PORT = "$BackendPort"
    $jvmArgs = if ([string]::IsNullOrWhiteSpace($env:JAVA_OPTS)) { "" } else { $env:JAVA_OPTS.Trim() }
    if ($jvmArgs -notmatch '(^|\s)-XX:\+UseCompactObjectHeaders(\s|$)') {
        if ($jvmArgs -notmatch '(^|\s)-XX:\+UnlockExperimentalVMOptions(\s|$)') {
            $jvmArgs = ($jvmArgs + " -XX:+UnlockExperimentalVMOptions").Trim()
        }
        $jvmArgs = ($jvmArgs + " -XX:+UseCompactObjectHeaders").Trim()
    }
    $env:JAVA_OPTS = $jvmArgs
    & "$Root\mvnw.cmd" spring-boot:run "-Dspring-boot.run.jvmArguments=$env:JAVA_OPTS"
} -ArgumentList $Root, $JavaHome, $BackendPort, $AdminUsername, $AdminPassword, $DbType, $DatasourceUrl, $DatasourceUsername, $DatasourcePassword

$frontendJob = Start-Job -Name "api-convert-frontend" -ScriptBlock {
    param($Root, $FrontendPort)
    Set-Location (Join-Path $Root "frontend")
    if (-not (Test-Path "node_modules")) {
        npm install
    }
    npm run dev -- --host 0.0.0.0 --port $FrontendPort
} -ArgumentList $Root, $FrontendPort

try {
    Write-Host "Backend:  http://localhost:$BackendPort"
    Write-Host "Frontend: http://localhost:$FrontendPort"
    if (-not [string]::IsNullOrWhiteSpace($AdminUsername)) {
        Write-Host "Admin:    $AdminUsername"
    }
    Write-Host "Press Ctrl+C to stop both processes."

    while ($true) {
        Receive-Job $backendJob, $frontendJob
        $stopped = @($backendJob, $frontendJob) | Where-Object { $_.State -in @("Completed", "Failed", "Stopped") }
        if ($stopped.Count -gt 0) {
            break
        }
        Start-Sleep -Seconds 1
    }
} finally {
    Stop-Job $backendJob, $frontendJob -ErrorAction SilentlyContinue
    Receive-Job $backendJob, $frontendJob -ErrorAction SilentlyContinue
    Remove-Job $backendJob, $frontendJob -Force -ErrorAction SilentlyContinue
}
