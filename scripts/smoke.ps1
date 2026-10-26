# Smoke-проверка запущенного стека (backend + web).
# Запуск: powershell -ExecutionPolicy Bypass -File .\scripts\smoke.ps1
# Требует: docker compose up --build уже выполнен, сервисы доступны.

param(
    [string]$Api = "http://localhost:8080",
    [string]$Web = "http://localhost:3000",
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$script:passed = 0
$script:failed = 0

function Pass([string]$message) {
    $script:passed++
    Write-Host "  [OK]   $message" -ForegroundColor Green
}

function Fail([string]$message) {
    $script:failed++
    Write-Host "  [FAIL] $message" -ForegroundColor Red
}

function Section([string]$title) {
    Write-Host ""
    Write-Host "== $title" -ForegroundColor Cyan
}

function BasicAuth([string]$login, [string]$password) {
    $token = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${login}:${password}"))
    return @{ Authorization = "Basic $token" }
}

function Invoke-Api {
    param(
        [string]$Method = "GET",
        [string]$Path,
        [hashtable]$Headers = @{},
        $Body = $null,
        [switch]$AllowError
    )

    $request = @{
        Uri             = "$Api$Path"
        Method          = $Method
        Headers         = $Headers
        UseBasicParsing = $true
        ErrorAction     = "Stop"
    }

    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10 -Compress
        $request.Body = [Text.Encoding]::UTF8.GetBytes($json)
        $request.ContentType = "application/json; charset=utf-8"
    }

    try {
        $response = Invoke-WebRequest @request
    } catch {
        $status = 0
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        if ($AllowError) {
            return [pscustomobject]@{ Status = $status; Data = $null }
        }
        throw "HTTP $status при $Method $Path`: $($_.Exception.Message)"
    }

    $text = if ($response.RawContentStream) {
        [Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    } else {
        $response.Content
    }

    $data = if ($text) { $text | ConvertFrom-Json } else { $null }
    return [pscustomobject]@{ Status = [int]$response.StatusCode; Data = $data }
}

$client = BasicAuth "client" "password"
$manager = BasicAuth "manager" "password"

Write-Host "Smoke-проверка кредитного кабинета" -ForegroundColor White
Write-Host "API: $Api"
Write-Host "Web: $Web"

Section "Веб-кабинет"
try {
    $page = Invoke-WebRequest -Uri $Web -UseBasicParsing -TimeoutSec 30
    if ($page.StatusCode -eq 200) { Pass "GET $Web -> 200" } else { Fail "GET $Web -> $($page.StatusCode)" }
} catch {
    Fail "Веб-кабинет недоступен: $($_.Exception.Message)"
}

Section "Backend и БД"
try {
    $health = Invoke-Api -Path "/actuator/health" -Headers $client
    if ($health.Data.status -eq "UP") { Pass "health = UP (БД и Redis доступны)" } else { Fail "health = $($health.Data.status)" }
} catch {
    Fail "health недоступен: $($_.Exception.Message)"
}

try {
    $products = Invoke-Api -Path "/api/products?page=0&size=100" -Headers $client
    if ($products.Data.items.Count -gt 0) {
        Pass "продукты загружены: $($products.Data.items.Count)"
    } else {
        Fail "список продуктов пуст"
    }
} catch {
    Fail "продукты: $($_.Exception.Message)"
}

try {
    $companies = Invoke-Api -Path "/api/companies?page=0&size=100" -Headers $client
    if ($companies.Data.items.Count -gt 0) {
        Pass "компании загружены: $($companies.Data.items.Count)"
    } else {
        Fail "список компаний пуст"
    }
} catch {
    Fail "компании: $($_.Exception.Message)"
}

Section "Предодобренное предложение"
try {
    $offer = Invoke-Api -Path "/api/offers/preapproved?inn=7704983159"
    if ($offer.Data.maxAmount -gt 0) {
        Pass "ИНН 7704983159: лимит $($offer.Data.maxAmount) ₽, ставка $($offer.Data.interestRate)%"
    } else {
        Fail "предодобренное предложение пустое"
    }
} catch {
    Fail "предодобренное предложение: $($_.Exception.Message)"
}

Section "Права доступа"
$forbidden = Invoke-Api -Path "/api/manager/applications/pending" -Headers $client -AllowError
if ($forbidden.Status -eq 403) {
    Pass "клиент не имеет доступа к /api/manager/** (403)"
} else {
    Fail "клиент получил /api/manager/applications/pending -> $($forbidden.Status), ожидался 403"
}

try {
    $pending = Invoke-Api -Path "/api/manager/applications/pending?page=0&size=20" -Headers $manager
    Pass "очередь менеджера доступна, заявок: $($pending.Data.items.Count)"
} catch {
    Fail "очередь менеджера: $($_.Exception.Message)"
}

Section "Сквозной сценарий клиента"
$applicationId = $null
try {
    $company = $companies.Data.items | Where-Object { $_.inn -eq "7704983159" } | Select-Object -First 1
    $product = $products.Data.items | Where-Object { $_.name -eq "Оборотный кредит" } | Select-Object -First 1
    if (-not $company) { throw "демо-компания 7704983159 не найдена" }
    if (-not $product) { throw "продукт «Оборотный кредит» не найден" }

    $created = Invoke-Api -Method POST -Path "/api/applications" -Headers ($client + @{
            "Idempotency-Key" = [guid]::NewGuid().ToString()
        }) -Body @{
            companyId           = $company.id
            productId           = $product.id
            requestedAmount     = 1000000
            requestedTermMonths = 12
            purpose             = "Пополнение оборотных средств"
            hasCollateral       = $false
        }

    $applicationId = $created.Data.id
    if ($applicationId) { Pass "заявка создана: $applicationId (статус $($created.Data.status))" } else { throw "backend не вернул id заявки" }

    Invoke-Api -Method POST -Path "/api/applications/$applicationId/consents" -Headers $client | Out-Null
    Pass "согласия БКИ и ПДн подписаны"

    Invoke-Api -Method POST -Path "/api/applications/$applicationId/submit" -Headers $client | Out-Null
    Pass "заявка отправлена на скоринг"
} catch {
    Fail "создание/отправка заявки: $($_.Exception.Message)"
}

if ($applicationId) {
    Section "Обработка заявки"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $status = "UNKNOWN"
    $final = @("APPROVED", "COUNTER_OFFER", "REJECTED", "MANUAL_REVIEW", "NEEDS_MORE_INFO", "FUNDED")

    while ((Get-Date) -lt $deadline) {
        try {
            $current = Invoke-Api -Path "/api/applications/$applicationId" -Headers $client
            $status = $current.Data.status
            if ($final -contains $status) { break }
        } catch {
            # backend ещё может быть занят обработкой
        }
        Start-Sleep -Seconds 2
    }

    if ($final -contains $status) {
        Pass "заявка обработана, итоговый статус: $status"
    } else {
        Fail "заявка не дошла до решения за $TimeoutSeconds c, текущий статус: $status"
    }

    if ($status -in @("APPROVED", "COUNTER_OFFER", "REJECTED", "FUNDED")) {
        try {
            $score = Invoke-Api -Path "/api/applications/$applicationId/score" -Headers $client
            Pass "скоринг: $($score.Data.score) баллов, грейд $($score.Data.grade), факторов $($score.Data.factors.Count)"
        } catch {
            Fail "скоринг: $($_.Exception.Message)"
        }

        try {
            $explanation = Invoke-Api -Path "/api/applications/$applicationId/explanation" -Headers $client
            if ($explanation.Data.summary) { Pass "объяснение решения получено" } else { Fail "объяснение пустое" }
        } catch {
            Fail "объяснение: $($_.Exception.Message)"
        }
    }

    if ($status -in @("APPROVED", "COUNTER_OFFER")) {
        try {
            $offers = Invoke-Api -Path "/api/applications/$applicationId/offers" -Headers $client
            if ($offers.Data.Count -gt 0) { Pass "предложений: $($offers.Data.Count)" } else { Fail "предложений нет" }
        } catch {
            Fail "предложения: $($_.Exception.Message)"
        }
    }

    try {
        $timeline = Invoke-Api -Path "/api/applications/$applicationId/timeline?page=0&size=100" -Headers $client
        if ($timeline.Data.items.Count -gt 0) {
            Pass "история событий: $($timeline.Data.items.Count) записей"
        } else {
            Fail "история событий пуста"
        }
    } catch {
        Fail "история: $($_.Exception.Message)"
    }
}

Section "Итог"
Write-Host "  Успешно: $script:passed" -ForegroundColor Green
Write-Host "  Ошибок:  $script:failed" -ForegroundColor $(if ($script:failed -gt 0) { "Red" } else { "Green" })

if ($script:failed -gt 0) {
    exit 1
}
