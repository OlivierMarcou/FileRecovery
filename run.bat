@echo off
setlocal enabledelayedexpansion

REM Script de lancement pour Windows
REM Outil de Récupération de Fichiers

echo ========================================
echo   Outil de Récupération de Fichiers
echo   Version Windows
echo ========================================
echo.

REM Vérifier si Java est installé
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERREUR] Java n'est pas installe ou n'est pas dans le PATH
    echo.
    echo Telechargez et installez Java 21 depuis:
    echo https://adoptium.net/
    echo.
    pause
    exit /b 1
)

REM Afficher la version Java
echo Verification de Java...
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION=%%g
)
echo Version Java: %JAVA_VERSION%
echo.

REM Vérifier si le JAR existe
set JAR_FILE=target\file-recovery-tool-standalone.jar
if not exist "%JAR_FILE%" (
    echo [AVERTISSEMENT] Le fichier JAR n'existe pas
    echo Compilation du projet avec Maven...
    echo.

    REM Vérifier si Maven est installé
    mvn -version >nul 2>&1
    if errorlevel 1 (
        echo [ERREUR] Maven n'est pas installe
        echo.
        echo Telechargez et installez Maven depuis:
        echo https://maven.apache.org/download.cgi
        echo.
        pause
        exit /b 1
    )

    echo Compilation en cours...
    call mvn clean package -q

    if errorlevel 1 (
        echo [ERREUR] Erreur lors de la compilation
        pause
        exit /b 1
    )

    echo [OK] Compilation réussie
    echo.
)

REM Vérifier les privilèges administrateur
net session >nul 2>&1
if errorlevel 1 (
    echo ========================================
    echo   ATTENTION: Privilèges limités
    echo ========================================
    echo.
    echo Ce programme fonctionne mais avec des limitations.
    echo Pour acceder aux disques physiques, executez en tant qu'Administrateur:
    echo   1. Clic droit sur run.bat
    echo   2. Executer en tant qu'administrateur
    echo.
    echo Appuyez sur une touche pour continuer quand meme...
    pause >nul
) else (
    echo [OK] Execution avec privileges administrateur
    echo.
)

REM Lancer l'application
echo Lancement de l'application...
echo.
java -jar "%JAR_FILE%"

if errorlevel 1 (
    echo.
    echo [ERREUR] L'application s'est terminee avec une erreur
    pause
    exit /b 1
)

exit /b 0