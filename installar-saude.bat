@echo off
rem v1.2.54 — Instala o jar na instancia "mod cet" (rodar com o JOGO FECHADO)
rem O 1.2.53 ficou travado porque o jogo estava aberto (Device or resource busy)

set I=%USERPROFILE%\curseforge\minecraft\Instances\mod cet
set JAR=%~dp0build\libs\intoxicantes-1.2.54.jar

echo === Backup do 1.2.53 ===
if exist "%I%\mods\intoxicantes-1.2.53.jar" move /Y "%I%\mods\intoxicantes-1.2.53.jar" "%I%\backups\"
if exist "%I%\mods\intoxicantes-1.2.52.jar" move /Y "%I%\mods\intoxicantes-1.2.52.jar" "%I%\backups\"

echo === Copiando 1.2.54 ===
copy /Y "%JAR%" "%I%\mods\"

echo === mods/ agora ===
dir /B "%I%\mods" | findstr intoxicantes

echo === Hash (deve bater com dist\SHA256-1.2.54.txt) ===
certutil -hashfile "%I%\mods\intoxicantes-1.2.54.jar" SHA256 | findstr /v hash

echo Pronto: proximo boot carrega a 1.2.54.
pause
