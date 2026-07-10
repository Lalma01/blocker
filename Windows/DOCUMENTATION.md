# STRIPARCOP — Technical Documentation

*STRIPARCOP = Strict Parental Control Program*

*Magyar dokumentáció a [második részben](#striparco--magyar-dokumentáció).*

---

## 1. Overview

**STRIPARCO** is a Windows desktop application (built on Electron) that combines a local
content filter with a screen-time guard. It is designed for self-control and parental-style
use on a single machine. Everything runs locally; the program never sends data to any
external server.

Two protections work together:

1. **Content filtering** — blocks adult ("NSFW") websites and NSFW-AI companion / image
   generator sites, while leaving mainstream AI assistants (ChatGPT, Claude, Gemini, Grok,
   Perplexity, Copilot, etc.) accessible.
2. **Screen-time guard** — enforces a configurable daily time budget. When the budget is
   spent, the machine is locked down until time is added with the password.

> **Related:** a full-parity Android port lives in [`../Android/`](../Android/)
> (see its [`README.md`](../Android/README.md)).

---

## 2. Features

| Area | Behaviour |
|------|-----------|
| **Website blocking** | The Windows `hosts` file redirects known adult / NSFW-AI domains to `127.0.0.1`. The list is self-repaired every 5 minutes. |
| **Browser-title monitor** | A background PowerShell loop reports browser window titles ~once per second. Matching titles trigger a redirect to the local block page plus an on-screen notice. The redirect **preserves and restores the user's clipboard**. |
| **Keyword tiers** | *Strong* keywords block on a single match. *Weak / ambiguous* keywords (e.g. "dating", "escort") only block when at least two appear in the same title — reducing false positives. |
| **AI allow-list** | Mainstream AI assistant domains and titles are never blocked. |
| **Screen-time limit** | A daily limit in minutes (0 = unlimited). Warnings appear **once** at 15, 5 and 1 minute(s) remaining; they re-arm after time is added. |
| **Lock-out** | When time runs out, all browsers are closed and a full-screen kiosk window covers every display. Only the password prompt (to add time) is usable. |
| **Password-protected settings** | When a password is set, the **main process** (not just the UI) requires it to change the screen-time limit, auto-start or the custom blocklist, and to add time or exit. **Theme and language stay changeable without the password** (cosmetic, safe). |
| **Theme** | Follows the Windows light/dark setting automatically; can be forced to Light or Dark in Settings. |
| **Bilingual UI** | English / Hungarian, auto-detected on first run, switchable in Settings. |
| **Persistence (anti-uninstall)** | Always-on, with one strong core + one light backup (no kernel driver — that would need EV + MS-attestation signing). **(a) LocalSystem guard service** — boot-started, auto-restarting; as SYSTEM it re-applies the tamper protection every ~20 s. **(b) Per-minute guard task** (pinned to the console user, interactive) — relaunches the GUI within ~60 s of any kill. Protection = NTFS **ACL lock** on the install dir (files can't be deleted) + the Control Panel/Settings uninstall entry **hidden on the real key** (matched by DisplayName, `SystemComponent`+`NoRemove`/`NoModify`). Note: while locked, installer-based updates require the password **Exit** first (which removes the service, ACL lock, uninstall-hide and guard task). |
| **Privacy** | No telemetry, no network calls, no logging of browsing to disk beyond a local block counter. |

---

## 3. Screen-time counting rules

Time is counted once per second **only while the user is actively using the machine**.
Counting **pauses** in all of the following cases:

- the workstation is **locked** (WIN+L, lock screen, switch user);
- the machine **suspends / sleeps** (and resumes counting on wake);
- the user **logs out or shuts down** (state is saved first);
- the **monitor powers off** or the session is otherwise **idle** for at least
  60 seconds (covers screensaver / display-off via the system idle state).

This is implemented with Electron's `powerMonitor` lock/suspend/shutdown events plus an
idle-state check (`powerMonitor.getSystemIdleState`) inside the per-second tick. Usage is
persisted to `config.json` every 10 seconds and on every pause event, so a sudden power
loss loses at most a few seconds.

---

## 4. What happens when time runs out

1. A system notification announces the limit was reached.
2. All running browsers are force-closed (other applications are left untouched).
3. The screen-time window switches to **kiosk** mode: full-screen, always-on-top
   (`screen-saver` level), not closable, not in the taskbar.
4. Every **secondary display** is covered by a blank always-on-top window.
5. A 1-second **enforcer** keeps the lock window focused and on top; if it loses focus or
   is closed it is restored. Browsers are re-closed every 15 seconds.
6. The **only** interactive element is the password field used to add 15 minutes. Entering
   the correct password (or, if no password is set, pressing the button) clears the
   lock-out immediately.

Exiting the app via the tray is refused while the machine is locked out.

---

## 5. Architecture & files

All program files live in the `Windows/` folder:

```
Windows/
├── app/
│   ├── main.js          Main process: config, hosts, screen-time, lock-out, IPC, tray, protection
│   ├── preload.js       Context-isolated bridge (window.api)
│   ├── protection.js    Tamper-protection core (ACL lock, uninstall-hide, guard task)
│   ├── service.js       LocalSystem guard service body (re-applies protection.js)
│   ├── service_control.js  Installs/removes the guard service (node-windows)
│   ├── i18n.js          Renderer translations (EN / HU)
│   ├── theme.css        Single shared stylesheet for every window (auto light/dark)
│   ├── index.html       Dashboard
│   ├── settings.html    Settings (theme, language, password, time limit, auto-start, blocklist)
│   ├── screentime.html  Screen-time ring + add-time / lock-out screen
│   ├── blocked.html     Local block page shown in the browser
│   ├── notification.html Toast shown when a title is blocked
│   ├── exit.html        Password prompt for exiting
│   └── version_bump.js  Sets version to <major>.<minor>.<git-commit-count> at build time
├── assets/              Application icons (png + win/icon.ico)
├── build.bat            One-click build script
├── package.json         App + electron-builder configuration
└── package-lock.json
```

The UI is intentionally consistent: every window loads the same `theme.css`, so all
surfaces, inputs, buttons and overlays share one visual language and one set of colour
tokens.

### Theme

`theme.css` defines colour tokens for light mode and overrides them under
`@media (prefers-color-scheme: dark)`. The main process sets
`nativeTheme.themeSource` from the saved `theme` setting (`system` / `light` / `dark`),
so the renderer's `prefers-color-scheme` reflects the chosen value live, without reloading.

---

## 6. Configuration

Settings are stored in:

```
%APPDATA%\STRIPARCO\config.json
```

| Key | Meaning |
|-----|---------|
| `screen_time_limit` | Daily limit in minutes (0 = unlimited). |
| `screen_time_used` / `screen_time_date` | Seconds used today and the date they belong to. |
| `password_protected` / `password_hash` | Whether a password is set, and its SHA-256 hash. |
| `auto_start` | Start with Windows (scheduled task, with a registry Run fallback). |
| `custom_blocked_sites` | User-added domains / keywords (treated as strong matches). |
| `theme` | `system` \| `light` \| `dark`. |
| `lang` | `hu` \| `en`. |
| `blocked_count` | Local counter of blocked page loads. |

The password is never stored in clear text; only its SHA-256 hash is kept and the hash is
never sent to any renderer.

**Password enforcement.** Changes to `screen_time_limit`, `auto_start` and
`custom_blocked_sites` are validated against the hash **in the main process** (`save-config`),
so they cannot be bypassed from the renderer. `theme` and `lang` are intentionally exempt.
After unlocking the Settings window once, the entered password is reused for that session's
protected saves so the user is not re-prompted repeatedly.

---

## 7. Building the installer

Requirements: Windows, Node.js, and `assets/icons/win/icon.ico`.

```bat
cd Windows
build.bat
```

`build.bat` runs `npm install`, bumps the version (`version_bump.js`), checks the icon, and
runs `npm run dist` (electron-builder, NSIS, x64 + ia32). The installer is written to
`Windows/dist/STRIPARCO Setup <version>.exe`.

The installer requests administrator rights, which are required to edit the `hosts` file.

---

## 8. Privacy & security

- No network requests are made by the application; all filtering is local.
- No browsing history is written to disk — only a numeric counter of blocked loads.
- The repository and the program contain no machine identifiers, IP addresses, user names
  or other host-specific data.

---

## 9. Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| Sites are not blocked | The app needs administrator rights to write the `hosts` file. Reinstall / run elevated. |
| A blocked title still shows briefly | The redirect needs the browser window in focus; it retries on the next monitor tick (~1 s). |
| App reopens after closing | Expected: the guard task/service restarts it. Use tray → Exit (with password) to stop it permanently. |
| Cannot uninstall from Settings / Control Panel | Expected: the entry is hidden and the install dir is ACL-locked while protection is on. Use tray → Exit (with password) first, which removes the service, ACL lock and uninstall-hide; then uninstall normally. |
| Theme does not match Windows | Set theme to **System** in Settings, or pick Light/Dark manually. |

---
---

# STRIPARCO — Magyar dokumentáció

## 1. Áttekintés

A **STRIPARCO** egy Windows asztali alkalmazás (Electron alapokon), amely egy helyi
tartalomszűrőt és egy képernyőidő-felügyelőt egyesít. Önkontrollra és szülői felügyelet
jellegű használatra készült egyetlen gépen. Minden helyben fut; a program soha nem küld
adatot külső szerverre.

Két védelem dolgozik együtt:

1. **Tartalomszűrés** — tiltja a felnőtt ("NSFW") oldalakat és az NSFW-AI „barátnő” /
   képgenerátor oldalakat, miközben a bevett AI asszisztensek (ChatGPT, Claude, Gemini,
   Grok, Perplexity, Copilot stb.) elérhetők maradnak.
2. **Képernyőidő-felügyelet** — beállítható napi időkeretet tart be. Ha az időkeret
   elfogy, a gép zárolódik, amíg jelszóval időt nem adunk hozzá.

> **Kapcsolódó:** a teljes funkciójú Android port a [`../Android/`](../Android/) mappában
> található (lásd a [`README.md`](../Android/README.md)-t).

---

## 2. Funkciók

| Terület | Működés |
|---------|---------|
| **Weboldal-tiltás** | A Windows `hosts` fájl a felnőtt / NSFW-AI domaineket a `127.0.0.1`-re irányítja. A lista 5 percenként önjavul. |
| **Böngésző-cím figyelés** | Egy háttérben futó PowerShell ciklus kb. másodpercenként jelenti a böngészők címsorát. A talált címek átirányítást és figyelmeztető buborékot váltanak ki. Az átirányítás **megőrzi és visszaállítja a felhasználó vágólapját**. |
| **Kulcsszó-szintek** | Az *erős* kulcsszavak egyetlen találatra is blokkolnak. A *gyenge / kétértelmű* szavak (pl. „dating”, „escort”) csak akkor, ha legalább kettő szerepel együtt — így kevesebb a téves találat. |
| **AI engedélyező lista** | A bevett AI asszisztensek domainjei és címei soha nincsenek tiltva. |
| **Képernyőidő-korlát** | Napi limit percben (0 = korlátlan). Figyelmeztetés **egyszer** 15, 5 és 1 perc maradéknál; idő hozzáadása után újra élesednek. |
| **Zárolás** | Az idő lejártakor minden böngésző bezárul, és egy teljes képernyős kiosk ablak takar minden kijelzőt. Csak a jelszó megadása (idő hozzáadása) használható. |
| **Jelszóvédett beállítások** | Ha van jelszó, a **fő folyamat** (nem csak a felület) megköveteli azt a képernyőidő-korlát, az automatikus indítás és az egyéni tiltólista módosításához, valamint idő hozzáadásához és kilépéshez. A **téma és a nyelv jelszó nélkül is módosítható** (kozmetikai, biztonságos). |
| **Téma** | Automatikusan követi a Windows világos/sötét beállítását; a Beállításokban kézzel Világosra vagy Sötétre is állítható. |
| **Kétnyelvű felület** | Magyar / angol, első indításkor automatikus felismeréssel, a Beállításokban váltható. |
| **Védelem (eltávolítás elleni)** | Mindig aktív, egy erős mag + egy könnyű tartalék (kernel-driver nélkül – az EV + Microsoft attestation-aláírást igényelne). **(a) LocalSystem guard-szolgáltatás** – boot-időben indul, automatikusan újraindul; SYSTEM-ként ~20 mp-enként újra-alkalmazza a védelmet. **(b) Percenkénti guard feladat** (a konzol-felhasználóhoz kötve, interaktívan) – bármilyen leállítás után ~60 mp-en belül visszahozza a GUI-t. A védelem: NTFS **ACL-zár** a telepítőmappán (a fájlok nem törölhetők) + a Vezérlőpult/Gépház eltávolító bejegyzésének **elrejtése a valódi kulcson** (DisplayName szerint, `SystemComponent`+`NoRemove`/`NoModify`). Megjegyzés: zárolt állapotban a telepítőalapú frissítéshez előbb a jelszavas **Kilépés** kell (ez eltávolítja a szolgáltatást, az ACL-zárat, az uninstall-rejtést és a guard feladatot). |
| **Adatvédelem** | Nincs telemetria, nincs hálózati hívás, a böngészésből csak egy helyi számláló kerül lemezre. |

---

## 3. A képernyőidő számolásának szabályai

Az idő másodpercenként csak akkor növekszik, **amikor a felhasználó ténylegesen használja
a gépet**. A számolás az alábbi esetekben **szünetel**:

- a munkaállomás **zárolva van** (WIN+L, zárképernyő, felhasználóváltás);
- a gép **alvó / felfüggesztett** módba lép (ébredéskor folytatódik);
- a felhasználó **kijelentkezik vagy leállítja a gépet** (előbb mentés történik);
- a **monitor kikapcsol**, vagy a munkamenet legalább 60 másodpercig **tétlen**
  (a képernyővédő / kijelző-kikapcsolás a rendszer tétlenségi állapotán keresztül).

Ez az Electron `powerMonitor` zárolás/alvás/leállás eseményeivel, valamint a
másodperces ciklusban végzett tétlenség-ellenőrzéssel (`getSystemIdleState`) valósul meg.
A felhasználás 10 másodpercenként és minden szüneteltető eseménynél mentésre kerül a
`config.json`-ba, így váratlan áramszünet esetén is csak néhány másodperc veszhet el.

---

## 4. Mi történik, ha lejár az idő

1. Rendszerértesítés jelzi, hogy elfogyott az időkeret.
2. Minden futó böngésző kényszerített bezárása (más programok érintetlenek maradnak).
3. A képernyőidő-ablak **kiosk** módba vált: teljes képernyő, mindig felül
   (`screen-saver` szint), nem bezárható, nincs a tálcán.
4. Minden **másodlagos kijelzőt** egy üres, mindig felül lévő ablak takar le.
5. Egy másodpercenkénti **felügyelő** fókuszban és felül tartja a zároló ablakot; ha
   elveszti a fókuszt vagy bezárják, visszaállítja. A böngészők 15 másodpercenként
   újra bezáródnak.
6. Az **egyetlen** használható elem a jelszómező, amellyel 15 perc adható hozzá. A helyes
   jelszó megadása (vagy jelszó hiányában a gomb) azonnal feloldja a zárolást.

A tálcáról való kilépés zárolt állapotban nem engedélyezett.

---

## 5. Felépítés és fájlok

Minden programfájl a `Windows/` mappában van:

```
Windows/
├── app/
│   ├── main.js          Fő folyamat: beállítások, hosts, képernyőidő, zárolás, IPC, tálca, védelem
│   ├── preload.js       Kontextus-izolált híd (window.api)
│   ├── protection.js    Védelmi mag (ACL-zár, uninstall-elrejtés, guard task)
│   ├── service.js       LocalSystem guard-szolgáltatás törzse (protection.js újra-alkalmazás)
│   ├── service_control.js  A guard-szolgáltatás telepítése/eltávolítása (node-windows)
│   ├── i18n.js          Renderer fordítások (EN / HU)
│   ├── theme.css        Egyetlen közös stíluslap minden ablakhoz (automatikus világos/sötét)
│   ├── index.html       Vezérlőpult
│   ├── settings.html    Beállítások (téma, nyelv, jelszó, időkeret, automatikus indítás, tiltólista)
│   ├── screentime.html  Képernyőidő-gyűrű + idő hozzáadása / zároló képernyő
│   ├── blocked.html     Helyi blokkoló oldal a böngészőben
│   ├── notification.html Buborék egy tiltott cím esetén
│   ├── exit.html        Jelszókérő a kilépéshez
│   └── version_bump.js  A verziót <major>.<minor>.<git-commit-szám> alakra állítja buildkor
├── assets/              Alkalmazás-ikonok (png + win/icon.ico)
├── build.bat            Egygombos build szkript
├── package.json         Alkalmazás + electron-builder konfiguráció
└── package-lock.json
```

A felület szándékosan egységes: minden ablak ugyanazt a `theme.css`-t tölti be, így minden
felület, beviteli mező, gomb és átfedő réteg ugyanazt a vizuális nyelvet és színkészletet
használja.

### Téma

A `theme.css` világos módra definiál színeket, és a
`@media (prefers-color-scheme: dark)` blokkban felülírja őket sötét módra. A fő folyamat a
mentett `theme` beállításból (`system` / `light` / `dark`) állítja a
`nativeTheme.themeSource` értéket, így a renderer `prefers-color-scheme` értéke élőben,
újratöltés nélkül követi a választást.

---

## 6. Beállítások

A beállítások helye:

```
%APPDATA%\STRIPARCO\config.json
```

| Kulcs | Jelentés |
|-------|----------|
| `screen_time_limit` | Napi limit percben (0 = korlátlan). |
| `screen_time_used` / `screen_time_date` | A ma felhasznált másodpercek és a hozzájuk tartozó dátum. |
| `password_protected` / `password_hash` | Van-e jelszó, és annak SHA-256 lenyomata. |
| `auto_start` | Indulás a Windows-zal (ütemezett feladat, registry Run tartalékkal). |
| `custom_blocked_sites` | Felhasználó által hozzáadott domainek / kulcsszavak (erős találatként kezelve). |
| `theme` | `system` \| `light` \| `dark`. |
| `lang` | `hu` \| `en`. |
| `blocked_count` | A blokkolt oldalbetöltések helyi számlálója. |

A jelszó soha nem kerül tárolásra nyílt szövegként; csak az SHA-256 lenyomata, amely
sosem jut el egyetlen renderer ablakhoz sem.

**Jelszó-kényszerítés.** A `screen_time_limit`, `auto_start` és `custom_blocked_sites`
módosítását a **fő folyamat** ellenőrzi a lenyomattal (`save-config`), így a renderer felől
nem kerülhető meg. A `theme` és a `lang` szándékosan kivétel. A Beállítások egyszeri
feloldása után a megadott jelszót a munkamenet védett mentéseihez újrahasználja, hogy ne
kelljen ismételten beírni.

---

## 7. A telepítő elkészítése

Követelmények: Windows, Node.js és az `assets/icons/win/icon.ico`.

```bat
cd Windows
build.bat
```

A `build.bat` lefuttatja az `npm install`-t, növeli a verziót (`version_bump.js`),
ellenőrzi az ikont, majd lefuttatja az `npm run dist`-et (electron-builder, NSIS, x64 +
ia32). A telepítő helye: `Windows/dist/STRIPARCO Setup <verzió>.exe`.

A telepítő rendszergazdai jogot kér, amely a `hosts` fájl módosításához szükséges.

---

## 8. Adatvédelem és biztonság

- Az alkalmazás nem indít hálózati kéréseket; minden szűrés helyben történik.
- Nem ír böngészési előzményt lemezre — csak a blokkolt betöltések számát.
- A repó és a program nem tartalmaz gép-azonosítót, IP-címet, felhasználónevet vagy más,
  a géphez köthető adatot.

---

## 9. Hibaelhárítás

| Tünet | Ok / megoldás |
|-------|---------------|
| Az oldalak nem blokkolódnak | Az alkalmazásnak rendszergazdai jog kell a `hosts` íráshoz. Telepítsd újra / futtasd emelt jogon. |
| Egy tiltott cím rövid ideig még látszik | Az átirányításhoz a böngészőablaknak fókuszban kell lennie; a következő figyelési körben (~1 mp) újrapróbálja. |
| Az alkalmazás bezárás után újranyílik | Ez normális: a guard task/szolgáltatás újraindítja. Végleges leállítás: tálca → Kilépés (jelszóval). |
| Nem tudom eltávolítani a Gépházból / Vezérlőpultból | Ez szándékos: a bejegyzés rejtve van, a telepítőmappa ACL-zárolt, amíg a védelem aktív. Előbb tálca → Kilépés (jelszóval) – ez eltávolítja a szolgáltatást, az ACL-zárat és az uninstall-rejtést –, utána normálisan eltávolítható. |
| A téma nem egyezik a Windows-zal | Állítsd a témát **Rendszer szerint**-re a Beállításokban, vagy válassz kézzel Világos/Sötét értéket. |
