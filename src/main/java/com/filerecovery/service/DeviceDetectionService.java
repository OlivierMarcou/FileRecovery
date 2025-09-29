package com.filerecovery.service;

import com.filerecovery.model.BlockDevice;
import com.filerecovery.util.OSDetector;
import java.util.List;
import java.util.ArrayList;

/**
 * Service de détection des périphériques de stockage
 */
public class DeviceDetectionService {
    private final WindowsDeviceService windowsService;
    private final LinuxDeviceService linuxService;

    public DeviceDetectionService() {
        this.windowsService = new WindowsDeviceService();
        this.linuxService = new LinuxDeviceService();
    }

    /**
     * Détecte tous les périphériques disponibles selon l'OS
     */
    public List<BlockDevice> detectDevices() {
        if (OSDetector.isWindows()) {
            return windowsService.detectDevices();
        } else if (OSDetector.isLinux()) {
            return linuxService.detectDevices();
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * Ajoute le périphérique spécial pour recherche de partitions
     */
    public BlockDevice getPartitionSearchDevice() {
        String nullDevice = OSDetector.isWindows() ? "NULL" : "/dev/null";
        return new BlockDevice(nullDevice, "Rechercher partitions perdues", 0, false);
    }

    /**
     * Vérifie si un périphérique est accessible
     */
    public boolean isDeviceAccessible(BlockDevice device) {
        if (OSDetector.isWindows()) {
            return windowsService.isDeviceAccessible(device);
        } else if (OSDetector.isLinux()) {
            return linuxService.isDeviceAccessible(device);
        }
        return false;
    }
}