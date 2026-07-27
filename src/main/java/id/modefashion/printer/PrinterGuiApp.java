package id.modefashion.printer;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;

public class PrinterGuiApp extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(PrinterGuiApp.class);
    private JButton startStopButton;
    private JTextArea logArea;
    private PrintServer printServer;
    private PropertiesConfiguration config;
    private boolean serverRunning = false;

    public PrinterGuiApp() {
        logger.info("Starting PrinterGuiApp");
        setTitle("MPOS Printer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(700, 500);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Menu
        JMenuBar menuBar = new JMenuBar();
        JMenu configMenu = new JMenu("Configuration");
        JMenuItem editConfigItem = new JMenuItem("Edit printer.properties");
        editConfigItem.addActionListener(e -> showConfigDialog());
        configMenu.add(editConfigItem);
        menuBar.add(configMenu);
        setJMenuBar(menuBar);

        // Start/Stop button
        startStopButton = new JButton("Start");
        startStopButton.addActionListener(e -> toggleServer());
        JPanel topPanel = new JPanel();
        topPanel.add(startStopButton);
        add(topPanel, BorderLayout.NORTH);

        // Log area
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);
        DefaultCaret caret = (DefaultCaret) logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        // Redirect logs
        LogbackTextAreaAppender.setTextArea(logArea);

        // Load config
        try {
            config = new PropertiesConfiguration("printer.properties");
        } catch (ConfigurationException e) {
            appendLog("Failed to load printer.properties: " + e.getMessage());
        }
    }

    private void toggleServer() {
        if (!serverRunning) {
            try {
                printServer = new PrintServer(config);
                printServer.start();
                serverRunning = true;
                startStopButton.setText("Stop");
                appendLog("WebSocket server started on port " + config.getInt("printer.port"));
            } catch (Exception ex) {
                appendLog("Failed to start server: " + ex.getMessage());
            }
        } else {
            try {
                printServer.stop();
                serverRunning = false;
                startStopButton.setText("Start");
                appendLog("WebSocket server stopped.");
            } catch (Exception ex) {
                appendLog("Failed to stop server: " + ex.getMessage());
            }
        }
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
        });
    }

    private void showConfigDialog() {
        JDialog dialog = new JDialog(this, "Edit Configuration", true);
        dialog.setSize(400, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));

        // Load current config into fields
        JTextField portField = new JTextField(config.getString("printer.port"));
        JTextField nameField = new JTextField(config.getString("printer.name"));
        JTextField dpiField = new JTextField(config.getString("printer.dpi"));
        JTextField widthField = new JTextField(config.getString("paper.width"));
        JTextField heightField = new JTextField(config.getString("paper.height"));
        JTextField orientationField = new JTextField(config.getString("paper.orientation"));
        JTextField marginLeftField = new JTextField(config.getString("paper.margin.left"));
        JTextField marginRightField = new JTextField(config.getString("paper.margin.right"));
        JTextField marginTopField = new JTextField(config.getString("paper.margin.top"));
        JTextField marginBottomField = new JTextField(config.getString("paper.margin.bottom"));
        JTextField fontFamilyField = new JTextField(config.getString("font.family"));
        JTextField fontSizeField = new JTextField(config.getString("font.size"));
        JTextField fontLineHeightField = new JTextField(config.getString("font.line.height"));
        JTextField escpPrinterNameField = new JTextField(config.getString("escp.printer.name"));
        JTextField escpPitchField = new JTextField(config.getString("escp.pitch"));
        JTextField escpLineSpacingField = new JTextField(config.getString("escp.line.spacing"));
        JTextField escpQueueCapacityField = new JTextField(config.getString("escp.queue.capacity"));
        JTextField escpRetryMaxAttemptsField = new JTextField(config.getString("escp.retry.max.attempts"));
        JTextField escpRetryBackoffField = new JTextField(config.getString("escp.retry.backoff.ms"));

        formPanel.add(new JLabel("Printer Port:"));
        formPanel.add(portField);
        formPanel.add(new JLabel("Printer Name:"));
        formPanel.add(nameField);
        formPanel.add(new JLabel("Printer DPI:"));
        formPanel.add(dpiField);
        formPanel.add(new JLabel("Paper Width:"));
        formPanel.add(widthField);
        formPanel.add(new JLabel("Paper Height:"));
        formPanel.add(heightField);
        formPanel.add(new JLabel("Paper Orientation (0=LANDSCAPE, 1=PORTRAIT):"));
        formPanel.add(orientationField);
        formPanel.add(new JLabel("Margin Left:"));
        formPanel.add(marginLeftField);
        formPanel.add(new JLabel("Margin Right:"));
        formPanel.add(marginRightField);
        formPanel.add(new JLabel("Margin Top:"));
        formPanel.add(marginTopField);
        formPanel.add(new JLabel("Margin Bottom:"));
        formPanel.add(marginBottomField);
        formPanel.add(new JLabel("Font Family:"));
        formPanel.add(fontFamilyField);
        formPanel.add(new JLabel("Font Size:"));
        formPanel.add(fontSizeField);
        formPanel.add(new JLabel("Font Line Height:"));
        formPanel.add(fontLineHeightField);

        JLabel escpSectionLabel = new JLabel("ESC/P (Continuous Form)");
        escpSectionLabel.setFont(escpSectionLabel.getFont().deriveFont(Font.BOLD));
        formPanel.add(escpSectionLabel);
        formPanel.add(new JLabel("ESC/P Printer Name (raw queue):"));
        formPanel.add(escpPrinterNameField);
        formPanel.add(new JLabel("ESC/P Pitch (10=Pica, 12=Elite):"));
        formPanel.add(escpPitchField);
        formPanel.add(new JLabel("ESC/P Line Spacing (n/180 inch):"));
        formPanel.add(escpLineSpacingField);
        formPanel.add(new JLabel("ESC/P Queue Capacity:"));
        formPanel.add(escpQueueCapacityField);
        formPanel.add(new JLabel("ESC/P Retry Max Attempts:"));
        formPanel.add(escpRetryMaxAttemptsField);
        formPanel.add(new JLabel("ESC/P Retry Backoff (ms):"));
        formPanel.add(escpRetryBackoffField);

        dialog.add(new JScrollPane(formPanel), BorderLayout.CENTER);

        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> {
            try {
                config.setProperty("printer.port", portField.getText());
                config.setProperty("printer.name", nameField.getText());
                config.setProperty("printer.dpi", dpiField.getText());
                config.setProperty("paper.width", widthField.getText());
                config.setProperty("paper.height", heightField.getText());
                config.setProperty("paper.orientation", orientationField.getText());
                config.setProperty("paper.margin.left", marginLeftField.getText());
                config.setProperty("paper.margin.right", marginRightField.getText());
                config.setProperty("paper.margin.top", marginTopField.getText());
                config.setProperty("paper.margin.bottom", marginBottomField.getText());
                config.setProperty("font.family", fontFamilyField.getText());
                config.setProperty("font.size", fontSizeField.getText());
                config.setProperty("font.line.height", fontLineHeightField.getText());
                config.setProperty("escp.printer.name", escpPrinterNameField.getText());
                config.setProperty("escp.pitch", escpPitchField.getText());
                config.setProperty("escp.line.spacing", escpLineSpacingField.getText());
                config.setProperty("escp.queue.capacity", escpQueueCapacityField.getText());
                config.setProperty("escp.retry.max.attempts", escpRetryMaxAttemptsField.getText());
                config.setProperty("escp.retry.backoff.ms", escpRetryBackoffField.getText());
                config.save();
                JOptionPane.showMessageDialog(dialog, "Configuration saved. Please restart the server to apply changes.");
                dialog.dispose();
            } catch (ConfigurationException ex) {
                JOptionPane.showMessageDialog(dialog, "Failed to save configuration: " + ex.getMessage());
            }
        });
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(saveButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PrinterGuiApp app = new PrinterGuiApp();
            app.setVisible(true);
        });
    }
} 