package com.filerecovery.service;

import com.filerecovery.model.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

/**
 * Service de récupération RÉELLE de fichiers
 * Lit réellement les données depuis le disque et reconstruit les fichiers
 */
public class RealRecoveryService {

    public interface RecoveryProgressListener {
        void onProgress(int percentage, String message);
        void onFileRecovered(RecoveredFile file, boolean success);
        void onComplete(int successCount, int failCount);
        void onError(String error);
    }

    private RecoveryProgressListener listener;

    public void setProgressListener(RecoveryProgressListener listener) {
        this.listener = listener;
    }

    /**
     * Récupère réellement les fichiers depuis le disque
     */
    public RecoveryResult recoverFiles(List<RecoveredFile> files,
                                       String sourcePath,
                                       String destinationPath) {
        int successCount = 0;
        int failCount = 0;

        notifyProgress(0, "Début de la récupération...");

        for (int i = 0; i < files.size(); i++) {
            RecoveredFile file = files.get(i);

            try {
                boolean success = recoverSingleFile(file, sourcePath, destinationPath);
                if (success) {
                    successCount++;
                    notifyFileRecovered(file, true);
                } else {
                    failCount++;
                    notifyFileRecovered(file, false);
                }
            } catch (Exception e) {
                failCount++;
                notifyError("Erreur récupération " + file.getName() + ": " + e.getMessage());
                notifyFileRecovered(file, false);
            }

            int progress = ((i + 1) * 100) / files.size();
            notifyProgress(progress, String.format("Récupéré %d/%d fichiers", i + 1, files.size()));
        }

        notifyComplete(successCount, failCount);
        return new RecoveryResult(successCount, failCount);
    }

    /**
     * Récupère un fichier unique depuis le disque
     * LIT VRAIMENT LES DONNÉES du disque secteur par secteur
     */
    private boolean recoverSingleFile(RecoveredFile file, String sourcePath, String destinationPath)
            throws IOException {

        File outputFile = new File(destinationPath, file.getName());

        // Créer les dossiers si nécessaire
        outputFile.getParentFile().mkdirs();

        // Ouvrir le périphérique source en lecture RAW
        try (RandomAccessFile sourceRaf = new RandomAccessFile(sourcePath, "r");
             FileChannel sourceChannel = sourceRaf.getChannel();
             FileOutputStream fos = new FileOutputStream(outputFile);
             FileChannel outputChannel = fos.getChannel()) {

            // Se positionner EXACTEMENT à l'offset du fichier sur le disque
            sourceChannel.position(file.getOffset());

            // Lire VRAIMENT les données secteur par secteur
            long bytesToRead = file.getSize();
            long bytesRead = 0;
            ByteBuffer buffer = ByteBuffer.allocate(64 * 1024); // 64KB buffer

            System.out.println(String.format("=== RÉCUPÉRATION RÉELLE ==="));
            System.out.println(String.format("Fichier: %s", file.getName()));
            System.out.println(String.format("Source: %s", sourcePath));
            System.out.println(String.format("Offset: 0x%X (%d bytes)", file.getOffset(), file.getOffset()));
            System.out.println(String.format("Taille: %d bytes", file.getSize()));
            System.out.println(String.format("Destination: %s", outputFile.getAbsolutePath()));

            while (bytesRead < bytesToRead) {
                buffer.clear();

                // Limiter la lecture à ce qui reste
                if (bytesToRead - bytesRead < buffer.capacity()) {
                    buffer.limit((int)(bytesToRead - bytesRead));
                }

                // LECTURE RÉELLE DES SECTEURS DU DISQUE
                int read = sourceChannel.read(buffer);
                if (read == -1) {
                    System.out.println("⚠ Fin de lecture prématurée");
                    break;
                }

                // ÉCRITURE DES VRAIES DONNÉES dans le fichier de sortie
                buffer.flip();
                int written = outputChannel.write(buffer);
                bytesRead += read;

                if (bytesRead % (1024 * 1024) == 0) {
                    System.out.println(String.format("  Progress: %d MB / %d MB",
                            bytesRead / (1024 * 1024),
                            bytesToRead / (1024 * 1024)));
                }
            }

            boolean success = bytesRead == bytesToRead;
            System.out.println(String.format("✓ Récupération %s: %d bytes écrits",
                    success ? "RÉUSSIE" : "PARTIELLE", bytesRead));
            System.out.println("==========================");

            // Vérifier si on a bien récupéré toute la taille attendue
            return success;
        }
    }

    /**
     * Récupère un fichier avec détection automatique de la fin
     * Plus précis que la méthode basée sur la taille estimée
     */
    public boolean recoverFileWithEndDetection(RecoveredFile file,
                                               String sourcePath,
                                               String destinationPath) throws IOException {

        File outputFile = new File(destinationPath, file.getName());
        outputFile.getParentFile().mkdirs();

        try (RandomAccessFile sourceRaf = new RandomAccessFile(sourcePath, "r");
             FileChannel sourceChannel = sourceRaf.getChannel();
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            sourceChannel.position(file.getOffset());

            byte[] endMarker = getEndMarkerForType(file.getType());
            if (endMarker == null) {
                // Si pas de marqueur de fin, utiliser la taille estimée
                return recoverSingleFile(file, sourcePath, destinationPath);
            }

            // Lire jusqu'à trouver le marqueur de fin
            ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
            byte[] searchBuffer = new byte[endMarker.length];
            int searchPos = 0;
            long totalBytesRead = 0;
            long maxSize = file.getSize() * 2; // Limite de sécurité

            while (totalBytesRead < maxSize) {
                buffer.clear();
                int read = sourceChannel.read(buffer);
                if (read == -1) break;

                buffer.flip();
                byte[] data = new byte[read];
                buffer.get(data);

                // Écrire les données
                fos.write(data);
                totalBytesRead += read;

                // Rechercher le marqueur de fin
                for (byte b : data) {
                    searchBuffer[searchPos] = b;
                    searchPos = (searchPos + 1) % endMarker.length;

                    if (matchesEndMarker(searchBuffer, searchPos, endMarker)) {
                        return true; // Fichier complet trouvé
                    }
                }
            }

            return totalBytesRead > 0;
        }
    }

    /**
     * Retourne le marqueur de fin pour un type de fichier
     */
    private byte[] getEndMarkerForType(String type) {
        return switch (type.toUpperCase()) {
            case "JPEG" -> new byte[]{(byte)0xFF, (byte)0xD9}; // EOI marker
            case "PNG" -> new byte[]{(byte)0x49, (byte)0x45, (byte)0x4E, (byte)0x44,
                    (byte)0xAE, (byte)0x42, (byte)0x60, (byte)0x82}; // IEND
            case "PDF" -> "%%EOF".getBytes();
            case "ZIP" -> new byte[]{0x50, 0x4B, 0x05, 0x06}; // End of central directory
            default -> null;
        };
    }

    private boolean matchesEndMarker(byte[] buffer, int pos, byte[] marker) {
        for (int i = 0; i < marker.length; i++) {
            int bufferIdx = (pos - marker.length + i + buffer.length) % buffer.length;
            if (buffer[bufferIdx] != marker[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Valide l'intégrité d'un fichier récupéré
     */
    public boolean validateRecoveredFile(File file, String expectedType) {
        try {
            // Vérifier que le fichier n'est pas vide
            if (file.length() == 0) {
                return false;
            }

            // Vérifier la signature du fichier
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] header = new byte[8];
                int read = fis.read(header);

                if (read < 2) return false;

                return switch (expectedType.toUpperCase()) {
                    case "JPEG" -> header[0] == (byte)0xFF && header[1] == (byte)0xD8;
                    case "PNG" -> header[0] == (byte)0x89 && header[1] == 0x50;
                    case "PDF" -> header[0] == 0x25 && header[1] == 0x50;
                    case "ZIP" -> header[0] == 0x50 && header[1] == 0x4B;
                    default -> true;
                };
            }
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Calcule le checksum d'un fichier pour vérification
     */
    public String calculateChecksum(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;

            while ((read = fis.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("Erreur calcul checksum", e);
        }
    }

    private void notifyProgress(int percentage, String message) {
        if (listener != null) {
            listener.onProgress(percentage, message);
        }
    }

    private void notifyFileRecovered(RecoveredFile file, boolean success) {
        if (listener != null) {
            listener.onFileRecovered(file, success);
        }
    }

    private void notifyComplete(int successCount, int failCount) {
        if (listener != null) {
            listener.onComplete(successCount, failCount);
        }
    }

    private void notifyError(String error) {
        if (listener != null) {
            listener.onError(error);
        }
    }

    /**
     * Classe résultat de récupération
     */
    public static class RecoveryResult {
        private final int successCount;
        private final int failCount;

        public RecoveryResult(int successCount, int failCount) {
            this.successCount = successCount;
            this.failCount = failCount;
        }

        public int getSuccessCount() { return successCount; }
        public int getFailCount() { return failCount; }
        public int getTotalCount() { return successCount + failCount; }
        public double getSuccessRate() {
            return getTotalCount() > 0 ? (successCount * 100.0) / getTotalCount() : 0;
        }
    }
}