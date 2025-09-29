package com.filerecovery.service;

import com.filerecovery.model.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

/**
 * Service de scan de périphériques pour la récupération de fichiers (VERSION DEMO)
 */
public class ScanService {
    private final FileSignatureService signatureService;
    private ScanProgressListener progressListener;

    public interface ScanProgressListener {
        void onProgress(int percentage, String message);
        void onFileFound(RecoveredFile file);
        void onComplete(int filesFound);
        void onError(String error);
    }

    public ScanService() {
        this.signatureService = new FileSignatureService();
    }

    public void setProgressListener(ScanProgressListener listener) {
        this.progressListener = listener;
    }

    /**
     * Scan en mode raw (file carving)
     */
    public List<RecoveredFile> scanRaw(BlockDevice device) throws IOException {
        List<RecoveredFile> files = new ArrayList<>();

        notifyProgress(0, "Ouverture du périphérique...");

        try (RandomAccessFile raf = new RandomAccessFile(device.getPath(), "r");
             FileChannel channel = raf.getChannel()) {

            long deviceSize = channel.size();
            if (deviceSize == 0 && device.getSize() > 0) {
                deviceSize = device.getSize();
            }

            notifyProgress(5, "Taille du périphérique: " + formatSize(deviceSize));

            ByteBuffer buffer = ByteBuffer.allocate(512 * 1024); // 512KB
            long position = 0;
            long maxScan = Math.min(deviceSize, 100 * 1024 * 1024); // Limite 100MB pour démo

            notifyProgress(10, "Début du scan...");

            while (position < deviceSize && position < maxScan) {
                channel.position(position);
                buffer.clear();
                int bytesRead = channel.read(buffer);

                if (bytesRead <= 0) break;

                buffer.flip();
                byte[] data = new byte[bytesRead];
                buffer.get(data);

                // Recherche de signatures
                List<RecoveredFile> foundFiles = signatureService.findFiles(data, position, device);
                files.addAll(foundFiles);

                for (RecoveredFile file : foundFiles) {
                    notifyFileFound(file);
                }

                position += bytesRead;

                // Mise à jour de la progression
                int progress = 10 + (int)((position * 80) / maxScan);
                notifyProgress(progress, String.format("Scanné: %s / %s",
                        formatSize(position), formatSize(maxScan)));
            }

            notifyProgress(100, "Scan terminé");
            notifyComplete(files.size());
        } catch (IOException e) {
            notifyError("Erreur lors du scan: " + e.getMessage());
            throw e;
        }

        return files;
    }

    /**
     * Scan simulé (pour test ou volumes montés)
     */
    public List<RecoveredFile> scanSimulated(BlockDevice device) {
        List<RecoveredFile> files = new ArrayList<>();
        Random random = new Random();

        String[] types = {"JPEG", "PNG", "PDF", "ZIP", "MP4"};

        notifyProgress(0, "Début du scan simulé...");

        for (int i = 0; i < types.length; i++) {
            String type = types[i];
            int fileCount = random.nextInt(15) + 5;

            for (int j = 0; j < fileCount; j++) {
                String fileName = generateFileName(type, j);
                long offset = random.nextInt(1000000) * 4096L;
                long size = random.nextInt(10000000) + 1000;

                RecoveredFile file = new RecoveredFile(
                        fileName,
                        device.getPath(),
                        size,
                        type,
                        RecoveredFile.FileState.RECOVERABLE,
                        offset
                );

                files.add(file);
                notifyFileFound(file);
            }

            int progress = ((i + 1) * 100) / types.length;
            notifyProgress(progress, "Scan en cours: " + type);
        }

        notifyProgress(100, "Scan simulé terminé");
        notifyComplete(files.size());

        return files;
    }

    /**
     * Recherche de partitions perdues
     */
    public List<RecoveredFile> scanForLostPartitions() {
        List<RecoveredFile> partitions = new ArrayList<>();
        Random random = new Random();

        notifyProgress(0, "Recherche de partitions perdues...");

        String[] partTypes = {"NTFS", "EXT4", "FAT32", "BTRFS", "XFS"};
        int partCount = random.nextInt(4) + 1;

        for (int i = 0; i < partCount; i++) {
            String partType = partTypes[random.nextInt(partTypes.length)];
            String partName = String.format("Partition_%s_%d", partType, i);
            long offset = random.nextInt(1000) * 2048L * 512L;
            long size = (random.nextInt(100) + 10) * 1024L * 1024L * 1024L;

            RecoveredFile part = new RecoveredFile(
                    partName,
                    "Secteur " + (offset / 512),
                    size,
                    "Partition " + partType,
                    RecoveredFile.FileState.RECOVERABLE,
                    offset
            );

            partitions.add(part);
            notifyFileFound(part);
            notifyProgress((i + 1) * 100 / partCount,
                    "Partition trouvée: " + partType);
        }

        notifyComplete(partitions.size());
        return partitions;
    }

    private String generateFileName(String type, int index) {
        String ext = signatureService.getExtensionForType(type);
        return String.format("%s_%03d.%s", type.toLowerCase(), index, ext);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    protected void notifyProgress(int percentage, String message) {
        if (progressListener != null) {
            progressListener.onProgress(percentage, message);
        }
    }

    protected void notifyFileFound(RecoveredFile file) {
        if (progressListener != null) {
            progressListener.onFileFound(file);
        }
    }

    protected void notifyComplete(int filesFound) {
        if (progressListener != null) {
            progressListener.onComplete(filesFound);
        }
    }

    protected void notifyError(String error) {
        if (progressListener != null) {
            progressListener.onError(error);
        }
    }
}