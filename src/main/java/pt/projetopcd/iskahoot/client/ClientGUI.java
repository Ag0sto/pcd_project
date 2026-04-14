package pt.projetopcd.iskahoot.client;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableModel;

public class ClientGUI {

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> new ClientGUI().createAndShowGUI());
    }

    public void createAndShowGUI() {

        UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());

        JFrame frame = new JFrame("Kahoot Client");
        frame.setSize(800, 300);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Left panel with question buttons
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new GridLayout(5, 1,5,5));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Perguntas"));

        for (int i = 1; i <= 4; i++) {
            JButton questionButton = new JButton(String.valueOf(i));
            leftPanel.add(questionButton);
        }

        // Center panel with game info
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new GridLayout(2,1));

        JLabel kahootLabel = new JLabel("Kahoot!");
        kahootLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel timerLabel = new JLabel("Timer: ");
        timerLabel.setHorizontalAlignment(SwingConstants.CENTER);


        centerPanel.add(kahootLabel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        centerPanel.add(timerLabel);

        // Right panel with scores
        String[] columns = {"Team", "Score", "Last round points"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        JTable table = new JTable(model);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Scores"));

        // Add panels to frame
        // Split 1: center + right
        JSplitPane rightSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                centerPanel,
                scrollPane
        );
        rightSplit.setResizeWeight(0.6);
        rightSplit.setDividerSize(0);
        rightSplit.setBorder(null);
        rightSplit.setEnabled(false);

        // Split 2: left + (center + right)
        JSplitPane mainSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftPanel,
                rightSplit
        );
        mainSplit.setResizeWeight(0.3);
        mainSplit.setDividerSize(0);
        mainSplit.setBorder(null);
        mainSplit.setEnabled(false);

        frame.add(mainSplit, BorderLayout.CENTER);

        // Set initial divider positions
        SwingUtilities.invokeLater(() -> {
            mainSplit.setDividerLocation(0.58);
            rightSplit.setDividerLocation(0.16);
        });

        // remover completamente a UI do divider
        mainSplit.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override
                    public void setBorder(Border b) {}
                };
            }
        });

        rightSplit.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override
                    public void setBorder(Border b) {}
                };
            }
        });

        frame.setVisible(true);

        new Thread(() -> {
            int timer = 20;
            while (timer >= 0) {
                timerLabel.setText("Timer: " + timer);
                timer--;
                try {Thread.sleep(1000);} catch (InterruptedException e) {}
            }
        }).start();
    }
}
