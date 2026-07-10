; ── STRIPARCOP custom NSIS hooks ────────────────────────────────────────────
; The running app ACL-locks its install directory (deny delete + write-DAC +
; write-owner to Everyone, owner = SYSTEM), removes the guard task and runs a
; SYSTEM guard service. A normal installer/uninstaller therefore cannot touch
; the files. These hooks lift that protection first so upgrades and the
; sanctioned uninstall keep working ("update-compatible"). Well-known SIDs are
; used so nothing breaks on a non-English Windows.

!macro DisableStriparcopProtection
  ; Stop & remove the SYSTEM guard service (node-windows/winsw id) and guard task.
  nsExec::Exec 'net stop "striparco_service.exe"'
  nsExec::Exec 'sc delete "striparco_service.exe"'
  nsExec::Exec 'schtasks /delete /tn "STRIPARCO_Guard" /f'
  ; Reclaim ownership (Administrators = S-1-5-32-544) and strip the deny ACE
  ; (Everyone = S-1-1-0) from the install dir and the legacy default location.
  nsExec::Exec 'icacls "$INSTDIR" /setowner "*S-1-5-32-544" /T /C'
  nsExec::Exec 'icacls "$INSTDIR" /remove:d "*S-1-1-0" /T /C'
  nsExec::Exec 'icacls "$PROGRAMFILES64\STRIPARCO" /setowner "*S-1-5-32-544" /T /C'
  nsExec::Exec 'icacls "$PROGRAMFILES64\STRIPARCO" /remove:d "*S-1-1-0" /T /C'
  nsExec::Exec 'icacls "$PROGRAMFILES\STRIPARCO" /setowner "*S-1-5-32-544" /T /C'
  nsExec::Exec 'icacls "$PROGRAMFILES\STRIPARCO" /remove:d "*S-1-1-0" /T /C'
!macroend

; Runs early in the installer .onInit — before the previous version is replaced.
!macro customInit
  !insertmacro DisableStriparcopProtection
!macroend

; Runs at the start of uninstallation — so even a protected install can be removed.
!macro customUnInit
  !insertmacro DisableStriparcopProtection
!macroend
