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
  faint: '#8E97A8',
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

const SCENES = {
  hero: {
    src: 'promo-02-plate-peaks.png',                 // the clean plate: the ridge, and nothing of ours in it
    eyebrow: 'FIRST-PERSON UTILITY CLIENT',
    title: 'Everything you reach for.\nNothing you didn\'t.',
    cat: 'visuals',
    inset: { from: 'promo-00-hero-peaks.png', crop: MENU_PANEL, zoom: 0.72, bleed: -24 },
  },
}

/** The page. One background, one grade stack, one inset, one line of type — in that order, no clutter. */
function page(scene) {
  const src = join(RAW, scene.src)
  if (!existsSync(src)) throw new Error(`no raw frame: ${src}\n  run the promo director first (CLUB_PROMO=1)`)
  const bg = `data:image/png;base64,${b64(src)}`
  const accent = T.cat[scene.cat] ?? T.accent

  let insetHtml = ''
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

  return `<style>
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
    .type { position:absolute; left:104px; bottom:104px; width:880px; }
    .mark { display:flex; align-items:center; gap:10px; margin-bottom:26px; }
    .mark svg { width:22px; height:22px; }
    .mark span { font-weight:600; font-size:17px; letter-spacing:.34em; color:${T.textHi}; opacity:.9 }
    .eyebrow { font-weight:500; font-size:15px; letter-spacing:.24em; color:${accent};
               margin-bottom:18px; display:flex; align-items:center; gap:12px }
    .eyebrow::before { content:''; width:26px; height:2px; background:${accent}; display:block }
    h1 { font-weight:600; font-size:76px; line-height:1.06; letter-spacing:-.022em; color:${T.textHi};
         text-shadow:0 2px 40px rgba(0,0,0,.5) }
    h1 .dim { color:${T.faint} }

    /* ---- inset ---- */
    .inset { position:absolute; top:50%; transform:translateY(-50%); }
    .insetImg { position:absolute; inset:0; border-radius:16px;
                border:1px solid rgba(124,171,255,.22);
                box-shadow:0 48px 120px rgba(0,0,0,.72), 0 0 0 1px rgba(255,255,255,.04) inset; }
    /* the quadratic halo the mod itself uses under its panels — allowed here, at promo strength */
    .insetGlow { position:absolute; inset:-14%; border-radius:40px; filter:blur(90px); opacity:.24 }
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
      <div class="eyebrow">${scene.eyebrow}</div>
      <h1>${line1}<br><span class="${line2?.startsWith('None') ? 'dim' : ''}">${line2 ?? ''}</span></h1>
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
