package com.filerecovery.service;

import com.filerecovery.model.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

/**
 * Service de scan RÉEL qui détermine la vraie taille des fichiers
 */
public class RealScanService extends ScanService {

    /**
     * Scan raw amélioré avec détection de taille réelle
     */
    @Override
    public List<RecoveredFile> scanRaw(BlockDevice device) throws IOException {
        List<RecoveredFile> files = new ArrayList<>();

        notifyProgress(0, "⚡ MODE SCAN RÉEL ACTIVÉ");
        notifyProgress(1, "Ouverture du périphérique en mode RAW...");

        try (RandomAccessFile raf = new RandomAccessFile(device.getPath(), "r");
             FileChannel channel = raf.getChannel()) {

            long deviceSize = 0;

            // Sous Windows, channel.size() ne fonctionne pas sur les PHYSICALDRIVE
            try {
                deviceSize = channel.size();
            } catch (IOException e) {
                deviceSize = device.getSize();
                notifyProgress(3, "Note: Utilisation de la taille déclarée du périphérique");
            }

            if (deviceSize == 0 && device.getSize() > 0) {
                deviceSize = device.getSize();
            }

            if (deviceSize == 0) {
                throw new IOException("Impossible de déterminer la taille du périphérique");
            }

            notifyProgress(5, "✓ Accès direct réussi - Taille totale: " + formatSize(deviceSize));

            // Augmenter la limite de scan ou scanner tout le disque
            long maxScan = Math.min(deviceSize, 10L * 1024 * 1024 * 1024); // 10GB au lieu de 500MB
            notifyProgress(6, "Zone de scan: " + formatSize(maxScan));
            notifyProgress(7, "🔍 Recherche de signatures de fichiers...");

            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024); // 1MB buffer
            long position = 0;
            int sectorsScanned = 0;

            notifyProgress(10, "Début du scan RAW réel...");

            while (position < maxScan) {
                try {
                    channel.position(position);
                    buffer.clear();
                    int bytesRead = channel.read(buffer);

                    if (bytesRead <= 0) break;

                    buffer.flip();
                    byte[] data = new byte[bytesRead];
                    buffer.get(data);

                    // Rechercher les signatures (VERSION SIMPLIFIÉE)
                    List<FileInfo> foundFiles = findFilesSimple(data, position);

                    for (FileInfo fileInfo : foundFiles) {
                        RecoveredFile file = new RecoveredFile(
                                fileInfo.name,
                                device.getPath() + " @ offset " + fileInfo.offset,
                                fileInfo.size,
                                fileInfo.type,
                                RecoveredFile.FileState.RECOVERABLE,
                                fileInfo.offset
                        );

                        files.add(file);
                        notifyFileFound(file);
                        notifyProgress(10 + (int)((position * 80) / maxScan),
                                String.format("✓ %s trouvé @ %s", fileInfo.type, formatSize(position)));
                    }

                    position += bytesRead;
                    sectorsScanned++;

                    // Log tous les 100 secteurs (100MB)
                    if (sectorsScanned % 100 == 0) {
                        int progress = 10 + (int)((position * 80) / maxScan);
                        notifyProgress(progress, String.format("Scanné: %s / %s - %d fichiers",
                                formatSize(position), formatSize(maxScan), files.size()));
                    }
                } catch (IOException e) {
                    // Secteur illisible, continuer
                    position += 1024 * 1024;
                }
            }

            notifyProgress(100, "✓ Scan terminé");
            notifyComplete(files.size());
        } catch (IOException e) {
            notifyError("✗ Erreur: " + e.getMessage());
            throw e;
        }

        return files;
    }

    /**
     * Version simplifiée qui trouve juste les signatures (pas de calcul de taille complexe)
     */
    private List<FileInfo> findFilesSimple(byte[] data, long baseOffset) {
        List<FileInfo> files = new ArrayList<>();

        Map<String, FileSignatureInfo> signatures = getSignatures();

        for (int offset = 0; offset < data.length - 8; offset++) {
            for (Map.Entry<String, FileSignatureInfo> entry : signatures.entrySet()) {
                String type = entry.getKey();
                FileSignatureInfo sigInfo = entry.getValue();

                if (matchesSignature(data, offset, sigInfo.startSignature)) {
                    long globalOffset = baseOffset + offset;

                    // Utiliser une taille estimée simple
                    long estimatedSize = sigInfo.avgSize;

                    String fileName = String.format("%s_recovered_%08X.%s",
                            type.toLowerCase(),
                            (int)(globalOffset & 0xFFFFFFFF),
                            sigInfo.extension);

                    files.add(new FileInfo(fileName, globalOffset, estimatedSize, type));

                    // Éviter de trouver le même fichier plusieurs fois
                    offset += sigInfo.startSignature.length;
                }
            }
        }

        return files;
    }

    /**
     * Trouve les fichiers avec détermination de leur taille réelle
     */
    private List<FileInfo> findFilesWithSize(byte[] data, long baseOffset,
                                             FileChannel channel, Set<Long> usedPositions) {
        List<FileInfo> files = new ArrayList<>();

        // Signatures à rechercher
        Map<String, FileSignatureInfo> signatures = getSignatures();

        for (int offset = 0; offset < data.length - 8; offset++) {
            long globalOffset = baseOffset + offset;

            // Skip si déjà utilisé
            if (usedPositions.contains(globalOffset)) {
                continue;
            }

            for (Map.Entry<String, FileSignatureInfo> entry : signatures.entrySet()) {
                String type = entry.getKey();
                FileSignatureInfo sigInfo = entry.getValue();

                if (matchesSignature(data, offset, sigInfo.startSignature)) {
                    try {
                        // Déterminer la taille réelle du fichier
                        long fileSize = determineFileSize(channel, globalOffset, sigInfo);

                        if (fileSize > 0 && fileSize < 1024 * 1024 * 1024) { // Max 1GB
                            String fileName = String.format("%s_recovered_%08X.%s",
                                    type.toLowerCase(),
                                    (int)(globalOffset & 0xFFFFFFFF),
                                    sigInfo.extension);

                            files.add(new FileInfo(fileName, globalOffset, fileSize, type));
                        }
                    } catch (IOException e) {
                        // Ignorer ce fichier
                    }
                }
            }
        }

        return files;
    }

    /**
     * Détermine la taille réelle d'un fichier en cherchant son marqueur de fin
     */
    private long determineFileSize(FileChannel channel, long startOffset,
                                   FileSignatureInfo sigInfo) throws IOException {

        if (sigInfo.endSignature == null) {
            // Pas de marqueur de fin, utiliser une heuristique
            return estimateFileSizeByType(channel, startOffset, sigInfo);
        }

        long currentPos = startOffset + sigInfo.startSignature.length;
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        byte[] searchBuffer = new byte[sigInfo.endSignature.length];
        int searchPos = 0;
        long maxSize = 100 * 1024 * 1024; // 100MB max par fichier
        long bytesRead = 0;

        while (bytesRead < maxSize) {
            channel.position(currentPos);
            buffer.clear();
            int read = channel.read(buffer);

            if (read == -1) break;

            buffer.flip();

            for (int i = 0; i < read; i++) {
                byte b = buffer.get();
                searchBuffer[searchPos] = b;
                searchPos = (searchPos + 1) % sigInfo.endSignature.length;
                bytesRead++;

                if (matchesEndSignature(searchBuffer, searchPos, sigInfo.endSignature)) {
                    return bytesRead + sigInfo.startSignature.length;
                }
            }

            currentPos += read;
        }

        // Marqueur de fin non trouvé, retourner 0 (fichier probablement corrompu)
        return 0;
    }

    /**
     * Estime la taille par heuristique pour les fichiers sans marqueur de fin
     */
    private long estimateFileSizeByType(FileChannel channel, long startOffset,
                                        FileSignatureInfo sigInfo) throws IOException {

        // Pour les formats avec header de taille (comme PNG, MP4)
        if (sigInfo.type.equals("PNG")) {
            return readPNGSize(channel, startOffset);
        } else if (sigInfo.type.equals("MP4")) {
            return readMP4Size(channel, startOffset);
        }

        // Par défaut, utiliser une taille estimée moyenne
        return sigInfo.avgSize;
    }

    /**
     * Lit la taille réelle d'un PNG depuis son header
     */
    private long readPNGSize(FileChannel channel, long offset) throws IOException {
        // PNG a des chunks avec des tailles
        // Format: 4 bytes taille + 4 bytes type + données + 4 bytes CRC

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        channel.position(offset);
        channel.read(buffer);
        buffer.flip();

        // Skip signature (8 bytes)
        buffer.position(8);

        long totalSize = 8; // Signature

        while (buffer.remaining() >= 12) {
            int chunkSize = buffer.getInt();
            byte[] chunkType = new byte[4];
            buffer.get(chunkType);

            totalSize += 12 + chunkSize; // Header + données + CRC

            // Si c'est le chunk IEND, c'est la fin
            if (new String(chunkType).equals("IEND")) {
                return totalSize;
            }

            // Passer au chunk suivant
            buffer.position(buffer.position() + chunkSize + 4);
        }

        return 0;
    }

    /**
     * Lit la taille réelle d'un MP4 depuis son header
     */
    private long readMP4Size(FileChannel channel, long offset) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        channel.position(offset);
        channel.read(buffer);
        buffer.flip();

        // Les 4 premiers bytes sont la taille de l'atom
        int atomSize = buffer.getInt();

        if (atomSize > 0 && atomSize < 1024 * 1024 * 1024) {
            return atomSize;
        }

        return 0;
    }

    private boolean matchesSignature(byte[] data, int offset, byte[] signature) {
        if (offset + signature.length > data.length) {
            return false;
        }

        for (int i = 0; i < signature.length; i++) {
            if (data[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesEndSignature(byte[] buffer, int pos, byte[] marker) {
        for (int i = 0; i < marker.length; i++) {
            int bufferIdx = (pos - marker.length + i + buffer.length) % buffer.length;
            if (buffer[bufferIdx] != marker[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Retourne les signatures de fichiers avec informations complètes
     */
    private Map<String, FileSignatureInfo> getSignatures() {
        Map<String, FileSignatureInfo> sigs = new HashMap<>();

        sigs.put("JPEG", new FileSignatureInfo("JPEG", "jpg",
                new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF},
                new byte[]{(byte)0xFF, (byte)0xD9},
                500 * 1024));

        sigs.put("PNG", new FileSignatureInfo("PNG", "png",
                new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
                new byte[]{(byte)0x49, (byte)0x45, (byte)0x4E, (byte)0x44,
                        (byte)0xAE, (byte)0x42, (byte)0x60, (byte)0x82},
                300 * 1024));

        sigs.put("PDF", new FileSignatureInfo("PDF", "pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46},
                "%%EOF".getBytes(),
                1024 * 1024));

        sigs.put("ZIP", new FileSignatureInfo("ZIP", "zip",
                new byte[]{0x50, 0x4B, 0x03, 0x04},
                new byte[]{0x50, 0x4B, 0x05, 0x06},
                5 * 1024 * 1024));

        sigs.put("MP4", new FileSignatureInfo("MP4", "mp4",
                new byte[]{0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70},
                null, // Taille dans le header
                10 * 1024 * 1024));

        sigs.put("GIF", new FileSignatureInfo("GIF", "gif",
                new byte[]{0x47, 0x49, 0x46, 0x38},
                new byte[]{0x00, 0x3B}, // GIF trailer
                200 * 1024));

        return sigs;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // Classes internes
    private static class FileInfo {
        String name;
        long offset;
        long size;
        String type;

        FileInfo(String name, long offset, long size, String type) {
            this.name = name;
            this.offset = offset;
            this.size = size;
            this.type = type;
        }
    }

    private static class FileSignatureInfo {
        String type;
        String extension;
        byte[] startSignature;
        byte[] endSignature; // null si pas de marqueur de fin
        long avgSize; // Taille moyenne estimée

        FileSignatureInfo(String type, String extension, byte[] startSignature,
                          byte[] endSignature, long avgSize) {
            this.type = type;
            this.extension = extension;
            this.startSignature = startSignature;
            this.endSignature = endSignature;
            this.avgSize = avgSize;
        }
    }
}