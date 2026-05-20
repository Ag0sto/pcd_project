package pt.projetopcd.iskahoot.client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import pt.projetopcd.iskahoot.model.Message;
import pt.projetopcd.iskahoot.model.Player;

/**
 * Interface gráfica do cliente IsKahoot.
 *
 * Ecrãs (CardLayout): LOGIN - campos: gameId, equipa, username; botão ligar
 * WAITING - mensagem de espera até o jogo começar GAME - pergunta + opções +
 * timer + placar RESULT - resultado da ronda (resposta certa, pontos) END -
 * ecrã de fim de jogo com vencedor
 */
public class ClientGUI {

    // -------------------------------------------------------
    // Componentes principais
    // -------------------------------------------------------
    private JFrame frame;
    private JPanel mainPanel;
    private CardLayout cardLayout;

    // LOGIN
    private JTextField loginGameIdField, loginTeamField, loginUsernameField;
    private JLabel loginStatusLabel;
    private JButton loginConnectBtn;

    // WAITING
    private JLabel waitingLabel;

    // GAME
    private JLabel questionLabel;
    private JLabel questionInfoLabel;  // "Pergunta 2/7 [EQUIPA] (5 pts)"
    private JLabel timerLabel;
    private JButton[] answerButtons;
    private JLabel playerInfoLabel;
    private DefaultTableModel scoreTableModel;

    // RESULT
    private JLabel resultTitleLabel;
    private JLabel resultCorrectLabel;
    private JTextArea resultScoreArea;

    // END
    private JLabel endWinnerLabel;
    private JTextArea endScoreArea;

    // -------------------------------------------------------
    // Estado
    // -------------------------------------------------------
    private Player me;
    private Client client;
    private javax.swing.Timer countdownTimer;
    private int timeLeft;
    private Message.QuestionMsg currentQuestion;
    private volatile boolean answered;

    // -------------------------------------------------------
    // Construção
    // -------------------------------------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientGUI().showLoginScreen());
    }

    /**
     * Mostra o ecrã de login com campos vazios.
     */
    public void showLoginScreen() {
        SwingUtilities.invokeLater(() -> {
            buildFrame();
            cardLayout.show(mainPanel, "LOGIN");
            frame.setVisible(true);
        });
    }

    /**
     * Preenche os campos de login com valores vindos dos argumentos (modo cmd).
     */
    public void showLoginScreen(String gameId, String team, String username) {
        SwingUtilities.invokeLater(() -> {
            buildFrame();
            loginGameIdField.setText(gameId);
            loginTeamField.setText(team);
            loginUsernameField.setText(username);
            cardLayout.show(mainPanel, "LOGIN");
            frame.setVisible(true);
        });
    }

    public void showGameScreen(Player player) {
        this.me = player;
        SwingUtilities.invokeLater(() -> {
            playerInfoLabel.setText(
                    "Jogador: " + player.getName() + "  |  Equipa: " + player.getTeam()
            );
            cardLayout.show(mainPanel, "WAITING");
            frame.setVisible(true);
        });
    }

    public void showWaiting(String msg) {
        SwingUtilities.invokeLater(() -> {
            waitingLabel.setText("<html><center>" + msg + "</center></html>");
            cardLayout.show(mainPanel, "WAITING");
        });
    }

    public void showError(String msg) {
        SwingUtilities.invokeLater(() -> {
            loginStatusLabel.setText("<html><font color='red'>" + msg + "</font></html>");
            if (loginConnectBtn != null) {
                loginConnectBtn.setEnabled(true);
            }
        });
    }

    public void showQuestion(Message.QuestionMsg q) {
        SwingUtilities.invokeLater(() -> {
            currentQuestion = q;
            answered = false;

            // Atualiza pergunta e informação
            questionLabel.setText("<html><center>" + q.questionText + "</center></html>");
            questionInfoLabel.setText("Pergunta " + q.questionNumber + "/" + q.totalQuestions
                    + (q.isTeamRound ? "  [RONDA DE EQUIPA]" : "  [INDIVIDUAL]")
                    + "  •  " + q.points + " pts");

            // Botões de resposta
            List<String> opts = q.options;
            for (int i = 0; i < answerButtons.length; i++) {
                if (i < opts.size()) {
                    answerButtons[i].setText("<html>" + opts.get(i) + "</html>");
                    answerButtons[i].setEnabled(true);
                    answerButtons[i].setBackground(getOptionColor(i));
                } else {
                    answerButtons[i].setText("");
                    answerButtons[i].setEnabled(false);
                }
            }

            // Timer
            startCountdown(q.timeLimitSeconds);

            cardLayout.show(mainPanel, "GAME");
        });
    }

    public void showRoundResult(Message.RoundResult r) {
        SwingUtilities.invokeLater(() -> {
            stopCountdown();

            // Destaca a opção correta nos botões
            if (currentQuestion != null) {
                for (int i = 0; i < answerButtons.length; i++) {
                    if (i < currentQuestion.options.size()) {
                        answerButtons[i].setEnabled(false);
                        answerButtons[i].setBackground(
                                i == r.correctOption ? new Color(0x4CAF50) : new Color(0xF44336)
                        );
                    }
                }
            }

            // Mostra no ecrã de resultado
            resultTitleLabel.setText(answered
                    ? "Fim da Ronda!"
                    : "Tempo esgotado!");
            resultCorrectLabel.setText("Resposta correta: "
                    + (currentQuestion != null ? currentQuestion.options.get(r.correctOption) : r.correctOption));

            StringBuilder sb = new StringBuilder("Pontuações:\n");
            r.teamScores.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> {
                        int round = r.roundPoints.getOrDefault(e.getKey(), 0);
                        sb.append(String.format("  %-15s  Total: %4d  (+%d esta ronda)%n",
                                e.getKey(), e.getValue(), round));
                    });
            resultScoreArea.setText(sb.toString());

            // Atualiza tabela de scores
            updateScoreTable(r.teamScores, r.roundPoints);

            cardLayout.show(mainPanel, "RESULT");
        });
    }

    public void showGameEnd(Message.RoundResult r) {
        SwingUtilities.invokeLater(() -> {
            stopCountdown();

            endWinnerLabel.setText("🏆 Vencedor: " + r.winnerTeam + " 🏆");

            StringBuilder sb = new StringBuilder("Classificação final:\n\n");
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(r.teamScores.entrySet());
            sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
            int rank = 1;
            for (Map.Entry<String, Integer> e : sorted) {
                sb.append(String.format("  %d. %-15s  %d pts%n", rank++, e.getKey(), e.getValue()));
            }
            endScoreArea.setText(sb.toString());

            cardLayout.show(mainPanel, "END");
        });
    }

    // -------------------------------------------------------
    // Timer
    // -------------------------------------------------------
    private void startCountdown(int seconds) {
        stopCountdown();
        timeLeft = seconds;
        timerLabel.setText("⏱ " + timeLeft + "s");

        countdownTimer = new javax.swing.Timer(1000, e -> {
            timeLeft--;
            timerLabel.setText("⏱ " + timeLeft + "s");
            timerLabel.setForeground(timeLeft <= 5 ? Color.RED : Color.BLACK);
            if (timeLeft <= 0) {
                stopCountdown();
            }
        });
        countdownTimer.start();
    }

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
    }

    // -------------------------------------------------------
    // Construção da frame
    // -------------------------------------------------------
    private void buildFrame() {
        if (frame != null) {
            return;
        }

        frame = new JFrame("IsKahoot!");
        frame.setSize(900, 550);
        frame.setMinimumSize(new Dimension(700, 400));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        mainPanel.add(buildLoginPanel(), "LOGIN");
        mainPanel.add(buildWaitingPanel(), "WAITING");
        mainPanel.add(buildGamePanel(), "GAME");
        mainPanel.add(buildResultPanel(), "RESULT");
        mainPanel.add(buildEndPanel(), "END");

        frame.add(mainPanel);
    }

    // -------------------------------------------------------
    // LOGIN
    // -------------------------------------------------------
    private JPanel buildLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(30, 80, 30, 80));
        panel.setBackground(new Color(0x2C3E50));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 8, 8, 8);
        g.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("IsKahoot!", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 36));
        title.setForeground(Color.WHITE);
        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 2;
        panel.add(title, g);

        g.gridwidth = 1;
        Font labelFont = new Font("SansSerif", Font.PLAIN, 14);
        Color labelColor = new Color(0xECF0F1);

        JLabel l1 = new JLabel("Código do Jogo:");
        l1.setFont(labelFont);
        l1.setForeground(labelColor);
        g.gridx = 0;
        g.gridy = 1;
        panel.add(l1, g);
        loginGameIdField = new JTextField(20);
        g.gridx = 1;
        panel.add(loginGameIdField, g);

        JLabel l2 = new JLabel("Equipa:");
        l2.setFont(labelFont);
        l2.setForeground(labelColor);
        g.gridx = 0;
        g.gridy = 2;
        panel.add(l2, g);
        loginTeamField = new JTextField(20);
        g.gridx = 1;
        panel.add(loginTeamField, g);

        JLabel l3 = new JLabel("Nome do Jogador:");
        l3.setFont(labelFont);
        l3.setForeground(labelColor);
        g.gridx = 0;
        g.gridy = 3;
        panel.add(l3, g);
        loginUsernameField = new JTextField(20);
        g.gridx = 1;
        panel.add(loginUsernameField, g);

        loginConnectBtn = new JButton("Entrar no Jogo");
        loginConnectBtn.setFont(new Font("SansSerif", Font.BOLD, 15));
        loginConnectBtn.setBackground(new Color(0x27AE60));
        loginConnectBtn.setForeground(Color.WHITE);
        loginConnectBtn.setFocusPainted(false);
        g.gridx = 0;
        g.gridy = 4;
        g.gridwidth = 2;
        panel.add(loginConnectBtn, g);

        loginStatusLabel = new JLabel("", SwingConstants.CENTER);
        loginStatusLabel.setForeground(new Color(0xE74C3C));
        g.gridy = 5;
        panel.add(loginStatusLabel, g);

        loginConnectBtn.addActionListener(e -> {
            String gameId = loginGameIdField.getText().trim();
            String team = loginTeamField.getText().trim();
            String username = loginUsernameField.getText().trim();

            if (gameId.isEmpty() || team.isEmpty() || username.isEmpty()) {
                loginStatusLabel.setText("Preenche todos os campos.");
                return;
            }
            loginConnectBtn.setEnabled(false);
            loginStatusLabel.setText("A ligar...");

            client = new Client(this);
            new Thread(() -> client.connect(gameId, team, username)).start();
        });

        return panel;
    }

    // -------------------------------------------------------
    // WAITING
    // -------------------------------------------------------
    private JPanel buildWaitingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(0x2980B9));

        waitingLabel = new JLabel("A aguardar os outros jogadores...", SwingConstants.CENTER);
        waitingLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        waitingLabel.setForeground(Color.WHITE);
        panel.add(waitingLabel, BorderLayout.CENTER);

        playerInfoLabel = new JLabel("", SwingConstants.CENTER);
        playerInfoLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        playerInfoLabel.setForeground(new Color(0xD6EAF8));
        panel.add(playerInfoLabel, BorderLayout.SOUTH);

        return panel;
    }

    // -------------------------------------------------------
    // GAME
    // -------------------------------------------------------
    private JPanel buildGamePanel() {
        JPanel wrapper = new JPanel(new BorderLayout(5, 5));
        wrapper.setBackground(new Color(0x1A252F));

        // Barra superior: info jogador + tipo de ronda
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(0x2C3E50));
        topBar.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        playerInfoLabel = new JLabel("", SwingConstants.LEFT);
        playerInfoLabel.setForeground(new Color(0xECF0F1));
        questionInfoLabel = new JLabel("", SwingConstants.RIGHT);
        questionInfoLabel.setForeground(new Color(0xF39C12));
        topBar.add(playerInfoLabel, BorderLayout.WEST);
        topBar.add(questionInfoLabel, BorderLayout.EAST);
        wrapper.add(topBar, BorderLayout.NORTH);

        // Centro: pergunta + timer
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(new Color(0x1A252F));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 10, 30));

        questionLabel = new JLabel("", SwingConstants.CENTER);
        questionLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        questionLabel.setForeground(Color.WHITE);
        centerPanel.add(questionLabel, BorderLayout.CENTER);

        timerLabel = new JLabel("⏱ 30s", SwingConstants.CENTER);
        timerLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        timerLabel.setForeground(Color.WHITE);
        centerPanel.add(timerLabel, BorderLayout.SOUTH);

        // Botões de resposta (4)
        JPanel buttonsPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));
        buttonsPanel.setBackground(new Color(0x1A252F));
        answerButtons = new JButton[4];
        for (int i = 0; i < 4; i++) {
            JButton btn = new JButton();
            btn.setFont(new Font("SansSerif", Font.BOLD, 15));
            btn.setForeground(Color.WHITE);
            btn.setFocusPainted(false);
            btn.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            btn.setOpaque(true);
            final int idx = i;
            btn.addActionListener(e -> onAnswer(idx));
            answerButtons[i] = btn;
            buttonsPanel.add(btn);
        }

        // Tabela de scores à direita
        String[] cols = {"Equipa", "Pontos"};
        scoreTableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable scoreTable = new JTable(scoreTableModel);
        scoreTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        scoreTable.setRowHeight(24);
        JScrollPane scoreScroll = new JScrollPane(scoreTable);
        scoreScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0x34495E)),
                "Pontuações", 0, 0,
                new Font("SansSerif", Font.BOLD, 12), Color.WHITE));
        scoreScroll.setBackground(new Color(0x1A252F));
        scoreScroll.setPreferredSize(new Dimension(200, 0));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildCenter(centerPanel, buttonsPanel), scoreScroll);
        split.setResizeWeight(0.75);
        split.setDividerSize(4);
        split.setBorder(null);
        wrapper.add(split, BorderLayout.CENTER);

        return wrapper;
    }

    private JPanel buildCenter(JPanel center, JPanel buttons) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(0x1A252F));
        p.add(center, BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    private void onAnswer(int idx) {
        if (answered) {
            return;
        }
        answered = true;
        // Desativa todos os botões
        for (JButton b : answerButtons) {
            b.setEnabled(false);
        }
        answerButtons[idx].setBackground(new Color(0xF39C12)); // laranja = selecionado
        // Envia ao servidor
        if (client != null) {
            client.sendAnswer(idx);
        }
    }

    private Color getOptionColor(int i) {
        Color[] colors = {
            new Color(0xE74C3C), // vermelho
            new Color(0x2980B9), // azul
            new Color(0xF39C12), // laranja
            new Color(0x27AE60) // verde
        };
        return colors[i % colors.length];
    }

    private void updateScoreTable(Map<String, Integer> totals, Map<String, Integer> round) {
        SwingUtilities.invokeLater(() -> {
            scoreTableModel.setRowCount(0);
            totals.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> scoreTableModel.addRow(new Object[]{
                e.getKey(), e.getValue()
            }));
        });
    }

    // -------------------------------------------------------
    // RESULT
    // -------------------------------------------------------
    private JPanel buildResultPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(0x1A252F));
        panel.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        resultTitleLabel = new JLabel("Fim da Ronda!", SwingConstants.CENTER);
        resultTitleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        resultTitleLabel.setForeground(Color.WHITE);

        resultCorrectLabel = new JLabel("", SwingConstants.CENTER);
        resultCorrectLabel.setFont(new Font("SansSerif", Font.PLAIN, 18));
        resultCorrectLabel.setForeground(new Color(0x2ECC71));

        JPanel top = new JPanel(new GridLayout(2, 1, 5, 5));
        top.setBackground(new Color(0x1A252F));
        top.add(resultTitleLabel);
        top.add(resultCorrectLabel);

        resultScoreArea = new JTextArea();
        resultScoreArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        resultScoreArea.setEditable(false);
        resultScoreArea.setBackground(new Color(0x2C3E50));
        resultScoreArea.setForeground(Color.WHITE);
        resultScoreArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(resultScoreArea), BorderLayout.CENTER);

        JLabel hint = new JLabel("A próxima pergunta começa em breve...", SwingConstants.CENTER);
        hint.setForeground(new Color(0x95A5A6));
        panel.add(hint, BorderLayout.SOUTH);

        return panel;
    }

    // -------------------------------------------------------
    // END
    // -------------------------------------------------------
    private JPanel buildEndPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(0x1A252F));
        panel.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        endWinnerLabel = new JLabel("", SwingConstants.CENTER);
        endWinnerLabel.setFont(new Font("SansSerif", Font.BOLD, 30));
        endWinnerLabel.setForeground(new Color(0xF1C40F));

        endScoreArea = new JTextArea();
        endScoreArea.setFont(new Font("Monospaced", Font.PLAIN, 15));
        endScoreArea.setEditable(false);
        endScoreArea.setBackground(new Color(0x2C3E50));
        endScoreArea.setForeground(Color.WHITE);
        endScoreArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton closeBtn = new JButton("Fechar");
        closeBtn.addActionListener(e -> System.exit(0));

        panel.add(endWinnerLabel, BorderLayout.NORTH);
        panel.add(new JScrollPane(endScoreArea), BorderLayout.CENTER);
        panel.add(closeBtn, BorderLayout.SOUTH);

        return panel;
    }
}
