# 📁 Outil de Récupération de Fichiers - Multi-plateforme

Outil professionnel de récupération de données pour **Linux** et **Windows** avec interface graphique Swing.

## 🖥️ Support des systèmes d'exploitation

### ✅ Linux
- Accès direct aux périphériques blocs (`/dev/sdX`, `/dev/nvme0n1`)
- File carving (recherche par signatures)
- Démontage automatique
- **Nécessite:** Permissions root (sudo)

### ✅ Windows
- Détection des lecteurs montés (C:, D:, E:, etc.)
- Détection des disques physiques via WMIC
- Accès aux volumes
- **Recommandé:** Exécution en tant qu'Administrateur

## 🚀 Installation rapide

### Windows

#### 1. Prérequis
- **Java 21** - [Télécharger Adoptium](https://adoptium.net/)
- **Maven** - [Télécharger Apache Maven](https://maven.apache.org/download.cgi)

#### 2. Installation
```batch
REM Télécharger le projet
git clone <repo>
cd file-recovery-tool

REM Compiler
mvn clean package

REM Lancer (méthode 1 - avec le script)
run.bat

REM Lancer (méthode 2 - manuellement)
java -jar target\file-recovery-tool-standalone.jar
```

#### 3. Exécution en Administrateur
Pour accéder aux disques physiques :
1. Clic droit sur `run.bat`
2. **Exécuter en tant qu'administrateur**

### Linux

#### 1. Prérequis
```bash
# Installer Java 21
sudo apt install openjdk-21-jdk maven    # Debian/Ubuntu
sudo dnf install java-21-openjdk maven   # Fedora
sudo pacman -S jdk21-openjdk maven       # Arch Linux
```

#### 2. Installation
```bash
# Télécharger le projet
git clone <repo>
cd file-recovery-tool

# Rendre le script exécutable
chmod +x run.sh

# Lancer (le script gère sudo automatiquement)
./run.sh

# Ou manuellement
mvn clean package
sudo java -jar target/file-recovery-tool-standalone.jar
```

## 📦 Structure du projet

```
file-recovery-tool/
│
├── pom.xml                    # Configuration Maven
├── run.sh                     # Script Linux
├── run.bat                    # Script Windows
├── README.md
├── QUICKSTART.md
├── .gitignore
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── filerecovery/
│   │   │           └── FileRecoveryTool.java
│   │   │
│   │   └── resources/
│   │       └── application.properties
│   │
│   └── test/
│       └── java/
│
└── target/
    └── file-recovery-tool-standalone.jar  # JAR généré
```

## 🎯 Fonctionnalités

### 🔍 Détection automatique

**Linux:**
- `/dev/sda`, `/dev/sdb`, etc. (disques SATA)
- `/dev/nvme0n1`, `/dev/nvme0n2`, etc. (disques NVMe)
- `/dev/vda`, `/dev/vdb` (disques virtuels)
- `/dev/mmcblk0` (cartes SD)

**Windows:**
- `C:\`, `D:\`, `E:\` (lecteurs montés)
- `\\.\PHYSICALDRIVE0`, `\\.\PHYSICALDRIVE1` (disques physiques)
- Informations: modèle, interface, taille

### 🔓 Gestion du montage

**Linux:**
- Détection automatique si monté (via `/proc/mounts`)
- Démontage avec `pkexec umount`
- Vérification de sécurité

**Windows:**
- Instructions de démontage manuel
- Compatibilité avec Gestion des disques

### 🔎 Modes de scan

#### 1. **Accès direct (Linux uniquement)**
- File carving secteur par secteur
- Signatures supportées:
    - **JPEG** (FF D8 FF)
    - **PNG** (89 50 4E 47)
    - **PDF** (25 50 44 46)
    - **ZIP** (50 4B 03 04)
    - **MP4** (00 00 00 20 66 74 79 70)

#### 2. **Scan standard (Linux & Windows)**
- Parcours du système de fichiers
- Simulation de récupération
- Détection par type

#### 3. **Recherche de partitions perdues**
- Signatures MBR/GPT
- Types: NTFS, EXT4, FAT32, BTRFS, XFS
- Affichage offset et taille

### 👁️ Prévisualisation
- Aperçu graphique des images
- Métadonnées des fichiers
- Informations: offset, taille, type, état

### 💾 Récupération
- Sélection multiple (Ctrl+Clic)
- Récupération par dossier
- Barre de progression
- Journal détaillé

## 🛠️ Commandes Maven

```bash
# Compiler
mvn clean compile

# Créer le JAR
mvn clean package

# Tests
mvn test

# Nettoyer
mvn clean

# Exécuter directement
mvn exec:java -Dexec.mainClass="com.filerecovery.FileRecoveryTool"
```

## 🔧 Utilisation

### Workflow général

#### 1. Lancement
**Windows:** Double-clic sur `run.bat` ou exécuter en admin  
**Linux:** `./run.sh` ou `sudo java -jar target/file-recovery-tool-standalone.jar`

#### 2. Sélection du périphérique
- Choisir dans la liste déroulante
- Vérifier le statut (monté/non monté)
- Observer la taille et les informations

#### 3. Démontage (si nécessaire)
**Linux:** Bouton "Démonter"  
**Windows:** Éjecter depuis l'Explorateur

#### 4. Configuration du scan
- Cocher "Accès direct" pour file carving (Linux uniquement)
- Cliquer sur "Scanner le périphérique"

#### 5. Exploration
- Parcourir l'arborescence des fichiers
- Prévisualiser en cliquant
- Sélectionner ce qui doit être récupéré

#### 6. Récupération
- Bouton "Récupérer la sélection"
- Choisir le dossier de destination
- Attendre la fin du processus

## 🐛 Résolution des problèmes

### Windows

#### Aucun disque physique détecté
```batch
REM Exécuter en administrateur
REM Clic droit sur run.bat → Exécuter en tant qu'administrateur

REM Vérifier avec WMIC
wmic diskdrive list brief

REM Vérifier la Gestion des disques
diskmgmt.msc
```

#### Java non trouvé
```batch
REM Vérifier l'installation
java -version

REM Ajouter au PATH si nécessaire
REM Panneau de configuration → Système → Variables d'environnement
```

### Linux

#### "Permission denied"
```bash
# Exécuter avec sudo
sudo java -jar target/file-recovery-tool-standalone.jar

# Vérifier les permissions du périphérique
ls -l /dev/sda

# Ajouter l'utilisateur au groupe disk (non recommandé en production)
sudo usermod -a -G disk $USER
```

#### Périphérique occupé lors du démontage
```bash
# Identifier les processus
sudo lsof /dev/sda1
sudo fuser -v /dev/sda1

# Arrêter les processus si nécessaire
sudo systemctl stop udisks2

# Forcer le démontage
sudo umount -f /dev/sda1
```

#### Périphérique non détecté
```bash
# Lister tous les périphériques blocs
lsblk

# Vérifier /dev
ls -la /dev/sd*

# Rescanner les bus SCSI
echo "- - -" | sudo tee /sys/class/scsi_host/host*/scan
```

## 🔐 Permissions

### Linux
**Nécessaire pour:**
- Lecture de `/dev/sdX`
- Exécution de `umount`
- Accès aux secteurs bruts
- Lecture de `/proc/mounts` et `/sys/block/`

**Solution:** Exécuter avec `sudo`

### Windows
**Recommandé pour:**
- Accès aux `\\.\PHYSICALDRIVE*`
- Utilisation de WMIC
- Opérations bas niveau

**Solution:** Exécuter en tant qu'Administrateur

## 📊 Signatures de fichiers supportées

| Type | Signature (hex) | Extension | Description |
|------|----------------|-----------|-------------|
| JPEG | FF D8 FF | .jpg | Image JPEG |
| PNG | 89 50 4E 47 0D 0A 1A 0A | .png | Image PNG |
| PDF | 25 50 44 46 | .pdf | Document PDF |
| ZIP | 50 4B 03 04 | .zip | Archive ZIP |
| MP4 | 00 00 00 xx 66 74 79 70 | .mp4 | Vidéo MP4 |
| GIF | 47 49 46 38 | .gif | Image GIF |
| BMP | 42 4D | .bmp | Image Bitmap |

## 🏗️ Architecture technique

### Détection OS
```java
String osName = System.getProperty("os.name").toLowerCase();
boolean isWindows = osName.contains("windows");
boolean isLinux = osName.contains("linux");
```

### Accès aux périphériques

**Linux:**
```java
RandomAccessFile raf = new RandomAccessFile("/dev/sda", "r");
FileChannel channel = raf.getChannel();
ByteBuffer buffer = ByteBuffer.allocate(512 * 1024);
```

**Windows:**
```java
// Via File.listRoots() pour les lecteurs
File[] roots = File.listRoots();

// Via WMIC pour les disques physiques
ProcessBuilder pb = new ProcessBuilder("wmic", "diskdrive", "get", 
    "DeviceID,Size,Model,InterfaceType", "/format:csv");
```

### Détection de la taille

**Linux:**
```bash
# Via /sys/block
cat /sys/block/sda/size  # secteurs

# Via blockdev
blockdev --getsize64 /dev/sda  # bytes
```

**Windows:**
```batch
REM Via WMIC
wmic diskdrive get Size
```

## 📚 Dépendances Maven

```xml
<dependencies>
    <!-- FlatLaf - Look and Feel moderne -->
    <dependency>
        <groupId>com.formdev</groupId>
        <artifactId>flatlaf</artifactId>
        <version>3.4.1</version>
    </dependency>
    
    <!-- Apache Commons IO -->
    <dependency>
        <groupId>commons-io</groupId>
        <artifactId>commons-io</artifactId>
        <version>2.15.1</version>
    </dependency>
    
    <!-- JNA - Accès natif -->
    <dependency>
        <groupId>net.java.dev.jna</groupId>
        <artifactId>jna-platform</artifactId>
        <version>5.14.0</version>
    </dependency>
    
    <!-- Metadata Extractor -->
    <dependency>
        <groupId>com.drewnoakes</groupId>
        <artifactId>metadata-extractor</artifactId>
        <version>2.19.0</version>
    </dependency>
</dependencies>
```

## 🚨 Avertissements

### ⚠️ IMPORTANT

**Ne jamais:**
- Scanner un disque système en cours d'utilisation
- Écrire sur le disque à récupérer
- Ignorer les erreurs de démontage

**Toujours:**
- Faire une sauvegarde avant toute récupération
- Démonter avant de scanner (Linux)
- Tester d'abord sur un disque non critique
- Vérifier l'espace disponible pour la récupération

### Windows spécifique
- Les disques physiques nécessitent les droits admin
- Le mode raw n'est pas disponible sans driver spécial
- Utilisez des outils comme Arsenal Image Mounter pour l'accès direct

### Linux spécifique
- Root est obligatoire pour l'accès direct
- Attention aux permissions de /dev
- Vérifier que le disque n'est pas utilisé avant démontage

## 🔄 Améliorations futures

- [ ] Support de plus de signatures (RAR, 7Z, DOCX, etc.)
- [ ] Mode deep scan pour secteurs endommagés
- [ ] Reconstruction de systèmes de fichiers
- [ ] Support RAID
- [ ] Export de rapport HTML
- [ ] Mode CLI pour scripts
- [ ] Support macOS
- [ ] Détection de fichiers fragmentés
- [ ] Accès direct sous Windows (via driver)
- [ ] Récupération de métadonnées EXIF
- [ ] Filtre par date/taille/type
- [ ] Calcul de hash MD5/SHA256

## 📝 Notes de version

### Version 1.0.0
- ✅ Support Linux et Windows
- ✅ Détection automatique des périphériques
- ✅ File carving (Linux)
- ✅ Prévisualisation d'images
- ✅ Interface Swing moderne
- ✅ Démontage automatique (Linux)
- ✅ Sélection multiple
- ✅ Journal détaillé

## 📄 Licence

Ce projet est fourni à des fins éducatives et de démonstration.

## 🤝 Contribution

Les contributions sont les bienvenues ! Pour les fonctionnalités avancées de récupération, des bibliothèques natives spécialisées seraient nécessaires.

## 📞 Support

Pour les bugs et suggestions, créez une issue sur le dépôt GitHub.

---

**Note de sécurité:** Cet outil est destiné à la récupération de données légitimes. Ne l'utilisez jamais pour accéder à des données sans autorisation appropriée.

**Outils professionnels recommandés:**
- **Linux:** TestDisk, PhotoRec, Foremost, Scalpel
- **Windows:** Recuva, EaseUS Data Recovery, R-Studio
- **Multi-plateforme:** Autopsy, The Sleuth Kit