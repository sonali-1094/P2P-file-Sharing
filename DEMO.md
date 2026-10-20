# Tracker Server Demo

## Prerequisites

- Java 21 and Maven
- A running MySQL server and an empty database named `p2p_tracker`
- PowerShell and the tracker server dependencies downloaded by Maven

The application applies its schema migrations at startup. Do not create or edit schema objects manually.

## Environment variables

In PowerShell, set the database username, database password, and a JWT signing secret of at least 32 UTF-8 bytes. Replace each placeholder locally; do not commit actual values.

```powershell
$env:DB_USER = '<database username>'
$env:DB_PASSWORD = '<database password>'
$env:JWT_SECRET = '<JWT signing secret, at least 32 bytes>'
```

The database URL defaults to `jdbc:mysql://localhost:3306/p2p_tracker`.

## Start the server

From the repository root, run:

```powershell
mvn -pl tracker-server spring-boot:run
```

## Run the smoke test

Keep the server running, open another PowerShell window in the repository root, and run:

```powershell
./scripts/smoke-test.ps1
```

When prompted, enter the requested test account password. The script generates a fresh email address and prints a PASS or FAIL result for each API check.

## Swagger UI

Open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).
