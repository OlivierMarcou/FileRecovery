package com.filerecovery.service;

import com.filerecovery.model.BlockDevice;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Service spécifique Linux pour la détection de périphériques
 */
public class LinuxDeviceService {

    public List<BlockDevice> detectDevices() {
        List<BlockDevice> devices = new ArrayList<>();

        try {
            File devDir = new File("/dev");
            File[] deviceFiles = devDir.listFiles((dir, name) ->
                    name.matches("sd[a-z]|nvme[0-9]+n[0-9]+|vd[a-z]|hd[a-z]|mmcblk[0-9]+"));

            if (deviceFiles != null) {
                Arrays.sort(deviceFiles);
                for (File deviceFile : deviceFiles) {
                    try {
                        String path = deviceFile.getAbsolutePath();
                        String name = deviceFile.getName();
                        long size = getDeviceSize(path);
                        boolean mounted = isDeviceMounted(path);

                        BlockDevice device = new BlockDevice(path, name, size, mounted);
                        devices.add(device);
                    } catch (Exception e) {
                        // Ignorer ce périphérique
                    }
                }
            }
        } catch (Exception e) {
            // Erreur lors de la lecture de /dev
        }

        return devices;
    }

    private long getDeviceSize(String devicePath) {
        try {
            String deviceName = new File(devicePath).getName();
            Path sizePath = Paths.get("/sys/block/" + deviceName + "/size");

            if (Files.exists(sizePath)) {
                String sizeStr = Files.readString(sizePath).trim();
                long sectors = Long.parseLong(sizeStr);
                return sectors * 512; // Conversion en bytes
            }

            // Fallback: utiliser blockdev
            ProcessBuilder pb = new ProcessBuilder("blockdev", "--getsize64", devicePath);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null) {
                return Long.parseLong(line.trim());
            }
        } catch (Exception e) {
            // Taille non disponible
        }
        return 0;
    }

    private boolean isDeviceMounted(String devicePath) {
        try {
            List<String> lines = Files.readAllLines(Paths.get("/proc/mounts"));
            for (String line : lines) {
                if (line.startsWith(devicePath + " ")) {
                    return true;
                }
            }

            // Vérifier aussi les partitions
            String baseName = new File(devicePath).getName();
            for (String line : lines) {
                if (line.startsWith("/dev/" + baseName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Erreur lors de la lecture de /proc/mounts
        }
        return false;
    }

    public boolean unmountDevice(String devicePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("pkexec", "umount", devicePath);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isDeviceAccessible(BlockDevice device) {
        File deviceFile = new File(device.getPath());
        return deviceFile.exists() && deviceFile.canRead();
    }
}