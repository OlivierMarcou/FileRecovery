package com.filerecovery.controller;

import com.filerecovery.model.*;
import com.filerecovery.service.*;
import com.filerecovery.view.MainView;
import javax.swing.SwingWorker;
import java.util.*;

/**
 * Contrôleur principal de l'application
 * Coordonne les interactions entre la vue et les services
 */
public class MainController {
    private final MainView view;
    private final DeviceDetectionService deviceService;
    private final RealScanService scanService;
    private final RealRecoveryService recoveryService;

    private BlockDevice selectedDevice;
    private List<RecoveredFile> recoveredFiles;

    public MainController(MainView view) {
        this.view = view;
        this.deviceService = new DeviceDetectionService();
        this.scanService = new RealScanService();
        this.recoveryService = new RealRecoveryService();
        this.recoveredFiles = new ArrayList<>();

        initializeServices();
        setupListeners();
    }

    private void initializeServices() {
        // Configuration du listener de progression du scan
        scanService.setProgressListener(new ScanService.ScanProgressListener() {
            @Override
            public void onProgress(int percentage, String message) {
                view.updateProgress(percentage, message);
            }

            @Override
            public void onFileFound(RecoveredFile file) {
                view.addRecoveredFile(file);
                recoveredFiles.add(file);
            }

            @Override
            public void onComplete(int filesFound) {
                view.showScanComplete(filesFound);
            }

            @Override
            public void onError(String error) {
                view.showError(error);
            }
        });
    }

    private void setupListeners() {
        // Listener pour le rafraîchissement des périphériques
        view.onRefreshDevices(this::refreshDevices);

        // Listener pour la sélection d'un périphérique
        view.onDeviceSelected(this::onDeviceSelected);

        // Listener pour le démarrage du scan
        view.onStartScan(this::startScan);

        // Listener pour le démontage
        view.onUnmountDevice(this::unmountDevice);

        // Listener pour la récupération
        view.onRecoverFiles(this::recoverFiles);
    }

    /**
     * Rafraîchit la liste des périphériques disponibles
     */
    public void refreshDevices() {
        view.showMessage("Chargement des périphériques...");

        SwingWorker<List<BlockDevice>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<BlockDevice> doInBackground() {
                List<BlockDevice> devices = deviceService.detectDevices();
                devices.add(deviceService.getPartitionSearchDevice());
                return devices;
            }

            @Override
            protected void done() {
                try {
                    List<BlockDevice> devices = get();
                    view.updateDeviceList(devices);
                    view.showMessage(devices.size() + " périphérique(s) détecté(s)");
                } catch (Exception e) {
                    view.showError("Erreur lors du chargement: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    /**
     * Gère la sélection d'un périphérique
     */
    private void onDeviceSelected(BlockDevice device) {
        this.selectedDevice = device;
        view.showMessage("Sélectionné: " + device.getName());
    }

    /**
     * Démarre le scan du périphérique sélectionné
     */
    private void startScan(boolean rawMode) {
        if (selectedDevice == null) {
            view.showError("Aucun périphérique sélectionné");
            return;
        }

        // Vérification des avertissements
        if (!view.confirmScan(selectedDevice)) {
            return;
        }

        view.clearResults();
        recoveredFiles.clear();
        view.setScanningState(true);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    if (selectedDevice.isSpecialDevice()) {
                        // Recherche de partitions perdues
                        scanService.scanForLostPartitions();
                    } else {
                        // ✅ TOUJOURS utiliser le scan RAW RÉEL (pas de simulation)
                        view.showMessage("Mode scan RÉEL activé - Détection de taille réelle des fichiers");
                        scanService.scanRaw(selectedDevice);
                    }
                } catch (Exception e) {
                    view.showError("Erreur de scan: " + e.getMessage());
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void done() {
                view.setScanningState(false);
            }
        };
        worker.execute();
    }

    /**
     * Démonte le périphérique sélectionné
     */
    private void unmountDevice() {
        if (selectedDevice == null) {
            view.showError("Aucun périphérique sélectionné");
            return;
        }

        view.showUnmountInstructions(selectedDevice);
    }

    /**
     * Récupère les fichiers sélectionnés
     */
    private void recoverFiles(List<RecoveredFile> files, String destinationPath) {
        if (files.isEmpty()) {
            view.showError("Aucun fichier sélectionné");
            return;
        }

        if (selectedDevice == null) {
            view.showError("Aucun périphérique sélectionné");
            return;
        }

        view.showMessage("Récupération RÉELLE de " + files.size() + " fichier(s)...");

        SwingWorker<RealRecoveryService.RecoveryResult, String> worker = new SwingWorker<>() {
            @Override
            protected RealRecoveryService.RecoveryResult doInBackground() {
                // Configuration du listener de récupération
                recoveryService.setProgressListener(new RealRecoveryService.RecoveryProgressListener() {
                    @Override
                    public void onProgress(int percentage, String message) {
                        publish(message);
                        setProgress(percentage);
                    }

                    @Override
                    public void onFileRecovered(RecoveredFile file, boolean success) {
                        if (success) {
                            publish("✓ Récupéré: " + file.getName());
                        } else {
                            publish("✗ Échec: " + file.getName());
                        }
                    }

                    @Override
                    public void onComplete(int successCount, int failCount) {
                        publish(String.format("Terminé: %d succès, %d échecs",
                                successCount, failCount));
                    }

                    @Override
                    public void onError(String error) {
                        publish("Erreur: " + error);
                    }
                });

                // ✅ RÉCUPÉRATION RÉELLE
                return recoveryService.recoverFiles(
                        files,
                        selectedDevice.getPath(),  // Source (disque physique)
                        destinationPath            // Destination
                );
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    view.showMessage(msg);
                }
            }

            @Override
            protected void done() {
                try {
                    RealRecoveryService.RecoveryResult result = get();
                    view.showRecoveryComplete(result.getSuccessCount(), destinationPath);
                    view.showMessage(String.format(
                            "Taux de succès: %.1f%% (%d/%d)",
                            result.getSuccessRate(),
                            result.getSuccessCount(),
                            result.getTotalCount()
                    ));
                } catch (Exception e) {
                    view.showError("Erreur de récupération: " + e.getMessage());
                }
            }
        };

        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                view.updateProgress((Integer) evt.getNewValue(), "Récupération en cours...");
            }
        });

        worker.execute();
    }

    /**
     * Initialise l'application
     */
    public void initialize() {
        view.initialize();
        refreshDevices();
    }
}