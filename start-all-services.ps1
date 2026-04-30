# BuildLedger Microservices - Startup Script
# Run this in PowerShell to start all services in correct order

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  BuildLedger Microservices Startup" -ForegroundColor Cyan
Write-Host "  9 Business Services + Infrastructure" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host ""

$baseDir = "C:\Users\2479792\Videos\Project\final_Backend"

# Check prerequisites
Write-Host "Checking prerequisites..." -ForegroundColor Yellow
$mysqlRunning = Get-Process mysqld -ErrorAction SilentlyContinue
if (-not $mysqlRunning) {
    Write-Host "⚠️  MySQL is not running! Please start MySQL first." -ForegroundColor Red
    exit 1
}
Write-Host "✅ MySQL is running" -ForegroundColor Green

Write-Host ""
Write-Host "Starting services in sequence..." -ForegroundColor Yellow
Write-Host ""

# 1. Eureka Server
Write-Host "[1/10] Starting Eureka Server on port 8761..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$baseDir\eureka-server'; mvn spring-boot:run"
Write-Host "Waiting 20 seconds for Eureka to initialize..." -ForegroundColor Gray
Start-Sleep -Seconds 20

# 2. API Gateway
Write-Host "[2/10] Starting API Gateway on port 8079..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$baseDir\api-gateway'; mvn spring-boot:run"
Start-Sleep -Seconds 10

# 3. IAM Service
Write-Host "[3/10] Starting IAM Service on port 8081..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$baseDir\iam-service'; mvn spring-boot:run"
Start-Sleep -Seconds 8

# 4. Vendor Service
Write-Host "[4/10] Starting Vendor Service on port 8082..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$baseDir\vendor-service'; mvn spring-boot:run"
Start-Sleep -Seconds 8

# 5. Contract Service (includes Projects)
Write-Host "[5/10] Starting Contract Service (Projects + Contracts) on port 8084..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$baseDir\contract-service'; mvn spring-boot:run"
Start-Sleep -Seconds 8

# 6. Delivery Service (includes Service Tracking)
Write-Host "[6/10] Starting Delivery Service (Deliveries + Services) on port 8085..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$baseDir\delivery-service'; mvn spring-boot:run"
Start-Sleep -Seconds 8

# 7. Finance Service (Invoices + Payments)
Write-Host "[7/10] Starting Finance Service (Invoices + Payments) on port 8086..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$baseDir\finance-service'; mvn spring-boot:run"
Start-Sleep -Seconds 8

# 8. Compliance Service (Compliance + Audits)
Write-Host "[8/10] Starting Compliance Service (Compliance + Audits) on port 8087..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$baseDir\compliance-service'; mvn spring-boot:run"
Start-Sleep -Seconds 8

# 9. Notification Service
Write-Host "[9/10] Starting Notification Service on port 8088..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$baseDir\notification-service'; mvn spring-boot:run"
Start-Sleep -Seconds 8

# 10. Report Service
Write-Host "[10/10] Starting Report Service on port 8089..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$baseDir\report-service'; mvn spring-boot:run"

Write-Host ""
Write-Host "==================================================" -ForegroundColor Green
Write-Host "  All services are starting up!" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
Write-Host ""
Write-Host "🌐 Eureka Dashboard: http://localhost:8761" -ForegroundColor White
Write-Host "🔌 API Gateway: http://localhost:8079" -ForegroundColor White
Write-Host "📚 Swagger (via Gateway): http://localhost:8079/swagger-ui.html" -ForegroundColor White
Write-Host ""
Write-Host "🔐 Default Admin Credentials:" -ForegroundColor Yellow
Write-Host "   Username: admin" -ForegroundColor White
Write-Host "   Password: Admin@1234" -ForegroundColor White
Write-Host ""
Write-Host "📦 Services:" -ForegroundColor Cyan
Write-Host "   1. IAM (8081) - User Management" -ForegroundColor White
Write-Host "   2. Vendor (8082) - Vendor Onboarding" -ForegroundColor White
Write-Host "   3. Contract (8084) - Projects + Contracts" -ForegroundColor White
Write-Host "   4. Delivery (8085) - Deliveries + Service Tracking" -ForegroundColor White
Write-Host "   5. Finance (8086) - Invoices + Payments" -ForegroundColor White
Write-Host "   6. Compliance (8087) - Compliance + Audits" -ForegroundColor White
Write-Host "   7. Notification (8088) - Kafka Notifications" -ForegroundColor White
Write-Host "   8. Report (8089) - Aggregated Reports" -ForegroundColor White
Write-Host ""
Write-Host "Wait 2-3 minutes for all services to register with Eureka." -ForegroundColor Gray
