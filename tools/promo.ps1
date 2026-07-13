# DRAFT - NEVER EXECUTED. Starting point for the promo gallery, see docs/PROMO-TZ.md.
# Club - Modrinth gallery art.
# Not screenshots with a caption. The UI is the hero: cropped, enlarged, lifted off a dimmed world
# on the client's own quadratic halo, with type that sells the thing in one line.

Add-Type -AssemblyName System.Drawing

$SRC  = "C:\Club\Club\run\screenshots"
$OUT  = "C:\Users\User\AppData\Local\Temp\claude\c--Club-Club\df7021ea-a527-49f2-b831-d9ee4eaed991\scratchpad\promo"
$FONT = "C:\Club\Club\tools\fonts"
New-Item -ItemType Directory -Force -Path $OUT | Out-Null

$pfc = New-Object System.Drawing.Text.PrivateFontCollection
$pfc.AddFontFile("$FONT\inter_semibold.ttf")
$pfc.AddFontFile("$FONT\inter_medium.ttf")
$pfc.AddFontFile("$FONT\inter_regular.ttf")
$fam   = $pfc.Families
$FBOLD = ($fam | Where-Object { $_.Name -match "SemiBold" })[0]
$FMED  = ($fam | Where-Object { $_.Name -match "Medium" })[0]
$FBODY = ($fam | Where-Object { $_.Name -notmatch "Medium|SemiBold" })[0]

function F($family, $size) { New-Object System.Drawing.Font($family, $size, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel) }
function C([string]$hex, [int]$a = 255) {
  $r = [Convert]::ToInt32($hex.Substring(0,2),16); $g = [Convert]::ToInt32($hex.Substring(2,2),16); $b = [Convert]::ToInt32($hex.Substring(4,2),16)
  [System.Drawing.Color]::FromArgb($a, $r, $g, $b)
}

$INK = "05080D"; $SURFACE = "0F1624"; $LINE = "2A3550"
$HI = "F4F6FA"; $TXT = "A6ADBB"; $FAINT = "5A6273"
$ACC = "7CABFF"; $ACC2 = "9CC2FF"; $GOOD = "2ECC71"
$W = 1920; $H = 1080

function RoundPath($x, $y, $w, $h, $r) {
  $p = New-Object System.Drawing.Drawing2D.GraphicsPath
  $d = [Math]::Max(2, $r * 2)
  $p.AddArc($x, $y, $d, $d, 180, 90)
  $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
  $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
  $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
  $p.CloseFigure()
  $p
}
function Fill($g, $path, $color) { $b = New-Object System.Drawing.SolidBrush($color); $g.FillPath($b, $path); $b.Dispose() }
function Stroke($g, $path, $color, $w) { $p = New-Object System.Drawing.Pen($color, $w); $g.DrawPath($p, $path); $p.Dispose() }
function Text($g, $s, $font, $color, $x, $y) {
  $b = New-Object System.Drawing.SolidBrush($color)
  $g.DrawString($s, $font, $b, (New-Object System.Drawing.PointF($x, $y)))
  $b.Dispose()
}
function Tracked($g, $s, $font, $color, $x, $y, $track) {
  $cx = $x
  foreach ($ch in $s.ToCharArray()) {
    Text $g ([string]$ch) $font $color $cx $y
    $cx += $g.MeasureString([string]$ch, $font).Width - 3 + $track
  }
  $cx
}
# manual wrap so the headline breaks where WE want it, not where GDI feels like it
function Lines($g, $s, $font, $color, $x, $y, $maxW, $lead) {
  $words = $s.Split(" ")
  $line = ""
  $cy = $y
  foreach ($w in $words) {
    $try = if ($line -eq "") { $w } else { "$line $w" }
    if ($g.MeasureString($try, $font).Width -gt $maxW -and $line -ne "") {
      Text $g $line $font $color $x $cy
      $cy += $lead
      $line = $w
    } else { $line = $try }
  }
  if ($line -ne "") { Text $g $line $font $color $x $cy; $cy += $lead }
  $cy
}

# --- atmosphere --------------------------------------------------------------
function Vignette($g) {
  $e = New-Object System.Drawing.Drawing2D.GraphicsPath
  $e.AddEllipse(-520, -420, ($W + 1040), ($H + 840))
  $br = New-Object System.Drawing.Drawing2D.PathGradientBrush($e)
  $br.CenterColor = (C $INK 0)
  $br.SurroundColors = @((C $INK 235))
  $br.FocusScales = (New-Object System.Drawing.PointF(0.35, 0.30))
  $g.FillRectangle($br, 0, 0, $W, $H)
  $br.Dispose(); $e.Dispose()
}
# a soft pool of accent light behind the hero panel - promo only; the UI itself stays flat
function Bloom($g, $cx, $cy, $r, $alpha) {
  $e = New-Object System.Drawing.Drawing2D.GraphicsPath
  $e.AddEllipse(($cx - $r), ($cy - $r), ($r * 2), ($r * 2))
  $br = New-Object System.Drawing.Drawing2D.PathGradientBrush($e)
  $br.CenterColor = (C $ACC $alpha)
  $br.SurroundColors = @((C $ACC 0))
  $g.FillPath($br, $e)
  $br.Dispose(); $e.Dispose()
}
# the client's own halo law: thin rings, quadratic falloff - real depth, no fake blur
$HALO = @(26, 22, 19, 16, 13, 11, 9, 7, 6, 5, 4, 3, 3, 2, 2, 1)
function Panel($g, $img, $sx, $sy, $sw, $sh, $dx, $dy, $dw, $dh, $rad) {
  for ($i = $HALO.Length; $i -ge 1; $i--) {
    $s = $i * 4
    $p = RoundPath ($dx - $s) ($dy - $s + $s * 0.35) ($dw + 2 * $s) ($dh + 2 * $s) ($rad + $s)
    Fill $g $p (C "000000" $HALO[$i - 1])
    $p.Dispose()
  }
  $p = RoundPath $dx $dy $dw $dh $rad
  $g.SetClip($p)
  $g.DrawImage($img, (New-Object System.Drawing.Rectangle($dx, $dy, $dw, $dh)),
               (New-Object System.Drawing.Rectangle($sx, $sy, $sw, $sh)), [System.Drawing.GraphicsUnit]::Pixel)
  $g.ResetClip()
  Stroke $g $p (C $LINE 235) 2
  $p.Dispose()
}
# the trefoil, same geometry as the in-game logo
function Mark($g, $x, $y, $size, $color) {
  $k = $size / 24.0
  $b = New-Object System.Drawing.SolidBrush($color)
  $g.FillEllipse($b, ($x + 7.8 * $k), ($y + 3.2 * $k), (8.4 * $k), (8.4 * $k))
  $g.FillEllipse($b, ($x + 2.9 * $k), ($y + 9.7 * $k), (8.4 * $k), (8.4 * $k))
  $g.FillEllipse($b, ($x + 12.7 * $k), ($y + 9.7 * $k), (8.4 * $k), (8.4 * $k))
  $st = RoundPath ($x + 10.8 * $k) ($y + 13.5 * $k) (2.4 * $k) (7.0 * $k) (1.2 * $k)
  $g.FillPath($b, $st); $st.Dispose()
  $b.Dispose()
}
function Chip($g, $s, $font, $x, $y, $hue) {
  $tw = $g.MeasureString($s, $font).Width
  $w = $tw + 30; $h = 42
  $p = RoundPath $x $y $w $h 21
  Fill $g $p (C $hue 30); Stroke $g $p (C $hue 110) 1
  Text $g $s $font (C $hue 250) ($x + 15) ($y + 9)
  $p.Dispose()
  $x + $w + 10
}

function NewCanvas($base, $bloomX, $bloomY) {
  $img = [System.Drawing.Bitmap]::FromFile("$SRC\$base")
  $cv  = New-Object System.Drawing.Bitmap $W, $H
  $g   = [System.Drawing.Graphics]::FromImage($cv)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
  # world, pushed back: scale up a touch so no edge shows, then drown it
  $g.DrawImage($img, -60, -34, ($W + 120), ($H + 68))
  $dim = New-Object System.Drawing.SolidBrush (C $INK 200)
  $g.FillRectangle($dim, 0, 0, $W, $H); $dim.Dispose()
  Vignette $g
  if ($bloomX -gt 0) { Bloom $g $bloomX $bloomY 620 34 }
  @($cv, $g, $img)
}
function Save($cv, $g, $img, $file) {
  $cv.Save("$OUT\$file", [System.Drawing.Imaging.ImageFormat]::Png)
  $g.Dispose(); $cv.Dispose(); $img.Dispose()
  Write-Output "  $file"
}
function Brand($g, $x, $y) {
  Mark $g $x ($y - 2) 34 (C $ACC)
  $f = F $FBOLD 30
  $e = Tracked $g "CLUB" $f (C $HI 245) ($x + 46) $y 3
  $f.Dispose()
  $p = RoundPath ($e + 12) ($y + 4) 78 26 13
  Fill $g $p (C $ACC 38); Stroke $g $p (C $ACC 120) 1
  Text $g "v0.1.1" (F $FBODY 15) (C $ACC 250) ($e + 21) ($y + 7)
  $p.Dispose()
}

Write-Output "composing:"

# =============================================================================
# 1. HERO - the featured card. What the mod IS, in one breath.
# =============================================================================
$c = NewCanvas "club-04-menu-category.png" 1340 520
$cv = $c[0]; $g = $c[1]; $img = $c[2]
Brand $g 110 96
$eb = F $FMED 19
Tracked $g "FABRIC 1.21.1 / CLIENT-SIDE" $eb (C $ACC) 110 300 4 | Out-Null
$eb.Dispose()
$hf = F $FBOLD 82
$y = Lines $g "A first-person client that stays out of the way." $hf (C $HI) 107 340 660 92
$hf.Dispose()
$bf = F $FBODY 25
$y = Lines $g "Zoom, freelook, fullbright, toggle sprint, custom hands and a HUD you can put wherever you like. One flat menu, no clutter, no cheats." $bf (C $TXT) 110 ($y + 22) 640 38
$bf.Dispose()
$cf = F $FMED 19
$x = Chip $g "Zoom" $cf 110 ($y + 30) $ACC
$x = Chip $g "Freelook" $cf $x ($y + 30) $ACC
$x = Chip $g "Fullbright" $cf $x ($y + 30) $ACC
$x = Chip $g "Toggle Sprint" $cf $x ($y + 30) $ACC
$x = Chip $g "Hands" $cf 110 ($y + 82) $ACC
$x = Chip $g "Movable HUD" $cf $x ($y + 82) $ACC
$x = Chip $g "Screen Stretch" $cf $x ($y + 82) $ACC
$cf.Dispose()
# the menu, floated big and bleeding off the right edge
Panel $g $img 300 160 1320 760 880 190 1180 680 18
Save $cv $g $img "01-hero.png"

# =============================================================================
# 2. ZOOM
# =============================================================================
$c = NewCanvas "club-05-popover.png" 1360 560
$cv = $c[0]; $g = $c[1]; $img = $c[2]
Brand $g 110 96
$eb = F $FMED 19
Tracked $g "HOLD TO ZOOM" $eb (C "9E8BD9") 110 320 4 | Out-Null
$eb.Dispose()
$hf = F $FBOLD 84
$y = Lines $g "Zoom that aims like it should." $hf (C $HI) 107 360 640 96
$hf.Dispose()
$bf = F $FBODY 25
$y = Lines $g "Hold your key, scroll to change the strength mid-zoom, and your aim slows down with it, so the world crosses the screen at one speed at any magnification." $bf (C $TXT) 110 ($y + 22) 620 38
$bf.Dispose()
Panel $g $img 718 542 490 280 1030 320 800 458 16
Save $cv $g $img "02-zoom.png"

# =============================================================================
# 3. HUD
# =============================================================================
$c = NewCanvas "club-00-hud-world.png" 1380 540
$cv = $c[0]; $g = $c[1]; $img = $c[2]
Brand $g 110 96
$eb = F $FMED 19
Tracked $g "HUD" $eb (C "7FBFA6") 110 320 4 | Out-Null
$eb.Dispose()
$hf = F $FBOLD 84
$y = Lines $g "Your HUD. Your corners." $hf (C $HI) 107 360 620 96
$hf.Dispose()
$bf = F $FBODY 25
$y = Lines $g "Armour, effects, the entity under your crosshair, coordinates, FPS. Drag any of it anywhere, scale each piece on its own, and snap it to a grid." $bf (C $TXT) 110 ($y + 22) 620 38
$bf.Dispose()
Panel $g $img 0 280 300 300 1160 300 640 640 16
Save $cv $g $img "03-hud.png"

# =============================================================================
# 4. HUD EDITOR
# =============================================================================
$c = NewCanvas "club-15-editor.png" 1300 560
$cv = $c[0]; $g = $c[1]; $img = $c[2]
Brand $g 110 96
$eb = F $FMED 19
Tracked $g "HUD EDITOR" $eb (C "7FBFA6") 110 320 4 | Out-Null
$eb.Dispose()
$hf = F $FBOLD 84
$y = Lines $g "Move it. Done." $hf (C $HI) 107 360 620 96
$hf.Dispose()
$bf = F $FBODY 25
$y = Lines $g "One screen, everything draggable, magnetic guides while you nudge. Right-click any element for its own settings. Nothing to type, nothing to look up." $bf (C $TXT) 110 ($y + 22) 620 38
$bf.Dispose()
Panel $g $img 0 0 1360 620 980 260 880 400 16
Save $cv $g $img "04-editor.png"

# =============================================================================
# 5. PERFORMANCE - the numbers ARE the picture
# =============================================================================
$c = NewCanvas "club-00-hud-world.png" 1360 540
$cv = $c[0]; $g = $c[1]; $img = $c[2]
Brand $g 110 96
$eb = F $FMED 19
Tracked $g "PERFORMANCE" $eb (C $GOOD) 110 320 4 | Out-Null
$eb.Dispose()
$hf = F $FBOLD 84
$y = Lines $g "Costs you almost nothing." $hf (C $HI) 107 360 640 96
$hf.Dispose()
$bf = F $FBODY 25
$y = Lines $g "The whole in-world HUD is batched into eleven GL calls a frame. Measured in-game, not guessed." $bf (C $TXT) 110 ($y + 22) 620 38
$bf.Dispose()
# a big honest number block
$px = 1080; $py = 300
$card = RoundPath $px $py 740 480 20
Fill $g $card (C $SURFACE 240); Stroke $g $card (C $LINE) 2; $card.Dispose()
$kf = F $FMED 17
$nf = F $FBOLD 96
$sf = F $FBODY 22
Tracked $g "GL DRAW CALLS PER FRAME" $kf (C $FAINT) ($px + 48) ($py + 52) 3 | Out-Null
Text $g "11" $nf (C $GOOD) ($px + 44) ($py + 78)
Text $g "was 43" $sf (C $FAINT) ($px + 170) ($py + 130)
$sep = RoundPath ($px + 48) ($py + 236) 644 2 1
Fill $g $sep (C $LINE); $sep.Dispose()
Tracked $g "HUD FRAME COST" $kf (C $FAINT) ($px + 48) ($py + 288) 3 | Out-Null
Text $g "0.35 ms" $nf (C $GOOD) ($px + 44) ($py + 314)
Text $g "was 1.25 ms" $sf (C $FAINT) ($px + 420) ($py + 366)
$kf.Dispose(); $nf.Dispose(); $sf.Dispose()
Save $cv $g $img "05-performance.png"

# =============================================================================
# 6. COMPATIBILITY
# =============================================================================
$c = NewCanvas "club-02-sprint-chip.png" 1360 540
$cv = $c[0]; $g = $c[1]; $img = $c[2]
Brand $g 110 96
$eb = F $FMED 19
Tracked $g "COMPATIBILITY" $eb (C $ACC) 110 320 4 | Out-Null
$eb.Dispose()
$hf = F $FBOLD 84
$y = Lines $g "Drops into your modpack." $hf (C $HI) 107 360 640 96
$hf.Dispose()
$bf = F $FBODY 25
$y = Lines $g "Client-side only, no server needed. Run and verified alongside the mods you already have." $bf (C $TXT) 110 ($y + 22) 620 38
$bf.Dispose()
$px = 1120; $py = 320
$card = RoundPath $px $py 700 440 20
Fill $g $card (C $SURFACE 240); Stroke $g $card (C $LINE) 2; $card.Dispose()
$mods = @("Sodium", "Iris", "Freecam")
$mf = F $FBOLD 38
$i = 0
foreach ($m in $mods) {
  $y2 = $py + 56 + $i * 86
  $chk = RoundPath ($px + 48) $y2 40 40 12
  Fill $g $chk (C $GOOD 45); Stroke $g $chk (C $GOOD 175) 1; $chk.Dispose()
  $pen = New-Object System.Drawing.Pen((C $GOOD 250), 4)
  $g.DrawLines($pen, @(
    (New-Object System.Drawing.Point(($px + 59), ($y2 + 20))),
    (New-Object System.Drawing.Point(($px + 66), ($y2 + 28))),
    (New-Object System.Drawing.Point(($px + 79), ($y2 + 12)))
  ))
  $pen.Dispose()
  Text $g $m $mf (C $HI) ($px + 110) ($y2 - 4)
  $i++
}
$tf = F $FMED 18
Tracked $g "39 / 39 IN-GAME CHECKS PASS" $tf (C $FAINT) ($px + 48) ($py + 356) 3 | Out-Null
$tf.Dispose(); $mf.Dispose()
Save $cv $g $img "06-compat.png"

Write-Output "done -> $OUT"
