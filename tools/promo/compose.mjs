// Club promo compositor.
//
// Takes a RAW frame out of the promo director (run/screenshots/promo-*.png) and turns it into a gallery
// image: cinematic grade, bloom, vignette, grain, one magnified inset of the interface, and the line the
// frame is actually about — typeset in the mod's own face.
//
// Why a browser. The previous generator was PowerShell + System.Drawing, and it hit a wall that had nothing
// to do with effort: GDI+ has no letter-spacing, no blur worth the name, no blend modes. Everything we need
// is a solved problem in a rendering engine that is already on this machine — so the frame is composed as a
// page and photographed by headless Chrome. No npm install, no node_modules: Chrome is driven by CLI.
//
//   node tools/promo/compose.mjs --scene hero
//   node tools/promo/compose.mjs --all
//
// Fonts come from tools/fonts/inter_*.ttf, which — despite the file names — really are Onest, the face the
// mod ships and renders its own UI with. The gallery has to be typeset in the product's voice, not Arial's.

import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { dirname, resolve, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO = resolve(HERE, '..', '..')
const RAW = join(REPO, 'run', 'screenshots')
const OUT = join(REPO, 'docs', 'gallery')
const TMP = join(REPO, 'run', 'promo-html')

const CHROME = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
].find(existsSync)

// The design tokens, straight out of docs/DESIGN.md — the promo may not invent a palette the product
// doesn't have. (What it MAY do, and the UI may not: glow, gradient, vignette. Those are promo grammar.)
const T = {
  ink: '#05080D',
  accent: '#7CABFF',
  accent2: '#78D7FF',
  textHi: '#F4F6FA',
  text: '#A6ADBB',      // the muted line of the pair — the menu's own hierarchy
  faint: '#8E97A8',
  good: '#2ECC71',
  cat: { visuals: '#9E8BD9', player: '#7FBFA6', combat: '#C9808A', misc: '#8C9BB5' },
}

const b64 = (p) => readFileSync(p).toString('base64')
const font = (f) => `url(data:font/ttf;base64,${b64(join(REPO, 'tools', 'fonts', f))}) format('truetype')`

/**
 * A scene = one gallery frame. `inset` crops a region out of a source frame and blows it up: the interface
 * has to be READABLE in a Modrinth thumbnail, and at 1:1 in a 1920px frame it simply is not.
 *   crop: [x, y, w, h] in source pixels · at: where the inset sits · zoom: magnification
 */
// The Club menu, as it sits in a 1920x1080 frame: the canvas is 960x540 Club units at 2 physical px each,
// and the window is a fixed 660x380 dead centre — so the panel is exactly here. Cut from its own frame and
// stood on the clean one, because the menu dims the world behind it in game (correct there, useless here).
const MENU_PANEL = [300, 160, 1320, 760]

// The Zoom popover and the editor's toolbar sit on the same fixed canvas, so their rectangles are constants
// too — read off the frames, not guessed.
const ZOOM_POPOVER = [714, 538, 496, 292]     // Strength · Smoothness · Hold key · Reset — the whole sheet
const EDITOR_TOOLBAR = [600, 8, 762, 112]   // the floating toolbar itself: Grid snap · Reset · Done

const SCENES = {
  '01-hero': {
    src: 'promo-02-plate-peaks.png',                 // the clean plate: the ridge, and nothing of ours in it
    // In the mod's voice: plain, concrete, understated. It does not sell, it states — the same register the
    // menu is written in ("Hold to magnify", "Reset to Default"), not a slogan with a pun in it.
    eyebrow: 'FABRIC 1.21.1 · CLIENT-SIDE',
    title: 'The first-person client\nthat stays out of the way.',
    cat: 'visuals',
    // The panel sits WHOLE in the frame, with air around it. Bleeding it off the right edge cropped the
    // search field and the version line mid-word — which reads as a mistake, not as a device (owner).
    inset: { from: 'promo-00-hero-peaks.png', crop: MENU_PANEL, zoom: 0.68, bleed: 104 },
  },

  '02-zoom': {
    src: 'promo-02-plate-peaks.png',
    eyebrow: 'VISUALS / ZOOM',
    title: 'Hold to magnify.\nYour aim slows with it.',
    cat: 'visuals',
    inset: { from: 'promo-03-zoom-popover.png', crop: ZOOM_POPOVER, zoom: 1.6, bleed: 130 },
  },

  '03-hud': {
    // The ridge again, but this time with the HUD in it — the chips ARE the subject, so the frame has to be
    // one where they are actually alive: worn armour, running effect timers, the sprint chip.
    src: 'promo-01-world-peaks.png',
    eyebrow: 'HUD',
    title: 'Armour, effects, target.\nWhere you put them.',
    cat: 'player',
    inset: { from: 'promo-01-world-peaks.png', crop: [0, 280, 260, 290], zoom: 2.3, bleed: 140 },
  },

  '04-editor': {
    src: 'promo-05-hud-taiga.png',
    eyebrow: 'HUD EDITOR',
    title: 'Drag it. Snap it.\nNudge it a pixel.',
    cat: 'player',
    inset: { from: 'promo-06-editor-taiga.png', crop: EDITOR_TOOLBAR, zoom: 1.85, bleed: 110 },
  },

  // Two frames whose subject is a NUMBER, not a screenshot — drawn in the mod's own card language instead of
  // photographed. A picture of a profiler is a picture of a profiler; it is not a picture of a fast mod.
  '05-perf': {
    src: 'promo-02-plate-peaks.png',
    eyebrow: 'PERFORMANCE',
    title: 'It costs you\nalmost nothing.',
    cat: 'misc',
    card: {
      rows: [
        { k: 'GL draw calls per frame', was: '43', now: '11' },
        { k: 'HUD draw time', was: '1.25 ms', now: '0.45 ms' },
      ],
      foot: 'Measured in-game on every build — and asserted, so it fails its own test if it creeps back.',
    },
  },

  '06-compat': {
    src: 'promo-05-hud-taiga.png',
    eyebrow: 'COMPATIBILITY',
    title: 'Drops into\nyour modpack.',
    cat: 'misc',
    card: {
      list: ['Sodium', 'Iris + shaders', 'Freecam'],
      foot: 'These frames were shot with all three running. Client-side only — installs on no server.',
    },
  },
}

/** The page. One background, one grade stack, one inset, one line of type — in that order, no clutter. */
function page(scene) {
  const src = join(RAW, scene.src)
  if (!existsSync(src)) throw new Error(`no raw frame: ${src}\n  run the promo director first (CLUB_PROMO=1)`)
  const bg = `data:image/png;base64,${b64(src)}`
  const accent = T.cat[scene.cat] ?? T.accent

  let insetHtml = ''
  if (scene.card) {
    const c = scene.card
    const rows = (c.rows ?? []).map(r => `
      <div class="cRow">
        <span class="cK">${r.k}</span>
        <span class="cWas">${r.was}</span>
        <span class="cArrow">→</span>
        <span class="cNow">${r.now}</span>
      </div>`).join('')
    const list = (c.list ?? []).map(n => `
      <div class="cRow">
        <svg class="cTick" viewBox="0 0 16 16" fill="none" stroke="${T.good}" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round"><path d="M3 8.5l3.2 3.2L13 5"/></svg>
        <span class="cName">${n}</span>
      </div>`).join('')
    insetHtml = `
      <div class="inset card" style="right:${c.right ?? 120}px">
        <div class="insetGlow" style="background:${accent}"></div>
        <div class="cardBody">
          ${rows}${list}
          <div class="cFoot">${c.foot}</div>
        </div>
      </div>`
  }
  if (scene.inset) {
    const from = join(RAW, scene.inset.from)
    const [cx, cy, cw, ch] = scene.inset.crop
    const z = scene.inset.zoom
    const w = Math.round(cw * z), h = Math.round(ch * z)
    const right = scene.inset.bleed ?? 96      // negative = the panel runs off the frame edge
    insetHtml = `
      <div class="inset" style="width:${w}px;height:${h}px;right:${right}px">
        <div class="insetGlow" style="background:${accent}"></div>
        <div class="insetImg" style="
          background-image:url(data:image/png;base64,${b64(from)});
          background-size:${1920 * z}px ${1080 * z}px;
          background-position:-${cx * z}px -${cy * z}px;"></div>
      </div>`
  }

  const [line1, line2] = scene.title.split('\n')

  // Chrome loads this over file:// and, with no charset declared, decodes it as windows-1252 — which turned
  // the middot in the eyebrow into "Â·" in a shipped frame. Declare it.
  return `<meta charset="utf-8">
  <style>
    @font-face { font-family:'Onest'; src:${font('inter_regular.ttf')};  font-weight:400 }
    @font-face { font-family:'Onest'; src:${font('inter_medium.ttf')};   font-weight:500 }
    @font-face { font-family:'Onest'; src:${font('inter_semibold.ttf')}; font-weight:600 }
    * { margin:0; padding:0; box-sizing:border-box }
    body { width:1920px; height:1080px; overflow:hidden; background:${T.ink};
           font-family:'Onest',sans-serif; -webkit-font-smoothing:antialiased }
    .frame { position:absolute; inset:0 }

    /* The world, graded. The shaderpack already did the hard part — this is a grade, not a rescue: hold the
       contrast, deepen it slightly, and drop the level so the panel and the type have somewhere to live.
       (First cut lifted brightness AND piled on bloom, and the mountain dissolved into milk. A promo grade
       that erases the thing you photographed is just an expensive blur.) */
    .world { position:absolute; inset:0; background:url(${bg}) center/cover no-repeat;
             filter:contrast(1.14) saturate(1.04) brightness(0.74); }

    /* Bloom, from the HIGHLIGHTS only: crush everything below the top of the range to black first, so the
       glow comes off the sun and the fog and nothing else. Restraint is what separates it from a smear. */
    .bloom { position:absolute; inset:0; background:url(${bg}) center/cover no-repeat;
             filter:brightness(2.4) contrast(3.2) saturate(.9) blur(34px);
             mix-blend-mode:screen; opacity:.15; }

    /* Warm the light, cool the shadows — the oldest trick in colour and still the most effective. */
    .split { position:absolute; inset:0; mix-blend-mode:soft-light; opacity:.32;
             background:linear-gradient(160deg, rgba(255,190,120,.5), rgba(0,0,0,0) 50%,
                                                rgba(60,110,190,.45)); }

    .vignette { position:absolute; inset:0;
                background:radial-gradient(125% 95% at 52% 42%, rgba(0,0,0,0) 40%, rgba(0,0,0,.72) 100%); }

    /* A scrim only where the type is. A full-frame darkening would kill the picture we just staged. */
    .scrim { position:absolute; inset:0;
             background:linear-gradient(72deg, rgba(5,8,13,.92) 0%, rgba(5,8,13,.6) 26%,
                                               rgba(5,8,13,0) 54%); }

    .grain { position:absolute; inset:0; opacity:.055; mix-blend-mode:overlay;
             background-image:url("data:image/svg+xml;utf8,\
<svg xmlns='http://www.w3.org/2000/svg' width='220' height='220'>\
<filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.82' numOctaves='3'/></filter>\
<rect width='220' height='220' filter='url(%23n)'/></svg>"); }

    /* ---- type ---- */
    /* The column stops where the panel begins: 1920 - 104 (right margin) - 898 (panel) = 918, minus a gutter.
       Type that runs under a floating panel is how a poster starts looking accidental. */
    .type { position:absolute; left:104px; bottom:104px; width:740px; }
    /* The mod's own type rules, not a poster's (owner: "подпись не в нашем стиле клиента").
       Flat. No glow, no text-shadow, no gradient on text — the one sanctioned gradient in the whole design
       system is the active tab's underline, so that is the only one that appears here, and it appears once.
       Hierarchy is the menu's: one bright line (textHi), one muted line (text). The wordmark is set exactly
       as the menu header sets it. */
    .mark { display:flex; align-items:center; gap:11px; margin-bottom:22px; }
    .mark svg { width:21px; height:21px; }
    .mark span { font-weight:600; font-size:18px; letter-spacing:.34em; color:${T.textHi}; }
    .rule { width:56px; height:2px; border-radius:2px; margin-bottom:22px;
            background:linear-gradient(90deg, ${T.accent}, ${T.accent2}); }
    .eyebrow { font-weight:500; font-size:13px; letter-spacing:.26em; color:${T.accent};
               margin-bottom:20px; }
    h1 { font-weight:600; font-size:60px; line-height:1.12; letter-spacing:-.018em; color:${T.textHi}; }
    h1 .dim { color:${T.text}; font-weight:500 }

    /* ---- inset ---- */
    .inset { position:absolute; top:50%; transform:translateY(-50%); }
    .insetImg { position:absolute; inset:0; border-radius:16px;
                border:1px solid rgba(124,171,255,.22);
                box-shadow:0 48px 120px rgba(0,0,0,.72), 0 0 0 1px rgba(255,255,255,.04) inset; }
    /* the quadratic halo the mod itself uses under its panels — allowed here, at promo strength */
    .insetGlow { position:absolute; inset:-14%; border-radius:40px; filter:blur(90px); opacity:.24 }

    /* ---- the drawn card (perf, compat): the menu's own surface, edge and radius ---- */
    .card { width:660px }
    .cardBody { position:relative; background:#0F1624; border:1px solid #2A3550; border-radius:16px;
                padding:34px 38px; box-shadow:0 48px 120px rgba(0,0,0,.72); }
    .cRow { display:flex; align-items:center; gap:16px; padding:16px 0; border-top:1px solid #1C2740 }
    .cRow:first-child { border-top:0; padding-top:4px }
    .cK { flex:1; font-size:20px; font-weight:400; color:#A6ADBB }
    .cWas { font-size:22px; font-weight:500; color:#5A6273; text-decoration:line-through;
            font-variant-numeric:tabular-nums }
    .cArrow { font-size:19px; color:#5A6273 }
    .cNow { font-size:34px; font-weight:600; color:${T.textHi}; font-variant-numeric:tabular-nums;
            min-width:112px; text-align:right }
    .cTick { width:20px; height:20px; flex:0 0 auto }
    .cName { font-size:26px; font-weight:500; color:${T.textHi} }
    .cFoot { margin-top:22px; padding-top:20px; border-top:1px solid #1C2740;
             font-size:16px; line-height:1.5; color:#5A6273 }
  </style>

  <div class="frame">
    <div class="world"></div>
    <div class="bloom"></div>
    <div class="split"></div>
    <div class="vignette"></div>
    <div class="scrim"></div>
    ${insetHtml}
    <div class="type">
      <div class="mark">
        <svg viewBox="0 0 24 24" fill="${T.textHi}">
          <circle cx="12" cy="7" r="4"/><circle cx="7" cy="14" r="4"/><circle cx="17" cy="14" r="4"/>
          <path d="M11 15h2l1.4 6h-4.8z"/>
        </svg>
        <span>CLUB</span>
      </div>
      <div class="rule"></div>
      <div class="eyebrow">${scene.eyebrow}</div>
      <h1>${line1}<br><span class="dim">${line2 ?? ''}</span></h1>
    </div>
    <div class="grain"></div>
  </div>`
}

function render(name) {
  const scene = SCENES[name]
  if (!scene) throw new Error(`unknown scene "${name}" (have: ${Object.keys(SCENES).join(', ')})`)
  mkdirSync(TMP, { recursive: true }); mkdirSync(OUT, { recursive: true })
  const html = join(TMP, `${name}.html`)
  const png = join(OUT, `${name}.png`)
  writeFileSync(html, page(scene))
  execFileSync(CHROME, [
    '--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
    '--window-size=1920,1080', '--virtual-time-budget=4000',
    `--screenshot=${png}`, `file:///${html.replace(/\\/g, '/')}`,
  ], { stdio: 'pipe' })
  console.log(`  ${name} → ${png}`)
}

const args = process.argv.slice(2)
if (!CHROME) throw new Error('no Chrome/Edge found — the compositor renders the page in a real browser')
const which = args.includes('--all') ? Object.keys(SCENES)
            : [args[args.indexOf('--scene') + 1]].filter(Boolean)
if (!which.length) { console.log(`usage: node tools/promo/compose.mjs --scene <${Object.keys(SCENES).join('|')}> | --all`); process.exit(1) }
for (const n of which) render(n)
