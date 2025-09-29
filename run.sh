#!/bin/bash

# Script de lancement pour l'outil de récupération de fichiers
# Nécessite les permissions root pour accès aux périphériques blocs

set -e

# Couleurs pour les messages
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}═══════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Outil de Récupération de Fichiers - Linux${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════${NC}"
echo ""

# Vérifier si Java est installé
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java n'est pas installé${NC}"
    echo -e "${YELLOW}Installez Java 21 avec:${NC}"
    echo "  sudo apt install openjdk-21-jdk    # Debian/Ubuntu"
    echo "  sudo dnf install java-21-openjdk   # Fedora"
    echo "  sudo pacman -S jdk21-openjdk       # Arch Linux"
    exit 1
fi

# Vérifier la version de Java
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo -e "${YELLOW}⚠ Version Java détectée: $JAVA_VERSION${NC}"
    echo -e "${YELLOW}⚠ Java 21 ou supérieur est recommandé${NC}"
    echo ""
fi

# Vérifier si le JAR existe
JAR_FILE="target/file-recovery-tool-standalone.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${YELLOW}⚠ Le fichier JAR n'existe pas${NC}"
    echo -e "${YELLOW}Compilation du projet...${NC}"

    if ! command -v mvn &> /dev/null; then
        echo -e "${RED}❌ Maven n'est pas installé${NC}"
        echo -e "${YELLOW}Installez Maven avec:${NC}"
        echo "  sudo apt install maven    # Debian/Ubuntu"
        echo "  sudo dnf install maven    # Fedora"
        echo "  sudo pacman -S maven      # Arch Linux"
        exit 1
    fi

    mvn clean package -q

    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ Erreur lors de la compilation${NC}"
        exit 1
    fi

    echo -e "${GREEN}✓ Compilation réussie${NC}"
    echo ""
fi

# Vérifier les permissions
if [ "$EUID" -ne 0 ]; then
    echo -e "${YELLOW}⚠ Ce programme nécessite les permissions root pour:${NC}"
    echo "  - Lire les périphériques blocs (/dev/sdX)"
    echo "  - Démonter les partitions"
    echo "  - Accéder aux secteurs bruts du disque"
    echo ""
    echo -e "${YELLOW}Lancement avec sudo...${NC}"
    echo ""

    # Relancer avec sudo
    exec sudo -E java -jar "$JAR_FILE"
else
    echo -e "${GREEN}✓ Exécution en mode root${NC}"
    echo ""

    # Lancer l'application
    java -jar "$JAR_FILE"
fi