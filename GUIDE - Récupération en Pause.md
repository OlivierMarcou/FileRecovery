# 🔄 Guide : Récupération en Pause avec Données Réelles

## 🎯 Fonctionnalité Principale

**Vous pouvez maintenant récupérer les fichiers détectés PENDANT que le scan est en pause**, et le système récupère les **vraies données** secteur par secteur depuis le disque.

---

## 📖 Comment Utiliser

### Scénario Classique

```
1. Démarrer le scan
   ├─ Cliquer sur "▶ Scanner"
   └─ Le scan commence à analyser le disque
   
2. Mettre en pause
   ├─ Cliquer sur "⏸ Pause" pendant le scan
   └─ Le scan se met en pause
   
3. Sélectionner des fichiers
   ├─ Parcourir l'arbre des fichiers trouvés
   ├─ Cliquer sur un fichier (ou Ctrl+Clic pour plusieurs)
   └─ Sélectionner ce que vous voulez récupérer
   
4. Récupérer EN PAUSE
   ├─ Cliquer sur "💾 Récupérer la sélection"
   ├─ Choisir un dossier de destination
   └─ La récupération démarre
   
5. Continuer ou Arrêter
   ├─ Option A: Cliquer sur "▶ Reprendre" pour continuer le scan
   └─ Option B: Cliquer sur "⏹ Arrêter" pour terminer
```

---

## 🔬 Comment Fonctionne la Récupération Réelle

### 1. **Détection pendant le Scan**
```java
// Le scan trouve un fichier à l'offset 0x12AB3400
RecoveredFile file = new RecoveredFile(
    "photo_001.jpg",
    "/dev/sda @ offset 314089472",
    2457600,  // Taille réelle détectée: 2.4 MB
    "JPEG",
    FileState.RECOVERABLE,
    314089472  // Offset exact sur le disque
);
```

### 2. **Récupération des Données Brutes**
```java
// RealRecoveryService.recoverSingleFile()

// Ouvre le disque en mode RAW
RandomAccessFile sourceRaf = new RandomAccessFile("/dev/sda", "r");
FileChannel sourceChannel = sourceRaf.getChannel();

// Se positionne EXACTEMENT à l'offset du fichier
sourceChannel.position(file.getOffset());  // 0x12AB3400

// Lit les données RÉELLES secteur par secteur
ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
while (bytesRead < file.getSize()) {
    int read = sourceChannel.read(buffer);  // LIT VRAIMENT LE DISQUE
    buffer.flip();
    outputChannel.write(buffer);  // ÉCRIT dans le fichier de sortie
    bytesRead += read;
}
```

### 3. **Résultat**
Le fichier récupéré contient les **vraies données binaires** lues depuis le disque, pas une simulation !

---

## 💾 Architecture de la Récupération

```
┌─────────────────────────────────────────────────────────┐
│                    DISQUE PHYSIQUE                       │
│  /dev/sda  ou  \\.\PHYSICALDRIVE0                       │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  Offset 0x00000000 ┌──────────────┐                     │
│                    │  Secteur 0    │                     │
│  Offset 0x00000200 ├──────────────┤                     │
│                    │  Secteur 1    │                     │
│         ...        │     ...       │                     │
│                    │               │                     │
│  Offset 0x12AB3400 ┌──────────────┐ ← FICHIER DÉTECTÉ  │
│                    │ FF D8 FF E0   │   (Header JPEG)     │
│                    │ ... données..│                      │
│                    │ ... photo ... │                     │
│  Offset 0x12AB5C00 │ FF D9        │   (Footer JPEG)     │
│                    └──────────────┘                      │
│         ...                                              │
└─────────────────────────────────────────────────────────┘
                          │
                          │ RealRecoveryService
                          │ .recoverSingleFile()
                          ↓
        ┌─────────────────────────────────┐
        │  LECTURE SECTEUR PAR SECTEUR    │
        │  Position: 0x12AB3400           │
        │  Taille: 2.4 MB                 │
        └─────────────────────────────────┘
                          │
                          ↓
        ┌─────────────────────────────────┐
        │   FICHIER RÉCUPÉRÉ              │
        │   photo_001.jpg                 │
        │   Destination: /home/user/...   │
        │   Taille: 2.4 MB                │
        │   Status: ✓ RÉCUPÉRÉ            │
        └─────────────────────────────────┘
```

---

## 🔍 Détails Techniques

### Processus de Récupération

1. **Ouverture du Périphérique**
   ```java
   RandomAccessFile raf = new RandomAccessFile(sourcePath, "r");
   FileChannel channel = raf.getChannel();
   ```

2. **Positionnement à l'Offset**
   ```java
   channel.position(file.getOffset());  // Ex: 314089472
   ```

3. **Lecture des Données Réelles**
   ```java
   ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
   int bytesRead = channel.read(buffer);  // LIT LE DISQUE
   ```

4. **Écriture du Fichier**
   ```java
   FileOutputStream fos = new FileOutputStream(outputFile);
   fos.write(buffer.array(), 0, bytesRead);
   ```

### Log de Récupération
```
=== RÉCUPÉRATION RÉELLE ===
Fichier: photo_001.jpg
Source: /dev/sda
Offset: 0x12AB3400 (314089472 bytes)
Taille: 2457600 bytes
Destination: /home/user/recovered/photo_001.jpg
  Progress: 1 MB / 2 MB
  Progress: 2 MB / 2 MB
✓ Récupération RÉUSSIE: 2457600 bytes écrits
==========================
```

---

## ⚙️ Options de Récupération

### Mode Normal
- Utilise la taille estimée du fichier
- Lit exactement `file.getSize()` octets

### Mode avec Détection de Fin (Plus Précis)
```java
// Pour JPEG: Cherche le marqueur FF D9
// Pour PNG: Cherche le chunk IEND
// Pour PDF: Cherche %%EOF
recoveryService.recoverFileWithEndDetection(file, source, destination);
```

---

## 📊 Validation de l'Intégrité

Après récupération, le système peut valider :

```java
// Vérifier la signature du fichier
boolean isValid = recoveryService.validateRecoveredFile(
    recoveredFile, 
    "JPEG"
);

// Calculer le checksum MD5
String checksum = recoveryService.calculateChecksum(recoveredFile);
```

---

## 🎯 Avantages de la Récupération en Pause

### ✅ **Récupération Immédiate**
- Pas besoin d'attendre la fin du scan complet
- Récupérer les fichiers importants en priorité

### ✅ **Données Authentiques**
- Lecture directe depuis le disque
- Pas de simulation ou de données factices
- Les fichiers récupérés sont utilisables

### ✅ **Contrôle Total**
- Pause/Reprendre à tout moment
- Sélection multiple (Ctrl+Clic)
- Récupération par catégorie (tous les JPEGs, etc.)

### ✅ **Performance**
- Récupération en parallèle du scan
- Buffer de 64KB pour des lectures optimisées
- Progress en temps réel

---

## 🚨 Points Importants

### ⚠️ Permissions Requises
```bash
# Linux: Root obligatoire
sudo java -jar file-recovery-tool.jar

# Windows: Administrateur requis
# Clic droit → Exécuter en tant qu'Administrateur
```

### ⚠️ Disque Démonté
```bash
# Linux: Démonter avant scan
sudo umount /dev/sda1

# Windows: Éjecter le volume
# (Le disque physique reste accessible)
```

### ⚠️ Espace Disponible
Vérifier que le dossier de destination a assez d'espace :
```
Fichier: video.mp4 (500 MB)
→ Destination doit avoir au moins 500 MB libres
```

---

## 💡 Exemples d'Utilisation

### Exemple 1 : Récupérer une Photo Urgente
```
1. Scanner le disque
2. Le scan trouve "photo_mariage_001.jpg" après 30 secondes
3. ⏸ Pause immédiate
4. Sélectionner uniquement cette photo
5. Récupérer → Choisir destination
6. ✓ Photo récupérée en 5 secondes
7. ▶ Reprendre le scan ou ⏹ Arrêter
```

### Exemple 2 : Récupération Sélective
```
1. Scanner le disque (5 minutes)
2. ⏸ Pause après 2 minutes
3. 150 fichiers trouvés
4. Sélectionner seulement les 10 PDFs importants
5. Récupérer ces 10 fichiers
6. ▶ Reprendre pour continuer
7. ⏸ Pause à nouveau après 1 minute
8. Récupérer 20 photos supplémentaires
9. ⏹ Arrêter le scan
```

### Exemple 3 : Récupération par Catégorie
```
1. Scanner trouve :
   - 🖼️ Images (45)
   - 🎬 Vidéos (12)
   - 📄 Documents (78)
2. ⏸ Pause
3. Cliquer sur le dossier "Images" entier
4. Récupérer → Récupère les 45 images
5. ▶ Reprendre si besoin de plus
```

---

## 📈 Statistiques de Récupération

Après chaque récupération, vous voyez :

```
📊 STATISTIQUES DE RÉCUPÉRATION:
   Total: 10 fichiers
   ✓ Succès: 9 fichiers
   ✗ Échecs: 1 fichiers
   📈 Taux de succès: 90.0%

✓ 9 fichiers récupérés avec succès dans: /home/user/recovered
```

---

## 🔧 Dépannage

### ❌ "Permission Denied"
```bash
# Solution Linux
sudo java -jar file-recovery-tool.jar

# Solution Windows
# Exécuter en Administrateur
```

### ❌ "Fichier Corrompu"
- Le fichier était peut-être déjà endommagé sur le disque
- Essayer avec détection de fin automatique
- Vérifier que le disque n'a pas d'erreurs physiques

### ❌ "Échec de Récupération"
- Vérifier l'espace disque disponible
- Le fichier est peut-être fragmenté (non supporté)
- Essayer de récupérer vers un autre disque

---

## 🎓 Conclusion

La fonctionnalité de **Récupération en Pause** vous permet de :
- ✅ Récupérer immédiatement les fichiers importants
- ✅ Obtenir les vraies données binaires du disque
- ✅ Contrôler totalement le processus
- ✅ Économiser du temps

**Les données récupérées sont authentiques et utilisables !** 🚀