package com.filerecovery.view;

import com.filerecovery.model.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Interface de la vue principale avec support Pause/Resume
 * Définit les méthodes que la vue doit implémenter
 */
public interface MainView {

    /**
     * Initialise la vue
     */
    void initialize();

    /**
     * Met à jour la liste des périphériques
     */
    void updateDeviceList(List<BlockDevice> devices);

    /**
     * Ajoute un fichier récupéré à l'affichage
     */
    void addRecoveredFile(RecoveredFile file);

    /**
     * Met à jour la barre de progression
     */
    void updateProgress(int percentage, String message);

    /**
     * Affiche un message d'information
     */
    void showMessage(String message);

    /**
     * Affiche un message d'erreur
     */
    void showError(String error);

    /**
     * Affiche la fin du scan
     */
    void showScanComplete(int filesFound);

    /**
     * Affiche la fin de la récupération
     */
    void showRecoveryComplete(int filesRecovered, String destination);

    /**
     * Efface les résultats précédents
     */
    void clearResults();

    /**
     * Active/désactive l'état de scan
     */
    void setScanningState(boolean scanning);

    /**
     * Active/désactive le bouton pause/resume
     */
    void setPauseResumeEnabled(boolean enabled);

    /**
     * Active/désactive le bouton stop
     */
    void setStopEnabled(boolean enabled);

    /**
     * Change le texte du bouton pause/resume
     */
    void setPauseResumeButtonText(String text);

    /**
     * Active/désactive le bouton de récupération sélective
     */
    void setRecoverSelectedEnabled(boolean enabled);

    /**
     * Demande confirmation pour le scan
     */
    boolean confirmScan(BlockDevice device);

    /**
     * Affiche les instructions de démontage
     */
    void showUnmountInstructions(BlockDevice device);

    // Callbacks pour les actions utilisateur
    void onRefreshDevices(Runnable callback);
    void onDeviceSelected(Consumer<BlockDevice> callback);
    void onStartScan(Consumer<Boolean> callback); // Boolean = rawMode
    void onPauseResume(Runnable callback); // NOUVEAU
    void onStopScan(Runnable callback); // NOUVEAU
    void onUnmountDevice(Runnable callback);
    void onRecoverFiles(RecoverFilesCallback callback);

    @FunctionalInterface
    interface RecoverFilesCallback {
        void recover(List<RecoveredFile> files, String destinationPath);
    }
}