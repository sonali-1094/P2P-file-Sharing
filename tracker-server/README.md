# Tracker Server

The tracker server is the backend REST API for P2P account authentication, online peer discovery, file manifests, transfer status, and administration. It uses Spring Boot 4.1.1, MySQL, Flyway, and JPA. The API is stateless and protected with HS256 bearer JWTs.

## Environment variables

| Variable | Purpose |
| --- | --- |
| `DB_URL` | JDBC URL; defaults to `jdbc:mysql://localhost:3306/p2p_tracker` |
| `DB_USER` | MySQL username; defaults to `p2p` |
| `DB_PASSWORD` | MySQL password |
| `JWT_SECRET` | Required HS256 signing secret, at least 32 UTF-8 bytes |
| `ADMIN_EMAIL` | Optional email for startup administrator seeding; set with `ADMIN_PASSWORD` |
| `ADMIN_PASSWORD` | Optional password for the seeded administrator; stored as BCrypt and never logged |
| `CORS_ORIGINS` | Allowed browser origins, comma-separated; defaults to `http://localhost:5173` |

To generate a local secret in PowerShell, use `$env:JWT_SECRET = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`. Never store a real secret in source control.

## Run

Create the MySQL database, set the environment variables in your shell, then run from this directory:

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/p2p_tracker'
$env:DB_USER = 'p2p'
$env:DB_PASSWORD = 'your-local-database-password'
$env:JWT_SECRET = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
mvn spring-boot:run
```

Flyway applies the schema migrations at startup. OpenAPI JSON is available at `/v3/api-docs`; Swagger UI is at `/swagger-ui.html`.

## PowerShell walkthrough

Run the server in another terminal and set `$base` to its URL. The commands below register an account, log in, send a peer heartbeat, register a one-chunk file manifest, and list visible files.

```powershell
$base = 'http://localhost:8080'
$email = 'demo@example.test'
$password = 'demo-password-123'

Invoke-RestMethod -Method Post -Uri "$base/api/auth/register" -ContentType 'application/json' -Body (@{ email = $email; password = $password } | ConvertTo-Json)
$login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body (@{ email = $email; password = $password } | ConvertTo-Json)
$token = $login.token
$headers = @{ Authorization = "Bearer $token" }

Invoke-RestMethod -Method Post -Uri "$base/api/peers/heartbeat" -Headers $headers -ContentType 'application/json' -Body (@{ peerUid = 'demo-peer-1'; port = 9000 } | ConvertTo-Json)

Set-Content -Path '.\sample.txt' -Value 'P2P file manifest example' -NoNewline
$hash = (Get-FileHash '.\sample.txt' -Algorithm SHA256).Hash.ToLowerInvariant()
$size = (Get-Item '.\sample.txt').Length
$manifest = @{
    name = 'sample.txt'; sizeBytes = $size; chunkSize = $size; fileHash = $hash; visibility = 'SHARED'
    chunks = @(@{ index = 0; sha256 = $hash; sizeBytes = $size })
} | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post -Uri "$base/api/files" -Headers $headers -ContentType 'application/json' -Body $manifest

Invoke-RestMethod -Method Get -Uri "$base/api/files?page=0&size=20" -Headers $headers
```

## API routes

- Authentication: `POST /api/auth/register`, `POST /api/auth/login`
- Peers: `POST /api/peers/heartbeat`, `GET /api/peers/online`
- Files: `POST /api/files`, `GET /api/files`, `GET /api/files/{id}`, `GET /api/files/{id}/sources`, `PATCH /api/files/{id}/visibility`, `DELETE /api/files/{id}`
- Transfers: `POST /api/transfers`, `PATCH /api/transfers/{id}`, `GET /api/transfers`
- Admin (`ROLE_ADMIN`): `GET /api/admin/users`, `GET /api/admin/transfers`, `GET /api/admin/stats`
- Health: `GET /api/health`

All routes outside registration, login, health, and API documentation require a valid bearer token. Private file records are only visible to their owner and administrators.
