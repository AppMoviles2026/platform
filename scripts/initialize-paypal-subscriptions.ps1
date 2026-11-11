param([decimal]$EssentialAmount=5.00,[decimal]$GrowthAmount=10.00)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskEnv=Join-Path $taskRoot '.env'
$taskCheckpoint=Join-Path $taskRoot '.env.subscription-catalog.json'
$taskConfig=@{}
if(-not(Test-Path -LiteralPath $taskEnv)){throw 'Crea primero .env en la raíz del backend.'}
foreach($taskLine in [IO.File]::ReadAllLines($taskEnv)){if($taskLine -match '^([A-Z_]+)=(.*)$'){$taskConfig[$Matches[1]]=$Matches[2].Trim()}}
foreach($taskName in @('PAYPAL_CLIENT_ID','PAYPAL_CLIENT_SECRET')){
    $taskValue=[Environment]::GetEnvironmentVariable($taskName)
    if($taskValue){$taskConfig[$taskName]=$taskValue}
    if([string]::IsNullOrWhiteSpace($taskConfig[$taskName])){throw "Falta $taskName."}
}
if($EssentialAmount -le 0 -or $GrowthAmount -le 0 -or [decimal]::Round($EssentialAmount,2) -ne $EssentialAmount -or [decimal]::Round($GrowthAmount,2) -ne $GrowthAmount){throw 'Los importes USD deben ser positivos y tener como máximo dos decimales.'}
function Save-TaskCheckpoint {
    $taskState | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $taskCheckpoint -Encoding utf8
}
function Invoke-TaskPayPal([string]$Method,[string]$Path,$Body=$null,[string]$RequestId=$null){
    $script:taskOperation=$Method+' '+$Path
    $taskHeaders=@{Authorization=('Bearer '+$taskToken.access_token)}
    if($RequestId){$taskHeaders['PayPal-Request-Id']=$RequestId}
    $taskArgs=@{Method=$Method;Uri=('https://api-m.sandbox.paypal.com'+$Path);Headers=$taskHeaders;SkipHttpErrorCheck=$true;TimeoutSec=20}
    if($null -ne $Body){$taskArgs.ContentType='application/json';$taskArgs.Body=($Body|ConvertTo-Json -Depth 10 -Compress)}
    $taskResponse=Invoke-WebRequest @taskArgs
    if([int]$taskResponse.StatusCode -eq 404){return $null}
    if([int]$taskResponse.StatusCode -notin 200,201,204){throw ("PayPal Sandbox rechazó la operación: HTTP "+[int]$taskResponse.StatusCode)}
    if($taskResponse.Content){return ConvertFrom-Json -AsHashtable $taskResponse.Content}
    return @{}
}
try{
    $taskBasic=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($taskConfig['PAYPAL_CLIENT_ID']+':'+$taskConfig['PAYPAL_CLIENT_SECRET']))
    $taskAuth=Invoke-WebRequest -Method Post -Uri 'https://api-m.sandbox.paypal.com/v1/oauth2/token' -Headers @{Authorization=('Basic '+$taskBasic)} -ContentType 'application/x-www-form-urlencoded' -Body 'grant_type=client_credentials' -SkipHttpErrorCheck -TimeoutSec 20
    if([int]$taskAuth.StatusCode -ne 200){throw 'Credenciales Sandbox rechazadas.'}
    $taskToken=ConvertFrom-Json $taskAuth.Content
    $taskClientHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($taskConfig['PAYPAL_CLIENT_ID'])))
    $taskState=if(Test-Path -LiteralPath $taskCheckpoint){Get-Content -Raw -LiteralPath $taskCheckpoint|ConvertFrom-Json -AsHashtable}else{@{clientHash=$taskClientHash;plans=@{}}}
    if($taskState.clientHash -ne $taskClientHash){throw 'El archivo local de recuperación pertenece a otra aplicación PayPal.'}
    $taskProductId='COLLABPRO-DEMO-'+$taskClientHash.Substring(0,24)
    $taskProduct=Invoke-TaskPayPal 'GET' ('/v1/catalogs/products/'+$taskProductId)
    if($null -eq $taskProduct){
        if(-not $taskState.productRequest){$taskState.productRequest=[guid]::NewGuid().ToString();Save-TaskCheckpoint}
        $taskProduct=Invoke-TaskPayPal 'POST' '/v1/catalogs/products' @{id=$taskProductId;name='CollabPro Sandbox';description='Planes mensuales de demostración';type='SERVICE';category='SOFTWARE'} $taskState.productRequest
    }
    if($taskProduct.id -ne $taskProductId){throw 'Identidad de producto inesperada.'}
    foreach($taskDefinition in @(@{code='ESSENTIAL';name='Esencial';amount=$EssentialAmount},@{code='GROWTH';name='Crecimiento';amount=$GrowthAmount})){
        $taskCode=$taskDefinition.code
        $taskPrice=$taskDefinition.amount.ToString('0.00',[Globalization.CultureInfo]::InvariantCulture)
        $taskKey=$taskCode+'-'+$taskPrice
        $taskPlanId=$taskConfig['PAYPAL_'+$taskCode+'_PLAN_ID']
        if(-not $taskState.plans.ContainsKey($taskKey)){$taskState.plans[$taskKey]=@{request=[guid]::NewGuid().ToString();firstAttempt=[DateTimeOffset]::UtcNow.ToString('o');id=$taskPlanId};Save-TaskCheckpoint}
        $taskEntry=$taskState.plans[$taskKey]
        if($taskEntry.id){$taskPlan=Invoke-TaskPayPal 'GET' ('/v1/billing/plans/'+$taskEntry.id)}else{
            if(([DateTimeOffset]::UtcNow-[DateTimeOffset]::Parse($taskEntry.firstAttempt)).TotalHours -ge 48){throw 'Reconciliar manualmente el intento de plan: la ventana segura de reintento venció.'}
            $taskPlan=Invoke-TaskPayPal 'POST' '/v1/billing/plans' @{
                product_id=$taskProductId;name=('CollabPro '+$taskDefinition.name+' Sandbox');description='Plan mensual de demostración, sin prueba gratuita';status='ACTIVE';
                billing_cycles=@(@{frequency=@{interval_unit='MONTH';interval_count=1};tenure_type='REGULAR';sequence=1;total_cycles=0;pricing_scheme=@{fixed_price=@{value=$taskPrice;currency_code='USD'}}});
                payment_preferences=@{auto_bill_outstanding=$false;setup_fee_failure_action='CANCEL';payment_failure_threshold=1}
            } $taskEntry.request
            $taskEntry.id=$taskPlan.id;Save-TaskCheckpoint
            $taskPlan=Invoke-TaskPayPal 'GET' ('/v1/billing/plans/'+$taskEntry.id)
        }
        if($null -eq $taskPlan -or $taskPlan.status -ne 'ACTIVE' -or $taskPlan.product_id -ne $taskProductId -or @($taskPlan.billing_cycles).Count -ne 1 -or
            [decimal]::Parse($taskPlan.billing_cycles[0].pricing_scheme.fixed_price.value,[Globalization.CultureInfo]::InvariantCulture) -ne [decimal]::Parse($taskPrice,[Globalization.CultureInfo]::InvariantCulture) -or $taskPlan.billing_cycles[0].pricing_scheme.fixed_price.currency_code -ne 'USD' -or
            $taskPlan.billing_cycles[0].frequency.interval_unit -ne 'MONTH' -or $taskPlan.billing_cycles[0].frequency.interval_count -ne 1 -or $taskPlan.billing_cycles[0].tenure_type -ne 'REGULAR' -or $taskPlan.billing_cycles[0].total_cycles -ne 0){
            throw 'El plan remoto no coincide con el catálogo solicitado. No se modifica una tarifa existente.'
        }
        $taskConfig['PAYPAL_'+$taskCode+'_PLAN_ID']=$taskPlan.id
    }
    # Append/update only non-secret catalog settings; preserve all existing credential lines.
    $taskUpdates=@{PAYPAL_ESSENTIAL_PLAN_ID=$taskConfig['PAYPAL_ESSENTIAL_PLAN_ID'];PAYPAL_GROWTH_PLAN_ID=$taskConfig['PAYPAL_GROWTH_PLAN_ID'];BILLING_ESSENTIAL_AMOUNT=$EssentialAmount.ToString('0.00',[Globalization.CultureInfo]::InvariantCulture);BILLING_GROWTH_AMOUNT=$GrowthAmount.ToString('0.00',[Globalization.CultureInfo]::InvariantCulture);BILLING_DEMO_CATALOG_ENABLED='true'}
    $taskLines=[Collections.Generic.List[string]]::new()
    foreach($taskLine in [IO.File]::ReadAllLines($taskEnv)){
        if($taskLine -match '^([A-Z_]+)='){$taskSetting=$Matches[1];if($taskUpdates.ContainsKey($taskSetting)){$taskLines.Add($taskSetting+'='+$taskUpdates[$taskSetting]);$taskUpdates.Remove($taskSetting);continue}}
        $taskLines.Add($taskLine)
    }
    foreach($taskSetting in $taskUpdates.Keys){$taskLines.Add($taskSetting+'='+$taskUpdates[$taskSetting])}
    [IO.File]::WriteAllLines($taskEnv,$taskLines,[Text.UTF8Encoding]::new($false))
    [pscustomobject]@{Environment='SANDBOX';EssentialPlanConfigured=$true;GrowthPlanConfigured=$true;ActualPaymentsCreated=$false}
 }catch{
    [pscustomobject]@{Stage=$script:taskOperation;FailureType=$_.Exception.GetType().Name;LocalValidationFailure=($_.Exception.Message -match "^El plan remoto");ScriptLine=$_.InvocationInfo.ScriptLineNumber} | ConvertTo-Json -Compress | Write-Output
    throw 'No se pudo inicializar el catálogo Sandbox. Revisa permisos, conectividad y el archivo local de recuperación; no se imprimen secretos.'
}finally{
    Remove-Variable taskBasic,taskAuth,taskToken,taskConfig -ErrorAction SilentlyContinue
}
