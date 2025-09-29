package com.filerecovery.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Utilitaires pour le formatage de données
 */
public class FormatUtils {

    /**
     * Formate une taille en bytes en format lisible
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        }
        if (bytes < 1024L * 1024 * 1024 * 1024) {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
        return String.format("%.2f TB", bytes / (1024.0 * 1024 * 1024 * 1024));
    }

    /**
     * Formate un timestamp en format lisible
     */
    public static String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }

    /**
     * Formate un offset en hexadécimal
     */
    public static String formatOffset(long offset) {
        return String.format("0x%X", offset);
    }

    /**
     * Formate un pourcentage
     */
    public static String formatPercentage(int percentage) {
        return String.format("%d%%", percentage);
    }

    /**
     * Formate une durée en millisecondes
     */
    public static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
}