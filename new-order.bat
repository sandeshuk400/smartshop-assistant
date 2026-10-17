@echo off
if "%~2"=="" (
  echo Usage: new-order CUSTOMER_ID TOTAL_AMOUNT
  echo Example: new-order 1 250.50
  exit /b 1
)
powershell -NoProfile -Command "try { $r = Invoke-RestMethod -Method Post -Uri http://localhost:8081/orders -ContentType 'application/json' -Body (@{customerId=%1; totalAmount=%2} | ConvertTo-Json); Write-Host ('Order ' + $r.id + ' created for customer ' + $r.customerId + ', total Rs ' + $r.totalAmount); Write-Host ('WRITE THIS CODE ON THE BILL: ' + $r.billCode) } catch { Write-Host ('Could not create the order: ' + $_.Exception.Message) }"