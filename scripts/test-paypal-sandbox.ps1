param([switch]$PrepareConsent)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskEnvPath=Join-Path $taskRoot '.env'
$taskConfig=@{}
if(Test-Path -LiteralPath $taskEnvPath){
    foreach($taskLine in [IO.File]::ReadAllLines($taskEnvPath)){
        if($taskLine -match '^([A-Z_]+)=(.*)$'){$taskConfig[$Matches[1]]=$Matches[2].Trim()}
    }
}
foreach($taskName in @('PAYPAL_CLIENT_ID','PAYPAL_CLIENT_SECRET')){
    $taskValue=[Environment]::GetEnvironmentVariable($taskName)
    if($taskValue){$taskConfig[$taskName]=$taskValue}
    if([string]::IsNullOrWhiteSpace($taskConfig[$taskName])){throw "Falta $taskName en .env o en el entorno."}
}
try{
    $taskBasic=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($taskConfig['PAYPAL_CLIENT_ID']+':'+$taskConfig['PAYPAL_CLIENT_SECRET']))
    $taskAuth=Invoke-WebRequest -Method Post -Uri 'https://api-m.sandbox.paypal.com/v1/oauth2/token' -Headers @{Authorization=('Basic '+$taskBasic)} -ContentType 'application/x-www-form-urlencoded' -Body 'grant_type=client_credentials' -SkipHttpErrorCheck -TimeoutSec 20
    if([int]$taskAuth.StatusCode -ne 200){throw 'PayPal Sandbox no aceptó las credenciales.'}
    $taskToken=ConvertFrom-Json $taskAuth.Content
    if(-not $taskToken.access_token){throw 'La respuesta de autorización no tiene un token.'}
    [pscustomobject]@{Environment='SANDBOX';Authenticated=$true;VaultScope=([string]$taskToken.scope -match 'vault');WebhookScope=([string]$taskToken.scope -match 'webhook')}
    if($PrepareConsent){
        $taskProbeId=[guid]::NewGuid().ToString()
        $taskReturn=$taskConfig['PAYPAL_RETURN_URL']
        if(-not $taskReturn){$taskReturn='http://localhost:8081/api/v1/billing/paypal/callback'}
        $taskBody=@{customer=@{merchant_customer_id=$taskProbeId};payment_source=@{paypal=@{description='CollabPro Sandbox: provider probe without purchase';usage_pattern='IMMEDIATE';usage_type='MERCHANT';customer_type='CONSUMER';permit_multiple_payment_tokens=$false;experience_context=@{shipping_preference='NO_SHIPPING';return_url=$taskReturn;cancel_url=($taskReturn+'?cancelled=true')}}}}|ConvertTo-Json -Depth 6 -Compress
        $taskResponse=Invoke-WebRequest -Method Post -Uri 'https://api-m.sandbox.paypal.com/v3/vault/setup-tokens' -Headers @{Authorization=('Bearer '+$taskToken.access_token);'PayPal-Request-Id'=('billing-probe-'+$taskProbeId)} -ContentType 'application/json' -Body $taskBody -SkipHttpErrorCheck -TimeoutSec 20
        $taskData=ConvertFrom-Json $taskResponse.Content
        [pscustomobject]@{Environment='SANDBOX';HttpStatus=[int]$taskResponse.StatusCode;Status=[string]$taskData.status;SetupCreated=($null -ne $taskData.id);CustomerReturned=($null -ne $taskData.customer.id)}
        if([int]$taskResponse.StatusCode -notin 200,201){throw 'No se pudo preparar Vault: revisar permisos en la aplicación Sandbox.'}
    }
}catch{
    # Do not print HTTP bodies or tokens returned by the provider.
    throw 'Falló la comprobación PayPal Sandbox. Revisa conectividad, credenciales y permisos; no se imprimen secretos.'
}finally{
    Remove-Variable taskConfig,taskBasic,taskAuth,taskToken,taskBody,taskResponse,taskData -ErrorAction SilentlyContinue
}
