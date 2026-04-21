param(
    [Parameter(Mandatory = $true)]
    [string]$LogPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputSqlPath
)

function Get-DetectedEncoding {
    param([string]$Path)

    $utf8 = [System.Text.Encoding]::UTF8
    $gbk = [System.Text.Encoding]::GetEncoding(936)
    $sampleSize = 1024 * 1024
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    if ($bytes.Length -gt $sampleSize) {
        $bytes = $bytes[0..($sampleSize - 1)]
    }

    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        return $utf8
    }

    $patterns = @(
        '开始拓扑:',
        'start_equip:',
        '获取key[rtdb:',
        'key[rtdb:',
        '不存在',
        '拓扑结束',
        '找到边界设备',
        'equip:'
    )

    function Get-Score([string]$sample) {
        $score = 0
        foreach ($pattern in $patterns) {
            $index = 0
            while ($true) {
                $found = $sample.IndexOf($pattern, $index, [System.StringComparison]::Ordinal)
                if ($found -lt 0) {
                    break
                }
                $score += 10
                $index = $found + $pattern.Length
            }
        }
        foreach ($char in $sample.ToCharArray()) {
            if ([int][char]$char -eq 65533) {
                $score -= 3
            }
        }
        return $score
    }

    $utf8Score = Get-Score ($utf8.GetString($bytes))
    $gbkScore = Get-Score ($gbk.GetString($bytes))
    if ($utf8Score -ge $gbkScore) {
        return $utf8
    }
    return $gbk
}

$encoding = Get-DetectedEncoding -Path $LogPath
$lines = [System.IO.File]::ReadLines($LogPath, $encoding)

$startPattern = [regex]'(?:开始拓扑|start_equip):(\d+),(\d+)'
$topologyEndPattern = [regex]'线路(\d+)拓扑结束,已完成数(\d+)'
$devicePattern = [regex]'equip:(\d+)'
$boundaryPattern = [regex]'找到边界设备(\d+),终止分支拓扑'
$measurePattern = [regex]'key\[rtdb:(\d+)\]'

$feederIds = New-Object 'System.Collections.Generic.HashSet[string]'
$outgoingSwitchIds = New-Object 'System.Collections.Generic.HashSet[string]'
$allDeviceIds = New-Object 'System.Collections.Generic.HashSet[string]'
$deviceFeederMap = New-Object 'System.Collections.Generic.Dictionary[string,string]'

$currentFeederId = $null

function Derive-DeviceId {
    param([string]$MeasureId)

    $binary = [Convert]::ToString([Int64]$MeasureId, 2).PadLeft(64, '0')
    $chars = $binary.ToCharArray()
    for ($i = 16; $i -le 31; $i++) {
        $chars[$i] = '0'
    }
    return [Convert]::ToInt64((-join $chars), 2)
}

function Add-DeviceMapping {
    param(
        [string]$DeviceId,
        [string]$FeederId
    )

    if ([string]::IsNullOrWhiteSpace($DeviceId)) {
        return
    }

    $null = $allDeviceIds.Add($DeviceId)

    if (-not [string]::IsNullOrWhiteSpace($FeederId) -and -not $deviceFeederMap.ContainsKey($DeviceId)) {
        $deviceFeederMap[$DeviceId] = $FeederId
    }
}

foreach ($line in $lines) {
    $startMatch = $startPattern.Match($line)
    if ($startMatch.Success) {
        $currentFeederId = $startMatch.Groups[1].Value
        $outgoingSwitchId = $startMatch.Groups[2].Value
        $null = $feederIds.Add($currentFeederId)
        $null = $outgoingSwitchIds.Add($outgoingSwitchId)
        Add-DeviceMapping -DeviceId $outgoingSwitchId -FeederId $currentFeederId
        continue
    }

    $topologyEndMatch = $topologyEndPattern.Match($line)
    if ($topologyEndMatch.Success) {
        if ($currentFeederId -eq $topologyEndMatch.Groups[1].Value) {
            $currentFeederId = $null
        }
        continue
    }

    $boundaryMatch = $boundaryPattern.Match($line)
    if ($boundaryMatch.Success) {
        Add-DeviceMapping -DeviceId $boundaryMatch.Groups[1].Value -FeederId $currentFeederId
        continue
    }

    $deviceMatch = $devicePattern.Match($line)
    if ($deviceMatch.Success) {
        Add-DeviceMapping -DeviceId $deviceMatch.Groups[1].Value -FeederId $currentFeederId
        continue
    }

    $measureMatch = $measurePattern.Match($line)
    if ($measureMatch.Success) {
        $derivedDeviceId = Derive-DeviceId -MeasureId $measureMatch.Groups[1].Value
        Add-DeviceMapping -DeviceId $derivedDeviceId.ToString() -FeederId $currentFeederId
    }
}

$sqlLines = New-Object System.Collections.Generic.List[string]
$sqlLines.Add("SET NAMES utf8mb4;")
$sqlLines.Add("USE ies_ms;")
$sqlLines.Add("")

foreach ($feederId in $feederIds) {
    $sqlLines.Add("INSERT INTO feeder_b (id, name)")
    $sqlLines.Add("SELECT $feederId, '模拟馈线_$feederId'")
    $sqlLines.Add("FROM dual")
    $sqlLines.Add("WHERE NOT EXISTS (SELECT 1 FROM feeder_b WHERE id = $feederId);")
    $sqlLines.Add("")
}

foreach ($outgoingSwitchId in $outgoingSwitchIds) {
    $feederId = $deviceFeederMap[$outgoingSwitchId]
    $sqlLines.Add("INSERT IGNORE INTO breaker_b (id, name, feeder_id) VALUES ($outgoingSwitchId, '模拟出线开关_$outgoingSwitchId', $feederId);")
}

if ($outgoingSwitchIds.Count -gt 0) {
    $sqlLines.Add("")
}

foreach ($entry in $deviceFeederMap.GetEnumerator()) {
    if ($outgoingSwitchIds.Contains($entry.Key)) {
        continue
    }
    $sqlLines.Add("INSERT IGNORE INTO aclinesegment_b (id, name, feeder_id) VALUES ($($entry.Key), '模拟设备_$($entry.Key)', $($entry.Value));")
}

foreach ($deviceId in $allDeviceIds) {
    if ($outgoingSwitchIds.Contains($deviceId) -or $deviceFeederMap.ContainsKey($deviceId)) {
        continue
    }
    $sqlLines.Add("INSERT IGNORE INTO aclinesegment_b (id, name, feeder_id) VALUES ($deviceId, '模拟设备_$deviceId', NULL);")
}

$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($OutputSqlPath, $sqlLines, $utf8WithoutBom)
