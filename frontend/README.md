
cd backend
.\mvn-local.ps1 spring-boot:run


$body = @{
  username = "admin"
  password = "password"
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri "http://localhost:8080/api/auth/login" `
  -Method POST `
  -ContentType "application/json" `
  -Body $body