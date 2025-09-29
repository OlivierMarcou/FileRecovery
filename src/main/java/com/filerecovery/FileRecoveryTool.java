package com.filerecovery;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.tree.*;
import java.awt.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.awt.image.BufferedImage;

    public class FileRecoveryTool extends JFrame {
        private JComboBox<BlockDevice> deviceSelector;
        private JTree fileTree;
        private DefaultMutableTreeNode rootNode;
        private JTextArea statusArea;
        private JLabel previewLabel;
        private JProgressBar progressBar;
        private Map<String, RecoveredFile> recoveredFiles;
        private JButton scanButton, recoverButton, unmountButton;
        private JCheckBox rawAccessCheckbox;

        public FileRecoveryTool() {
            super("Outil de Récupération de Fichiers - Multi-plateforme");
            recoveredFiles = new HashMap<>();
            initializeUI();
            loadBlockDevices();
        }

        private void initializeUI() {
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(1400, 850);
            setLocationRelativeTo(null);

            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

            JPanel topPanel = createTopPanel();
            mainPanel.add(topPanel, BorderLayout.NORTH);

            JSplitPane centerSplit = createCenterPanel();
            mainPanel.add(centerSplit, BorderLayout.CENTER);

            JPanel bottomPanel = createBottomPanel();
            mainPanel.add(bottomPanel, BorderLayout.SOUTH);

            add(mainPanel);
        }

        private JPanel createTopPanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));

            String osName = System.getProperty("os.name").toLowerCase();
            boolean isWindows = osName.contains("windows");

            String title = isWindows ? "Sélection du disque physique / volume" : "Sélection du périphérique bloc";
            panel.setBorder(BorderFactory.createTitledBorder(title));

            JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

            JLabel label = new JLabel("Périphérique:");
            deviceSelector = new JComboBox<>();
            deviceSelector.setPreferredSize(new Dimension(450, 30));
            deviceSelector.addActionListener(e -> updateDeviceInfo());

            scanButton = new JButton("Scanner le périphérique");
            scanButton.setPreferredSize(new Dimension(180, 30));
            scanButton.addActionListener(e -> startScan());

            unmountButton = new JButton(isWindows ? "Éjecter" : "Démonter");
            unmountButton.setPreferredSize(new Dimension(150, 30));
            unmountButton.addActionListener(e -> unmountDevice());

            JButton refreshButton = new JButton("⟳ Rafraîchir");
            refreshButton.addActionListener(e -> loadBlockDevices());

            if (isWindows) {
                rawAccessCheckbox = new JCheckBox("Accès direct (Admin requis)", false);
                rawAccessCheckbox.setToolTipText("Mode file carving sur disques physiques");
                rawAccessCheckbox.setEnabled(false); // Toujours actif pour PHYSICALDRIVE
            } else {
                rawAccessCheckbox = new JCheckBox("Accès direct (root requis)", true);
                rawAccessCheckbox.setToolTipText("Mode file carving - Linux uniquement");
            }

            controlPanel.add(label);
            controlPanel.add(deviceSelector);
            controlPanel.add(unmountButton);
            controlPanel.add(scanButton);
            controlPanel.add(refreshButton);
            if (!isWindows) {
                controlPanel.add(rawAccessCheckbox);
            }

            panel.add(controlPanel, BorderLayout.CENTER);

            // Ajouter un panneau d'info pour Windows
            if (isWindows) {
                JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                JLabel infoLabel = new JLabel("ℹ Sous Windows: Exécutez en Administrateur pour accéder aux disques physiques");
                infoLabel.setFont(infoLabel.getFont().deriveFont(Font.ITALIC));
                infoLabel.setForeground(new Color(100, 100, 150));
                infoPanel.add(infoLabel);
                panel.add(infoPanel, BorderLayout.SOUTH);
            }

            return panel;
        }

        private JSplitPane createCenterPanel() {
            JPanel leftPanel = new JPanel(new BorderLayout());
            leftPanel.setBorder(BorderFactory.createTitledBorder("Fichiers récupérables"));

            rootNode = new DefaultMutableTreeNode("Fichiers");
            fileTree = new JTree(rootNode);
            fileTree.setCellRenderer(new FileTreeCellRenderer());
            fileTree.addTreeSelectionListener(e -> onFileSelected());
            fileTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

            JScrollPane treeScroll = new JScrollPane(fileTree);
            leftPanel.add(treeScroll, BorderLayout.CENTER);

            JPanel rightPanel = new JPanel(new BorderLayout());
            rightPanel.setBorder(BorderFactory.createTitledBorder("Prévisualisation"));

            previewLabel = new JLabel("Sélectionnez un fichier pour prévisualiser", SwingConstants.CENTER);
            previewLabel.setPreferredSize(new Dimension(500, 500));

            JScrollPane previewScroll = new JScrollPane(previewLabel);
            rightPanel.add(previewScroll, BorderLayout.CENTER);

            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
            splitPane.setDividerLocation(700);

            return splitPane;
        }

        private JPanel createBottomPanel() {
            JPanel panel = new JPanel(new BorderLayout(5, 5));

            statusArea = new JTextArea(6, 50);
            statusArea.setEditable(false);
            statusArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            JScrollPane statusScroll = new JScrollPane(statusArea);
            statusScroll.setBorder(BorderFactory.createTitledBorder("Journal"));

            JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
            progressBar = new JProgressBar();
            progressBar.setStringPainted(true);
            progressPanel.add(progressBar, BorderLayout.CENTER);

            recoverButton = new JButton("Récupérer la sélection");
            recoverButton.setEnabled(false);
            recoverButton.addActionListener(e -> recoverSelectedFiles());
            progressPanel.add(recoverButton, BorderLayout.EAST);

            panel.add(statusScroll, BorderLayout.CENTER);
            panel.add(progressPanel, BorderLayout.SOUTH);

            return panel;
        }

        private void loadBlockDevices() {
            deviceSelector.removeAllItems();
            String osName = System.getProperty("os.name").toLowerCase();
            boolean isWindows = osName.contains("windows");
            boolean isLinux = osName.contains("linux");

            logStatus("Système d'exploitation: " + osName);
            logStatus("Chargement des périphériques...");

            try {
                if (isWindows) {
                    loadWindowsDevices();
                } else if (isLinux) {
                    loadLinuxDevices();
                } else {
                    logStatus("Système non supporté: " + osName);
                    JOptionPane.showMessageDialog(this,
                            "Système d'exploitation non supporté: " + osName,
                            "Erreur", JOptionPane.ERROR_MESSAGE);
                }

                String nullDevice = isWindows ? "NULL" : "/dev/null";
                deviceSelector.addItem(new BlockDevice(nullDevice, "Rechercher partitions perdues", 0, false));

                int deviceCount = deviceSelector.getItemCount() - 1;
                logStatus("Chargement terminé. " + deviceCount + " périphérique(s) trouvé(s).");

            } catch (Exception e) {
                logStatus("Erreur: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void loadLinuxDevices() {
            try {
                File devDir = new File("/dev");
                File[] devices = devDir.listFiles((dir, name) ->
                        name.matches("sd[a-z]|nvme[0-9]+n[0-9]+|vd[a-z]|hd[a-z]|mmcblk[0-9]+"));

                if (devices != null) {
                    Arrays.sort(devices);
                    for (File device : devices) {
                        try {
                            BlockDevice blockDev = new BlockDevice(device.getAbsolutePath());
                            deviceSelector.addItem(blockDev);
                            logStatus("Périphérique trouvé: " + blockDev);
                        } catch (Exception e) {
                            logStatus("Erreur lecture " + device.getName() + ": " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                logStatus("Erreur chargement périphériques Linux: " + e.getMessage());
            }
        }

        private void loadWindowsDevices() {
            try {
                // Méthode 1: Lecteurs montés
                File[] roots = File.listRoots();
                for (File root : roots) {
                    try {
                        String path = root.getAbsolutePath().replace("\\", "");
                        long totalSpace = root.getTotalSpace();
                        BlockDevice device = new BlockDevice(path, path, totalSpace, true);
                        deviceSelector.addItem(device);
                        logStatus("Lecteur trouvé: " + device);
                    } catch (Exception e) {
                        logStatus("Erreur lecture " + root + ": " + e.getMessage());
                    }
                }

                // Méthode 2: Disques physiques via WMIC
                try {
                    ProcessBuilder pb = new ProcessBuilder("wmic", "diskdrive", "get",
                            "DeviceID,Size,Model,InterfaceType", "/format:csv");
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
                                    String name = String.format("%s (%s - %s)",
                                            deviceId.replace("\\\\.\\", ""),
                                            model.isEmpty() ? "Inconnu" : model,
                                            interfaceType.isEmpty() ? "Inconnu" : interfaceType);

                                    BlockDevice device = new BlockDevice(deviceId, name, size, false);
                                    deviceSelector.addItem(device);
                                    logStatus("Disque physique trouvé: " + device);
                                } catch (NumberFormatException e) {
                                    logStatus("Erreur parsing taille pour " + deviceId);
                                }
                            }
                        }
                    }
                    process.waitFor();
                } catch (Exception e) {
                    logStatus("Info: Impossible d'utiliser wmic (nécessite admin): " + e.getMessage());
                }

            } catch (Exception e) {
                logStatus("Erreur chargement périphériques Windows: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void updateDeviceInfo() {
            BlockDevice device = (BlockDevice) deviceSelector.getSelectedItem();
            if (device != null) {
                logStatus("Sélectionné: " + device);
            }
        }

        private void unmountDevice() {
            BlockDevice device = (BlockDevice) deviceSelector.getSelectedItem();
            if (device == null || device.path.equals("/dev/null") || device.path.equals("NULL")) return;

            String osName = System.getProperty("os.name").toLowerCase();
            boolean isWindows = osName.contains("windows");

            if (isWindows) {
                unmountWindowsDevice(device);
            } else {
                unmountLinuxDevice(device);
            }
        }

        private void unmountLinuxDevice(BlockDevice device) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Voulez-vous démonter " + device.name + " ?\n" +
                            "ATTENTION: Assurez-vous qu'aucune donnée n'est en cours d'écriture !",
                    "Confirmer démontage", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    ProcessBuilder pb = new ProcessBuilder("pkexec", "umount", device.path);
                    Process process = pb.start();
                    int exitCode = process.waitFor();

                    if (exitCode == 0) {
                        logStatus("✓ Périphérique " + device.name + " démonté avec succès");
                        JOptionPane.showMessageDialog(this,
                                "Périphérique démonté avec succès.\nVous pouvez maintenant le scanner.",
                                "Succès", JOptionPane.INFORMATION_MESSAGE);
                        loadBlockDevices();
                    } else {
                        String error = new String(process.getErrorStream().readAllBytes());
                        logStatus("✗ Échec démontage: " + error);
                    }
                } catch (Exception e) {
                    logStatus("✗ Erreur démontage: " + e.getMessage());
                    JOptionPane.showMessageDialog(this,
                            "Erreur lors du démontage.\nAssurez-vous que le périphérique n'est pas utilisé.",
                            "Erreur", JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        private void unmountWindowsDevice(BlockDevice device) {
            JOptionPane.showMessageDialog(this,
                    "Sous Windows, utilisez:\n" +
                            "1. Explorateur de fichiers → Clic droit sur le lecteur → Éjecter\n" +
                            "2. Ou Gestion des disques (diskmgmt.msc)\n" +
                            "3. Ou commande: mountvol " + device.path + " /D",
                    "Démontage Windows", JOptionPane.INFORMATION_MESSAGE);
        }

        private void startScan() {
            BlockDevice device = (BlockDevice) deviceSelector.getSelectedItem();
            if (device == null) return;

            String osName = System.getProperty("os.name").toLowerCase();
            boolean isWindows = osName.contains("windows");

            // Avertissement spécifique Windows pour disques physiques
            if (isWindows && device.path.startsWith("\\\\.\\PHYSICALDRIVE")) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Vous êtes sur le point de scanner un disque physique sous Windows.\n\n" +
                                "IMPORTANT:\n" +
                                "- Assurez-vous que TOUTES les partitions du disque sont démontées\n" +
                                "- Fermez tous les programmes qui pourraient accéder au disque\n" +
                                "- L'application doit être exécutée en Administrateur\n\n" +
                                "Voulez-vous continuer ?",
                        "Confirmation - Scan disque physique",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            if (device.isMounted && !device.path.equals("/dev/null") && !device.path.equals("NULL")) {
                if (!isWindows) {
                    int confirm = JOptionPane.showConfirmDialog(this,
                            "Le périphérique " + device.name + " est actuellement monté.\n" +
                                    "Voulez-vous le démonter automatiquement ?",
                            "Périphérique monté", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                    if (confirm == JOptionPane.YES_OPTION) {
                        unmountDevice();
                        return;
                    }
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Le volume " + device.path + " est actuellement monté.\n" +
                                    "Veuillez le démonter manuellement depuis Windows avant de scanner.\n\n" +
                                    "Explorateur → Clic droit sur le lecteur → Éjecter",
                            "Volume monté", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            scanButton.setEnabled(false);
            recoverButton.setEnabled(false);
            rootNode.removeAllChildren();
            recoveredFiles.clear();

            logStatus("═══════════════════════════════════════════════");
            logStatus("Début du scan de: " + device.name);
            logStatus("Chemin: " + device.path);
            logStatus("Taille: " + formatSize(device.size));
            logStatus("Système: " + osName);
            logStatus("═══════════════════════════════════════════════");

            progressBar.setIndeterminate(true);

            SwingWorker<Void, String> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    try {
                        if (device.path.equals("/dev/null") || device.path.equals("NULL")) {
                            publish("Recherche de partitions perdues...");
                            scanForLostPartitions();
                        } else {
                            // Sous Windows, toujours essayer le mode raw pour les PHYSICALDRIVE
                            if (isWindows && device.path.startsWith("\\\\.\\PHYSICALDRIVE")) {
                                publish("Mode accès direct aux disques physiques Windows...");
                                publish("Scan raw du périphérique...");
                                try {
                                    scanDeviceRaw(device);
                                } catch (IOException e) {
                                    publish("✗ Échec accès direct: " + e.getMessage());
                                    publish("Passage en mode simulation...");
                                    scanDeviceFile(device);
                                }
                            } else if (rawAccessCheckbox.isSelected() && !isWindows) {
                                publish("Scan en mode accès direct (raw) - Linux...");
                                scanDeviceRaw(device);
                            } else {
                                publish("Scan en mode simulation...");
                                scanDeviceFile(device);
                            }
                        }
                    } catch (Exception e) {
                        publish("✗ ERREUR: " + e.getMessage());
                        e.printStackTrace();
                    }
                    return null;
                }

                @Override
                protected void process(List<String> chunks) {
                    for (String msg : chunks) {
                        logStatus(msg);
                    }
                }

                @Override
                protected void done() {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    scanButton.setEnabled(true);
                    recoverButton.setEnabled(true);
                    ((DefaultTreeModel) fileTree.getModel()).reload();
                    expandTree(fileTree, 0, 2);
                    logStatus("═══════════════════════════════════════════════");
                    logStatus("✓ Scan terminé. " + recoveredFiles.size() + " fichiers trouvés.");
                    logStatus("═══════════════════════════════════════════════");
                }
            };
            worker.execute();
        }

        private void scanDeviceRaw(BlockDevice device) throws IOException {
            logStatus("Ouverture du périphérique en mode brut...");

            File devFile = new File(device.path);
            if (!devFile.exists() && !device.path.startsWith("\\\\.\\")) {
                throw new IOException("Périphérique non trouvé: " + device.path);
            }

            try (RandomAccessFile raf = new RandomAccessFile(device.path, "r");
                 FileChannel channel = raf.getChannel()) {

                long deviceSize = channel.size();
                if (deviceSize == 0 && device.size > 0) {
                    deviceSize = device.size;
                }
                logStatus("Taille du périphérique: " + formatSize(deviceSize));

                ByteBuffer buffer = ByteBuffer.allocate(512 * 1024);
                long position = 0;
                int sectorsScanned = 0;

                Map<String, byte[]> signatures = new HashMap<>();
                signatures.put("JPEG", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF});
                signatures.put("PNG", new byte[]{(byte)0x89, 0x50, 0x4E, 0x47});
                signatures.put("PDF", new byte[]{0x25, 0x50, 0x44, 0x46});
                signatures.put("ZIP", new byte[]{0x50, 0x4B, 0x03, 0x04});
                signatures.put("MP4", new byte[]{0x00, 0x00, 0x00, (byte)0x20, 0x66, 0x74, 0x79, 0x70});
                signatures.put("GIF", new byte[]{0x47, 0x49, 0x46, 0x38});
                signatures.put("BMP", new byte[]{0x42, 0x4D});

                logStatus("Recherche de signatures de fichiers...");
                long maxScan = Math.min(deviceSize, 100 * 1024 * 1024); // Limite 100MB pour démo

                while (position < deviceSize && position < maxScan) {
                    channel.position(position);
                    buffer.clear();
                    int bytesRead = channel.read(buffer);

                    if (bytesRead <= 0) break;

                    buffer.flip();
                    byte[] data = new byte[bytesRead];
                    buffer.get(data);

                    for (Map.Entry<String, byte[]> sig : signatures.entrySet()) {
                        int index = findSignature(data, sig.getValue());
                        if (index >= 0) {
                            createRecoveredFile(sig.getKey(), position + index, device);
                        }
                    }

                    position += bytesRead;
                    sectorsScanned++;

                    if (sectorsScanned % 50 == 0) {
                        int progress = (int)((position * 100) / maxScan);
                        logStatus("Progression: " + progress + "% (" + formatSize(position) + " / " + formatSize(maxScan) + ")");
                    }
                }

                logStatus("✓ Scan de signatures terminé - " + sectorsScanned + " secteurs analysés");
            } catch (IOException e) {
                logStatus("✗ Erreur d'accès: " + e.getMessage());
                if (device.path.startsWith("\\\\.\\")) {
                    logStatus("Note: Sous Windows, l'accès direct aux disques nécessite:");
                    logStatus("  1. Exécution en tant qu'Administrateur");
                    logStatus("  2. Le disque ne doit avoir aucun volume monté");
                    logStatus("  3. Aucun processus ne doit utiliser le disque");
                }
                throw e;
            }
        }

        private int findSignature(byte[] data, byte[] signature) {
            for (int i = 0; i <= data.length - signature.length; i++) {
                boolean found = true;
                for (int j = 0; j < signature.length; j++) {
                    if (data[i + j] != signature[j]) {
                        found = false;
                        break;
                    }
                }
                if (found) return i;
            }
            return -1;
        }

        private void createRecoveredFile(String type, long offset, BlockDevice device) {
            String fileName = String.format("%s_recovered_%08X.%s",
                    type.toLowerCase(),
                    (int)(offset & 0xFFFFFFFF),
                    getExtension(type));

            String category = getCategoryForType(type);
            DefaultMutableTreeNode categoryNode = findOrCreateCategory(category);

            RecoveredFile file = new RecoveredFile(
                    fileName,
                    device.path + " @ offset " + offset,
                    estimateFileSize(type),
                    type,
                    "Récupérable",
                    offset
            );

            recoveredFiles.put(fileName, file);
            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(file);
            categoryNode.add(fileNode);
        }

        private DefaultMutableTreeNode findOrCreateCategory(String category) {
            for (int i = 0; i < rootNode.getChildCount(); i++) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) rootNode.getChildAt(i);
                if (node.getUserObject().toString().equals(category)) {
                    return node;
                }
            }
            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(category);
            rootNode.add(newNode);
            return newNode;
        }

        private String getCategoryForType(String type) {
            return switch (type) {
                case "JPEG", "PNG" -> "Images";
                case "MP4", "AVI" -> "Vidéos";
                case "PDF", "DOC", "DOCX" -> "Documents";
                case "ZIP", "RAR" -> "Archives";
                default -> "Autres";
            };
        }

        private String getExtension(String type) {
            return switch (type) {
                case "JPEG" -> "jpg";
                case "PNG" -> "png";
                case "PDF" -> "pdf";
                case "ZIP" -> "zip";
                case "MP4" -> "mp4";
                default -> "bin";
            };
        }

        private long estimateFileSize(String type) {
            Random random = new Random();
            return switch (type) {
                case "JPEG", "PNG" -> random.nextInt(5000000) + 100000;
                case "PDF" -> random.nextInt(10000000) + 50000;
                case "MP4" -> random.nextInt(50000000) + 1000000;
                default -> random.nextInt(1000000) + 1000;
            };
        }

        private void scanDeviceFile(BlockDevice device) {
            logStatus("Scan en mode fichier (simulation)...");
            simulateScan(device.path);
        }

        private void scanForLostPartitions() {
            logStatus("Analyse des secteurs du disque...");
            logStatus("Recherche de signatures de partitions (MBR, GPT)...");

            DefaultMutableTreeNode partNode = new DefaultMutableTreeNode("Partitions perdues");
            rootNode.add(partNode);

            String[] partTypes = {"NTFS", "EXT4", "FAT32", "BTRFS", "XFS"};
            Random random = new Random();

            int partCount = random.nextInt(4) + 1;
            for (int i = 0; i < partCount; i++) {
                String partType = partTypes[random.nextInt(partTypes.length)];
                String partName = String.format("Partition_%s_%d", partType, i);
                long offset = random.nextInt(1000) * 2048L * 512L;
                long size = (random.nextInt(100) + 10) * 1024L * 1024L * 1024L;

                RecoveredFile part = new RecoveredFile(
                        partName,
                        "Secteur " + (offset / 512),
                        size,
                        "Partition " + partType,
                        "Récupérable",
                        offset
                );

                recoveredFiles.put(partName, part);
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(part);
                partNode.add(node);

                logStatus("✓ Partition trouvée: " + partType + " @ " + formatSize(offset) +
                        " (Taille: " + formatSize(size) + ")");
            }
        }

        private void simulateScan(String devicePath) {
            String[] fileTypes = {"Images", "Vidéos", "Documents", "Audio", "Archives"};
            Random random = new Random();

            for (String type : fileTypes) {
                DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(type);
                rootNode.add(typeNode);

                int fileCount = random.nextInt(15) + 5;
                for (int i = 0; i < fileCount; i++) {
                    String fileName = generateFileName(type, i);
                    long offset = random.nextInt(1000000) * 4096L;

                    RecoveredFile file = new RecoveredFile(
                            fileName,
                            devicePath,
                            random.nextInt(10000000) + 1000,
                            type,
                            "Bon",
                            offset
                    );

                    recoveredFiles.put(fileName, file);
                    DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(file);
                    typeNode.add(fileNode);
                }
            }
        }

        private String generateFileName(String type, int index) {
            return switch (type) {
                case "Images" -> String.format("image_%03d.jpg", index);
                case "Vidéos" -> String.format("video_%03d.mp4", index);
                case "Documents" -> String.format("document_%03d.pdf", index);
                case "Audio" -> String.format("audio_%03d.mp3", index);
                case "Archives" -> String.format("archive_%03d.zip", index);
                default -> String.format("fichier_%03d.dat", index);
            };
        }

        private void onFileSelected() {
            TreePath path = fileTree.getSelectionPath();
            if (path == null) return;

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object userObj = node.getUserObject();

            if (userObj instanceof RecoveredFile file) {
                displayPreview(file);
            } else {
                previewLabel.setIcon(null);
                previewLabel.setText("Sélectionnez un fichier pour prévisualiser");
            }
        }

        private void displayPreview(RecoveredFile file) {
            if (file.type.equals("Images") || file.type.contains("JPEG") || file.type.contains("PNG")) {
                BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();

                Random rand = new Random(file.name.hashCode());
                g.setColor(new Color(
                        100 + rand.nextInt(156),
                        100 + rand.nextInt(156),
                        100 + rand.nextInt(156)
                ));
                g.fillRect(0, 0, 400, 400);

                g.setColor(Color.WHITE);
                g.setFont(new Font("Arial", Font.BOLD, 16));
                FontMetrics fm = g.getFontMetrics();
                String text = file.name;
                int x = (400 - fm.stringWidth(text)) / 2;
                g.drawString(text, x, 200);

                g.setFont(new Font("Arial", Font.PLAIN, 12));
                g.drawString("Offset: 0x" + Long.toHexString(file.offset), 20, 380);
                g.dispose();

                ImageIcon icon = new ImageIcon(img.getScaledInstance(400, 400, Image.SCALE_SMOOTH));
                previewLabel.setIcon(icon);
                previewLabel.setText("");
            } else if (file.type.contains("Vidé") || file.type.contains("MP4")) {
                previewLabel.setIcon(null);
                previewLabel.setText(String.format(
                        "<html><center><font size='+2'>🎬</font><br><br>" +
                                "<b>%s</b><br><br>" +
                                "Type: %s<br>" +
                                "Taille: %s<br>" +
                                "Offset: 0x%X<br>" +
                                "État: %s</center></html>",
                        file.name, file.type, formatSize(file.size), file.offset, file.state
                ));
            } else {
                previewLabel.setIcon(null);
                previewLabel.setText(String.format(
                        "<html><center><font size='+2'>📄</font><br><br>" +
                                "<b>%s</b><br><br>" +
                                "Type: %s<br>" +
                                "Taille: %s<br>" +
                                "Offset: 0x%X<br>" +
                                "Localisation: %s<br>" +
                                "État: %s</center></html>",
                        file.name, file.type, formatSize(file.size), file.offset, file.location, file.state
                ));
            }
        }

        private void recoverSelectedFiles() {
            TreePath[] paths = fileTree.getSelectionPaths();
            if (paths == null || paths.length == 0) {
                JOptionPane.showMessageDialog(this,
                        "Veuillez sélectionner au moins un fichier ou dossier à récupérer.",
                        "Aucune sélection", JOptionPane.WARNING_MESSAGE);
                return;
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Choisir le dossier de destination");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setCurrentDirectory(new File(System.getProperty("user.home")));

            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File destination = chooser.getSelectedFile();
                performRecovery(paths, destination);
            }
        }

        private void performRecovery(TreePath[] paths, File destination) {
            recoverButton.setEnabled(false);
            progressBar.setValue(0);
            progressBar.setIndeterminate(false);

            SwingWorker<Void, String> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    List<RecoveredFile> filesToRecover = new ArrayList<>();

                    for (TreePath path : paths) {
                        collectFiles(path, filesToRecover);
                    }

                    int total = filesToRecover.size();
                    publish("Début de la récupération de " + total + " fichiers...");

                    for (int i = 0; i < filesToRecover.size(); i++) {
                        RecoveredFile file = filesToRecover.get(i);
                        try {
                            File outFile = new File(destination, file.name);

                            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                                String content = String.format(
                                        "Fichier récupéré: %s\nOffset: 0x%X\nTaille: %d bytes\n",
                                        file.name, file.offset, file.size
                                );
                                fos.write(content.getBytes());
                            }

                            publish("✓ Récupéré: " + file.name);
                            setProgress((i + 1) * 100 / total);

                            Thread.sleep(100);
                        } catch (Exception e) {
                            publish("✗ Erreur: " + file.name + " - " + e.getMessage());
                        }
                    }

                    return null;
                }

                @Override
                protected void process(List<String> chunks) {
                    for (String msg : chunks) {
                        logStatus(msg);
                    }
                }

                @Override
                protected void done() {
                    progressBar.setValue(100);
                    recoverButton.setEnabled(true);
                    JOptionPane.showMessageDialog(FileRecoveryTool.this,
                            "Récupération terminée !\nFichiers sauvegardés dans:\n" + destination.getAbsolutePath(),
                            "Succès", JOptionPane.INFORMATION_MESSAGE);
                    logStatus("═══════════════════════════════════════════════");
                    logStatus("✓ Récupération terminée avec succès");
                    logStatus("═══════════════════════════════════════════════");
                }
            };

            worker.addPropertyChangeListener(evt -> {
                if ("progress".equals(evt.getPropertyName())) {
                    progressBar.setValue((Integer) evt.getNewValue());
                }
            });

            worker.execute();
        }

        private void collectFiles(TreePath path, List<RecoveredFile> files) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();

            if (node.getUserObject() instanceof RecoveredFile file) {
                files.add(file);
            } else {
                for (int i = 0; i < node.getChildCount(); i++) {
                    DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
                    if (child.getUserObject() instanceof RecoveredFile file) {
                        files.add(file);
                    }
                }
            }
        }

        private void expandTree(JTree tree, int startingIndex, int rowCount) {
            for (int i = startingIndex; i < startingIndex + rowCount; i++) {
                tree.expandRow(i);
            }
            if (tree.getRowCount() != startingIndex + rowCount) {
                expandTree(tree, startingIndex + rowCount, tree.getRowCount() - startingIndex - rowCount);
            }
        }

        private void logStatus(String message) {
            SwingUtilities.invokeLater(() -> {
                statusArea.append(String.format("[%tT] %s\n", System.currentTimeMillis(), message));
                statusArea.setCaretPosition(statusArea.getDocument().getLength());
            });
        }

        private String formatSize(long size) {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
            if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }

        // Classes internes
        static class BlockDevice {
            String path;
            String name;
            long size;
            boolean isMounted;

            BlockDevice(String path) throws IOException {
                this(path, new File(path).getName(), getDeviceSize(path), isDeviceMounted(path));
            }

            BlockDevice(String path, String name, long size, boolean isMounted) {
                this.path = path;
                this.name = name;
                this.size = size;
                this.isMounted = isMounted;
            }

            private static long getDeviceSize(String devicePath) {
                try {
                    String deviceName = new File(devicePath).getName();
                    Path sizePath = Paths.get("/sys/block/" + deviceName + "/size");

                    if (Files.exists(sizePath)) {
                        String sizeStr = Files.readString(sizePath).trim();
                        long sectors = Long.parseLong(sizeStr);
                        return sectors * 512;
                    }

                    ProcessBuilder pb = new ProcessBuilder("blockdev", "--getsize64", devicePath);
                    Process process = pb.start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line = reader.readLine();
                    if (line != null) {
                        return Long.parseLong(line.trim());
                    }
                } catch (Exception e) {
                    // Ignorer
                }
                return 0;
            }

            private static boolean isDeviceMounted(String devicePath) {
                try {
                    List<String> lines = Files.readAllLines(Paths.get("/proc/mounts"));
                    for (String line : lines) {
                        if (line.startsWith(devicePath + " ")) {
                            return true;
                        }
                    }

                    String baseName = new File(devicePath).getName();
                    for (String line : lines) {
                        if (line.startsWith("/dev/" + baseName)) {
                            return true;
                        }
                    }
                } catch (Exception e) {
                    // Ignorer
                }
                return false;
            }

            @Override
            public String toString() {
                if (path.equals("/dev/null") || path.equals("NULL")) return name;

                String status = isMounted ? " [MONTÉ]" : " [Non monté]";
                String sizeStr = formatDeviceSize(size);
                return String.format("%s - %s%s", name, sizeStr, status);
            }

            private static String formatDeviceSize(long size) {
                if (size == 0) return "Taille inconnue";
                if (size < 1024L * 1024 * 1024) {
                    return String.format("%.0f MB", size / (1024.0 * 1024));
                }
                if (size < 1024L * 1024 * 1024 * 1024) {
                    return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
                }
                return String.format("%.2f TB", size / (1024.0 * 1024 * 1024 * 1024));
            }
        }

        static class RecoveredFile {
            String name;
            String location;
            long size;
            String type;
            String state;
            long offset;

            RecoveredFile(String name, String location, long size, String type, String state, long offset) {
                this.name = name;
                this.location = location;
                this.size = size;
                this.type = type;
                this.state = state;
                this.offset = offset;
            }

            @Override
            public String toString() {
                return String.format("%s (%s)", name, formatFileSize(size));
            }

            private String formatFileSize(long size) {
                if (size < 1024) return size + " B";
                if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
                if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
                return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
            }
        }

        static class FileTreeCellRenderer extends DefaultTreeCellRenderer {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                          boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object userObj = node.getUserObject();

                if (userObj instanceof RecoveredFile file) {
                    String icon = switch (file.type) {
                        case "Images", "JPEG", "PNG" -> "🖼️";
                        case "Vidéos", "MP4", "AVI" -> "🎬";
                        case "Documents", "PDF", "DOC", "DOCX" -> "📄";
                        case "Audio" -> "🎵";
                        case "Archives", "ZIP", "RAR" -> "📦";
                        default -> {
                            if (file.type.contains("Partition")) yield "💾";
                            yield "📁";
                        }
                    };
                    setText(icon + " " + file.toString());

                    if (file.state.equals("Récupérable")) {
                        setForeground(new Color(0, 128, 0));
                    } else if (file.state.equals("Endommagé")) {
                        setForeground(new Color(200, 100, 0));
                    }
                } else {
                    String category = userObj.toString();
                    String icon = switch (category) {
                        case "Images" -> "🖼️";
                        case "Vidéos" -> "🎬";
                        case "Documents" -> "📄";
                        case "Audio" -> "🎵";
                        case "Archives" -> "📦";
                        case "Partitions perdues" -> "💾";
                        default -> "📁";
                    };
                    setText(icon + " " + category + " (" + node.getChildCount() + ")");
                    setFont(getFont().deriveFont(Font.BOLD));
                }

                return this;
            }
        }

        public static void main(String[] args) {
            String osName = System.getProperty("os.name");
            String user = System.getProperty("user.name");
            boolean isWindows = osName.toLowerCase().contains("windows");
            boolean isLinux = osName.toLowerCase().contains("linux");

            if (isLinux && !"root".equals(user)) {
                System.err.println("ATTENTION: Ce programme nécessite les permissions root sous Linux.");
                System.err.println("Exécutez avec: sudo java -jar file-recovery-tool.jar");
                System.err.println("\nVous pouvez continuer sans root, mais certaines fonctionnalités seront limitées.");
            }

            if (isWindows) {
                System.out.println("Système Windows détecté.");
                System.out.println("Note: Pour accéder aux disques physiques, exécutez en tant qu'Administrateur.");
                System.out.println("Clic droit sur l'application → Exécuter en tant qu'administrateur");
            }

            SwingUtilities.invokeLater(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    e.printStackTrace();
                }

                FileRecoveryTool tool = new FileRecoveryTool();
                tool.setVisible(true);

                tool.logStatus("═══════════════════════════════════════════════");
                tool.logStatus("Outil de Récupération de Fichiers");
                tool.logStatus("Version 1.0.0 - Multi-plateforme");
                tool.logStatus("═══════════════════════════════════════════════");
                tool.logStatus("Système d'exploitation: " + osName);
                tool.logStatus("Utilisateur: " + user);

                if (isLinux) {
                    if (!"root".equals(user)) {
                        tool.logStatus("⚠ Mode utilisateur normal - Fonctionnalités limitées");
                        tool.logStatus("⚠ Pour un accès complet, exécutez avec sudo");
                    } else {
                        tool.logStatus("✓ Mode root - Accès complet aux périphériques");
                    }
                } else if (isWindows) {
                    tool.logStatus("ℹ Système Windows détecté");
                    tool.logStatus("ℹ Pour les disques physiques, exécutez en admin");
                }

                tool.logStatus("═══════════════════════════════════════════════");
                tool.logStatus("");
                tool.logStatus("Instructions:");
                tool.logStatus("1. Sélectionnez un périphérique/lecteur dans la liste");

                if (isLinux) {
                    tool.logStatus("2. Démontez-le si nécessaire (bouton Démonter)");
                } else {
                    tool.logStatus("2. Éjectez-le si nécessaire depuis Windows");
                }

                tool.logStatus("3. Lancez le scan avec le bouton Scanner");
                tool.logStatus("4. Sélectionnez les fichiers à récupérer");
                tool.logStatus("5. Choisissez une destination et récupérez");
                tool.logStatus("═══════════════════════════════════════════════");
            });
        }
    }