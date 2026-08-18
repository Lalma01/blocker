@echo off
REM PS-BLOCK Android – build script
echo ============================================
echo  PS-BLOCK Android – APK keszitese
echo ============================================
echo.

if not exist gradlew.bat (
  echo Wrapper hianyzik, generalas...
  gradle wrapper --gradle-version 8.7 || ( echo HIBA: gradle wrapper sikertelen & pause & exit /b 1 )
)

echo [1/2] Release APK forditasa...
call gradlew.bat assembleRelease
if %errorlevel% neq 0 ( echo HIBA: build sikertelen! & pause & exit /b 1 )

if not exist release mkdir release
copy /Y app\build\outputs\apk\release\app-release.apk release\PS-BLOCK.apk >nul

echo.
echo ============================================
echo  KESZ! APK: release\PS-BLOCK.apk
echo ============================================
pause
