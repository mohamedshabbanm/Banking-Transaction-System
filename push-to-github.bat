@echo off
cd /d "%~dp0"
echo Pushing Ershak to Banking-Transaction-System ...
where git >nul 2>nul || (echo Git is not installed & pause & exit /b 1)
if not exist .git git init -b main
git remote remove origin >nul 2>nul
git remote add origin https://github.com/omarahmedgooda-arch/Banking-Transaction-System.git
git fetch origin || goto :fail
git reset origin/main || goto :fail
git checkout origin/main -- LICENSE
git add .
git commit -m "Add Ershak: banking system with Fraud Guard" || goto :fail
git push origin main || goto :fail
echo.
echo DONE - pushed OK
pause
exit /b 0
:fail
echo.
echo FAILED - see messages above
pause
exit /b 1
