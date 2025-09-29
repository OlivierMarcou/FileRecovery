package com.filerecovery.service;

import com.filerecovery.model.BlockDevice;
import java.io.*;
import java.util.*;

/**
 * Service spécifique Windows pour la détection de périphériques
 */
public class WindowsDeviceService {

    public List<BlockDevice> detectDevices() {
        List<BlockDevice> devices = new ArrayList<>();

        // Méthode 1: Disques physiques via WMIC (prioritaire)
        devices.addAll(detectPhysicalDrives());

        // Méthode 2: Volumes montés (pour référence)
        devices.addAll(detectMountedVolumes());

        // Méthode 3: Énumération directe si WMIC échoue
        if (devices.isEmpty()) {
            devices.addAll(enumeratePhysicalDrives());
        }

        return devices;
    }

    private List<BlockDevice> detectPhysicalDrives() {
        List<BlockDevice> devices = new ArrayList<>();

        try {
            ProcessBuilder pb = new ProcessBuilder("wmic", "diskdrive", "get",
                    "DeviceID,Size,Model,InterfaceType,Status", "/format:csv");
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    String deviceId = parts[1].trim();
                    String interfaceType = parts[2].trim();
                    String model = parts[3].trim();
                    String sizeStr = parts[4].trim();

                    if (!deviceId.isEmpty() && deviceId.startsWith("\\\\.\\PHYSICALDRIVE")) {
                        try {
                            long size = sizeStr.isEmpty() ? 0 : Long.parseLong(sizeStr);
                            String diskNum = deviceId.replace("\\\\.\\PHYSICALDRIVE", "");
                            String name = String.format("Disque %s - %s (%s)",
                                    diskNum,
                                    model.isEmpty() ? "Disque physique" : model,
                                    interfaceType.isEmpty() ? "Inconnu" : interfaceType);

                            BlockDevice device = new BlockDevice(deviceId, name, size, false, model, interfaceType);
                            devices.add(device);
                        } catch (NumberFormatException e) {
                            // Ignorer ce périphérique
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            // WMIC a échoué, on essaie les autres méthodes
        }

        return devices;
    }

    private List<BlockDevice> detectMountedVolumes() {
        List<BlockDevice> devices = new ArrayList<>();

        File[] roots = File.listRoots();
        for (File root : roots) {
            try {
                String path = root.getAbsolutePath();
                long totalSpace = root.getTotalSpace();
                if (totalSpace > 0) {
                    String volumeName = String.format("Volume %s (monté)", path.replace("\\", ""));
                    BlockDevice device = new BlockDevice(path, volumeName, totalSpace, true);
                    devices.add(device);
                }
            } catch (Exception e) {
                // Ignorer ce volume
            }
        }

        return devices;
    }

    private List<BlockDevice> enumeratePhysicalDrives() {
        List<BlockDevice> devices = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            String devicePath = "\\\\.\\" + "PHYSICALDRIVE" + i;
            try {
                File testFile = new File(devicePath);
                String name = String.format("Disque physique %d", i);
                BlockDevice device = new BlockDevice(devicePath, name, 0, false);
                devices.add(device);
            } catch (Exception e) {
                break;
            }
        }

        return devices;
    }

    public boolean isDeviceAccessible(BlockDevice device) {
        try {
            RandomAccessFile raf = new RandomAccessFile(device.getPath(), "r");
            raf.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}