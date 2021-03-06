@echo off

echo    ------------------------------------
echo.
echo          XEService installation
echo.
echo    ------------------------------------
echo.

:addregister
echo install XeXtension...
echo.
reg add HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Run /v XEService /d "%~dp0%XEService.exe"
if %errorlevel% equ 0 (
	goto startxex
)

:addstartup
set xpzh="%userprofile%\「开始」菜单\程序\启动\"
set xpen="%userprofile%\Start Menu\Programs\Startup\"
set win7="%USERPROFILE%\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Startup\"
REM set win7AllUsers="C:\ProgramData\Microsoft\Windows\Start Menu\Programs\StartUp\"
if exist %xpzh% (
	set pth="%xpzh%"
) else (
	if exist %xpen% (
		set pth="%xpen%"
	) else (
		if exist %win7% (
			set pth="%win7%"
		) else (
			echo install failed, can not found startup folder
			goto end
		)
	)
)
set pth=%pth%xextension.bat
echo @echo off > "%pth%"
echo %~d0 >> "%pth%"
echo cd "%~dp0" >> "%pth%"
echo start XEService.exe >> "%pth%"
if not exist "%pth%" (
	echo install failed, can not create startup script file
	goto end
)

:startxex
echo.
echo startup XeXtension...
echo.
%~d0
cd "%~dp0%"
start XEService.exe
if %errorlevel% equ 0 (
	echo XeXtension has been installed successfully on your computer
) else (
	echo startup failed
)
echo.

:end
pause
