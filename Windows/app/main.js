'use strict';
const { app, BrowserWindow, ipcMain, Tray, Menu, nativeImage, Notification, screen, powerMonitor, nativeTheme } = require('electron');
const path = require('path');
const fs   = require('fs');
const crypto = require('crypto');
const { exec, spawn } = require('child_process');
const P = require('./protection');
const { ensureServiceInstalled, removeService } = require('./service_control');

// ── Paths ──────────────────────────────────────────────────────────────────
const HOSTS_FILE    = 'C:\\Windows\\System32\\drivers\\etc\\hosts';
const MARKER_START  = '# === STRIPARCO START ===';
const MARKER_END    = '# === STRIPARCO END ===';
const CONFIG_PATH   = path.join(app.getPath('userData'), 'config.json');
const PRELOAD       = path.join(__dirname, 'preload.js');
const BLOCKED_HTML_PATH = path.join(app.getPath('userData'), 'blocked.html');

// Pause screen-time counting after this many seconds of inactivity (covers monitor
// power-off / idle). Lock and suspend pause immediately via power-monitor events.
const IDLE_PAUSE_SECONDS = 60;

// ── Built-in blocked domains ───────────────────────────────────────────────
const BUILTIN_DOMAINS = [
  // NSFW Porn
  'pornhub.com','xvideos.com','xnxx.com','xhamster.com','redtube.com',
  'youporn.com','tube8.com','beeg.com','spankbang.com','eporner.com',
  'brazzers.com','bangbros.com','naughtyamerica.com','chaturbate.com',
  'cam4.com','myfreecams.com','livejasmin.com','stripchat.com',
  'bongacams.com','onlyfans.com','nhentai.net','hentaihaven.xxx',
  'e621.net','gelbooru.com','rule34.xxx','danbooru.donmai.us',
  'jerkmate.com','camsoda.com','flirt4free.com','adulttime.com',
  'realitykings.com','mofos.com','tnaflix.com','porndig.com',
  'drtuber.com','slutload.com','xtube.com','pornmd.com','keezmovies.com',
  // NSFW AI Chat
  'spicychat.ai','crushon.ai','janitorai.com','candy.ai','muah.ai',
  'dreamgf.ai','dreambf.ai','erogen.ai','intimateai.com',
  'character.ai','naughtydog.ai','kindroid.ai','soulgen.ai','venus.chub.ai',
  // NSFW AI Image generators
  'civitai.com','pornpen.ai','aiporncreator.ai','nudify.online',
  'seduced.ai','promptchan.ai','undress.app','deepnude.cc','nsfwgenerator.ai',
  'replika.com','yodayo.com',
  // AI Image generators (non-NSFW chatbots are intentionally NOT blocked)
  'perchance.org', 'midjourney.com', 'leonardo.ai', 'nightcafe.studio',
  'lexica.art', 'playgroundai.com', 'craiyon.com', 'stablediffusionweb.com',
  'mage.space', 'tensor.art', 'runwayml.com', 'ideogram.ai', 'krea.ai',
  'designer.microsoft.com',
];

// Mainstream AI assistants that must stay accessible (never blocked / never written to hosts).
const ALLOWED_DOMAINS = [
  'grok.com', 'claude.ai', 'perplexity.ai', 'gemini.google.com', 'x.com',
  'chatgpt.com', 'chat.openai.com', 'openai.com', 'copilot.microsoft.com',
  'poe.com', 'you.com', 'pi.ai', 'bard.google.com',
];

// Strong keywords: a single match blocks (unambiguous porn / NSFW-AI / image-generator brands & terms).
const STRONG_KEYWORDS = [
  'pornhub','xvideos','xnxx','xhamster','redtube','youporn','onlyfans',
  'chaturbate','spankbang','stripchat','spicychat','crushon','janitorai',
  'character.ai','nudify','pornpen','civitai','nhentai','rule34',
  'porn','xxx','nsfw','hentai','live sex','sex chat','adult content',
  'replika','candy.ai','dreamgf','dreambf','muah.ai','soulgen','kindroid',
  'deepnude','undress','seduced.ai','promptchan','aiporncreator','nsfwgenerator',
  'perchance','midjourney','leonardo.ai','nightcafe','lexica.art','playgroundai',
  'craiyon','mage.space','tensor.art','ideogram','image generator','képgenerátor'
];

// Weak/ambiguous keywords: block only when at least two of them appear in the same title.
const WEAK_KEYWORDS = [
  'sex','sexy','nude','naked','dating','hookup','free date','meet singles',
  'meet girls','hot women','escort','sugar daddy','megismerkedés'
];

// Titles of these mainstream AI assistants are never blocked.
const WHITELIST_AI_KEYWORDS = ['grok', 'claude', 'perplexity', 'gemini', 'chatgpt', 'copilot', 'openai', 'pi.ai', 'you.com'];

// ── Main-process translations ──────────────────────────────────────────────
const MSG = {
  hu: {
    tray_open: 'STRIPARCOP megnyitása', tray_screentime: 'Képernyőidő',
    tray_settings: 'Beállítások', tray_exit: 'Kilépés',
    tray_unlimited: 'Korlátlan', tray_left: 'maradt',
    notif_time_title: 'Képernyőidő',
    notif_15: '15 perc maradt!', notif_5: '5 perc maradt!', notif_1: '1 perc maradt!',
    notif_limit: 'A napi képernyőidő-korlát elérve.',
    exit_prompt: 'Jelszó szükséges a kilépéshez', exit_ph: 'Jelszó',
    exit_ok: 'OK', exit_cancel: 'Mégse', exit_wrong: 'Hibás jelszó',
    err_pw_time: 'Jelszó szükséges a módosításhoz',
    err_pw_old: 'Hibás régi jelszó', err_pw_min: 'Min. 4 karakter szükséges',
    err_pw_wrong: 'Hibás jelszó',
    u_h: 'ó', u_m: 'p', u_s: 'mp', dur_h: 'óra', dur_m: 'perc',
  },
  en: {
    tray_open: 'Open STRIPARCOP', tray_screentime: 'Screen Time',
    tray_settings: 'Settings', tray_exit: 'Exit',
    tray_unlimited: 'Unlimited', tray_left: 'left',
    notif_time_title: 'Screen Time',
    notif_15: '15 minutes left!', notif_5: '5 minutes left!', notif_1: '1 minute left!',
    notif_limit: 'Daily screen time limit reached.',
    exit_prompt: 'Password required to exit', exit_ph: 'Password',
    exit_ok: 'OK', exit_cancel: 'Cancel', exit_wrong: 'Wrong password',
    err_pw_time: 'Password required to make this change',
    err_pw_old: 'Wrong current password', err_pw_min: 'Minimum 4 characters required',
    err_pw_wrong: 'Wrong password',
    u_h: 'h', u_m: 'm', u_s: 's', dur_h: 'h', dur_m: 'min',
  },
};
const tr = key => (MSG[config.lang] || MSG.hu)[key] || (MSG.hu[key] || key);

// ── State ──────────────────────────────────────────────────────────────────
let tray = null, mainWindow = null, settingsWindow = null;
let notifWindow = null, screenTimeWindow = null, notifCloseTimer = null;
let coverWindows = [];
let config = {};
let screenTimeSecondsUsed = 0;
let limitReached = false;
let screenTimePaused = false;
let lastNotifKey = '', lastNotifTime = 0;
let warned15 = false, warned5 = false, warned1 = false;
let lastCloseOthers = 0;
let lockoutEnforcer = null;

// ── Helpers ────────────────────────────────────────────────────────────────
const todayDate  = () => new Date().toISOString().slice(0,10);
const hashPw     = pw  => crypto.createHash('sha256').update(pw + 'cb_v1').digest('hex');
const escRx      = s   => s.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
const fmtTime    = sec => {
  if (sec < 0) return '∞';
  sec = Math.floor(sec);
  const h = Math.floor(sec/3600), m = Math.floor((sec%3600)/60);
  return `${h} ${tr('dur_h')} ${m} ${tr('dur_m')}`;
};

// ── Config ─────────────────────────────────────────────────────────────────
function defConfig() {
  return {
    password_protected: false, password_hash: '',
    screen_time_limit: 0,      // minutes, 0=unlimited
    screen_time_used: 0,       // seconds used today
    screen_time_date: todayDate(),
    auto_start: true,
    custom_blocked_sites: [],
    blocked_count: 0,
    theme: 'system',           // 'system' | 'light' | 'dark'
    lang: (app.getLocale && app.getLocale().startsWith('hu')) ? 'hu' : 'en',
  };
}

function loadConfig() {
  try {
    config = fs.existsSync(CONFIG_PATH)
      ? { ...defConfig(), ...JSON.parse(fs.readFileSync(CONFIG_PATH,'utf8')) }
      : defConfig();
  } catch { config = defConfig(); }

  if (config.screen_time_date !== todayDate()) {
    config.screen_time_used = 0;
    config.screen_time_date = todayDate();
  }
  screenTimeSecondsUsed = config.screen_time_used || 0;
  // Protection is always on; uninstallation is only re-enabled by the sanctioned
  // password-protected exit (disableProtection → removeProtection).
}

// ── Theme ──────────────────────────────────────────────────────────────────
function applyTheme() {
  const t = config.theme || 'system';
  nativeTheme.themeSource = (t === 'light' || t === 'dark') ? t : 'system';
}

function saveConfig() {
  config.screen_time_used = screenTimeSecondsUsed;
  config.screen_time_date = todayDate();
  try { fs.writeFileSync(CONFIG_PATH, JSON.stringify(config,null,2),'utf8'); } catch(e) { console.error(e); }
}

// ── Hosts file ─────────────────────────────────────────────────────────────
function updateHosts() {
  let content;
  try { content = fs.readFileSync(HOSTS_FILE,'utf8'); } catch { return false; }
  const rx = new RegExp(`\\r?\\n?${escRx(MARKER_START)}[\\s\\S]*?${escRx(MARKER_END)}\\r?\\n?`,'g');
  let clean = content.replace(rx,'');
  const allowed = new Set(ALLOWED_DOMAINS);
  const all = [...new Set([...BUILTIN_DOMAINS, ...config.custom_blocked_sites])]
    .filter(d => !allowed.has(d) && !ALLOWED_DOMAINS.some(a => d === a || d.endsWith('.' + a)));
  const lines = ['\n'+MARKER_START];
  for (const d of all) {
    lines.push(`127.0.0.1 ${d}`);
    if (!d.startsWith('www.')) lines.push(`127.0.0.1 www.${d}`);
  }
  lines.push(MARKER_END);
  try {
    try { require('child_process').execSync(`attrib -r "${HOSTS_FILE}"`); } catch(e) {}
    fs.writeFileSync(HOSTS_FILE, clean.trimEnd() + lines.join('\n') + '\n','utf8');
    exec('ipconfig /flushdns');
    return true;
  } catch(e) { console.error('Hosts write failed (no admin?):', e.message); return false; }
}

function cleanHosts() {
  try {
    const c = fs.readFileSync(HOSTS_FILE,'utf8');
    const rx = new RegExp(`\\r?\\n?${escRx(MARKER_START)}[\\s\\S]*?${escRx(MARKER_END)}\\r?\\n?`,'g');
    fs.writeFileSync(HOSTS_FILE, c.replace(rx,''),'utf8');
  } catch {}
}

// ── Auto-start ─────────────────────────────────────────────────────────────
// Protection is always on: the per-minute guard task (created in protection.js)
// already relaunches the app at logon, so this toggle is now only a stored user
// preference and intentionally creates no competing autostart vector.
function setAutoStart(_enabled) { /* guard task handles startup; see hardenPersistence */ }

// ── Persistence (anti-uninstall / anti-stop) ────────────────────────────────
// One strong core + one light backup, instead of the former five overlapping
// vectors:
//   • LocalSystem guard SERVICE → re-applies the tamper protection every ~20 s
//     and survives reboots; it runs as SYSTEM so it can re-lock the install dir
//     and re-hide the uninstall entry even against an admin.
//   • A single per-minute guard TASK → keeps the GUI alive in the user session
//     (a service cannot easily launch an interactive window).
// preventUninstallation()/applyProtection() are idempotent, so the periodic
// self-heal in startTimers() simply tops them up while the GUI is alive.
function hardenPersistence() {
  if (app.isQuitting) return;
  ensureServiceInstalled();
  P.applyProtection();
}

// Sanctioned teardown — only reached through the password-protected exit (doExit).
// Removes every protection vector so the owner can fully remove the app afterwards.
function disableProtection() {
  removeService();
  P.removeProtection();
}

function preventUninstallation() {
  P.hideUninstall();
  P.lockInstallDir();
}

// ── Browser title monitor ──────────────────────────────────────────────────
let psMonitor = null;
let psMonitorBuffer = '';

function titleMatches(kw, title, lo) {
  // Word-boundary match for short keywords to avoid false positives (e.g. "Essex" → "sex").
  if (kw.length <= 4) return new RegExp('\\b' + escRx(kw) + '\\b', 'i').test(title);
  return lo.includes(kw);
}

// Returns the matched reason string if the title should be blocked, otherwise null.
function evaluateTitle(title) {
  const lo = title.toLowerCase();
  // Never block mainstream AI assistants (Claude, Gemini, ChatGPT, Grok, Perplexity, Copilot…).
  for (const w of WHITELIST_AI_KEYWORDS) if (lo.includes(w)) return null;
  // User-defined custom sites are treated as strong (single match blocks).
  const strong = [...STRONG_KEYWORDS, ...config.custom_blocked_sites.map(s => s.toLowerCase())];
  for (const kw of strong) if (titleMatches(kw, title, lo)) return kw;
  // Ambiguous keywords only block when at least two of them appear together.
  const weakHits = WEAK_KEYWORDS.filter(kw => titleMatches(kw, title, lo));
  if (weakHits.length >= 2) return weakHits.join(' + ');
  return null;
}

function startBrowsersMonitor() {
  if (psMonitor) return;
  const psScript = `
    $browsers = "chrome","firefox","msedge","opera","brave","vivaldi","iexplore","waterfox","librewolf","thorium","arc","sidekick","ghostery","whale"
    while($true) {
      $p = Get-Process $browsers -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle };
      if ($p) {
        $json = $p | Select-Object -Property Id, MainWindowTitle | ConvertTo-Json -Compress
        Write-Output $json
      }
      Start-Sleep -Milliseconds 800
    }
  `;
  psMonitor = spawn('powershell', ['-NoProfile', '-NonInteractive', '-Command', psScript]);

  psMonitor.stdout.on('data', (data) => {
    psMonitorBuffer += data.toString();
    const lines = psMonitorBuffer.split('\n');
    // Keep the last (potentially incomplete) line in the buffer
    psMonitorBuffer = lines.pop() || '';
    for (let line of lines) {
      line = line.trim();
      if (!line || (!line.startsWith('[') && !line.startsWith('{'))) continue;
      try {
        let processes = JSON.parse(line);
        if (!Array.isArray(processes)) processes = [processes];
        for (const p of processes) {
          if (!p || !p.MainWindowTitle) continue;
          const hit = evaluateTitle(p.MainWindowTitle);
          if (hit) { triggerBlock(p.MainWindowTitle, hit, p.Id); return; }
        }
      } catch(e) {}
    }
  });

  psMonitor.on('exit', () => {
    psMonitor = null;
    psMonitorBuffer = '';
    setTimeout(startBrowsersMonitor, 3000); // Auto-restart if crashed
  });
}

function triggerBlock(title, keyword, pid) {
  const now = Date.now();
  if (keyword === lastNotifKey && now - lastNotifTime < 3000) return;
  lastNotifKey = keyword; lastNotifTime = now;
  config.blocked_count = (config.blocked_count||0)+1;
  saveConfig();

  // Show the notice immediately, then redirect — keeps the popup snappy.
  showNotifWindow(title, keyword);
  redirectBrowser(pid);
}

function redirectBrowser(pid) {
  const blockedUrl = "file:///" + BLOCKED_HTML_PATH.replace(/\\/g, '/');
  // Escape single quotes for PowerShell string literal
  const safeUrl = blockedUrl.replace(/'/g, "''");
  const ps = `
    Add-Type -AssemblyName Microsoft.VisualBasic
    $prev = $null
    try { $prev = Get-Clipboard -Raw -ErrorAction SilentlyContinue } catch {}
    try { [Microsoft.VisualBasic.Interaction]::AppActivate(${pid}) } catch {}
    Start-Sleep -Milliseconds 120
    Set-Clipboard -Value '${safeUrl}'
    $wshell = New-Object -ComObject wscript.shell
    $wshell.SendKeys('^l')
    Start-Sleep -Milliseconds 60
    $wshell.SendKeys('^a')
    $wshell.SendKeys('^v')
    Start-Sleep -Milliseconds 40
    $wshell.SendKeys('{ENTER}')
    Start-Sleep -Milliseconds 300
    try { if ($null -ne $prev) { Set-Clipboard -Value $prev } else { $null | Set-Clipboard } } catch {}
  `;
  exec(`powershell -NoProfile -NonInteractive -Command "${ps.replace(/\n/g, '; ')}"`);
}

// ── Notification window ────────────────────────────────────────────────────
function showNotifWindow(title, keyword) {
  // Single auto-close timer, re-armed on every show so repeated alerts don't stack timers.
  const armClose = () => {
    if (notifCloseTimer) clearTimeout(notifCloseTimer);
    notifCloseTimer = setTimeout(() => {
      if (notifWindow && !notifWindow.isDestroyed()) notifWindow.close();
    }, 8000);
  };

  if (notifWindow && !notifWindow.isDestroyed()) {
    notifWindow.webContents.send('update-notification', { title, keyword });
    notifWindow.showInactive();
    notifWindow.setAlwaysOnTop(true, 'screen-saver');
    armClose();
    return;
  }
  const { width } = screen.getPrimaryDisplay().workAreaSize;
  notifWindow = new BrowserWindow({
    width:380, height:170, x: width-400, y:20,
    frame:false, alwaysOnTop:true, skipTaskbar:true, resizable:false,
    transparent:true, focusable:false, show:false,
    webPreferences:{ nodeIntegration:false, contextIsolation:true, preload:PRELOAD }
  });
  notifWindow.setAlwaysOnTop(true, 'screen-saver');
  notifWindow.loadFile(path.join(__dirname,'notification.html'));
  notifWindow.once('ready-to-show', () => {
    notifWindow.webContents.send('update-notification', { title, keyword });
    notifWindow.showInactive();
  });
  notifWindow.on('closed', () => {
    notifWindow = null;
    if (notifCloseTimer) { clearTimeout(notifCloseTimer); notifCloseTimer = null; }
  });
  armClose();
}

// ── Screen time ────────────────────────────────────────────────────────────
function getRemaining() {
  if (config.screen_time_limit <= 0) return -1;
  return Math.max(0, config.screen_time_limit * 60 - screenTimeSecondsUsed);
}

function updateTray() {
  if (!tray || tray.isDestroyed()) return;
  const rem = getRemaining();
  const timeStr = rem < 0 ? tr('tray_unlimited') : fmtTime(rem) + ' ' + tr('tray_left');
  tray.setToolTip(`STRIPARCOP  ·  ${timeStr}`);
}

// True when time should not be counted: explicit pause (lock/suspend) or system idle
// (monitor powered off / screensaver / inactivity).
function isCountingPaused() {
  if (screenTimePaused) return true;
  try {
    const state = powerMonitor.getSystemIdleState(IDLE_PAUSE_SECONDS);
    if (state === 'locked' || state === 'idle') return true;
  } catch (e) {}
  return false;
}

function startTimers() {
  // Pause screen time on lock/sleep, resume on unlock/wake.
  if (!global.__powerMonitorInitialized) {
    powerMonitor.on('lock-screen',   () => { screenTimePaused = true;  saveConfig(); });
    powerMonitor.on('unlock-screen', () => { screenTimePaused = false; });
    powerMonitor.on('suspend',       () => { screenTimePaused = true;  saveConfig(); });
    powerMonitor.on('resume',        () => { screenTimePaused = false; });
    // Logout / shutdown: persist and stop counting.
    powerMonitor.on('shutdown',      () => { screenTimePaused = true;  saveConfig(); });
    global.__powerMonitorInitialized = true;
  }
  // User session end (logout or shutdown): persist and quit gracefully.
  app.on('session-end', () => {
    screenTimePaused = true;
    saveConfig();
    if (!app.isQuitting) app.quit();
  });

  setInterval(() => {
    if (config.screen_time_date !== todayDate()) {
      config.screen_time_date = todayDate(); screenTimeSecondsUsed = 0; limitReached = false;
      warned15 = warned5 = warned1 = false;
    }

    if (!isCountingPaused()) {
      screenTimeSecondsUsed++;
      if (screenTimeSecondsUsed % 10 === 0) saveConfig();
    }

    const rem = getRemaining();
    updateTray();

    // One-shot warnings at 15 / 5 / 1 minute(s) left. Flags reset when time goes back above
    // a threshold (e.g. after add-time) or on a new day, so they fire reliably each descent.
    if (rem < 0) {
      warned15 = warned5 = warned1 = false;
    } else {
      if (rem > 15 * 60) { warned15 = warned5 = warned1 = false; }
      if (rem > 0 && rem <= 15 * 60 && !warned15) { warned15 = true; showTimeNotif(tr('notif_15')); }
      if (rem > 0 && rem <= 5 * 60  && !warned5)  { warned5  = true; showTimeNotif(tr('notif_5'));  }
      if (rem > 0 && rem <= 60      && !warned1)  { warned1  = true; showTimeNotif(tr('notif_1'));  }
    }

    if (rem === 0 && !limitReached) { limitReached = true; onLimitReached(); }
    // Re-enforce browser closing periodically (not every second) to keep CPU usage low
    if (limitReached && Date.now() - lastCloseOthers > 15000) closeOtherPrograms();

    if (screenTimeWindow && !screenTimeWindow.isDestroyed()) {
      screenTimeWindow.webContents.send('time-tick', { remaining: rem, used: screenTimeSecondsUsed, limit: config.screen_time_limit });
    }
  }, 1000);

  // Browser monitor
  startBrowsersMonitor();

  // Self-repair hosts file periodically (every 5 mins)
  setInterval(() => {
    updateHosts();
  }, 5 * 60 * 1000);

  // Self-repair persistence: re-create any autostart / guard vector that was removed.
  setInterval(() => {
    if (!app.isQuitting) hardenPersistence();
  }, 3 * 60 * 1000);
}

function onLimitReached() {
  showTimeNotif(tr('notif_limit'));
  closeOtherPrograms();
  openScreenTimeWindow();
  startLockoutEnforcer();
}

function closeOtherPrograms() {
  lastCloseOthers = Date.now();
  // Close every browser, but leave other apps (Word, Excel, …) alone.
  const browsers = ['chrome','firefox','msedge','opera','brave','vivaldi','iexplore','waterfox','librewolf','thorium','arc','sidekick','ghostery','whale'];
  const nameFilter = browsers.map(b => `$_.ProcessName -eq '${b}'`).join(' -or ');
  const ps = `
    Get-Process | Where-Object { $_.MainWindowTitle -and (${nameFilter}) -and $_.Id -ne ${process.pid} } | Stop-Process -Force -ErrorAction SilentlyContinue
  `;
  exec(`powershell -NoProfile -NonInteractive -Command "${ps.replace(/\n/g, '; ')}"`);
}

// While the daily limit is reached, keep the lock window covering the screen and on top
// so the machine cannot be used for anything except entering the password to add time.
function startLockoutEnforcer() {
  if (lockoutEnforcer) return;
  lockoutEnforcer = setInterval(() => {
    if (!limitReached || getRemaining() !== 0) {
      stopLockoutEnforcer();
      return;
    }
    if (!screenTimeWindow || screenTimeWindow.isDestroyed()) {
      openScreenTimeWindow();
      return;
    }
    // Re-assert the full-screen lockdown every tick so the machine cannot be used
    // (covers Alt+Tab away, focus theft, a moved/resized window, etc.).
    applyLockdown();
  }, 1000);
}

function stopLockoutEnforcer() {
  if (lockoutEnforcer) { clearInterval(lockoutEnforcer); lockoutEnforcer = null; }
  destroyCoverWindows();
}

// Cover every secondary display with a blank always-on-top window so nothing else is
// reachable. Idempotent: re-asserts existing covers instead of recreating them (the
// lockout enforcer calls this every second).
function createCoverWindows() {
  const primary = screen.getPrimaryDisplay();
  const secondary = screen.getAllDisplays().filter(d => d.id !== primary.id);
  if (coverWindows.length === secondary.length && coverWindows.every(w => w && !w.isDestroyed())) {
    for (const w of coverWindows) { try { w.setAlwaysOnTop(true, 'screen-saver'); w.moveTop(); if (!w.isVisible()) w.show(); } catch (e) {} }
    return;
  }
  destroyCoverWindows();
  for (const d of secondary) {
    const w = new BrowserWindow({
      x: d.bounds.x, y: d.bounds.y, width: d.bounds.width, height: d.bounds.height,
      frame:false, fullscreen:true, skipTaskbar:true, alwaysOnTop:true, focusable:false,
      backgroundColor:'#000000',
      webPreferences:{ nodeIntegration:false, contextIsolation:true }
    });
    w.setAlwaysOnTop(true, 'screen-saver');
    w.on('closed', () => {});
    coverWindows.push(w);
  }
}

function destroyCoverWindows() {
  for (const w of coverWindows) { try { if (w && !w.isDestroyed()) w.destroy(); } catch(e) {} }
  coverWindows = [];
}

function showTimeNotif(body) {
  if (Notification.isSupported()) {
    new Notification({ title: tr('notif_time_title'), body }).show();
  }
}

// ── Window factory ─────────────────────────────────────────────────────────
function makeWindow(file, opts) {
  const w = new BrowserWindow({
    ...opts,
    backgroundColor: nativeTheme.shouldUseDarkColors ? '#1b1d22' : '#f4f5f7',
    webPreferences:{ nodeIntegration:false, contextIsolation:true, preload:PRELOAD }
  });
  w.loadFile(path.join(__dirname, file));
  w.setMenu(null);
  return w;
}

function openMainWindow() {
  if (limitReached) { openScreenTimeWindow(); return; }
  if (mainWindow && !mainWindow.isDestroyed()) { mainWindow.show(); mainWindow.focus(); return; }
  mainWindow = makeWindow('index.html', { width:700, height:560, resizable:false });
  mainWindow.on('close', e => { if (!app.isQuitting) { e.preventDefault(); mainWindow.hide(); } });
  mainWindow.on('closed', () => { mainWindow = null; });
}

function openSettingsWindow() {
  if (limitReached) { openScreenTimeWindow(); return; }
  if (settingsWindow && !settingsWindow.isDestroyed()) { settingsWindow.focus(); return; }
  settingsWindow = makeWindow('settings.html', { width:640, height:840, resizable:false });
  settingsWindow.on('closed', () => { settingsWindow = null; });
}

// Force the lock window to fill the whole primary display and sit above everything.
// Done with an explicit bounds + borderless full-screen overlay rather than `kiosk`,
// which did not reliably resize the window on newer Electron (it showed up tiny).
function applyLockdown() {
  if (!screenTimeWindow || screenTimeWindow.isDestroyed()) return;
  const w = screenTimeWindow;
  const b = screen.getPrimaryDisplay().bounds;
  try {
    // Idempotent: only toggle state that is not already correct, so re-asserting it
    // every second neither flickers nor fights the user's own typing focus.
    if (!w.isResizable())   w.setResizable(true);  // required for programmatic sizing
    if (w.isClosable())     w.setClosable(false);
    if (w.isMovable())      w.setMovable(false);
    if (w.isMinimizable())  w.setMinimizable(false);
    if (!w.isAlwaysOnTop()) w.setAlwaysOnTop(true, 'screen-saver');
    w.setSkipTaskbar(true);
    // Borderless full-screen overlay (more reliable than kiosk for sizing). setBounds
    // first puts the window on the primary display so full-screen covers that screen.
    if (!w.isFullScreen()) {
      w.setBounds(b);
      w.setFullScreen(true);
    } else {
      const cur = w.getBounds();
      if (cur.width !== b.width || cur.height !== b.height) { w.setFullScreen(false); w.setBounds(b); w.setFullScreen(true); }
    }
    if (!w.isVisible()) w.show();
    if (!w.isFocused()) { w.moveTop(); w.focus(); }
  } catch (e) {}
  createCoverWindows();
}

function releaseLockdown() {
  if (!screenTimeWindow || screenTimeWindow.isDestroyed()) return;
  const w = screenTimeWindow;
  try {
    if (w.isFullScreen()) w.setFullScreen(false);
    w.setAlwaysOnTop(false);
    w.setClosable(true);
    w.setMovable(true);
    w.setMinimizable(true);
    w.setSkipTaskbar(false);
    w.setSize(440, 620);
    w.setResizable(false);
    w.center();
  } catch (e) {}
  destroyCoverWindows();
}

function openScreenTimeWindow() {
  const isTimeUp = getRemaining() === 0;

  if (screenTimeWindow && !screenTimeWindow.isDestroyed()) {
    if (isTimeUp) applyLockdown(); else { releaseLockdown(); screenTimeWindow.show(); screenTimeWindow.focus(); }
    return;
  }

  screenTimeWindow = new BrowserWindow({
    width: 440, height: 620,
    // Locked window must be resizable so setBounds/setFullScreen can actually grow it
    // (a non-resizable window ignores programmatic sizing — that was the "tiny" bug).
    resizable: isTimeUp,
    frame: !isTimeUp,
    closable: !isTimeUp,
    backgroundColor: nativeTheme.shouldUseDarkColors ? '#1b1d22' : '#f4f5f7',
    webPreferences:{ nodeIntegration:false, contextIsolation:true, preload:PRELOAD }
  });

  screenTimeWindow.loadFile(path.join(__dirname, 'screentime.html'));
  screenTimeWindow.setMenu(null);

  // Apply the full lockdown once the page is ready so the bounds/full-screen stick.
  if (isTimeUp) {
    screenTimeWindow.once('ready-to-show', applyLockdown);
    applyLockdown();
  }

  screenTimeWindow.on('close', (e) => {
    if (getRemaining() === 0 && !app.isQuitting) {
      e.preventDefault();
    }
  });

  screenTimeWindow.on('closed', () => { screenTimeWindow = null; });
}

// ── Tray ───────────────────────────────────────────────────────────────────
function createTray() {
  const iconPath = path.join(__dirname, '..', 'assets', 'icons', 'win', 'icon.ico');
  tray = new Tray(iconPath);
  tray.setToolTip('STRIPARCOP');
  tray.on('click', openMainWindow);
  buildTrayMenu();
}

function buildTrayMenu() {
  if (!tray || tray.isDestroyed()) return;
  const menu = Menu.buildFromTemplate([
    { label:tr('tray_open'),       click: openMainWindow },
    { label:tr('tray_screentime'), click: openScreenTimeWindow },
    { label:tr('tray_settings'),   click: openSettingsWindow },
    { type:'separator' },
    { label:tr('tray_exit'), click: protectExit },
  ]);
  tray.setContextMenu(menu);
}

async function protectExit() {
  if (!config.password_protected) {
    doExit(); return;
  }
  // Don't allow exiting while the machine is locked out.
  if (limitReached && getRemaining() === 0) return;

  let exitWindow = makeWindow('exit.html', {
    width: 320, height: 200, resizable: false, frame: false, alwaysOnTop: true,
  });
  exitWindow.on('closed', () => { exitWindow = null; });
}

function doExit() {
  app.isQuitting = true;
  stopLockoutEnforcer();
  // Tear down every protection vector so the sanctioned (password-protected) exit
  // genuinely stops the app instead of being resurrected by the guard task/service.
  disableProtection();
  cleanHosts();
  // The guard service re-applies protection every ~20 s, so once its uninstall has
  // had time to complete, tear the protection down once more (clearing anything it
  // may have re-created) and only then quit.
  setTimeout(() => { P.removeProtection(); setTimeout(() => app.quit(), 800); }, 2500);
}

ipcMain.on('confirmed-exit', () => doExit());

// ── IPC handlers ───────────────────────────────────────────────────────────
ipcMain.handle('get-config', () => {
  const safe = { ...config };
  delete safe.password_hash;
  safe.screen_time_remaining = getRemaining();
  safe.screen_time_used_seconds = screenTimeSecondsUsed;
  return safe;
});

ipcMain.handle('get-version', () => {
  const pkg = require(path.join(__dirname, '..', 'package.json'));
  return pkg.version;
});

// Fields that require the password to change while protection is on.
// Theme and language are intentionally NOT protected (cosmetic, safe to change freely).
const PROTECTED_KEYS = ['screen_time_limit', 'auto_start', 'custom_blocked_sites'];

ipcMain.handle('save-config', (_, incoming) => {
  try {
    // Enforce the password in the main process for any protected field — the renderer
    // lock overlay alone is not a security boundary.
    if (config.password_protected && PROTECTED_KEYS.some(k => k in incoming)) {
      if (!incoming._password || hashPw(incoming._password) !== config.password_hash) {
        return { success:false, error:tr('err_pw_time') };
      }
    }
    if (incoming.custom_blocked_sites) {
       incoming.custom_blocked_sites = incoming.custom_blocked_sites
         .map(s => s.toLowerCase().replace(/[\s\r\n]/g, '').trim())
         .filter(s => s.length > 1 && s.length < 100);
    }
    const allowedKeys = ['screen_time_limit','auto_start','custom_blocked_sites','lang','theme'];
    for (const k of allowedKeys) { if (k in incoming) config[k] = incoming[k]; }
    saveConfig();
    updateHosts();
    if ('auto_start' in incoming) setAutoStart(incoming.auto_start);
    if ('theme' in incoming) applyTheme();
    if ('lang' in incoming) { buildTrayMenu(); updateTray(); }
    broadcastConfigChanged();
    return { success:true };
  } catch(e) { return { success:false, error:e.message }; }
});

ipcMain.handle('check-password', (_, pw) => {
  if (!config.password_protected) return { ok:true };
  return { ok: hashPw(pw) === config.password_hash };
});

ipcMain.handle('set-password', (_, data) => {
  // If password already set, require old password to change it
  if (config.password_protected) {
    const oldPw = typeof data === 'object' ? data.oldPassword : null;
    if (!oldPw || hashPw(oldPw) !== config.password_hash) {
      return { success:false, error:tr('err_pw_old') };
    }
  }
  const newPw = typeof data === 'object' ? data.newPassword : data;
  if (!newPw || newPw.length < 4) return { success:false, error:tr('err_pw_min') };
  config.password_protected = true;
  config.password_hash = hashPw(newPw);
  saveConfig();
  preventUninstallation();
  return { success:true };
});

ipcMain.handle('remove-password', (_, pw) => {
  if (!config.password_protected) return { success:true };
  if (hashPw(pw) !== config.password_hash) return { success:false, error:tr('err_pw_wrong') };
  config.password_protected = false;
  config.password_hash = '';
  saveConfig();
  return { success:true };
});

ipcMain.handle('open-settings',  () => openSettingsWindow());
ipcMain.handle('open-screentime',() => openScreenTimeWindow());

ipcMain.handle('get-screentime-status', () => ({
  remaining: getRemaining(),
  used: screenTimeSecondsUsed,
  limit: config.screen_time_limit,
  limitReached,
}));

ipcMain.handle('add-time', (_, data) => {
  // Password protection: require password if set
  if (config.password_protected) {
    const pw = typeof data === 'object' ? data.password : null;
    if (!pw || hashPw(pw) !== config.password_hash) {
      return { success:false, error:tr('err_pw_wrong') };
    }
  }
  const mins = typeof data === 'object' ? data.mins : data;
  // Validate minutes: must be a positive number, max 120 minutes at a time
  const safeMins = Math.min(120, Math.max(0, parseInt(mins) || 0));
  screenTimeSecondsUsed = Math.max(0, screenTimeSecondsUsed - safeMins * 60);
  limitReached = false;
  // Re-arm the warnings so they fire again as the new budget runs down.
  if (getRemaining() > 15 * 60) { warned15 = warned5 = warned1 = false; }
  else if (getRemaining() > 5 * 60) { warned5 = warned1 = false; }
  else if (getRemaining() > 60) { warned1 = false; }
  saveConfig();
  stopLockoutEnforcer();
  releaseLockdown();
  return { success:true, remaining: getRemaining() };
});

ipcMain.handle('close-notification', () => {
  if (notifWindow && !notifWindow.isDestroyed()) notifWindow.close();
});

ipcMain.handle('get-blocked-count', () => config.blocked_count || 0);

function broadcastConfigChanged() {
  for (const w of [mainWindow, settingsWindow, screenTimeWindow]) {
    if (w && !w.isDestroyed()) w.webContents.send('config-changed', {});
  }
}

// ── App lifecycle ──────────────────────────────────────────────────────────
const gotLock = app.requestSingleInstanceLock();
if (!gotLock) { app.quit(); }
else {
  app.on('second-instance', () => { openMainWindow(); });

  app.whenReady().then(() => {
    try {
      fs.writeFileSync(BLOCKED_HTML_PATH, fs.readFileSync(path.join(__dirname, 'blocked.html')));
    } catch(e) {}

    loadConfig();
    applyTheme();
    updateHosts();
    if (config.auto_start === false) setAutoStart(false); else setAutoStart(true);
    hardenPersistence();
    createTray();

    if (!process.argv.includes('--hidden')) {
      openMainWindow();
    }

    startTimers();
    updateTray();

    // Add signal handler for graceful termination (e.g., system shutdown)
    process.on('SIGTERM', () => { saveConfig(); });
  });

  app.on('window-all-closed', e => e.preventDefault());
  app.on('quit', () => { saveConfig(); });
  app.on('before-quit', () => {
    app.isQuitting = true;
    saveConfig();
  });
}
