@echo off
REM Prueba firestore.rules en el emulador LOCAL de Firestore.
REM No toca el proyecto real de Firebase: usa un proyecto de demostracion
REM ("demo-socio") que solo existe dentro del emulador.
REM
REM Uso:
REM   probar-reglas.cmd
REM
REM Ejecutalo siempre antes de publicar las reglas. Tiene que terminar con
REM "0 fallos".

set "NODEDIR=C:\Users\N00014928\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin"
set "FIREBASE=%~dp0..\.fbcli\node_modules\firebase-tools\lib\bin\firebase.js"

REM El emulador necesita Java 21; el Java 8 que hay por defecto no sirve.
set "PATH=C:\Program Files\Eclipse Adoptium\jre-21.0.12.101-hotspot\bin;%NODEDIR%;%PATH%"

set "CLOUDSDK_CONFIG=%~dp0..\.fbcli\config"
set "FIREBASE_CLI_DISABLE_UPDATE_CHECK=1"
if not exist "%CLOUDSDK_CONFIG%" mkdir "%CLOUDSDK_CONFIG%"

cd /d "%~dp0"

"%NODEDIR%\node.exe" "%FIREBASE%" emulators:exec --only firestore --project demo-socio "node firestore-tests/rules.test.js"
