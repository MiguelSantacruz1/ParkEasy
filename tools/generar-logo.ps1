# Genera el logo de PARKEASY (PNG y .ico) dibujándolo con System.Drawing.
# Uso: powershell -ExecutionPolicy Bypass -File tools\generar-logo.ps1
# Salida: packaging\icon.ico, packaging\logo-1024.png y src\main\composeResources\drawable\app_logo.png
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $PSScriptRoot
New-Item -ItemType Directory -Force (Join-Path $root 'packaging') | Out-Null
New-Item -ItemType Directory -Force (Join-Path $root 'src\main\composeResources\drawable') | Out-Null

$S = 1024
$bmp = New-Object System.Drawing.Bitmap $S, $S, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'
$g.PixelOffsetMode = 'HighQuality'
$g.Clear([System.Drawing.Color]::Transparent)

function RoundRect($x, $y, $w, $h, $r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = 2 * $r
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

# Fondo: cuadrado redondeado con degradado azul
$bg = RoundRect 24 24 ($S - 48) ($S - 48) 220
$grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush ((New-Object System.Drawing.Point 0, 0), (New-Object System.Drawing.Point $S, $S), ([System.Drawing.Color]::FromArgb(255, 25, 118, 210)), ([System.Drawing.Color]::FromArgb(255, 10, 38, 92)))
$g.FillPath($grad, $bg)

# Cuadrícula de plano, recortada al fondo
$g.SetClip($bg)
$gridPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(36, 255, 255, 255)), 5
for ($i = 1; $i -lt 8; $i++) {
    $g.DrawLine($gridPen, ($i * 128), 0, ($i * 128), $S)
    $g.DrawLine($gridPen, 0, ($i * 128), $S, ($i * 128))
}
$g.ResetClip()

# La "P": un trazo grueso con extremos redondeados
$pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::White), 118
$pen.StartCap = 'Round'; $pen.EndCap = 'Round'; $pen.LineJoin = 'Round'
$stemX = 345; $top = 235; $bottom = 800; $cx = 500; $cy = 395; $r = 160
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$path.AddLine($stemX, $bottom, $stemX, $top)
$path.AddLine($stemX, $top, $cx, $top)
$path.AddArc(($cx - $r), ($cy - $r), (2 * $r), (2 * $r), -90, 180)
$path.AddLine($cx, ($cy + $r), $stemX, ($cy + $r))
$g.DrawPath($pen, $path)

# Punto ámbar dentro de la P: el puesto ocupado (●)
$amber = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 255, 179, 0))
$g.FillEllipse($amber, ($cx - 50), ($cy - 50), 100, 100)

# Puesto de parqueo con un autito visto desde arriba, abajo a la derecha
$linePen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(230, 255, 255, 255)), 16
$linePen.StartCap = 'Round'; $linePen.EndCap = 'Round'
$g.DrawLine($linePen, 600, 650, 600, 850)
$g.DrawLine($linePen, 800, 650, 800, 850)
$body = RoundRect 640 668 120 178 38
$g.FillPath($amber, $body)
$glass = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 14, 52, 118))
$g.FillPath($glass, (RoundRect 655 704 90 40 14))
$g.FillPath($glass, (RoundRect 658 792 84 28 12))
$lamp = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
$g.FillEllipse($lamp, 650, 676, 22, 14)
$g.FillEllipse($lamp, 728, 676, 22, 14)
$g.Dispose()
$bmp.Save((Join-Path $root 'packaging\logo-1024.png'), [System.Drawing.Imaging.ImageFormat]::Png)

function Resized($size) {
    $b = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $gg = [System.Drawing.Graphics]::FromImage($b)
    $gg.InterpolationMode = 'HighQualityBicubic'; $gg.SmoothingMode = 'AntiAlias'; $gg.PixelOffsetMode = 'HighQuality'
    $gg.DrawImage($bmp, 0, 0, $size, $size)
    $gg.Dispose()
    return $b
}

# Imagen para la ventana de la app (512 px)
$w = Resized 512
$w.Save((Join-Path $root 'src\main\composeResources\drawable\app_logo.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$w.Dispose()

# .ico con PNG incrustados (16 a 256 px)
$sizes = 16, 24, 32, 48, 64, 128, 256
$pngs = @()
foreach ($s in $sizes) {
    $b = Resized $s
    $ms = New-Object System.IO.MemoryStream
    $b.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $pngs += , $ms.ToArray()
    $b.Dispose(); $ms.Dispose()
}
$out = New-Object System.IO.MemoryStream
$bw = New-Object System.IO.BinaryWriter $out
$bw.Write([uint16]0); $bw.Write([uint16]1); $bw.Write([uint16]$sizes.Count)
$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $dim = if ($sizes[$i] -ge 256) { 0 } else { $sizes[$i] }
    $bw.Write([byte]$dim); $bw.Write([byte]$dim); $bw.Write([byte]0); $bw.Write([byte]0)
    $bw.Write([uint16]1); $bw.Write([uint16]32)
    $bw.Write([uint32]$pngs[$i].Length); $bw.Write([uint32]$offset)
    $offset += $pngs[$i].Length
}
foreach ($p in $pngs) { $bw.Write($p) }
$bw.Flush()
[IO.File]::WriteAllBytes((Join-Path $root 'packaging\icon.ico'), $out.ToArray())
$bmp.Dispose()
Write-Output "Listo: packaging\icon.ico, packaging\logo-1024.png, src\main\composeResources\drawable\app_logo.png"
