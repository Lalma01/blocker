@echo off
REM Runs uninstall_gate.js "as Node" inside electron.exe (ELECTRON_RUN_AS_NODE
REM only needs to be set for this one child process, so a tiny batch file is
REM used instead of touching the calling installer/uninstaller's own
REM environment). Called from installer.nsh as:
REM   psblock_gate.bat "<path to PS-BLOCK.exe>" "<path to uninstall_gate.js>" --check
REM   psblock_gate.bat "<path to PS-BLOCK.exe>" "<path to uninstall_gate.js>" --teardown "<pwfile>"
set ELECTRON_RUN_AS_NODE=1
set "PB_EXE=%~1"
set "PB_JS=%~2"
shift
shift
"%PB_EXE%" "%PB_JS%" %*
