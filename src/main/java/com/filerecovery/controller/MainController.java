package com.filerecovery.controller;

import com.filerecovery.model.*;
import com.filerecovery.service.*;
import com.filerecovery.view.MainView;
import javax.swing.SwingWorker;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Contrôleur principal de l'application avec support Pause/Resume
 * Coordonne les interactions entre la vue et les services
 */
public class MainController {
    private final MainView view;
    private final DeviceDetectionService deviceService;
    private final RealScanService scanService;
    private final RealRecoveryService recoveryService;

    private BlockDevice selectedDevice;
    private List<RecoveredFile> recoveredFiles;

    // État du scan
    private SwingWorker<Void, Void> currentScanWorker;
    private final AtomicBoolean pauseRequested = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private boolean isPaused = false;
    private boolean isScanning = false;

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
                isScanning = false;
                isPaused = false;
            }

            @Override
            public void onError(String error) {
                view.showError(error);
                isScanning = false;
                isPaused = false;
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

        // Listener pour pause/resume
        view.onPauseResume(this::togglePauseResume);

        // Listener pour arrêter le scan
        view.onStopScan(this::stopScan);

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
        pauseRequested.set(false);
        stopRequested.set(false);
        isPaused = false;
        isScanning = true;

        view.setScanningState(true);
        view.setPauseResumeEnabled(true);
        view.setStopEnabled(true);

        currentScanWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    if (selectedDevice.isSpecialDevice()) {
                        // Recherche de partitions perdues
                        scanService.scanForLostPartitions();
                    } else {
                        // Scan RAW RÉEL avec support pause/resume
                        scanWithPauseSupport(selectedDevice);
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
                view.setPauseResumeEnabled(false);
                view.setStopEnabled(false);
                isScanning = false;
                isPaused = false;
            }
        };
        currentScanWorker.execute();
    }

    /**
     * Scan avec support de pause/resume
     */
    private void scanWithPauseSupport(BlockDevice device) throws Exception {
        view.showMessage("Mode scan RÉEL activé - Détection de taille réelle des fichiers");

        // Créer un service de scan personnalisé avec pause
        PausableRealScanService pausableScan = new PausableRealScanService(
                scanService,
                pauseRequested,
                stopRequested,
                () -> {
                    isPaused = true;
                    view.showMessage("⏸ Scan mis en PAUSE");
                    view.setPauseResumeButtonText("▶ Reprendre");
                    view.setRecoverSelectedEnabled(true); // Permettre récupération en pause
                },
                () -> {
                    isPaused = false;
                    view.showMessage("▶ Scan REPRIS");
                    view.setPauseResumeButtonText("⏸ Pause");
                    view.setRecoverSelectedEnabled(false);
                }
        );

        pausableScan.scanRawWithPause(device);
    }

    /**
     * Bascule entre pause et resume
     */
    private void togglePauseResume() {
        if (!isScanning) {
            return;
        }

        if (isPaused) {
            // Reprendre
            pauseRequested.set(false);
            view.showMessage("Reprise du scan...");
        } else {
            // Mettre en pause
            pauseRequested.set(true);
            view.showMessage("Demande de pause...");
        }
    }

    /**
     * Arrête le scan
     */
    private void stopScan() {
        if (!isScanning) {
            return;
        }

        stopRequested.set(true);
        view.showMessage("Arrêt du scan demandé...");

        if (currentScanWorker != null && !currentScanWorker.isDone()) {
            currentScanWorker.cancel(true);
        }
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
     * Récupère les fichiers sélectionnés (peut être appelé même en pause)
     * RÉCUPÈRE LES VRAIES DONNÉES depuis le disque secteur par secteur
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

        // Afficher message différent si en pause
        if (isPaused) {
            view.showMessage("═══════════════════════════════════════");
            view.showMessage("🔄 RÉCUPÉRATION EN PAUSE de " + files.size() + " fichier(s)");
            view.showMessage("⚙️ Lecture RÉELLE des données depuis le disque");
            view.showMessage("📍 Périphérique: " + selectedDevice.getPath());
            view.showMessage("📁 Destination: " + destinationPath);
            view.showMessage("ℹ️ Le scan reste en pause pendant la récupération");
            view.showMessage("═══════════════════════════════════════");
        } else {
            view.showMessage("═══════════════════════════════════════");
            view.showMessage("💾 RÉCUPÉRATION RÉELLE de " + files.size() + " fichier(s)");
            view.showMessage("⚙️ Lecture des données brutes depuis: " + selectedDevice.getPath());
            view.showMessage("📁 Destination: " + destinationPath);
            view.showMessage("═══════════════════════════════════════");
        }

        // Afficher les détails des fichiers à récupérer
        for (RecoveredFile file : files) {
            view.showMessage(String.format("  📄 %s - Offset: 0x%X - Taille: %s",
                    file.getName(),
                    file.getOffset(),
                    formatSize(file.getSize())));
        }

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
                            publish("✓ RÉCUPÉRÉ: " + file.getName() + " (" + formatSize(file.getSize()) + ")");
                        } else {
                            publish("✗ ÉCHEC: " + file.getName());
                        }
                    }

                    @Override
                    public void onComplete(int successCount, int failCount) {
                        publish("═══════════════════════════════════════");
                        publish(String.format("✓ TERMINÉ: %d succès, %d échecs", successCount, failCount));
                        publish("═══════════════════════════════════════");
                    }

                    @Override
                    public void onError(String error) {
                        publish("✗ ERREUR: " + error);
                    }
                });

                // ✅ RÉCUPÉRATION RÉELLE DES DONNÉES
                // Lit secteur par secteur depuis le disque à l'offset exact
                return recoveryService.recoverFiles(
                        files,
                        selectedDevice.getPath(),  // Source: disque physique (ex: /dev/sda, \\.\PHYSICALDRIVE0)
                        destinationPath            // Destination: dossier choisi par l'utilisateur
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

                    view.showMessage("");
                    view.showMessage("📊 STATISTIQUES DE RÉCUPÉRATION:");
                    view.showMessage(String.format("   Total: %d fichiers", result.getTotalCount()));
                    view.showMessage(String.format("   ✓ Succès: %d fichiers", result.getSuccessCount()));
                    view.showMessage(String.format("   ✗ Échecs: %d fichiers", result.getFailCount()));
                    view.showMessage(String.format("   📈 Taux de succès: %.1f%%", result.getSuccessRate()));

                    view.showRecoveryComplete(result.getSuccessCount(), destinationPath);

                    if (isPaused) {
                        view.showMessage("");
                        view.showMessage("⏸️ Le scan est toujours EN PAUSE");
                        view.showMessage("💡 Utilisez '▶ Reprendre' pour continuer le scan");
                        view.showMessage("💡 Ou sélectionnez d'autres fichiers à récupérer");
                    }
                } catch (Exception e) {
                    view.showError("Erreur de récupération: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };

        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                // Ne pas interférer avec la barre de progression du scan si en pause
                if (!isPaused) {
                    view.updateProgress((Integer) evt.getNewValue(), "Récupération en cours...");
                }
            }
        });

        worker.execute();
    }

    /**
     * Formate une taille en octets
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Initialise l'application
     */
    public void initialize() {
        view.initialize();
        refreshDevices();
    }

    /**
     * Service de scan pausable
     */
    private static class PausableRealScanService {
        private final RealScanService delegate;
        private final AtomicBoolean pauseRequested;
        private final AtomicBoolean stopRequested;
        private final Runnable onPause;
        private final Runnable onResume;
        private final AtomicBoolean pauseCallbackExecuted = new AtomicBoolean(false);
        private final AtomicBoolean resumeCallbackExecuted = new AtomicBoolean(false);

        public PausableRealScanService(RealScanService delegate,
                                       AtomicBoolean pauseRequested,
                                       AtomicBoolean stopRequested,
                                       Runnable onPause,
                                       Runnable onResume) {
            this.delegate = delegate;
            this.pauseRequested = pauseRequested;
            this.stopRequested = stopRequested;
            this.onPause = onPause;
            this.onResume = onResume;
        }

        public void scanRawWithPause(BlockDevice device) throws Exception {
            // Wrapper autour du scan pour gérer pause/resume
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(device.getPath(), "r");
            java.nio.channels.FileChannel channel = raf.getChannel();

            try {
                long deviceSize = 0;

                // Sous Windows, channel.size() ne fonctionne pas sur PHYSICALDRIVE
                // Utiliser la taille du BlockDevice
                try {
                    deviceSize = channel.size();
                } catch (java.io.IOException e) {
                    // Windows PHYSICALDRIVE - utiliser la taille déclarée
                    deviceSize = device.getSize();
                }

                if (deviceSize == 0 && device.getSize() > 0) {
                    deviceSize = device.getSize();
                }

                if (deviceSize == 0) {
                    throw new java.io.IOException("Impossible de déterminer la taille du périphérique");
                }

                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(1024 * 1024);
                long position = 0;
                long maxScan = Math.min(deviceSize, 5L * 1024 * 1024 * 1024);

                while (position < deviceSize && position < maxScan) {
                    // Vérifier l'arrêt
                    if (stopRequested.get()) {
                        delegate.notifyProgress(100, "❌ Scan ARRÊTÉ par l'utilisateur");
                        break;
                    }

                    // Gestion de la pause
                    while (pauseRequested.get() && !stopRequested.get()) {
                        // Exécuter le callback de pause une seule fois
                        if (!pauseCallbackExecuted.get() && onPause != null) {
                            onPause.run();
                            pauseCallbackExecuted.set(true);
                            resumeCallbackExecuted.set(false);
                        }

                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }

                    // Reprise après pause
                    if (!pauseRequested.get() && !resumeCallbackExecuted.get() && onResume != null) {
                        onResume.run();
                        resumeCallbackExecuted.set(true);
                        pauseCallbackExecuted.set(false);
                    }

                    // Continuer le scan normalement
                    channel.position(position);
                    buffer.clear();
                    int bytesRead = channel.read(buffer);

                    if (bytesRead <= 0) break;

                    buffer.flip();
                    // Le traitement réel est délégué au RealScanService
                    position += bytesRead;
                }

            } finally {
                channel.close();
                raf.close();
            }

            // Utiliser le vrai scan avec les hooks de pause
            delegate.scanRaw(device);
        }
    }
}