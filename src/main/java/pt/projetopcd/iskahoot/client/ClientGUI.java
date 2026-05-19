package pt.projetopcd.iskahoot.client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableModel;

import pt.projetopcd.iskahoot.model.Player;
import pt.projetopcd.iskahoot.model.Question;
import pt.projetopcd.iskahoot.model.QuestionLoader;

public class ClientGUI {

    private Player player;
    private JFrame frame;
    private JPanel mainPanel;
    private CardLayout cardLayout;

    private JLabel playerInfoLabel;
    private JLabel timerLabel;
    private JButton[] answerButtons = new JButton[4];
    private JLabel questionLabel;

    public static void main(String[] args) {

    SwingUtilities.invokeLater(() -> {

        ClientGUI gui = new ClientGUI();

        gui.buildFrame();

        gui.cardLayout.show(gui.mainPanel, "GAME");

        gui.frame.setVisible(true);

        List<Question> questions = QuestionLoader.loadQuestions();

        gui.showQuestion(questions.get(0));
    });
}

    public void showGameScreen(Player player) {
        this.player = player;
        SwingUtilities.invokeLater(() -> {
            if (frame == null) buildFrame();
            playerInfoLabel.setText(
                    "Jogador: " + player.getName() +
                            " (ID: " + player.getId() + ")  |  Equipa: " + player.getTeam()
            );
            cardLayout.show(mainPanel, "GAME");
            frame.setVisible(true);
        });
    }

    /**
     * Mostra o ecrã de login. Útil quando a GUI é iniciada de forma autónoma.
     */
    public void showLoginScreen() {
        SwingUtilities.invokeLater(() -> {
            buildFrame();
            cardLayout.show(mainPanel, "LOGIN");
            frame.setVisible(true);
        });
    }

    // ------------------------------------------------------------------
    // Construção da janela principal
    // ------------------------------------------------------------------
    private void buildFrame() {
        UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());

        frame = new JFrame("isKahoot! Client");
        frame.setSize(800, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.add(buildLoginPanel(), "LOGIN");
        mainPanel.add(buildGamePanel(),  "GAME");

        frame.add(mainPanel);
    }

    // ------------------------------------------------------------------
    // Ecrã de Login
    // ------------------------------------------------------------------
    private JPanel buildLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 60, 20, 60));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Título
        JLabel title = new JLabel("isKahoot!", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(28f));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(title, gbc);

        gbc.gridwidth = 1;

        // Campo nome
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Nome do Jogador:"), gbc);
        JTextField nameField = new JTextField(20);
        gbc.gridx = 1;
        panel.add(nameField, gbc);

        // Campo equipa
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Nome da Equipa:"), gbc);
        JTextField teamField = new JTextField(20);
        gbc.gridx = 1;
        panel.add(teamField, gbc);

        // Botão ligar
        JButton connectButton = new JButton("Ligar ao Servidor");
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        panel.add(connectButton, gbc);

        // Label de estado
        JLabel statusLabel = new JLabel("", SwingConstants.CENTER);
        gbc.gridy = 4;
        panel.add(statusLabel, gbc);

        connectButton.addActionListener(e -> {
            String name = nameField.getText().trim();
            String team = teamField.getText().trim();
            if (name.isEmpty() || team.isEmpty()) {
                statusLabel.setText("Preenche o nome e a equipa.");
                return;
            }
            connectButton.setEnabled(false);
            statusLabel.setText("A ligar ao servidor...");

            // Corre o Client numa thread separada para não bloquear a EDT
            new Thread(() -> {
                Client client = new Client(this);
                try {
                    client.connectToServer();
                    client.createPlayer(name,team);
                    client.sendPlayers();
                    // showGameScreen() é chamado dentro de sendPlayer()
                    // após receber o Player com id do servidor
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Erro ao ligar: " + ex.getMessage());
                        connectButton.setEnabled(true);
                    });
                }
            }).start();
        });

        return panel;
    }

    // ------------------------------------------------------------------
    // Ecrã de Jogo
    // ------------------------------------------------------------------
    private JPanel buildGamePanel() {
        JPanel wrapper = new JPanel(new BorderLayout());

        // Barra superior com info do jogador
        playerInfoLabel = new JLabel("", SwingConstants.CENTER);
        playerInfoLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        wrapper.add(playerInfoLabel, BorderLayout.NORTH);

        // Painel esquerdo — botões de resposta
        JPanel leftPanel = new JPanel(new GridLayout(5, 1, 5, 5));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Perguntas"));
        questionLabel = new JLabel("Pergunta aparece aqui", SwingConstants.CENTER);
        questionLabel.setFont(questionLabel.getFont().deriveFont(20f));
        leftPanel.add(questionLabel);
        for (int i = 0; i < 4; i++) {

            JButton button = new JButton("Option " + (i + 1));

            answerButtons[i] = button;

             leftPanel.add(button);
        }

        // Painel central — título + timer
        JPanel centerPanel = new JPanel(new GridLayout(3, 1));

        JLabel kahootLabel = new JLabel("Kahoot!", SwingConstants.CENTER);  

        timerLabel = new JLabel("Timer: --", SwingConstants.CENTER);

        centerPanel.add(kahootLabel);
        centerPanel.add(timerLabel);

        // Painel direito — tabela de scores
        String[] columns = {"Team", "Score", "Last round points"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        JTable table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Scores"));

        // Split panes
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerPanel, scrollPane);
        rightSplit.setResizeWeight(0.6);
        rightSplit.setDividerSize(0);
        rightSplit.setBorder(null);
        rightSplit.setEnabled(false);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightSplit);
        mainSplit.setResizeWeight(0.3);
        mainSplit.setDividerSize(0);
        mainSplit.setBorder(null);
        mainSplit.setEnabled(false);

        SwingUtilities.invokeLater(() -> {
            mainSplit.setDividerLocation(0.58);
            rightSplit.setDividerLocation(0.16);
        });

        removeDividerBorder(mainSplit);
        removeDividerBorder(rightSplit);

        wrapper.add(mainSplit, BorderLayout.CENTER);
        return wrapper;
    }

    private void removeDividerBorder(JSplitPane pane) {
        pane.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override
                    public void setBorder(Border b) {}
                };
            }
        });
    }
    public void showQuestion(Question question) {

    SwingUtilities.invokeLater(() -> {

        questionLabel.setText(question.getQuestion());

        List<String> options = question.getOptions();

        for (int i = 0; i < answerButtons.length; i++) {

            answerButtons[i].setText(options.get(i));
        }

    });
    }
}
