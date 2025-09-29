package com.filerecovery.model;

/**
 * Représente un fichier récupérable trouvé lors du scan
 */
public class RecoveredFile {
    private final String name;
    private final String location;
    private final long size;
    private final String type;
    private final FileState state;
    private final long offset;

    public enum FileState {
        RECOVERABLE("Récupérable"),
        DAMAGED("Endommagé"),
        CORRUPTED("Corrompu");

        private final String displayName;

        FileState(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public RecoveredFile(String name, String location, long size,
                         String type, FileState state, long offset) {
        this.name = name;
        this.location = location;
        this.size = size;
        this.type = type;
        this.state = state;
        this.offset = offset;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public long getSize() {
        return size;
    }

    public String getType() {
        return type;
    }

    public FileState getState() {
        return state;
    }

    public long getOffset() {
        return offset;
    }

    public String getCategory() {
        return switch (type.toUpperCase()) {
            case "JPEG", "PNG", "GIF", "BMP" -> "Images";
            case "MP4", "AVI", "MOV" -> "Vidéos";
            case "PDF", "DOC", "DOCX", "TXT" -> "Documents";
            case "MP3", "WAV", "FLAC" -> "Audio";
            case "ZIP", "RAR", "7Z" -> "Archives";
            default -> type.contains("Partition") ? "Partitions" : "Autres";
        };
    }

    public String getIcon() {
        return switch (getCategory()) {
            case "Images" -> "🖼️";
            case "Vidéos" -> "🎬";
            case "Documents" -> "📄";
            case "Audio" -> "🎵";
            case "Archives" -> "📦";
            case "Partitions" -> "💾";
            default -> "📁";
        };
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", name, formatSize(size));
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}