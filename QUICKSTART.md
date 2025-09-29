# 🚀 Guide de démarrage rapide

## Installation en 3 minutes

### 🪟 Windows

```batch
# 1. Vérifier Java
java -version

# Si pas installé, télécharger depuis: https://adoptium.net/

# 2. Compiler le projet
mvn clean package

# 3. Lancer (double-clic ou)
run.bat

# Pour accès complet aux disques:
# Clic droit sur run.bat → Exécuter en tant qu'administrateur
```

### 🐧 Linux

```bash
# 1. Installer les prérequis
sudo apt install openjdk-21-jdk maven  # Ubuntu/Debian

# 2. Compiler
mvn clean package

# 3. Lancer
chmod +x run.sh
./run.sh
# Le script demande automatiquement sudo si nécessaire
```

## 📋 Utilisation rapide

### Scénario 1: Récupérer des photos supprimées d'une clé USB

**Windows:**
1. Insérer la clé USB
2. Lancer l'application en admin
3. Sélectionner le lecteur (ex: E:\)
4. Scanner
5. Développer "Images" dans l'arbre
6. Sélectionner les photos (Ctrl+Clic pour sélection multiple)
7. Récupérer → Choisir destination

**Linux:**
1. Insérer la clé USB (elle se monte automatiquement)
2. Lancer l'application avec sudo
3. Sélectionner /dev/sdb1 (ou autre)
4. Cliquer "Démonter"
5. Scanner (cocher "Accès direct")
6. Sélectionner les fichiers
7. Récupérer

### Scénario 2: Rechercher des partitions perdues

1. Lancer l'application
2. Dans la liste, sélectionner "Rechercher partitions perdues"
3. Scanner
4. Explorer les partitions trouvées
5. Noter les offsets et tailles pour analyse ultérieure

### Scénario 3: Récupérer tout un dossier

1. Scanner le périphérique
2. Dans l'arbre, cliquer sur un dossier (ex: "Documents")
3. Récupérer → tous les fichiers du dossier seront récupérés

## ⚡ Raccourcis clavier

- **Ctrl+Clic**: Sélection multiple
- **Shift+Clic**: Sélection en plage
- **F5**: Rafraîchir la liste des périphériques
- **Ctrl+S**: Lancer le scan (si implémenté)

## 🎯 Cas d'usage courants

### ✅ Que puis-je récupérer ?

- Photos/images (JPEG, PNG, GIF, BMP)
- Vidéos (MP4, AVI)
- Documents (PDF)
- Archives (ZIP, RAR, 7Z)
- Musique (MP3)

### ❌ Limites actuelles

- Fichiers très fragmentés : difficile
- Disques fortement endommagés : limité
- Systèmes de fichiers cryptés : non supporté
- Fichiers écrasés plusieurs fois : impossible

## 🔍 Diagnostic rapide

### Le périphérique n'apparaît pas

**Windows:**
```batch
# Vérifier dans Gestion des disques
diskmgmt.msc

# Lister avec WMIC
wmic diskdrive list brief
```

**Linux:**
```bash
# Lister tous les disques
lsblk

# Vérifier /dev
ls -l /dev/sd*

# Rescanner
echo "- - -" | sudo tee /sys/class/scsi_host/host*/scan
```

### Erreur "Permission denied"

**Windows:**
- Exécuter en tant qu'Administrateur

**Linux:**
```bash
# Utiliser sudo
sudo java -jar target/file-recovery-tool-standalone.jar

# Ou via le script
./run.sh
```

### Le scan ne trouve rien

- Vérifier que le périphérique est démonté (Linux)
- Essayer le mode "Accès direct" (Linux uniquement)
- Le disque est peut-être trop endommagé
- Les fichiers ont peut-être été écrasés

## 💡 Astuces

### Maximiser les chances de récupération

1. **Arrêter immédiatement d'utiliser le disque** dès que vous réalisez la perte
2. **Ne jamais récupérer sur le même disque** que celui scanné
3. **Démonter le disque** avant de scanner (Linux)
4. **Scanner en mode raw** pour plus de résultats (Linux)

### Optimiser les performances

- Scanner de petites portions à la fois
- Fermer les autres applications
- Utiliser un SSD pour la destination de récupération
- Suffisamment d'espace libre sur la destination

### Prévisualisation

- Cliquer sur un fichier pour voir un aperçu
- Les images montrent une représentation visuelle
- Les métadonnées sont affichées (taille, offset, type)

## 📊 Comprendre les résultats

### États des fichiers

- **Récupérable** ✅ : Fichier intact, grande chance de succès
- **Endommagé** ⚠️ : Partiellement lisible, récupération possible
- **Corrompu** ❌ : Très endommagé, faible chance

### Types de fichiers

- 🖼️ **Images**: JPEG, PNG, GIF, BMP
- 🎬 **Vidéos**: MP4, AVI, MOV
- 📄 **Documents**: PDF, DOC, TXT
- 🎵 **Audio**: MP3, WAV, FLAC
- 📦 **Archives**: ZIP, RAR, 7Z
- 💾 **Partitions**: Partitions perdues ou formatées

## 🆘 Problèmes fréquents

### "Java not found"
- Installer Java 21 depuis https://adoptium.net/
- Ajouter Java au PATH système

### "Maven not found"
- Installer Maven depuis https://maven.apache.org/
- Ajouter Maven au PATH

### Le scan est très lent
- Normal pour de gros disques en mode raw
- Limiter la zone de scan si possible
- Le scan peut prendre plusieurs heures pour 1 TB

### Fichiers récupérés corrompus
- Le fichier était peut-être déjà endommagé sur le disque
- Essayer avec un autre outil professionnel
- Vérifier l'intégrité du disque source

## 🎓 Prochaines étapes

1. **Lire le README complet** pour comprendre toutes les fonctionnalités
2. **Tester sur un disque de test** avant un cas réel
3. **Apprendre les commandes système** (lsblk, fdisk, wmic)
4. **Explorer les outils professionnels** pour des cas complexes

## 📚 Ressources

- **TestDisk/PhotoRec**: https://www.cgsecurity.org/
- **Foremost**: http://foremost.sourceforge.net/
- **Autopsy**: https://www.autopsy.com/

## ⚠️ Disclaimer

Cet outil est destiné à l'apprentissage et aux cas simples. Pour des données critiques ou des situations complexes, consultez un professionnel de la récupération de données.

---

**Besoin d'aide ?** Consultez le README.md complet ou créez une issue sur GitHub.