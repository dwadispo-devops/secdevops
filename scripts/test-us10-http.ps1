# Integration smoke test. Uses ONLY compose.us10-demo.yaml and synthetic data.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $compose = @('compose', '--env-file', '.us10/demo.env', '-f', 'compose.us10-demo.yaml')
    $auth = 'http://127.0.0.1:18081'
    $pharmacy = 'http://127.0.0.1:18082'
    $prescriptions = 'http://127.0.0.1:18085'
    function JsonRequest($method, $url, $body, $token) {
        $headers = @{}
        if ($token) { $headers.Authorization = "Bearer $token" }
        Invoke-RestMethod -Method $method -Uri $url -ContentType 'application/json' -Headers $headers -Body ($body | ConvertTo-Json -Depth 8)
    }
    function ExpectStatus($code, $method, $url, $token, $body) {
        try {
            JsonRequest $method $url $body $token | Out-Null
            throw "Expected HTTP $code but request succeeded"
        } catch {
            if (!$_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne $code) { throw }
        }
    }
    # Every run creates unique test users; no existing account is modified.
    $suffix = [Guid]::NewGuid().ToString('N').Substring(0,12)
    $password = 'Review1-' + [Guid]::NewGuid().ToString('N')
    $accounts = @{}
    foreach ($kind in @('patient','pharmacist','other')) {
        $email = "us10-$kind-$suffix@example.invalid"
        $user = JsonRequest 'Post' "$auth/api/auth/register" @{email=$email;password=$password;firstName='Synthetic';lastName='US10'} $null
        $accounts[$kind] = @{id=$user.id;email=$email;password=$password}
    }
    $owner = [Guid]$accounts.pharmacist.id
    $other = [Guid]$accounts.other.id
    "UPDATE users SET role='PHARMACIEN' WHERE id IN ('$owner','$other');" |
        & docker @compose exec -T db psql -v ON_ERROR_STOP=1 -U us10_demo_owner -d auth_us10 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Demo role setup failed' }
    $patientToken = (JsonRequest 'Post' "$auth/api/auth/login" $accounts.patient $null).accessToken
    $ownerToken = (JsonRequest 'Post' "$auth/api/auth/login" $accounts.pharmacist $null).accessToken
    $otherToken = (JsonRequest 'Post' "$auth/api/auth/login" $accounts.other $null).accessToken
    $p = JsonRequest 'Post' "$pharmacy/api/pharmacies" @{
        name="Synthetic US10 $suffix";address='Synthetic demo only';phone='0600000000';openingHours='09:00-18:00'
        latitude=31.63;longitude=-8.0;licenseNumber="US10-$suffix"
    } $ownerToken
    $pharmacyId = [Guid]$p.id
    "UPDATE pharmacies SET status='APPROVED', owner_user_id='$owner' WHERE id='$pharmacyId';" |
        & docker @compose exec -T db psql -v ON_ERROR_STOP=1 -U us10_demo_owner -d pharmacy_us10 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Demo approval failed' }
    # A blank synthetic PNG, never a real medical document.
    Add-Type -AssemblyName System.Drawing
    $imagePath = Join-Path $root '.us10/synthetic.png'
    $bitmap = New-Object Drawing.Bitmap 300,300
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    try { $graphics.Clear([Drawing.Color]::White); $bitmap.Save($imagePath,[Drawing.Imaging.ImageFormat]::Png) }
    finally { $graphics.Dispose(); $bitmap.Dispose() }
    # Multipart via .NET is compatible with Windows PowerShell 5.1.
    Add-Type -AssemblyName System.Net.Http
    $client = New-Object Net.Http.HttpClient
    $form = New-Object Net.Http.MultipartFormDataContent
    try {
        $client.DefaultRequestHeaders.Authorization = New-Object Net.Http.Headers.AuthenticationHeaderValue('Bearer',$patientToken)
        $file = New-Object Net.Http.ByteArrayContent -ArgumentList (,[IO.File]::ReadAllBytes($imagePath))
        $file.Headers.ContentType = New-Object Net.Http.Headers.MediaTypeHeaderValue('image/png')
        $form.Add($file,'file','synthetic.png')
        $form.Add((New-Object Net.Http.StringContent 'true'),'consent')
        $uploaded = $client.PostAsync("$prescriptions/api/v1/prescriptions",$form).GetAwaiter().GetResult()
        if ([int]$uploaded.StatusCode -ne 201) { throw 'Synthetic image upload failed' }
        $prescription = $uploaded.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
    } finally { $form.Dispose(); $client.Dispose() }
    $request = JsonRequest 'Post' "$pharmacy/api/availability-requests" @{
        latitude=31.63;longitude=-8.0;radiusM=1000;medications=@(@{name='Synthetic medicine';quantity=1})
    } $patientToken
    JsonRequest 'Put' "$pharmacy/api/availability-requests/$($request.id)/prescription" @{prescriptionId=$prescription.id} $patientToken | Out-Null
    $base = "$pharmacy/api/pharmacies/$pharmacyId/requests/$($request.id)"
    $headers = @{Authorization="Bearer $ownerToken"}
    $inbox = Invoke-RestMethod "$pharmacy/api/pharmacies/me/requests" -Headers $headers
    if (!($inbox | Where-Object requestId -eq $request.id)) { throw 'Assigned request missing from inbox' }
    $detail = Invoke-RestMethod $base -Headers $headers
    if ($detail.medications[0].name -ne 'Synthetic medicine') { throw 'Wrong detail' }
    $view = Invoke-RestMethod "$base/prescription" -Headers $headers
    if (!$view.imageAvailable -or $view.ocrText) { throw 'Image missing or fabricated OCR' }
    $image = Invoke-WebRequest -UseBasicParsing "$base/prescription/image" -Headers $headers
    if ($image.StatusCode -ne 200 -or $image.Headers['Cache-Control'] -ne 'no-store') { throw 'Image proxy failed' }
    ExpectStatus 401 'Post' "$base/response" $null @{availability='UNAVAILABLE'}
    ExpectStatus 403 'Post' "$base/response" $patientToken @{availability='UNAVAILABLE'}
    ExpectStatus 404 'Post' "$base/response" $otherToken @{availability='UNAVAILABLE'}
    ExpectStatus 400 'Post' "$base/response" $ownerToken @{availability='AVAILABLE';price=-1}
    $response = JsonRequest 'Post' "$base/response" @{availability='AVAILABLE';price=42.50;comment='Synthetic HTTP smoke test'} $ownerToken
    if ($response.status -ne 'AVAILABLE' -or $response.price -ne 42.50) { throw 'Response was not saved' }
    ExpectStatus 409 'Post' "$base/response" $ownerToken @{availability='UNAVAILABLE'}
    $responses = Invoke-RestMethod "$pharmacy/api/availability-requests/$($request.id)/responses" -Headers @{Authorization="Bearer $patientToken"}
    if (!($responses | Where-Object { $_.pharmacyId -eq $pharmacyId -and $_.price -eq 42.50 })) { throw 'Patient cannot read saved response' }
    $second = JsonRequest 'Post' "$pharmacy/api/availability-requests" @{
        latitude=31.63;longitude=-8.0;radiusM=1000;medications=@(@{name='Synthetic second request';quantity=1})
    } $patientToken
    $unavailable = JsonRequest 'Post' "$pharmacy/api/pharmacies/$pharmacyId/requests/$($second.id)/response" @{availability='UNAVAILABLE'} $ownerToken
    if ($unavailable.status -ne 'UNAVAILABLE' -or $unavailable.price) { throw 'Unavailable response failed' }
    # A third request is deliberately left pending for the professor's UI demonstration.
    $pending = JsonRequest 'Post' "$pharmacy/api/availability-requests" @{
        latitude=31.63;longitude=-8.0;radiusM=1000;medications=@(@{name='Synthetic UI demonstration';quantity=1})
    } $patientToken
    JsonRequest 'Put' "$pharmacy/api/availability-requests/$($pending.id)/prescription" @{prescriptionId=$prescription.id} $patientToken | Out-Null
    $accounts.pendingRequestId=$pending.id; $accounts.prescriptionId=$prescription.id; $accounts.pharmacyId=$pharmacyId
    [IO.File]::WriteAllText((Join-Path $root '.us10/accounts.json'),($accounts | ConvertTo-Json -Depth 5))
    Write-Host 'HTTP US10 OK: real Auth JWTs, inbox, detail, original image, explicit absent OCR, available/unavailable, patient responses, 400/401/403/404/409.'
    Write-Host 'Pending request and synthetic accounts saved in .us10/accounts.json (ignored by Git).'
} finally { Pop-Location }
