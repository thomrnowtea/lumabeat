[CmdletBinding()]
param(
    [int]$ListenPort = 38900,
    [int]$WizPort = 38899,
    [switch]$DiscoverOnly
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-LanEndpoint {
    $interfaces = [System.Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces() |
        Where-Object {
            $_.OperationalStatus -eq [System.Net.NetworkInformation.OperationalStatus]::Up -and
            $_.NetworkInterfaceType -ne [System.Net.NetworkInformation.NetworkInterfaceType]::Loopback
        }

    foreach ($networkInterface in $interfaces) {
        $properties = $networkInterface.GetIPProperties()
        $hasGateway = @($properties.GatewayAddresses | Where-Object {
            $_.Address.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork
        }).Count -gt 0
        if (-not $hasGateway) { continue }

        $unicast = $properties.UnicastAddresses | Where-Object {
            $_.Address.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork -and
            $null -ne $_.IPv4Mask
        } | Select-Object -First 1
        if (-not $unicast) { continue }

        $addressBytes = $unicast.Address.GetAddressBytes()
        $maskBytes = $unicast.IPv4Mask.GetAddressBytes()
        $broadcastBytes = for ($index = 0; $index -lt 4; $index++) {
            $addressBytes[$index] -bor ((-bnot $maskBytes[$index]) -band 255)
        }
        return [pscustomobject]@{
            Address = $unicast.Address
            Broadcast = [System.Net.IPAddress]::new([byte[]]$broadcastBytes)
            Name = $networkInterface.Name
        }
    }
    throw "No active IPv4 LAN interface with a default gateway was found."
}

function New-BoundUdpClient {
    param([System.Net.IPAddress]$Address)
    $endpoint = [System.Net.IPEndPoint]::new($Address, 0)
    $client = [System.Net.Sockets.UdpClient]::new($endpoint)
    $client.EnableBroadcast = $true
    return $client
}

function Find-WizLights {
    param(
        [System.Net.IPAddress]$LanAddress,
        [System.Net.IPAddress]$BroadcastAddress,
        [int]$Port
    )

    $requestText = '{"method":"getSystemConfig","params":{}}'
    $requestBytes = [System.Text.Encoding]::UTF8.GetBytes($requestText)
    $target = [System.Net.IPEndPoint]::new($BroadcastAddress, $Port)
    $responses = [System.Collections.Generic.List[object]]::new()
    $seen = [System.Collections.Generic.HashSet[string]]::new()
    $client = New-BoundUdpClient -Address $LanAddress
    $client.Client.ReceiveTimeout = 180

    try {
        [void]$client.Send($requestBytes, $requestBytes.Length, $target)
        $deadline = [DateTime]::UtcNow.AddMilliseconds(1200)
        while ([DateTime]::UtcNow -lt $deadline) {
            $remote = [System.Net.IPEndPoint]::new([System.Net.IPAddress]::Any, 0)
            try {
                $bytes = $client.Receive([ref]$remote)
                if ($seen.Add($remote.Address.ToString())) {
                    $responses.Add([pscustomobject]@{
                        Address = $remote.Address
                        Json = [System.Text.Encoding]::UTF8.GetString($bytes)
                    })
                }
            } catch [System.Net.Sockets.SocketException] {
                if ($_.Exception.SocketErrorCode -ne [System.Net.Sockets.SocketError]::TimedOut) { throw }
            }
        }
    } finally {
        $client.Dispose()
    }
    return $responses
}

function Add-BridgeAddress {
    param([string]$Json, [string]$Address)
    $message = $Json | ConvertFrom-Json
    if (-not $message.result) { return $null }
    $message.result | Add-Member -NotePropertyName bridgeIp -NotePropertyValue $Address -Force
    return ($message | ConvertTo-Json -Compress -Depth 8)
}

function Send-BridgeReply {
    param(
        [System.Net.Sockets.UdpClient]$Client,
        [System.Net.IPEndPoint]$Destination,
        [string]$Text
    )
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
    [void]$Client.Send($bytes, $bytes.Length, $Destination)
}

$lan = Get-LanEndpoint
Write-Host "LAN: $($lan.Name) $($lan.Address) broadcast $($lan.Broadcast)" -ForegroundColor Cyan

if ($DiscoverOnly) {
    $lights = @(Find-WizLights -LanAddress $lan.Address -BroadcastAddress $lan.Broadcast -Port $WizPort)
    if ($lights.Count -eq 0) {
        Write-Host "No WiZ lights responded." -ForegroundColor Yellow
        exit 1
    }
    foreach ($light in $lights) {
        $message = $light.Json | ConvertFrom-Json
        Write-Host "$($light.Address)  $($message.result.moduleName)  $($message.result.mac)  fw=$($message.result.fwVersion)"
    }
    exit 0
}

$knownTargets = [System.Collections.Generic.HashSet[string]]::new()
$listenEndpoint = [System.Net.IPEndPoint]::new([System.Net.IPAddress]::Loopback, $ListenPort)
$listener = [System.Net.Sockets.UdpClient]::new($listenEndpoint)
Write-Host "WiZ emulator bridge listening on 127.0.0.1:$ListenPort. Press Ctrl+C to stop." -ForegroundColor Green

try {
    while ($true) {
        $requester = [System.Net.IPEndPoint]::new([System.Net.IPAddress]::Any, 0)
        $requestBytes = $listener.Receive([ref]$requester)
        $requestText = [System.Text.Encoding]::UTF8.GetString($requestBytes)
        try {
            $request = $requestText | ConvertFrom-Json
            if ($request.bridge -eq "discover") {
                $lights = @(Find-WizLights -LanAddress $lan.Address -BroadcastAddress $lan.Broadcast -Port $WizPort)
                foreach ($light in $lights) {
                    $address = $light.Address.ToString()
                    [void]$knownTargets.Add($address)
                    $reply = Add-BridgeAddress -Json $light.Json -Address $address
                    if ($reply) { Send-BridgeReply -Client $listener -Destination $requester -Text $reply }
                }
                Write-Host "[$(Get-Date -Format HH:mm:ss)] discovery -> $($lights.Count) light(s)"
                continue
            }

            if ($request.bridge -eq "send" -and $knownTargets.Contains([string]$request.target)) {
                $payload = $request.payload | ConvertTo-Json -Compress -Depth 8
                $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
                $target = [System.Net.IPEndPoint]::new(
                    [System.Net.IPAddress]::Parse([string]$request.target),
                    $WizPort
                )
                $sender = New-BoundUdpClient -Address $lan.Address
                try { [void]$sender.Send($payloadBytes, $payloadBytes.Length, $target) } finally { $sender.Dispose() }
                Write-Host "[$(Get-Date -Format HH:mm:ss)] command -> $($request.target)"
                continue
            }

            if ($request.bridge -eq "query" -and $knownTargets.Contains([string]$request.target)) {
                $payload = $request.payload | ConvertTo-Json -Compress -Depth 8
                $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
                $target = [System.Net.IPEndPoint]::new(
                    [System.Net.IPAddress]::Parse([string]$request.target),
                    $WizPort
                )
                $queryClient = New-BoundUdpClient -Address $lan.Address
                $queryClient.Client.ReceiveTimeout = 900
                try {
                    [void]$queryClient.Send($payloadBytes, $payloadBytes.Length, $target)
                    $remote = [System.Net.IPEndPoint]::new([System.Net.IPAddress]::Any, 0)
                    $responseBytes = $queryClient.Receive([ref]$remote)
                    $responseText = [System.Text.Encoding]::UTF8.GetString($responseBytes)
                    Send-BridgeReply -Client $listener -Destination $requester -Text $responseText
                } finally {
                    $queryClient.Dispose()
                }
                Write-Host "[$(Get-Date -Format HH:mm:ss)] query -> $($request.target)"
            }
        } catch {
            Write-Warning "Ignored malformed bridge request from $requester`: $($_.Exception.Message)"
        }
    }
} finally {
    $listener.Dispose()
}
