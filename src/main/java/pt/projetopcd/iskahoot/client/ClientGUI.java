package pt.projetopcd.iskahoot.client;

import pt.projetopcd.iskahoot.model.Message;
import pt.projetopcd.iskahoot.model.Player;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * GUI IsKahoot — nova arquitetura de pontuação individual + equipa.
 *
 * Ecrãs: LOGIN → WAITING → GAME → RESULT → END
 *
 * Scoreboard durante o jogo: tabela de equipas (ranking) + tabela de jogadores.
 * Ecrã RESULT: placar detalhado com jogadores (pontos, bónus, acerto) + ranking equipas.
 * Ecrã END: classificação final individual + por equipa.
 */
public class ClientGUI {

    // ── Paleta ────────────────────────────────────────────────
    private static final Color BG_DARK   = new Color(0x1A252F);
    private static final Color BG_MID    = new Color(0x2C3E50);
    private static final Color BG_PANEL  = new Color(0x263545);
    private static final Color C_GOLD    = new Color(0xF1C40F);
    private static final Color C_GREEN   = new Color(0x27AE60);
    private static final Color C_RED     = new Color(0xE74C3C);
    private static final Color C_BLUE    = new Color(0x2980B9);
    private static final Color C_ORANGE  = new Color(0xF39C12);
    private static final Color C_WHITE   = Color.WHITE;
    private static final Color C_LGRAY   = new Color(0xBDC3C7);
    private static final Color C_CORRECT = new Color(0x4CAF50);

    // ── Componentes principais ────────────────────────────────
    private JFrame     frame;
    private JPanel     mainPanel;
    private CardLayout cardLayout;

    // LOGIN
    private JTextField loginGameIdField, loginTeamField, loginUsernameField;
    private JLabel     loginStatusLabel;
    private JButton    loginConnectBtn;

    // WAITING
    private JLabel waitingMsgLabel;
    private JLabel waitingPlayerLabel;

    // GAME ─ barra superior
    private JLabel playerNameLabel;     // "Rafael  |  Equipa A"
    private JLabel playerScoreLabel;    // "Pontos: 30"
    private JLabel questionInfoLabel;   // "Pergunta 2/5 [EQUIPA] • 5 pts"
    // GAME ─ centro
    private JLabel    questionLabel;
    private JLabel    timerLabel;
    private JButton[] answerButtons;
    // GAME ─ scoreboard lateral (tabs: Equipas / Jogadores)
    private DefaultTableModel teamTableModel;
    private DefaultTableModel playerTableModel;

    // RESULT
    private JLabel        resultTitleLabel;
    private JLabel        resultCorrectLabel;
    private JLabel        resultMyPointsLabel;  // "Ganhaste X pts  (+bónus x2)"
    private DefaultTableModel resultPlayerModel;
    private DefaultTableModel resultTeamModel;

    // END
    private JLabel        endWinnerLabel;
    private DefaultTableModel endPlayerModel;
    private DefaultTableModel endTeamModel;

    // ── Estado ────────────────────────────────────────────────
    private Player                me;
    private Client                client;
    private javax.swing.Timer     countdownTimer;
    private int                   timeLeft;
    private Message.QuestionMsg   currentQuestion;
    private volatile boolean      answered;

    // ═════════════════════════════════════════════════════════
    // Pontos de entrada públicos
    // ═════════════════════════════════════════════════════════

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientGUI().showLoginScreen());
    }

    public void showLoginScreen() {
        SwingUtilities.invokeLater(() -> {
            buildFrame();
            cardLayout.show(mainPanel, "LOGIN");
            frame.setVisible(true);
        });
    }

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

    /** Chamado após REGISTERED — transita para WAITING. */
    public void showGameScreen(Player player) {
        this.me = player;
        SwingUtilities.invokeLater(() -> {
            updateTopBar(0, 0); // pontos iniciais = 0
            waitingPlayerLabel.setText("Jogador: " + player.getName()
                    + "   Equipa: " + player.getTeam());
            cardLayout.show(mainPanel, "WAITING");
            frame.setVisible(true);
        });
    }

    public void showWaiting(String msg) {
        SwingUtilities.invokeLater(() -> {
            waitingMsgLabel.setText("<html><center>" + msg + "</center></html>");
            cardLayout.show(mainPanel, "WAITING");
        });
    }

    public void showError(String msg) {
        SwingUtilities.invokeLater(() -> {
            loginStatusLabel.setText("<html><font color='red'>" + msg + "</font></html>");
            if (loginConnectBtn != null) loginConnectBtn.setEnabled(true);
        });
    }

    /** Chamado quando chega QUESTION. */
    public void showQuestion(Message.QuestionMsg q) {
        SwingUtilities.invokeLater(() -> {
            currentQuestion = q;
            answered        = false;

            questionLabel.setText("<html><center>" + q.questionText + "</center></html>");
            questionInfoLabel.setText("Pergunta " + q.questionNumber + "/" + q.totalQuestions
                    + (q.isTeamRound ? "  [EQUIPA]" : "  [INDIVIDUAL]")
                    + "  •  " + q.points + " pts");

            List<String> opts = q.options;
            for (int i = 0; i < answerButtons.length; i++) {
                answerButtons[i].setText(i < opts.size()
                        ? "<html><center>" + opts.get(i) + "</center></html>" : "");
                answerButtons[i].setEnabled(i < opts.size());
                answerButtons[i].setBackground(optionColor(i));
                answerButtons[i].setForeground(C_WHITE);
            }

            startCountdown(q.timeLimitSeconds);
            cardLayout.show(mainPanel, "GAME");
        });
    }

    /** Chamado quando chega ROUND_END. */
    public void showRoundResult(Message.RoundResult r) {
        SwingUtilities.invokeLater(() -> {
            stopCountdown();

            // Destaca resposta certa/errada nos botões
            if (currentQuestion != null) {
                for (int i = 0; i < answerButtons.length; i++) {
                    if (i < currentQuestion.options.size()) {
                        answerButtons[i].setEnabled(false);
                        answerButtons[i].setBackground(
                                i == r.correctOption ? C_CORRECT : C_RED);
                    }
                }
            }

            // Título
            resultTitleLabel.setText(answered ? "Fim da Ronda!" : "Tempo esgotado!");
            String correctText = (currentQuestion != null && r.correctOption >= 0
                    && r.correctOption < currentQuestion.options.size())
                    ? currentQuestion.options.get(r.correctOption)
                    : String.valueOf(r.correctOption);
            resultCorrectLabel.setText("✔  Resposta correta: " + correctText);

            // Pontos do jogador local nesta ronda
            Message.PlayerResult myResult = findMyResult(r.playerResults);
            if (myResult != null) {
                String bonusStr = myResult.bonusApplied > 1
                        ? "  (bónus ×" + myResult.bonusApplied + "!)" : "";
                String icon = myResult.answeredCorrectly ? "🎉 +" : (myResult.hasAnswered ? "❌  " : "⏰  ");
                resultMyPointsLabel.setText(icon + myResult.roundPoints + " pts" + bonusStr
                        + "   |   Total: " + myResult.totalPoints + " pts");
                updateTopBar(myResult.totalPoints, myResult.roundPoints);
            } else {
                resultMyPointsLabel.setText("");
            }

            // Tabela de jogadores
            fillPlayerTable(resultPlayerModel, r.playerResults);

            // Tabela de equipas
            fillTeamTable(resultTeamModel, r.teamResults, true);

            // Atualiza scoreboard do ecrã GAME também
            fillTeamTable(teamTableModel, r.teamResults, false);
            fillPlayerTable(playerTableModel, r.playerResults);

            cardLayout.show(mainPanel, "RESULT");
        });
    }

    /** Chamado quando chega GAME_END. */
    public void showGameEnd(Message.RoundResult r) {
        SwingUtilities.invokeLater(() -> {
            stopCountdown();

            endWinnerLabel.setText("🏆  Vencedor: " + r.winnerTeam + "  🏆");

            fillPlayerTable(endPlayerModel, r.playerResults);
            fillTeamTable(endTeamModel, r.teamResults, true);

            cardLayout.show(mainPanel, "END");
        });
    }

    // ═════════════════════════════════════════════════════════
    // Helpers de dados
    // ═════════════════════════════════════════════════════════

    private Message.PlayerResult findMyResult(List<Message.PlayerResult> list) {
        if (me == null || list == null) return null;
        for (Message.PlayerResult pr : list) {
            if (me.getName().equals(pr.username)) return pr;
        }
        return null;
    }

    /** Atualiza a barra de informação do jogador local. */
    private void updateTopBar(int total, int round) {
        if (me == null) return;
        playerNameLabel.setText(me.getName() + "   |   " + me.getTeam());
        playerScoreLabel.setText("Total: " + total + " pts"
                + (round > 0 ? "  (+" + round + ")" : ""));
    }

    /** Preenche tabela de jogadores: Pos | Jogador | Equipa | Ronda | Total | Bónus | ✔. */
    private void fillPlayerTable(DefaultTableModel model, List<Message.PlayerResult> results) {
        model.setRowCount(0);
        int pos = 1;
        for (Message.PlayerResult pr : results) {
            String bonus  = pr.bonusApplied > 1 ? "×" + pr.bonusApplied : "-";
            String acerto = !pr.hasAnswered ? "—" : (pr.answeredCorrectly ? "✔" : "✘");
            model.addRow(new Object[]{
                pos++, pr.username, pr.teamName,
                pr.roundPoints, pr.totalPoints, bonus, acerto
            });
        }
    }

    /** Preenche tabela de equipas: Pos | Equipa | Ronda | Total. */
    private void fillTeamTable(DefaultTableModel model,
                               List<Message.TeamResult> results,
                               boolean showRound) {
        model.setRowCount(0);
        int pos = 1;
        for (Message.TeamResult tr : results) {
            if (showRound) {
                model.addRow(new Object[]{ pos++, tr.teamName, tr.roundPoints, tr.totalPoints });
            } else {
                model.addRow(new Object[]{ pos++, tr.teamName, tr.totalPoints });
            }
        }
    }

    // ═════════════════════════════════════════════════════════
    // Timer
    // ═════════════════════════════════════════════════════════

    private void startCountdown(int seconds) {
        stopCountdown();
        timeLeft = seconds;
        timerLabel.setText("⏱ " + timeLeft + "s");
        timerLabel.setForeground(C_WHITE);

        countdownTimer = new javax.swing.Timer(1000, e -> {
            timeLeft--;
            timerLabel.setText("⏱ " + timeLeft + "s");
            timerLabel.setForeground(timeLeft <= 5 ? C_RED : C_WHITE);
            if (timeLeft <= 0) stopCountdown();
        });
        countdownTimer.start();
    }

    private void stopCountdown() {
        if (countdownTimer != null) { countdownTimer.stop(); countdownTimer = null; }
    }

    // ═════════════════════════════════════════════════════════
    // Construção da frame
    // ═════════════════════════════════════════════════════════

    private void buildFrame() {
        if (frame != null) return;

        frame = new JFrame("IsKahoot!");
        frame.setSize(1050, 620);
        frame.setMinimumSize(new Dimension(800, 500));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel  = new JPanel(cardLayout);

        mainPanel.add(buildLoginPanel(),   "LOGIN");
        mainPanel.add(buildWaitingPanel(), "WAITING");
        mainPanel.add(buildGamePanel(),    "GAME");
        mainPanel.add(buildResultPanel(),  "RESULT");
        mainPanel.add(buildEndPanel(),     "END");

        frame.add(mainPanel);
    }

    // ─────────────────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────────────────

    private JPanel buildLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG_MID);
        panel.setBorder(BorderFactory.createEmptyBorder(30, 90, 30, 90));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(9, 9, 9, 9);
        g.fill   = GridBagConstraints.HORIZONTAL;

        JLabel title = label("IsKahoot!", 38, true, C_GOLD);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        g.gridx = 0; g.gridy = 0; g.gridwidth = 2; panel.add(title, g);

        g.gridwidth = 1;
        addLoginRow(panel, g, 1, "Código do Jogo:", loginGameIdField = new JTextField(22));
        addLoginRow(panel, g, 2, "Equipa:",          loginTeamField   = new JTextField(22));
        addLoginRow(panel, g, 3, "Nome do Jogador:", loginUsernameField = new JTextField(22));

        loginConnectBtn = new JButton("Entrar no Jogo");
        loginConnectBtn.setFont(new Font("SansSerif", Font.BOLD, 15));
        loginConnectBtn.setBackground(C_GREEN);
        loginConnectBtn.setForeground(C_WHITE);
        loginConnectBtn.setFocusPainted(false);
        g.gridx = 0; g.gridy = 4; g.gridwidth = 2; panel.add(loginConnectBtn, g);

        loginStatusLabel = new JLabel("", SwingConstants.CENTER);
        loginStatusLabel.setForeground(C_RED);
        g.gridy = 5; panel.add(loginStatusLabel, g);

        loginConnectBtn.addActionListener(e -> {
            String gameId   = loginGameIdField.getText().trim();
            String team     = loginTeamField.getText().trim();
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

    private void addLoginRow(JPanel p, GridBagConstraints g, int row,
                             String labelText, JTextField field) {
        JLabel l = label(labelText, 14, false, new Color(0xECF0F1));
        g.gridx = 0; g.gridy = row; g.gridwidth = 1; p.add(l, g);
        g.gridx = 1; p.add(field, g);
    }

    // ─────────────────────────────────────────────────────────
    // WAITING
    // ─────────────────────────────────────────────────────────

    private JPanel buildWaitingPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(0x2471A3));

        waitingMsgLabel = label("A aguardar os outros jogadores...", 24, true, C_WHITE);
        waitingMsgLabel.setHorizontalAlignment(SwingConstants.CENTER);

        waitingPlayerLabel = label("", 14, false, new Color(0xD6EAF8));
        waitingPlayerLabel.setHorizontalAlignment(SwingConstants.CENTER);

        panel.add(waitingMsgLabel,   BorderLayout.CENTER);
        panel.add(waitingPlayerLabel, BorderLayout.SOUTH);
        return panel;
    }

    // ─────────────────────────────────────────────────────────
    // GAME
    // ─────────────────────────────────────────────────────────

    private JPanel buildGamePanel() {
        JPanel wrapper = new JPanel(new BorderLayout(4, 4));
        wrapper.setBackground(BG_DARK);

        // ── Barra superior ──────────────────────────────────
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG_MID);
        topBar.setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));

        playerNameLabel  = label("", 13, true,  new Color(0xECF0F1));
        playerScoreLabel = label("", 13, false, C_GOLD);
        questionInfoLabel = label("", 13, false, C_ORANGE);

        JPanel leftInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftInfo.setBackground(BG_MID);
        leftInfo.add(playerNameLabel);
        leftInfo.add(playerScoreLabel);

        topBar.add(leftInfo,          BorderLayout.WEST);
        topBar.add(questionInfoLabel, BorderLayout.EAST);
        wrapper.add(topBar, BorderLayout.NORTH);

        // ── Centro: pergunta + timer + botões ───────────────
        JPanel centerCol = new JPanel(new BorderLayout(4, 4));
        centerCol.setBackground(BG_DARK);
        centerCol.setBorder(BorderFactory.createEmptyBorder(14, 16, 10, 10));

        questionLabel = new JLabel("", SwingConstants.CENTER);
        questionLabel.setFont(new Font("SansSerif", Font.BOLD, 19));
        questionLabel.setForeground(C_WHITE);

        timerLabel = new JLabel("⏱ 30s", SwingConstants.CENTER);
        timerLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        timerLabel.setForeground(C_WHITE);

        JPanel qPanel = new JPanel(new BorderLayout(4, 4));
        qPanel.setBackground(BG_DARK);
        qPanel.add(questionLabel, BorderLayout.CENTER);
        qPanel.add(timerLabel,    BorderLayout.SOUTH);

        JPanel buttonsPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        buttonsPanel.setBackground(BG_DARK);
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

        answerButtons = new JButton[4];
        for (int i = 0; i < 4; i++) {
            JButton btn = new JButton();
            btn.setFont(new Font("SansSerif", Font.BOLD, 14));
            btn.setForeground(C_WHITE);
            btn.setFocusPainted(false);
            btn.setOpaque(true);
            btn.setBorder(BorderFactory.createEmptyBorder(14, 10, 14, 10));
            final int idx = i;
            btn.addActionListener(e -> onAnswer(idx));
            answerButtons[i] = btn;
            buttonsPanel.add(btn);
        }

        centerCol.add(qPanel,       BorderLayout.CENTER);
        centerCol.add(buttonsPanel, BorderLayout.SOUTH);

        // ── Painel lateral: scoreboard com tabs ─────────────
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG_PANEL);
        tabs.setForeground(C_WHITE);
        tabs.setFont(new Font("SansSerif", Font.BOLD, 12));
        tabs.setPreferredSize(new Dimension(260, 0));

        // Tab Equipas
        String[] teamCols = {"#", "Equipa", "Pts"};
        teamTableModel = new DefaultTableModel(teamCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tabs.addTab("Equipas", buildTableScroll(teamTableModel));

        // Tab Jogadores
        String[] playerCols = {"#", "Jogador", "Equipa", "Total"};
        playerTableModel = new DefaultTableModel(playerCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tabs.addTab("Jogadores", buildTableScroll(playerTableModel));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerCol, tabs);
        split.setResizeWeight(0.72);
        split.setDividerSize(4);
        split.setBorder(null);
        wrapper.add(split, BorderLayout.CENTER);

        return wrapper;
    }

    private void onAnswer(int idx) {
        if (answered) return;
        answered = true;
        for (JButton b : answerButtons) b.setEnabled(false);
        answerButtons[idx].setBackground(C_ORANGE);
        if (client != null) client.sendAnswer(idx);
    }

    // ─────────────────────────────────────────────────────────
    // RESULT
    // ─────────────────────────────────────────────────────────

    private JPanel buildResultPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(BG_DARK);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        // Cabeçalho
        resultTitleLabel   = label("Fim da Ronda!", 26, true, C_WHITE);
        resultCorrectLabel = label("", 16, false, C_GREEN);
        resultMyPointsLabel = label("", 16, true, C_GOLD);

        resultTitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        resultCorrectLabel.setHorizontalAlignment(SwingConstants.CENTER);
        resultMyPointsLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel header = new JPanel(new GridLayout(3, 1, 4, 4));
        header.setBackground(BG_DARK);
        header.add(resultTitleLabel);
        header.add(resultCorrectLabel);
        header.add(resultMyPointsLabel);
        panel.add(header, BorderLayout.NORTH);

        // Tabelas lado a lado
        String[] pCols = {"#", "Jogador", "Equipa", "+Ronda", "Total", "Bónus", "✔"};
        resultPlayerModel = new DefaultTableModel(pCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        String[] tCols = {"#", "Equipa", "+Ronda", "Total"};
        resultTeamModel = new DefaultTableModel(tCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JScrollPane playerScroll = buildTableScroll(resultPlayerModel);
        playerScroll.setBorder(titledBorder("Jogadores"));

        JScrollPane teamScroll = buildTableScroll(resultTeamModel);
        teamScroll.setBorder(titledBorder("Equipas"));

        JSplitPane tables = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                playerScroll, teamScroll);
        tables.setResizeWeight(0.65);
        tables.setDividerSize(4);
        tables.setBorder(null);
        panel.add(tables, BorderLayout.CENTER);

        JLabel hint = label("A próxima pergunta começa em breve...", 13, false, C_LGRAY);
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(hint, BorderLayout.SOUTH);

        return panel;
    }

    // ─────────────────────────────────────────────────────────
    // END
    // ─────────────────────────────────────────────────────────

    private JPanel buildEndPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(BG_DARK);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        endWinnerLabel = label("", 30, true, C_GOLD);
        endWinnerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(endWinnerLabel, BorderLayout.NORTH);

        String[] pCols = {"#", "Jogador", "Equipa", "Pontos Totais"};
        endPlayerModel = new DefaultTableModel(pCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        String[] tCols = {"#", "Equipa", "Pontos Totais"};
        endTeamModel = new DefaultTableModel(tCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JScrollPane pScroll = buildTableScroll(endPlayerModel);
        pScroll.setBorder(titledBorder("Classificação Individual"));

        JScrollPane tScroll = buildTableScroll(endTeamModel);
        tScroll.setBorder(titledBorder("Classificação por Equipa"));

        JSplitPane tables = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, pScroll, tScroll);
        tables.setResizeWeight(0.6);
        tables.setDividerSize(4);
        tables.setBorder(null);
        panel.add(tables, BorderLayout.CENTER);

        JButton closeBtn = new JButton("Fechar");
        closeBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        closeBtn.setBackground(C_RED);
        closeBtn.setForeground(C_WHITE);
        closeBtn.setFocusPainted(false);
        closeBtn.addActionListener(e -> System.exit(0));
        JPanel south = new JPanel();
        south.setBackground(BG_DARK);
        south.add(closeBtn);
        panel.add(south, BorderLayout.SOUTH);

        return panel;
    }

    // ─────────────────────────────────────────────────────────
    // Utilitários de UI
    // ─────────────────────────────────────────────────────────

    private static JLabel label(String text, int size, boolean bold, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(color);
        return l;
    }

    private static JScrollPane buildTableScroll(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setBackground(new Color(0x1E2E3D));
        table.setForeground(C_WHITE);
        table.setGridColor(new Color(0x34495E));
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.setRowHeight(22);
        table.getTableHeader().setBackground(new Color(0x1A252F));
        table.getTableHeader().setForeground(C_GOLD);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.setSelectionBackground(new Color(0x2C3E50));
        JScrollPane sp = new JScrollPane(table);
        sp.setBackground(BG_PANEL);
        sp.getViewport().setBackground(new Color(0x1E2E3D));
        return sp;
    }

    private static Border titledBorder(String title) {
        TitledBorder b = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0x34495E)), title);
        b.setTitleColor(new Color(0xECF0F1));
        b.setTitleFont(new Font("SansSerif", Font.BOLD, 12));
        return b;
    }

    private static Color optionColor(int i) {
        Color[] c = { C_RED, C_BLUE, C_ORANGE, C_GREEN };
        return c[i % c.length];
    }
}
