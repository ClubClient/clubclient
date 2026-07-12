# Club v0.1.1 - release gallery compositor.
# The frame stays pristine; the client's own panel language carries the caption below it.
# Neutral-dark surface, one flat accent, category hues, no glow. Typeset in the mod's real face.

Add-Type -AssemblyName System.Drawing

$SRC  = "C:\Club\Club\run\screenshots"
$OUT  = "C:\Users\User\AppData\Local\Temp\claude\c--Club-Club\df7021ea-a527-49f2-b831-d9ee4eaed991\scratchpad\gallery"
$FONT = "C:\Club\Club\tools\fonts"
New-Item -ItemType Directory -Force -Path $OUT | Out-Null

$pfc = New-Object System.Drawing.Text.PrivateFontCollection
$pfc.AddFontFile("$FONT\inter_semibold.ttf")
$pfc.AddFontFile("$FONT\inter_medium.ttf")
$pfc.AddFontFile("$FONT\inter_regular.ttf")
$fam = $pfc.Families
$FBOLD = ($fam | Where-Object { $_.Name -match "SemiBold" })[0]
$FMED  = ($fam | Where-Object { $_.Name -match "Medium" })[0]
$FBODY = ($fam | Where-Object { $_.Name -notmatch "Medium|SemiBold" })[0]
Write-Output ("faces: " + $FBOLD.Name + " / " + $FMED.Name + " / " + $FBODY.Name)

function F($family, $size) { New-Object System.Drawing.Font($family, $size, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel) }
function C([string]$hex, [int]$a = 255) {
  $r = [Convert]::ToInt32($hex.Substring(0,2),16); $g = [Convert]::ToInt32($hex.Substring(2,2),16); $b = [Convert]::ToInt32($hex.Substring(4,2),16)
  [System.Drawing.Color]::FromArgb($a, $r, $g, $b)
}

# tokens (docs/DESIGN.md)
$INK     = "06090C"
$SURFACE = "0F1624"
$LINE    = "2A3550"
$TEXT_HI = "F4F6FA"
$TEXT    = "A6ADBB"
$FAINT   = "5A6273"
$ACCENT  = "7CABFF"
$GOOD    = "2ECC71"
$HUES = @{ combat = "C9808A"; visuals = "9E8BD9"; player = "7FBFA6"; misc = "8C9BB5" }

$W = 1920; $SHOT_H = 1080; $BAND = 300; $H = $SHOT_H + $BAND

function RoundPath($x, $y, $w, $h, $r) {
  $p = New-Object System.Drawing.Drawing2D.GraphicsPath
  $d = $r * 2
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
function TextBox($g, $s, $font, $color, $x, $y, $w, $h) {
  $b = New-Object System.Drawing.SolidBrush($color)
  $fmt = New-Object System.Drawing.StringFormat
  $g.DrawString($s, $font, $b, (New-Object System.Drawing.RectangleF($x, $y, $w, $h)), $fmt)
  $b.Dispose()
}
# uppercase micro-label - System.Drawing has no tracking, so space it by hand
function Tracked($g, $s, $font, $color, $x, $y, $track) {
  $cx = $x
  foreach ($ch in $s.ToCharArray()) {
    Text $g ([string]$ch) $font $color $cx $y
    $cx += $g.MeasureString([string]$ch, $font).Width - 3 + $track
  }
  $cx
}

# =============================================================================
# One image = one change. The frame speaks; the panel says what changed and why.
# =============================================================================
function Card {
  param(
    [string]$base, [string]$cat, [string]$eyebrow, [string]$title, [string]$body,
    [scriptblock]$detail, [string]$file
  )
  $img = [System.Drawing.Bitmap]::FromFile("$SRC\$base")
  $cv  = New-Object System.Drawing.Bitmap $W, $H
  $g   = [System.Drawing.Graphics]::FromImage($cv)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

  # the frame, untouched - the whole point is that the UI stays readable
  $g.DrawImage($img, 0, 0, $W, $SHOT_H)

  $hue = C ($HUES[$cat])

  # wordmark on the frame, quiet, on a soft ink pill so it survives a bright sky
  $mx = 1920 - 96 - 262
  $scrim = RoundPath $mx 50 262 56 12
  Fill $g $scrim (C $INK 150); $scrim.Dispose()
  $wm = F $FBOLD 30
  $end = Tracked $g "CLUB" $wm (C $TEXT_HI 240) ($mx + 24) 62 3
  $vp = RoundPath ($end + 12) 66 84 26 13
  Fill $g $vp (C $ACCENT 40); Stroke $g $vp (C $ACCENT 130) 1
  Text $g "v0.1.1" (F $FBODY 16) (C $ACCENT 250) ($end + 22) 69
  $vp.Dispose(); $wm.Dispose()

  # the panel
  $bar = New-Object System.Drawing.SolidBrush (C $SURFACE)
  $g.FillRectangle($bar, 0, $SHOT_H, $W, $BAND); $bar.Dispose()
  $pen = New-Object System.Drawing.Pen((C $LINE), 2)
  $g.DrawLine($pen, 0, $SHOT_H, $W, $SHOT_H); $pen.Dispose()

  $py = $SHOT_H + 46
  $sp = RoundPath 96 $py 4 58 2
  Fill $g $sp $hue; $sp.Dispose()

  $tx = 124
  $eb = F $FMED 16
  Tracked $g $($eyebrow.ToUpper()) $eb $hue $tx $py 3 | Out-Null
  $eb.Dispose()

  $tf = F $FBOLD 44
  Text $g $title $tf (C $TEXT_HI) ($tx - 3) ($py + 26)
  $tf.Dispose()

  $bf = F $FBODY 21
  TextBox $g $body $bf (C $TEXT) $tx ($py + 92) 900 150
  $bf.Dispose()

  & $detail $g $img $hue

  $cv.Save("$OUT\$file", [System.Drawing.Imaging.ImageFormat]::Png)
  $g.Dispose(); $cv.Dispose(); $img.Dispose()
  Write-Output "  wrote $file"
}

# proof: a magnified crop of the real UI, framed in accent
function Inset($g, $img, $sx, $sy, $sw, $sh) {
  $maxW = 700; $maxH = 200
  $dw = $maxW; $dh = [int]($sh * $dw / $sw)
  if ($dh -gt $maxH) { $dh = $maxH; $dw = [int]($sw * $dh / $sh) }
  $dx = 1824 - $dw; $dy = $SHOT_H + 50 + [int](($maxH - $dh) / 2)
  $p = RoundPath $dx $dy $dw $dh 10
  $g.SetClip($p)
  $g.DrawImage($img, (New-Object System.Drawing.Rectangle($dx, $dy, $dw, $dh)),
               (New-Object System.Drawing.Rectangle($sx, $sy, $sw, $sh)), [System.Drawing.GraphicsUnit]::Pixel)
  $g.ResetClip()
  Stroke $g $p (C $ACCENT 160) 2
  $p.Dispose()
  $cf = F $FMED 15
  Tracked $g "STRAIGHT FROM THE BUILD" $cf (C $FAINT) $dx ($dy + $dh + 14) 2 | Out-Null
  $cf.Dispose()
}

Write-Output "composing:"

Card -base "club-05-popover.png" -cat "visuals" -eyebrow "Visuals / Zoom" `
  -title "Zoom, finally behaving" `
  -body "A real hold key now — the one you actually hold, and the one you will find under Options / Controls. It can no longer collide with a module's toggle key, which is exactly why zoom used to fire on every other press. Your aim also slows while zoomed, so the world crosses the screen at one speed at any magnification." `
  -file "01-zoom.png" -detail { param($g, $img, $hue) Inset $g $img 730 668 460 64 }

Card -base "club-04-menu-category.png" -cat "visuals" -eyebrow "Visuals / Screen Stretch" `
  -title "Your monitor is not 16:9" `
  -body "Screen Stretch shipped ON, forcing a 16:9 view. On a 16:10 laptop, an ultrawide or a 5:4 panel that squeezed the world and painted black bars over your hotbar — on a fresh install, before you ever opened the menu. It sits on Auto now, and does nothing at all until you pick a ratio yourself." `
  -file "02-stretch.png" -detail { param($g, $img, $hue) Inset $g $img 938 282 214 122 }

Card -base "club-11-menu-scale4.png" -cat "misc" -eyebrow "Interface" `
  -title "The size it was designed at" `
  -body "The menu used to live in Minecraft's GUI units, so a video setting quietly redesigned it: at GUI Scale 4 it hit the edges of the screen, dropped columns and squeezed its sidebar. It draws on its own canvas now — four columns and the full rail, from GUI Scale 1 to Auto." `
  -file "03-menu.png" -detail { param($g, $img, $hue) Inset $g $img 700 270 700 250 }

Card -base "club-00-hud-world.png" -cat "player" -eyebrow "Performance" `
  -title "The HUD draws 3.5x cheaper" `
  -body "The renderer was issuing one GL draw call per shape — and one per digit, because the HUD sets tabular numbers glyph by glyph. Shapes and text are batched now, and the result was checked pixel by pixel against 0.1.0." `
  -file "04-perf.png" -detail {
    param($g, $img, $hue)
    $bx = 1124; $by = $SHOT_H + 56; $bw = 600
    $rows = @(
      @{ k = "GL draw calls / frame"; fn = 0.256; lw = "43";      ln = "11" },
      @{ k = "HUD frame cost";        fn = 0.280; lw = "1.25 ms"; ln = "0.35 ms" }
    )
    $lf = F $FMED 15
    $nf = F $FBOLD 30
    $sf = F $FBODY 18
    $i = 0
    foreach ($r in $rows) {
      $y = $by + $i * 104
      Tracked $g $($r.k.ToUpper()) $lf (C $FAINT) $bx $y 2 | Out-Null
      $p1 = RoundPath $bx ($y + 30) $bw 10 5
      Fill $g $p1 (C $LINE 235); $p1.Dispose()
      $p2 = RoundPath $bx ($y + 30) ([int]($bw * $r.fn)) 10 5
      Fill $g $p2 (C $GOOD 240); $p2.Dispose()
      Text $g $($r.ln) $nf (C $GOOD) $bx ($y + 46)
      Text $g $($r.lw) $sf (C $FAINT) ($bx + $bw - 76) ($y + 54)
      $i++
    }
    $lf.Dispose(); $nf.Dispose(); $sf.Dispose()
  }

Card -base "club-02-sprint-chip.png" -cat "combat" -eyebrow "Compatibility" `
  -title "Plays nice with your modpack" `
  -body "Club's mouse-look hook used to claim the most contested call in the client outright — the same one every zoom, freelook and freecam mod hooks. Two mods claiming it is a game that will not launch. It composes now, and the whole suite was run against:" `
  -file "05-compat.png" -detail {
    param($g, $img, $hue)
    $mods = @("Sodium", "Iris", "Freecam")
    $bx = 1180; $by = $SHOT_H + 58
    $mf = F $FBOLD 30
    $tf = F $FMED 15
    $i = 0
    foreach ($m in $mods) {
      $y = $by + $i * 54
      $chk = RoundPath $bx $y 30 30 8
      Fill $g $chk (C $GOOD 45); Stroke $g $chk (C $GOOD 175) 1; $chk.Dispose()
      $pen = New-Object System.Drawing.Pen((C $GOOD 250), 3)
      $g.DrawLines($pen, @(
        (New-Object System.Drawing.Point(($bx + 8),  ($y + 15))),
        (New-Object System.Drawing.Point(($bx + 13), ($y + 21))),
        (New-Object System.Drawing.Point(($bx + 23), ($y + 9)))
      ))
      $pen.Dispose()
      Text $g $m $mf (C $TEXT_HI) ($bx + 48) ($y - 4)
      $i++
    }
    Tracked $g "39 / 39 IN-GAME CHECKS PASS — WITH THEM AND WITHOUT" $tf (C $FAINT) $bx ($by + 176) 2 | Out-Null
    $mf.Dispose(); $tf.Dispose()
  }

Card -base "club-15-editor.png" -cat "player" -eyebrow "Quality of life" `
  -title "Nothing gets lost any more" `
  -body "Drop your resolution or raise the GUI Scale and a HUD element could end up off-screen — still drawn in the world, but impossible to grab in the editor. Positions heal back onto the screen now. Settings panels keep their values clear of the scrollbar, stay usable on a small window, and the menu key can no longer close the menu while you are binding a hotkey." `
  -file "06-polish.png" -detail { param($g, $img, $hue) Inset $g $img 596 14 730 116 }

Write-Output "done -> $OUT"
