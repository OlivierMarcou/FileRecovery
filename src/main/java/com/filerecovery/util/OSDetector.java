package com.filerecovery.util;

/**
 * Utilitaire pour détecter le système d'exploitation
 */
public class OSDetector {
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();

    public static boolean isWindows() {
        return OS_NAME.contains("windows");
    }

    public static boolean isLinux() {
        return OS_NAME.contains("linux");
    }

    public static boolean isMac() {
        return OS_NAME.contains("mac");
    }

    public static String getOSName() {
        return System.getProperty("os.name");
    }

    public static String getUserName() {
        return System.getProperty("user.name");
    }

    public static boolean isRoot() {
        if (isLinux() || isMac()) {
            return "root".equals(getUserName());
        }
        return false;
    }

    public static boolean isAdmin() {
        if (isWindows()) {
            // Sur Windows, vérifier si on a les privilèges admin
            // Simplifié ici, une vraie vérification nécessiterait JNA
            return true; // À améliorer
        }
        return isRoot();
    }
}