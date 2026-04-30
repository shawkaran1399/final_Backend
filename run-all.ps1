# ============================================================
#  BuildLedger - Build & Start All Microservices
#  Usage:  .\run-all.ps1
#  Options:
#    .\run-all.ps1 -SkipBuild   # skip Maven build, just start
#    .\run-all.ps1 -BuildOnly   # build only, don't start
# ============================================================
param(
    [switch]$SkipBuild,
    [switch]$BuildOnly
)

$baseDir = "C:\Users\2479792\Videos\Project\final_Backend"

$services = [ordered]@{
    "eureka-server"       = 8761
    "api-gateway"         = 8079
    "iam-service"         = 8081
    "vendor-service"      = 8082
    "contract-service"    = 8084
    "delivery-service"    = 8085
    "finance-service"     = 8086
    "compliance-service"  = 8087
    "notification-service"= 8088
    "report-service"      = 8089
}

function Write-Banner {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host "   BuildLedger Microservices Platform" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host ""
}

function Check-Prerequisites {
    Write-Host "Checking prerequisites..." -ForegroundColor Yellow

    # Check MySQL
    $mysqlRunning = Get-Process mysqld -ErrorAction SilentlyContinue
    if (-not $mysqlRunning) {
        Write-Host "  [FAIL] MySQL is NOT running. Please start MySQL first." -ForegroundColor Red
        exit 1
    }
    Write-Host "  [ OK ] MySQL is running" -ForegroundColor Green

    # Check Maven
    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if (-not $mvn) {
        Write-Host "  [FAIL] Maven (mvn) not found in PATH." -ForegroundColor Red
        exit 1
    }
    Write-Host "  [ OK ] Maven found: $($mvn.Source)" -ForegroundColor Green

    # Check Java
    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) {
        Write-Host "  [FAIL] Java not found in PATH." -ForegroundColor Red
        exit 1
    }
    $javaVersion = (java -version 2>&1 | Select-Object -First 1).ToString()
    Write-Host "  [ OK ] Java found: $javaVersion" -ForegroundColor Green

    Write-Host ""
}

function Build-AllServices {
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host "  PHASE 1: Building All Services" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host ""

    $total = $services.Count
    $index = 0
    $failed = @()

    foreach ($svc in $services.Keys) {
        $index++
        Write-Host "[$index/$total] Building $svc ..." -ForegroundColor Cyan
        Push-Location "$baseDir\$svc"
        try {
            $output = mvn clean install -DskipTests -q 2>&1
            if ($LASTEXITCODE -eq 0) {
                Write-Host "       PASS $svc" -ForegroundColor Green
            } else {
                Write-Host "       FAIL $svc - see output above" -ForegroundColor Red
                $failed += $svc
            }
        } catch {
            Write-Host "       FAIL $svc - $_" -ForegroundColor Red
            $failed += $svc
        } finally {
            Pop-Location
        }
    }

    Write-Host ""
    if ($failed.Count -gt 0) {
        Write-Host "Build FAILED for: $($failed -join ', ')" -ForegroundColor Red
        Write-Host "Fix the errors above and re-run." -ForegroundColor Yellow
        exit 1
    }
    Write-Host "All $total services built successfully!" -ForegroundColor Green
    Write-Host ""
}

function Start-AllServices {
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host "  PHASE 2: Starting All Services" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host ""

    $total = $services.Count
    $index = 0

    foreach ($svc in $services.Keys) {
        $index++
        $port = $services[$svc]
        Write-Host "[$index/$total] Starting $svc on port $port ..." -ForegroundColor Cyan

        $svcDir = "$baseDir\$svc"
        $title  = "$svc :$port"
        $cmd    = "Set-Location '$svcDir'; `$host.UI.RawUI.WindowTitle = '$title'; mvn spring-boot:run"
        $encodedCmd = [Convert]::ToBase64String([System.Text.Encoding]::Unicode.GetBytes($cmd))
        Start-Process powershell.exe -ArgumentList "-NoExit -EncodedCommand $encodedCmd"

        # Stagger startup: Eureka needs more time, others need less
        if ($svc -eq "eureka-server") {
            Write-Host "       Waiting 20s for Eureka to initialize..." -ForegroundColor Gray
            Start-Sleep -Seconds 20
        } elseif ($svc -eq "api-gateway") {
            Start-Sleep -Seconds 12
        } else {
            Start-Sleep -Seconds 8
        }
    }
}

function Show-Summary {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host "  All services are starting up!" -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Eureka Dashboard : http://localhost:8761" -ForegroundColor White
    Write-Host "  API Gateway      : http://localhost:8079" -ForegroundColor White
    Write-Host "  Swagger UI       : http://localhost:8079/swagger-ui.html" -ForegroundColor White
    Write-Host ""
    Write-Host "  Default Admin Credentials:" -ForegroundColor Yellow
    Write-Host "    Username : admin" -ForegroundColor White
    Write-Host "    Password : Admin@1234" -ForegroundColor White
    Write-Host ""
    Write-Host "  Service Map:" -ForegroundColor Cyan
    foreach ($svc in $services.Keys) {
        $port = $services[$svc]
        Write-Host ("    {0,-25} http://localhost:{1}/api" -f $svc, $port) -ForegroundColor White
    }
    Write-Host ""
    Write-Host "  Wait 2-3 minutes for all services to fully register with Eureka." -ForegroundColor Gray
    Write-Host ""
}

# ── Main ────────────────────────────────────────────────────
Write-Banner
Check-Prerequisites

if (-not $SkipBuild) {
    Build-AllServices
}

if (-not $BuildOnly) {
    Start-AllServices
    Show-Summary
} else {
    Write-Host "Build complete. Run '.\run-all.ps1 -SkipBuild' to start services." -ForegroundColor Cyan
}

