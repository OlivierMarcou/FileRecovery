package com.filerecovery.view;

import com.filerecovery.model.*;
import com.filerecovery.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Implémentation Swing de la vue principale
 */
public class MainViewImpl extends JFrame implements MainView {

    // Composants UI
    private JComboBox<BlockDevice> deviceSelector;
    private JTree fileTree;
    private DefaultMutableTreeNode rootNode;
    private JTextArea statusArea;
    private JLabel previewLabel;
    private JProgressBar progressBar;
    private JButton scanButton, recoverButton, unmountButton, refreshButton;
    private JCheckBox rawAccessCheckbox;

    // Callbacks vers le contrôleur
    private Runnable refreshCallback;
    private Consumer<BlockDevice> deviceSelectedCallback;
    private Consumer<Boolean> startScanCallback;
    private Runnable unmountCallback;
    private RecoverFilesCallback recoverCallback;

    // Données
    private Map<String, DefaultMutableTreeNode> categoryNodes;

    public MainViewImpl() {
        super("Outil de Récupération de Fichiers - Architecture MVC");
        this.categoryNodes = new HashMap<>();
    }

    @Override
    public void initialize() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 850);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        mainPanel.add(createTopPanel(), BorderLayout.NORTH);
        mainPanel.add(createCenterPanel(), BorderLayout.CENTER);
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);

        add(mainPanel);
        setVisible(true);
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        String osName = OSDetector.getOSName();
        boolean isWindows = OSDetector.isWindows();

        String title = isWindows ? "Sélection du disque physique / volume" : "Sélection du périphérique bloc";
        panel.setBorder(BorderFactory.createTitledBorder(title));

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        JLabel label = new JLabel("Périphérique:");
        deviceSelector = new JComboBox<>();
        deviceSelector.setPreferredSize(new Dimension(450, 30));
        deviceSelector.addActionListener(e -> {
            BlockDevice selected = (BlockDevice) deviceSelector.getSelectedItem();
            if (selected != null && deviceSelectedCallback != null) {
                deviceSelectedCallback.accept(selected);
            }
        });

        scanButton = new JButton("Scanner le périphérique");
        scanButton.setPreferredSize(new Dimension(180, 30));
        scanButton.addActionListener(e -> {
            if (startScanCallback != null) {
                boolean rawMode = rawAccessCheckbox != null && rawAccessCheckbox.isSelected();
                startScanCallback.accept(rawMode);
            }
        });

        unmountButton = new JButton(isWindows ? "Éjecter" : "Démonter");
        unmountButton.setPreferredSize(new Dimension(150, 30));
        unmountButton.addActionListener(e -> {
            if (unmountCallback != null) {
                unmountCallback.run();
            }
        });

        refreshButton = new JButton("⟳ Rafraîchir");
        refreshButton.addActionListener(e -> {
            if (refreshCallback != null) {
                refreshCallback.run();
            }
        });

        controlPanel.add(label);
        controlPanel.add(deviceSelector);
        controlPanel.add(unmountButton);
        controlPanel.add(scanButton);
        controlPanel.add(refreshButton);

        if (!isWindows) {
            rawAccessCheckbox = new JCheckBox("Accès direct (root requis)", true);
            rawAccessCheckbox.setToolTipText("Mode file carving - Linux uniquement");
            controlPanel.add(rawAccessCheckbox);
        }

        panel.add(controlPanel, BorderLayout.CENTER);

        if (isWindows) {
            JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JLabel infoLabel = new JLabel("ℹ Exécutez en Administrateur pour accéder aux disques physiques");
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
        if (file.getCategory().equals("Images")) {
            BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();

            Random rand = new Random(file.getName().hashCode());
            g.setColor(new Color(
                    100 + rand.nextInt(156),
                    100 + rand.nextInt(156),
                    100 + rand.nextInt(156)
            ));
            g.fillRect(0, 0, 400, 400);

            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 16));
            FontMetrics fm = g.getFontMetrics();
            String text = file.getName();
            int x = (400 - fm.stringWidth(text)) / 2;
            g.drawString(text, x, 200);

            g.setFont(new Font("Arial", Font.PLAIN, 12));
            g.drawString("Offset: " + FormatUtils.formatOffset(file.getOffset()), 20, 380);
            g.dispose();

            ImageIcon icon = new ImageIcon(img.getScaledInstance(400, 400, Image.SCALE_SMOOTH));
            previewLabel.setIcon(icon);
            previewLabel.setText("");
        } else {
            previewLabel.setIcon(null);
            previewLabel.setText(String.format(
                    "<html><center>%s<br><br>" +
                            "<b>%s</b><br><br>" +
                            "Type: %s<br>" +
                            "Taille: %s<br>" +
                            "Offset: %s<br>" +
                            "État: %s</center></html>",
                    file.getIcon(),
                    file.getName(),
                    file.getType(),
                    FormatUtils.formatSize(file.getSize()),
                    FormatUtils.formatOffset(file.getOffset()),
                    file.getState().getDisplayName()
            ));
        }
    }

    private void recoverSelectedFiles() {
        TreePath[] paths = fileTree.getSelectionPaths();
        if (paths == null || paths.length == 0) {
            showError("Veuillez sélectionner au moins un fichier ou dossier à récupérer.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choisir le dossier de destination");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setCurrentDirectory(new File(System.getProperty("user.home")));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File destination = chooser.getSelectedFile();
            List<RecoveredFile> files = collectSelectedFiles(paths);

            if (recoverCallback != null) {
                recoverCallback.recover(files, destination.getAbsolutePath());
            }
        }
    }

    private List<RecoveredFile> collectSelectedFiles(TreePath[] paths) {
        List<RecoveredFile> files = new ArrayList<>();

        for (TreePath path : paths) {
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

        return files;
    }

    @Override
    public void updateDeviceList(List<BlockDevice> devices) {
        SwingUtilities.invokeLater(() -> {
            deviceSelector.removeAllItems();
            devices.forEach(deviceSelector::addItem);
        });
    }

    @Override
    public void addRecoveredFile(RecoveredFile file) {
        SwingUtilities.invokeLater(() -> {
            String category = file.getCategory();
            DefaultMutableTreeNode categoryNode = categoryNodes.get(category);

            if (categoryNode == null) {
                categoryNode = new DefaultMutableTreeNode(category);
                rootNode.add(categoryNode);
                categoryNodes.put(category, categoryNode);
            }

            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(file);
            categoryNode.add(fileNode);

            ((DefaultTreeModel) fileTree.getModel()).reload();
            expandTree(fileTree, 0, 2);
        });
    }

    @Override
    public void updateProgress(int percentage, String message) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(percentage);
            progressBar.setString(message);
        });
    }

    @Override
    public void showMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            statusArea.append(String.format("[%s] %s\n",
                    FormatUtils.formatTimestamp(System.currentTimeMillis()), message));
            statusArea.setCaretPosition(statusArea.getDocument().getLength());
        });
    }

    @Override
    public void showError(String error) {
        SwingUtilities.invokeLater(() -> {
            showMessage("✗ ERREUR: " + error);
            JOptionPane.showMessageDialog(this, error, "Erreur", JOptionPane.ERROR_MESSAGE);
        });
    }

    @Override
    public void showScanComplete(int filesFound) {
        SwingUtilities.invokeLater(() -> {
            showMessage("✓ Scan terminé. " + filesFound + " fichiers trouvés.");
            recoverButton.setEnabled(filesFound > 0);
        });
    }

    @Override
    public void showRecoveryComplete(int filesRecovered, String destination) {
        SwingUtilities.invokeLater(() -> {
            showMessage("✓ Récupération terminée: " + filesRecovered + " fichiers");
            JOptionPane.showMessageDialog(this,
                    String.format("Récupération terminée!\n%d fichiers sauvegardés dans:\n%s",
                            filesRecovered, destination),
                    "Succès", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override
    public void clearResults() {
        SwingUtilities.invokeLater(() -> {
            rootNode.removeAllChildren();
            categoryNodes.clear();
            ((DefaultTreeModel) fileTree.getModel()).reload();
            previewLabel.setIcon(null);
            previewLabel.setText("Sélectionnez un fichier pour prévisualiser");
        });
    }

    @Override
    public void setScanningState(boolean scanning) {
        SwingUtilities.invokeLater(() -> {
            scanButton.setEnabled(!scanning);
            recoverButton.setEnabled(!scanning);
            deviceSelector.setEnabled(!scanning);
        });
    }

    @Override
    public boolean confirmScan(BlockDevice device) {
        if (OSDetector.isWindows() && device.getPath().startsWith("\\\\.\\PHYSICALDRIVE")) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Vous êtes sur le point de scanner un disque physique sous Windows.\n\n" +
                            "IMPORTANT:\n" +
                            "- Assurez-vous que TOUTES les partitions du disque sont démontées\n" +
                            "- Fermez tous les programmes qui pourraient accéder au disque\n" +
                            "- L'application doit être exécutée en Administrateur\n\n" +
                            "Voulez-vous continuer ?",
                    "Confirmation - Scan disque physique",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            return result == JOptionPane.YES_OPTION;
        }
        return true;
    }

    @Override
    public void showUnmountInstructions(BlockDevice device) {
        if (OSDetector.isWindows()) {
            JOptionPane.showMessageDialog(this,
                    "Sous Windows, utilisez:\n" +
                            "1. Explorateur de fichiers → Clic droit sur le lecteur → Éjecter\n" +
                            "2. Ou Gestion des disques (diskmgmt.msc)\n" +
                            "3. Ou commande: mountvol " + device.getPath() + " /D",
                    "Démontage Windows", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Démontage en cours...\nCela peut prendre quelques secondes.",
                    "Démontage", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    @Override
    public void onRefreshDevices(Runnable callback) {
        this.refreshCallback = callback;
    }

    @Override
    public void onDeviceSelected(Consumer<BlockDevice> callback) {
        this.deviceSelectedCallback = callback;
    }

    @Override
    public void onStartScan(Consumer<Boolean> callback) {
        this.startScanCallback = callback;
    }

    @Override
    public void onUnmountDevice(Runnable callback) {
        this.unmountCallback = callback;
    }

    @Override
    public void onRecoverFiles(RecoverFilesCallback callback) {
        this.recoverCallback = callback;
    }

    private void expandTree(JTree tree, int startingIndex, int rowCount) {
        for (int i = startingIndex; i < startingIndex + rowCount; i++) {
            tree.expandRow(i);
        }
        if (tree.getRowCount() != startingIndex + rowCount) {
            expandTree(tree, startingIndex + rowCount, tree.getRowCount() - startingIndex - rowCount);
        }
    }

    // Renderer personnalisé pour l'arbre
    private static class FileTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObj = node.getUserObject();

            if (userObj instanceof RecoveredFile file) {
                setText(file.getIcon() + " " + file.toString());

                switch (file.getState()) {
                    case RECOVERABLE -> setForeground(new Color(0, 128, 0));
                    case DAMAGED -> setForeground(new Color(200, 100, 0));
                    case CORRUPTED -> setForeground(Color.RED);
                }
            } else if (userObj instanceof String category) {
                String icon = switch (category) {
                    case "Images" -> "🖼️";
                    case "Vidéos" -> "🎬";
                    case "Documents" -> "📄";
                    case "Audio" -> "🎵";
                    case "Archives" -> "📦";
                    case "Partitions" -> "💾";
                    default -> "📁";
                };
                setText(icon + " " + category + " (" + node.getChildCount() + ")");
                setFont(getFont().deriveFont(Font.BOLD));
            }

            return this;
        }
    }
}