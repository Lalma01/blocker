; ── PS-BLOCK custom NSIS hooks ───────────────────────────────────────────────
; The running app ACL-locks its install directory (deny delete + write-DAC +
; write-owner to Everyone, owner = SYSTEM), hides its uninstall entry, and runs
; a SYSTEM guard service + a per-minute guard task. A normal installer/
; uninstaller therefore cannot touch the files on its own.
;
; That protection must only ever be lifted by someone who knows the app's own
; password (when one is set) — NOT simply by running the uninstaller, and NOT
; simply by re-running the installer to "update" and then cancelling. Both of
; those used to unconditionally strip every protection vector with no password
; check at all, which meant a password-locked install was, in practice, freely
; removable. The fix moves the decision (and the SHA-256 password check) into
; uninstall_gate.js — the single source of truth already used by the app
; itself — and calls it here before any file is modified or removed.
;
; Flow, run once at the very start of both the installer and the uninstaller
; (.onInit / un.onInit), via the shared PSBLOCKGate macro below:
;   1) --check   asks the *currently installed* app whether it is password-
;      protected right now. If not, there is nothing to gate — proceed as
;      normal (this also keeps a first-ever install, where nothing is
;      installed yet, fast and prompt-free).
;   2) If it is protected, show a small masked-password prompt (pw_prompt.ps1)
;      up to 3 times.
;   3) --teardown <password file> re-verifies the password against the stored
;      hash and, only on a match, removes the ACL lock, the guard service and
;      the guard task. Wrong password 3 times, or Cancel, aborts the
;      installer/uninstaller outright — no files are touched and every
;      protection vector stays exactly as it was.
;
; electron.exe only runs uninstall_gate.js "as Node" (ELECTRON_RUN_AS_NODE=1);
; that env var is set inside psblock_gate.bat for that one child process only,
; so it never leaks into the installer/uninstaller's own environment (which
; must keep launching the real Electron GUI afterwards, e.g. "run after
; finish"). Both helper files ship as ordinary app assets (see package.json's
; "files" list), so they are already on disk — for the uninstaller too — with
; no NSIS-side file embedding needed.
;
; Path fragments below are defined WITHOUT quotes (plain !define text
; substitution) and quoted explicitly at each point of use — nesting an
; already-quoted !define inside another quoted string does not reliably
; reparse in NSIS, so every literal command line here is spelled out in full.

!include "LogicLib.nsh"

!define PB_GATE_REL  resources\app\assets\psblock_gate.bat
!define PB_JS_REL    resources\app\app\uninstall_gate.js
!define PB_PS1_REL   resources\app\assets\pw_prompt.ps1
!define PB_PWFILE    $TEMP\psblock_pw.tmp

; ${un} is "" when inserted into customInit and "un" when inserted into
; customUnInit, so the generated labels stay unique within the compiled script
; even though both call sites live in the same .nsh file.
!macro PSBLOCKGate un
  nsExec::ExecToStack '"$INSTDIR\${PB_GATE_REL}" "$INSTDIR\PS-BLOCK.exe" "$INSTDIR\${PB_JS_REL}" --check'
  Pop $R1
  Pop $R2
  ${If} $R2 == "protected"
    ; Localize the messages below without depending on the installer's own
    ; MUI language (which may not include Hungarian).
    System::Call 'kernel32::GetUserDefaultUILanguage() i.r3'
    StrCpy $R0 3 ; attempts left

    psblock_retry_${un}:
      Delete "${PB_PWFILE}"
      nsExec::ExecToStack 'powershell -NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File "$INSTDIR\${PB_PS1_REL}" "${PB_PWFILE}"'
      Pop $R1
      ${If} $R1 != 0
        Goto psblock_abort_${un} ; prompt cancelled/closed
      ${EndIf}

      nsExec::ExecToStack '"$INSTDIR\${PB_GATE_REL}" "$INSTDIR\PS-BLOCK.exe" "$INSTDIR\${PB_JS_REL}" --teardown "${PB_PWFILE}"'
      Pop $R1
      Pop $R2
      Delete "${PB_PWFILE}"
      ${If} $R1 == 0
        Goto psblock_done_${un} ; correct password — protection removed, proceed
      ${EndIf}

      IntOp $R0 $R0 - 1
      ${If} $R0 <= 0
        Goto psblock_abort_${un}
      ${EndIf}
      ${If} $R3 == 1038
        MessageBox MB_OK|MB_ICONEXCLAMATION "Hibás jelszó! Még $R0 próbálkozás van hátra."
      ${Else}
        MessageBox MB_OK|MB_ICONEXCLAMATION "Wrong password! Attempts left: $R0"
      ${EndIf}
      Goto psblock_retry_${un}

    psblock_abort_${un}:
      ${If} $R3 == 1038
        MessageBox MB_OK|MB_ICONSTOP "A PS-BLOCK jelszóval van zárolva. Jelszó nélkül nem távolítható el és nem frissíthető."
      ${Else}
        MessageBox MB_OK|MB_ICONSTOP "PS-BLOCK is password-locked. It cannot be removed or updated without the password."
      ${EndIf}
      Abort

    psblock_done_${un}:
  ${EndIf}
!macroend

; Runs early in the installer .onInit — before the previous version is replaced.
!macro customInit
  !insertmacro PSBLOCKGate ""
!macroend

; Runs at the start of uninstallation — before any file is removed.
!macro customUnInit
  !insertmacro PSBLOCKGate "un"
!macroend
