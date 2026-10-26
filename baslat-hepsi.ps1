# Beyin servisini, kaynak paketi sunucusunu (8090) ayri pencerelerde, Paper sunucusunu bu pencerede baslatir.
Start-Process powershell -ArgumentList '-NoExit','-File',"$PSScriptRoot\beyin\baslat.ps1"
Start-Process powershell -ArgumentList '-NoExit','-Command',"python -m http.server 8090 --bind 127.0.0.1 -d '$PSScriptRoot\paket'"
Start-Sleep 3
& "$PSScriptRoot\sunucu\baslat.ps1"
