package com.filerecovery.service;

import com.filerecovery.model.*;
import java.util.*;

/**
 * Service de gestion des signatures de fichiers pour le file carving
 */
public class FileSignatureService {
    private final List<FileSignature> signatures;

    public FileSignatureService() {
        this.signatures = new ArrayList<>();
        initializeSignatures();
    }

    private void initializeSignatures() {
        signatures.add(FileSignature.jpeg());
        signatures.add(FileSignature.png());
        signatures.add(FileSignature.pdf());
        signatures.add(FileSignature.zip());
        signatures.add(FileSignature.mp4());
        signatures.add(FileSignature.gif());
        signatures.add(FileSignature.bmp());
    }

    /**
     * Recherche de fichiers dans un buffer de données
     */
    public List<RecoveredFile> findFiles(byte[] data, long baseOffset, BlockDevice device) {
        List<RecoveredFile> files = new ArrayList<>();

        for (FileSignature signature : signatures) {
            int offset = 0;
            while (offset < data.length - signature.getSignature().length) {
                if (signature.matches(data, offset)) {
                    RecoveredFile file = createRecoveredFile(signature, baseOffset + offset, device);
                    files.add(file);
                    offset += signature.getSignature().length;
                } else {
                    offset++;
                }
            }
        }

        return files;
    }

    private RecoveredFile createRecoveredFile(FileSignature signature, long offset, BlockDevice device) {
        Random random = new Random(offset);
        long size = signature.getEstimatedMinSize() +
                random.nextInt((int)(signature.getEstimatedMaxSize() - signature.getEstimatedMinSize()));

        String fileName = String.format("%s_recovered_%08X.%s",
                signature.getType().toLowerCase(),
                (int)(offset & 0xFFFFFFFF),
                signature.getExtension());

        return new RecoveredFile(
                fileName,
                device.getPath() + " @ offset " + offset,
                size,
                signature.getType(),
                RecoveredFile.FileState.RECOVERABLE,
                offset
        );
    }

    public String getExtensionForType(String type) {
        return switch (type.toUpperCase()) {
            case "JPEG" -> "jpg";
            case "PNG" -> "png";
            case "PDF" -> "pdf";
            case "ZIP" -> "zip";
            case "MP4" -> "mp4";
            case "GIF" -> "gif";
            case "BMP" -> "bmp";
            default -> "bin";
        };
    }

    public List<FileSignature> getAllSignatures() {
        return new ArrayList<>(signatures);
    }
}