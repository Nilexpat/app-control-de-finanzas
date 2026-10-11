@echo off
REM Lanzador de la herramienta de Firebase para SocioFinanzas.
REM Evita tener que escribir la ruta larga de Node cada vez.
REM
REM Uso:
REM   firebase.cmd login                      (iniciar sesion, abre el navegador)
REM   firebase.cmd deploy --only hosting      (publicar la pagina de invitacion)
REM   firebase.cmd projects:list              (ver tus proyectos)

set "NODE=C:\Users\N00014928\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe"
set "FIREBASE=%~dp0..\.fbcli\node_modules\firebase-tools\lib\bin\firebase.js"

REM La herramienta intenta escribir su configuracion y comprobar actualizaciones
REM en la carpeta del usuario, donde no tiene permiso. Se redirige a una carpeta
REM escribible del proyecto y se desactiva la comprobacion.
set "CLOUDSDK_CONFIG=%~dp0..\.fbcli\config"
set "FIREBASE_CLI_DISABLE_UPDATE_CHECK=1"
if not exist "%CLOUDSDK_CONFIG%" mkdir "%CLOUDSDK_CONFIG%"

REM Todo comando se ejecuta desde la carpeta del proyecto (donde esta firebase.json)
cd /d "%~dp0"

"%NODE%" "%FIREBASE%" %*
