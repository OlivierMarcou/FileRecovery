package com.filerecovery.model;

/**
 * Représente un périphérique de stockage (disque dur, SSD, clé USB, etc.)
 */
public class BlockDevice {
    private final String path;
    private final String name;
    private final long size;
    private final boolean isMounted;
    private final String model;
    private final String interfaceType;

    public BlockDevice(String path, String name, long size, boolean isMounted) {
        this(path, name, size, isMounted, "", "");
    }

    public BlockDevice(String path, String name, long size, boolean isMounted,
                       String model, String interfaceType) {
        this.path = path;
        this.name = name;
        this.size = size;
        this.isMounted = isMounted;
        this.model = model;
        this.interfaceType = interfaceType;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public boolean isMounted() {
        return isMounted;
    }

    public String getModel() {
        return model;
    }

    public String getInterfaceType() {
        return interfaceType;
    }

    public boolean isPhysicalDrive() {
        return path.startsWith("\\\\.\\PHYSICALDRIVE") || path.startsWith("/dev/");
    }

    public boolean isSpecialDevice() {
        return path.equals("/dev/null") || path.equals("NULL");
    }

    @Override
    public String toString() {
        if (isSpecialDevice()) {
            return name;
        }

        String status = isMounted ? " [MONTÉ]" : " [Non monté]";
        String sizeStr = formatSize(size);
        return String.format("%s - %s%s", name, sizeStr, status);
    }

    private String formatSize(long bytes) {
        if (bytes == 0) return "Taille inconnue";
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.0f MB", bytes / (1024.0 * 1024));
        }
        if (bytes < 1024L * 1024 * 1024 * 1024) {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
        return String.format("%.2f TB", bytes / (1024.0 * 1024 * 1024 * 1024));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BlockDevice that = (BlockDevice) obj;
        return path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }
}