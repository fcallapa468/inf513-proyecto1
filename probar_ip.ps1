$ErrorActionPreference = 'Stop'
try {
    $ip = (Invoke-RestMethod -Uri 'https://api.ipify.org' -TimeoutSec 3).Trim()
} catch {
    $ip = 'Desconocida'
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   TEST DE RELAY SMTP - TECNOWEB (ENTEL)  " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "IP Publica detectada: $ip" -ForegroundColor Yellow

$tcp = New-Object System.Net.Sockets.TcpClient
try {
    # Usamos la IP directa para evitar el fallo del DNS caido de tecnoweb.org.bo
    $serverHost = '139.99.68.169'
    $tcp.Connect($serverHost, 25)
    $stream = $tcp.GetStream()
    $stream.ReadTimeout = 4000
    $reader = New-Object System.IO.StreamReader($stream)
    $writer = New-Object System.IO.StreamWriter($stream)
    $writer.AutoFlush = $true

    $banner = $reader.ReadLine()
    $writer.WriteLine('HELO mail.tecnoweb.org.bo')
    $respHelo = $reader.ReadLine()

    $writer.WriteLine('MAIL FROM: <grupo04sc@tecnoweb.org.bo>')
    $respFrom = $reader.ReadLine()

    $writer.WriteLine('RCPT TO: <luismifb2016@gmail.com>')
    $respRcpt = $reader.ReadLine()

    $writer.WriteLine('QUIT')

    Write-Host "Respuesta del servidor SMTP:" -ForegroundColor Gray
    Write-Host "  -> $respRcpt"

    Write-Host ""
    if ($respRcpt -match '^250') {
        Write-Host "--------------------------------------------------------" -ForegroundColor Green
        Write-Host " [EXITO] ¡TU IP ESTA AUTORIZADA PARA ENVIAR CORREOS!    " -ForegroundColor Green
        Write-Host " Ya puedes ejecutar:  mvn exec:java ... o ejecutar.bat  " -ForegroundColor Green
        Write-Host "--------------------------------------------------------" -ForegroundColor Green
    } else {
        Write-Host "--------------------------------------------------------" -ForegroundColor Red
        Write-Host " [BLOQUEADO] El servidor rechazo el envio (Relaying denied) " -ForegroundColor Red
        Write-Host " Tu IP actual ($ip) no esta autorizada por el docente." -ForegroundColor Red
        Write-Host " Vuelve a reiniciar el router ONT hasta cambiar de IP.  " -ForegroundColor Yellow
        Write-Host "--------------------------------------------------------" -ForegroundColor Red
    }
} catch {
    Write-Host "Error conectando a mail.tecnoweb.org.bo:25: $($_.Exception.Message)" -ForegroundColor Red
} finally {
    $tcp.Close()
}
Write-Host ""
