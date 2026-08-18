'use strict';
// ── PS-BLOCK uninstall/update gate ──────────────────────────────────────────
// Pure Node script (no Electron deps — run via electron.exe with
// ELECTRON_RUN_AS_NODE=1, exactly like service.js) invoked by the NSIS
// installer/uninstaller *before* it touches a single file. It is the single
// place that decides whether tamper protection may be torn down, so the
// password check lives here once instead of being duplicated in NSIS/PowerShell.
//
// Without this gate, simply running "Uninstall PS-BLOCK.exe" (found by opening
// the install folder directly, bypassing the hidden Control Panel entry) or
// re-running the installer used to unconditionally strip the ACL lock, the
// guard service and the guard task — no password needed. That was the bug:
// the app was "simply removable" despite being password-locked.
//
// Usage (from installer.nsh):
//   electron.exe uninstall_gate.js --check              → prints "protected" or "unprotected"
//   electron.exe uninstall_gate.js --teardown <pwfile>   → verifies the password stored in
//                                                           <pwfile> (never passed as an argv
//                                                           value, so it never shows up in a
//                                                           process list) and, only if it
//                                                           matches, removes every protection
//                                                           vector. Exit code 0 = allowed to
//                                                           proceed, non-zero = blocked.
// If no password is set at all (config missing or password_protected=false), both the
// check and the teardown succeed immediately — an unlocked install stays freely removable.

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const P = require('./protection');

const CONFIG_PATH = path.join(process.env.APPDATA || '', 'PS-BLOCK', 'config.json');
const hashPw = pw => crypto.createHash('sha256').update(pw + 'cb_v1').digest('hex');

function readConfig() {
  try { return JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8')); } catch { return null; }
}

function isProtected(cfg) {
  return !!(cfg && cfg.password_protected && cfg.password_hash);
}

async function teardown() {
  await P.removeProtection();
  try { await require('./service_control').removeServiceAsync(); } catch (e) {}
  // The guard service may have queued one more re-apply tick right before it
  // stopped; give it a moment, then clear whatever it re-created.
  await new Promise(r => setTimeout(r, 1500));
  await P.removeProtection();
}

async function main() {
  const mode = process.argv[2];
  const cfg = readConfig();
  const protectedOn = isProtected(cfg);

  if (mode === '--check') {
    process.stdout.write(protectedOn ? 'protected' : 'unprotected');
    process.exit(0);
  }

  if (mode === '--teardown') {
    if (protectedOn) {
      const pwFile = process.argv[3];
      let pw = '';
      try { pw = fs.readFileSync(pwFile, 'utf8').replace(/\r?\n$/, ''); } catch (e) {}
      if (!pw || hashPw(pw) !== cfg.password_hash) process.exit(1);
    }
    try { await teardown(); process.exit(0); } catch (e) { process.exit(1); }
    return;
  }

  process.exit(1); // unknown/missing mode — fail closed
}

main();
