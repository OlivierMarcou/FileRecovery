package com.filerecovery.model;

/**
 * Représente une signature de fichier pour le file carving
 */
public class FileSignature {
    private final String type;
    private final byte[] signature;
    private final String extension;
    private final long estimatedMinSize;
    private final long estimatedMaxSize;

    public FileSignature(String type, byte[] signature, String extension) {
        this(type, signature, extension, 1024, 10 * 1024 * 1024);
    }

    public FileSignature(String type, byte[] signature, String extension,
                         long estimatedMinSize, long estimatedMaxSize) {
        this.type = type;
        this.signature = signature;
        this.extension = extension;
        this.estimatedMinSize = estimatedMinSize;
        this.estimatedMaxSize = estimatedMaxSize;
    }

    public String getType() {
        return type;
    }

    public byte[] getSignature() {
        return signature;
    }

    public String getExtension() {
        return extension;
    }

    public long getEstimatedMinSize() {
        return estimatedMinSize;
    }

    public long getEstimatedMaxSize() {
        return estimatedMaxSize;
    }

    public boolean matches(byte[] data, int offset) {
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

    public static FileSignature jpeg() {
        return new FileSignature("JPEG",
                new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF},
                "jpg", 10 * 1024, 5 * 1024 * 1024);
    }

    public static FileSignature png() {
        return new FileSignature("PNG",
                new byte[]{(byte)0x89, 0x50, 0x4E, 0x47},
                "png", 10 * 1024, 5 * 1024 * 1024);
    }

    public static FileSignature pdf() {
        return new FileSignature("PDF",
                new byte[]{0x25, 0x50, 0x44, 0x46},
                "pdf", 1024, 10 * 1024 * 1024);
    }

    public static FileSignature zip() {
        return new FileSignature("ZIP",
                new byte[]{0x50, 0x4B, 0x03, 0x04},
                "zip", 1024, 100 * 1024 * 1024);
    }

    public static FileSignature mp4() {
        return new FileSignature("MP4",
                new byte[]{0x00, 0x00, 0x00, (byte)0x20, 0x66, 0x74, 0x79, 0x70},
                "mp4", 100 * 1024, 500 * 1024 * 1024);
    }

    public static FileSignature gif() {
        return new FileSignature("GIF",
                new byte[]{0x47, 0x49, 0x46, 0x38},
                "gif", 1024, 2 * 1024 * 1024);
    }

    public static FileSignature bmp() {
        return new FileSignature("BMP",
                new byte[]{0x42, 0x4D},
                "bmp", 1024, 10 * 1024 * 1024);
    }
}