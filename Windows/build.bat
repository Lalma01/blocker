@echo off
REM PS-BLOCK – Build Script
echo ============================================
echo  PS-BLOCK – Telepito keszitese
echo ============================================
echo.

where node >nul 2>&1
if %errorlevel% neq 0 ( echo HIBA: Node.js nem talalhato! & pause & exit /b 1 )

echo [1/3] Fuggosegek telepitese...
call npm install
if %errorlevel% neq 0 ( echo HIBA: npm install sikertelen! & pause & exit /b 1 )

echo.
echo [1.5/3] Verzió növelése...
node app\version_bump.js
if %errorlevel% neq 0 ( echo HIBA: Verzió növelése sikertelen! & pause & exit /b 1 )

echo.
echo [2/3] Ikon ellenorzese...
if not exist assets mkdir assets
if not exist assets\icons\win\icon.ico (
  echo FIGYELEM: assets\icons\win\icon.ico nem talalhato!
  echo Helyezz el egy icon.ico fajlt az assets\icons\win mappaba, majd futtasd ujra.
  pause & exit /b 1
)

echo.
echo [3/3] Telepito keszitese (x64 + ia32)...
call npm run dist
if %errorlevel% neq 0 ( echo HIBA: Build sikertelen! & pause & exit /b 1 )

echo.
echo ============================================
echo  KESZ! Telepito: dist\PS-BLOCK Setup*.exe
echo ============================================
pause
