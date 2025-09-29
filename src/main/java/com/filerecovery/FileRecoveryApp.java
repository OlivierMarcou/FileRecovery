package com.filerecovery;

import com.filerecovery.controller.MainController;
import com.filerecovery.view.MainView;
import com.filerecovery.view.MainViewImpl;
import com.filerecovery.util.OSDetector;
import javax.swing.*;

/**
 * Point d'entrée de l'application File Recovery Tool
 * Architecture MVC
 *
 * @version 1.1.0
 * @author File Recovery Team
 */
public class FileRecoveryApp {

    public static void main(String[] args) {
        // Afficher les informations système dans la console
        printSystemInfo();

        // Démarrer l'application avec Swing
        SwingUtilities.invokeLater(() -> {
            try {
                // Définir le Look and Feel du système
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Créer la vue (interface graphique)
            MainView view = new MainViewImpl();

            // Créer le contrôleur (coordination logique)
            MainController controller = new MainController(view);

            // Initialiser l'application
            controller.initialize();
        });
    }

    /**
     * Affiche les informations système dans la console
     */
    private static void printSystemInfo() {
        System.out.println("═══════════════════════════════════════════════");
        System.out.println("  Outil de Récupération de Fichiers");
        System.out.println("  Version 1.1.0 - Architecture MVC");
        System.out.println("═══════════════════════════════════════════════");
        System.out.println("Système: " + OSDetector.getOSName());
        System.out.println("Utilisateur: " + OSDetector.getUserName());
        System.out.println();

        if (OSDetector.isLinux()) {
            printLinuxInfo();
        } else if (OSDetector.isWindows()) {
            printWindowsInfo();
        } else if (OSDetector.isMac()) {
            System.out.println("⚠ macOS détecté - Support limité");
            System.out.println();
        }

        System.out.println("═══════════════════════════════════════════════");
    }

    /**
     * Affiche les informations spécifiques Linux
     */
    private static void printLinuxInfo() {
        if (!OSDetector.isRoot()) {
            System.err.println("⚠ ATTENTION: Permissions limitées sous Linux");
            System.err.println();
            System.err.println("Pour accès complet aux périphériques, exécutez avec:");
            System.err.println("  sudo java -jar file-recovery-tool.jar");
            System.err.println();
            System.err.println("OU utilisez le script:");
            System.err.println("  ./run.sh");
            System.err.println();
        } else {
            System.out.println("✓ Mode root - Accès complet aux périphériques");
            System.out.println();
        }
    }

    /**
     * Affiche les informations spécifiques Windows
     */
    private static void printWindowsInfo() {
        System.out.println("ℹ Système Windows détecté");
        System.out.println();
        System.out.println("IMPORTANT pour accéder aux disques physiques:");
        System.out.println("  1. Clic droit sur run.bat");
        System.out.println("  2. Sélectionner 'Exécuter en tant qu'Administrateur'");
        System.out.println();
        System.out.println("Pour scanner un disque:");
        System.out.println("  1. Démontez TOUTES ses partitions/volumes");
        System.out.println("     (Explorateur → Clic droit sur le lecteur → Éjecter)");
        System.out.println("  2. Le disque physique restera accessible");
        System.out.println("     (\\\\.\\\\ PHYSICALDRIVE0, \\\\.\\\\ PHYSICALDRIVE1, etc.)");
        System.out.println();
        System.out.println("Exemple:");
        System.out.println("  - Vous éjectez le lecteur E:\\ (volume monté)");
        System.out.println("  - Le disque physique reste visible comme PHYSICALDRIVE1");
        System.out.println("  - Vous pouvez alors le scanner");
        System.out.println();
    }
}