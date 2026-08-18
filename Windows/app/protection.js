'use strict';
// ── PS-BLOCK tamper-protection core ─────────────────────────────────────────
// Pure Node module (no Electron deps) so it can run both inside the GUI process
// and inside the SYSTEM service (electron.exe started with ELECTRON_RUN_AS_NODE=1).
//
// Strategy (no kernel driver — that would need EV + MS attestation signing):
//   1) NTFS ACL lock on the install directory  → files cannot be deleted.
//   2) Hide the uninstall entry on the CORRECT registry key (matched by
//      DisplayName, not a guessed appId) → nothing to click in Settings / Control
//      Panel, and the "Uninstall" button is disabled even if the entry is shown.
//   3) A single per-minute guard task keeps the GUI alive (the SYSTEM service
//      re-creates it if removed, and re-applies 1) and 2) every cycle).
//
// Everything here is idempotent and best-effort: failures are swallowed so a
// partially-applied state on the next cycle simply gets completed.

const { exec, execSync } = require('child_process');
const path = require('path');

const PRODUCT       = 'PS-BLOCK';
const SERVICE_NAME  = 'PS_BLOCK_Service';
const GUARD_TASK    = 'PS-BLOCK_Guard';
const EVERYONE_SID  = '*S-1-1-0';

const UNINSTALL_ROOTS = [
  'HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall',
  'HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall',
];

const run  = cmd => new Promise(res => exec(cmd, { windowsHide: true }, (e, so) => res({ e, so })));
const runQ = cmd => { try { return execSync(cmd, { windowsHide: true, stdio: ['ignore', 'pipe', 'ignore'] }).toString(); } catch { return ''; } };

const installDir = () => path.dirname(process.execPath);

// ── 1) Install-directory ACL lock ──────────────────────────────────────────
// Deny, to Everyone and inherited to every file/subfolder:
//   DE   delete            DC   delete child
//   WDAC write-DAC         WO   write-owner
// Denying WDAC/WO is what actually stops `rm -rf` / Explorer delete: with only
// DE+DC a tool (or MSYS `rm -f`, which chmods first) could rewrite the ACL and
// strip the deny. We also hand ownership to SYSTEM so an *administrator* (who is
// otherwise the implicit owner of Program Files and could rewrite the DACL) no
// longer can. SYSTEM, being the owner, can still re-apply this every cycle.
// A determined admin can still `takeown` first — the inherent user-mode limit —
// but a plain delete (incl. Git Bash) is blocked.
const DENY_RIGHTS = '(OI)(CI)(DE,DC,WDAC,WO)';
const SID_SYSTEM  = '*S-1-5-18';      // NT AUTHORITY\SYSTEM
const SID_ADMINS  = '*S-1-5-32-544';  // BUILTIN\Administrators
// SIDs (not names) so nothing breaks on a non-English Windows.

async function lockInstallDir() {
  const dir = installDir();
  await run(`icacls "${dir}" /deny "${EVERYONE_SID}:${DENY_RIGHTS}" /T /C`);
  // Best-effort: the SYSTEM service always succeeds; the elevated GUI may not.
  await run(`icacls "${dir}" /setowner "${SID_SYSTEM}" /T /C`);
}
async function unlockInstallDir() {
  const dir = installDir();
  // Seize ownership back to Administrators (the elevated GUI has SeTakeOwnership)
  // so the deny can be stripped. icacls /setowner is used instead of `takeown`
  // because takeown's /d confirmation letter is localized (e.g. "I" on Hungarian
  // Windows) and would otherwise hang/fail. Then remove the deny → uninstallable.
  await run(`icacls "${dir}" /setowner "${SID_ADMINS}" /T /C`);
  await run(`icacls "${dir}" /remove:d "${EVERYONE_SID}" /T /C`);
}

// ── 2) Uninstall registry entry ─────────────────────────────────────────────
// Find the real key(s) by matching DisplayName, regardless of the GUID name the
// NSIS installer chose.
function findUninstallKeys() {
  const keys = [];
  for (const root of UNINSTALL_ROOTS) {
    const out = runQ(`reg query "${root}" /s /v DisplayName`);
    if (!out) continue;
    let currentKey = null;
    for (const raw of out.split(/\r?\n/)) {
      const line = raw.trimEnd();
      if (/^HKEY_/i.test(line.trim())) { currentKey = line.trim(); continue; }
      const m = line.match(/DisplayName\s+REG_SZ\s+(.+)$/i);
      if (m && currentKey && m[1].trim().toLowerCase().includes(PRODUCT.toLowerCase())) {
        keys.push(currentKey);
        currentKey = null;
      }
    }
  }
  return [...new Set(keys)];
}

function hideUninstall() {
  const tasks = [];
  for (const key of findUninstallKeys()) {
    // SystemComponent=1 hides it from the Programs list entirely (honoured by both
    // the classic Control Panel and the modern Settings app). NoRemove/NoModify
    // additionally grey out the buttons if it is ever surfaced.
    tasks.push(run(`reg add "${key}" /v SystemComponent /t REG_DWORD /d 1 /f`));
    tasks.push(run(`reg add "${key}" /v NoRemove /t REG_DWORD /d 1 /f`));
    tasks.push(run(`reg add "${key}" /v NoModify /t REG_DWORD /d 1 /f`));
    tasks.push(run(`reg add "${key}" /v NoRepair /t REG_DWORD /d 1 /f`));
  }
  return Promise.all(tasks);
}

function restoreUninstall() {
  const tasks = [];
  for (const key of findUninstallKeys()) {
    tasks.push(run(`reg delete "${key}" /v SystemComponent /f`));
    tasks.push(run(`reg delete "${key}" /v NoRemove /f`));
    tasks.push(run(`reg delete "${key}" /v NoModify /f`));
    tasks.push(run(`reg delete "${key}" /v NoRepair /f`));
  }
  return Promise.all(tasks);
}

// ── 3) Guard task (keeps the GUI alive in the user session) ─────────────────
// The relaunched GUI must run in the interactive desktop session, not session 0
// (where the SYSTEM service lives), otherwise the lockout/notification windows
// would be invisible. We therefore pin the task to the console user with /it
// (interactive-only, no stored password). The single-instance lock makes a
// redundant launch a no-op while the app is already running.
function consoleUser() {
  // Works from both the GUI process (correct env) and the SYSTEM service (queries
  // the console session owner).
  // `wmic` is deprecated (removed entirely on newer Windows 11 builds), so the
  // CIM/PowerShell equivalent is tried first; wmic stays as a fast path on
  // systems that still ship it, then env vars as the final fallback.
  const fromCim = runQ('powershell -NoProfile -NonInteractive -Command "(Get-CimInstance Win32_ComputerSystem).UserName"').trim();
  if (fromCim) return fromCim;
  const fromWmic = runQ('wmic computersystem get username /value');
  const m = fromWmic.match(/UserName=(.+)/i);
  if (m && m[1].trim()) return m[1].trim();
  const d = process.env.USERDOMAIN, u = process.env.USERNAME;
  return (d && u && u.toLowerCase() !== `${process.env.COMPUTERNAME || ''}$`.toLowerCase())
    ? `${d}\\${u}` : '';
}
function createGuardTask() {
  const user = consoleUser();
  if (!user) return Promise.resolve();   // no one logged on yet — retry next cycle
  const tr = `\\"${process.execPath}\\" --hidden`;
  return run(`schtasks /create /tn "${GUARD_TASK}" /tr "${tr}" /sc minute /mo 1 /rl highest /ru "${user}" /it /f`);
}
function deleteGuardTask() {
  return run(`schtasks /delete /tn "${GUARD_TASK}" /f`);
}

// ── Aggregate apply / remove ────────────────────────────────────────────────
async function applyProtection() {
  await createGuardTask();
  await hideUninstall();
  await lockInstallDir();
}
async function removeProtection() {
  await deleteGuardTask();
  await restoreUninstall();
  await unlockInstallDir();
}

module.exports = {
  PRODUCT, SERVICE_NAME, GUARD_TASK, installDir,
  lockInstallDir, unlockInstallDir,
  findUninstallKeys, hideUninstall, restoreUninstall,
  createGuardTask, deleteGuardTask,
  applyProtection, removeProtection,
};
