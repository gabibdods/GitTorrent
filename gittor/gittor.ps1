param(
    [Parameter(ValueFromRemainingArguments=$true)]
    [string[]]$ArgsFromUser
)

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$classes = Join-Path $Root 'build\classes\java\main'
$resources = Join-Path $Root 'build\resources\main'
$lib = Join-Path $Root 'lib\*'
$cp = "$classes;$resources;$lib"
$mainClass = 'dev.Main'

$useJsonTemp = $false
$jsonName = $null
$filtered = @()

for ($i=0; $i -lt $ArgsFromUser.Count; $i++) {
    $a = $ArgsFromUser[$i]
    if ($a -eq '--json-to-temp') { $useJsonTemp = $true; continue }
    if ($a -eq '--json-name') {
        if ($i + 1 -lt $ArgsFromUser.Count) { $jsonName = $ArgsFromUser[$i+1]; $i++; continue }
    }
    $filtered += $a
}

if ($useJsonTemp -and ($filtered -notcontains '--json')) {
    $filtered += '--json'
}

$cmdName = if ($jsonName) { $jsonName } else {
    ($filtered | Where-Object { $_ -notmatch '^-{1,2}' } | Select-Object -First 1)
}
if (-not $cmdName) { $cmdName = 'cmd' }

$gitDir = Join-Path $Root '.gitor'
$outDir = Join-Path $gitDir 'out'

New-Item -ItemType Directory -Force -Path $gitDir | Out-Null
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not $jsonName) {
    $jsonName = ($filtered | Where-Object { $_ -notmatch '^-{1,2}' } | Select-Object -First 1)
    if ([string]::IsNullOrWhiteSpace($jsonName)) { $jsonName = 'cmd' }
}
$jsonName = ($jsonName -replace '[\\/:*?"<>|]', '_')

$stamp = (Get-Date).ToString('yyyyMMdd-HHmmss')
$outFile = Join-Path $outDir ("$stamp-$jsonName.json")

if ($useJsonTemp) {
    & java -cp $cp $mainClass @filtered | Out-File -FilePath $outFile -Encoding utf8
    Write-Host "JSON written to $outFile"
} else {
    & java -cp $cp $mainClass @filtered
}

# SIG # Begin signature block
# MIIFTAYJKoZIhvcNAQcCoIIFPTCCBTkCAQExCzAJBgUrDgMCGgUAMGkGCisGAQQB
# gjcCAQSgWzBZMDQGCisGAQQBgjcCAR4wJgIDAQAABBAfzDtgWUsITrck0sYpfvNR
# AgEAAgEAAgEAAgEAAgEAMCEwCQYFKw4DAhoFAAQUFlH4qGg3rGjdA3SIaZdYuNBX
# MPqgggLyMIIC7jCCAdagAwIBAgIQRiTqbKmfyaFFK4pws/V7bjANBgkqhkiG9w0B
# AQUFADAPMQ0wCwYDVQQDDARQU0NTMB4XDTI1MDgxMDA2MDUyNVoXDTI2MDgxMDA2
# MjUyNVowDzENMAsGA1UEAwwEUFNDUzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC
# AQoCggEBANs7qHFBeKA8g/yeq66xONmMAv16tG1nfL9u04FP7m+ZiLynVC2buVqa
# 0dZpc8R+8ob5IXd5FLRmor5I8jnwHKTPS3gywcfX+XTHoHx6ptYicCx48f/IAGTV
# xWQ7n6Z+VL2+or+W8oH8q7souO50LsIv7GOYUCyJWyN7WvDXwWcig9Rz2q2EfyhB
# R7s00OXRaaD37/A8o+1Zvt5iPYTj4oOqGjfsNhQaxzl4jjzNOj6hc8CAQfnU0olY
# WCJ3wXJ0pLNVeSe0ELgjv0sigxvNnsNM5ro2mCfUWfsIkuw+r3v3VGVzk0w0mFs9
# DJ26y6cRr15CZeymbOBJ4Gjb9a9KVkECAwEAAaNGMEQwDgYDVR0PAQH/BAQDAgeA
# MBMGA1UdJQQMMAoGCCsGAQUFBwMDMB0GA1UdDgQWBBQTB06yk3qtCuvmI2SaMy+B
# QHIjxDANBgkqhkiG9w0BAQUFAAOCAQEAqjvS7tZTpYuUnlvq2m1PAhsFMpOFqXzO
# wXyHbjkSq3hz+dHeAO2YWFcoefrrCPrShmC/05sgolrTbr047eq1TH4xw/r5MdCC
# mH3h/vMTb5H3a262Xta2nIFNIFYn/FNiC2yX74o2t/yn0SDxofLwnHVDhbjBX6t2
# VQZs1LSJCYKlyEK6MNUAT10sjpl8URcwnEAlHhF2Ok5heZRjuos7bSuILvnu5YSy
# bIpTMs6y/TgZ5eCP5bC0RqCBvXCPgS5+3J8UTcd34gjGDx1uvCkLqMN6rhC3Rzhi
# Dm2fjhEkq22S8ldPNv27/5V6i2on3g5sdhQRldplbnMRbrve0A208DGCAcQwggHA
# AgEBMCMwDzENMAsGA1UEAwwEUFNDUwIQRiTqbKmfyaFFK4pws/V7bjAJBgUrDgMC
# GgUAoHgwGAYKKwYBBAGCNwIBDDEKMAigAoAAoQKAADAZBgkqhkiG9w0BCQMxDAYK
# KwYBBAGCNwIBBDAcBgorBgEEAYI3AgELMQ4wDAYKKwYBBAGCNwIBFTAjBgkqhkiG
# 9w0BCQQxFgQUKSChdzWirxZfWT1gpjNP+9XcBiQwDQYJKoZIhvcNAQEBBQAEggEA
# sKnrz5rWhmWM1Bu2HMawC6rAlZ/KSkzQWHgQIVq58gZMdSZAZeINwC4QtQhDORzu
# EZ0z0kX/ggprhYNjio6D6EbRwSHSYBpCp0+ystryDyUBzZEhAeUFsAeoRW886GgQ
# 24WfSFq4LVwakfEFNWp0a4/oNqvCTKVMsuc1tTDuri4yxotJv0bb8fRtwYWdEC0d
# mSmLuop0dMBVh8MbTFf4dHxRsKZa1pC/IADxvkh3jjsEghoAOfaVxM56yWsKTWhj
# UZcnPI55m9flt5hJh1y94aK+P9vxjyRVRMhnaD1mjWxz/1QQKnsmsdQ9DDriFeiI
# Gn+G2Zx7xWl523FBfASiFg==
# SIG # End signature block
