import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.concurrent.ThreadLocalRandom;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public final class BasketballPong {
    private BasketballPong() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Basketball Pong");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new GamePanel());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static final class GamePanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final int WIDTH = 960;
        private static final int HEIGHT = 620;
        private static final int PADDLE_WIDTH = 22;
        private static final int PADDLE_HEIGHT = 120;
        private static final int BALL_SIZE = 26;
        private static final double BASE_SPEED = 6.0;
        private static final int WINNING_SCORE = 5;

        private final Timer timer;
        private double leftPaddleY = (HEIGHT - PADDLE_HEIGHT) / 2.0;
        private double rightPaddleY = (HEIGHT - PADDLE_HEIGHT) / 2.0;
        private double ballX;
        private double ballY;
        private double ballVelocityX;
        private double ballVelocityY;
        private int leftScore;
        private int rightScore;
        private boolean upPressed;
        private boolean downPressed;
        private boolean wPressed;
        private boolean sPressed;
        private boolean gameOver;
        private long matchStartedAt;

        GamePanel() {
            setPreferredSize(new Dimension(WIDTH, HEIGHT));
            setFocusable(true);
            configureKeys();
            resetMatch();
            timer = new Timer(16, this::updateGame);
            timer.start();
        }

        private void configureKeys() {
            InputMap input = getInputMap(WHEN_IN_FOCUSED_WINDOW);
            ActionMap actions = getActionMap();
            bindKey(input, actions, "pressed W", "w-pressed", () -> wPressed = true);
            bindKey(input, actions, "released W", "w-released", () -> wPressed = false);
            bindKey(input, actions, "pressed S", "s-pressed", () -> sPressed = true);
            bindKey(input, actions, "released S", "s-released", () -> sPressed = false);
            bindKey(input, actions, "pressed UP", "up-pressed", () -> upPressed = true);
            bindKey(input, actions, "released UP", "up-released", () -> upPressed = false);
            bindKey(input, actions, "pressed DOWN", "down-pressed", () -> downPressed = true);
            bindKey(input, actions, "released DOWN", "down-released", () -> downPressed = false);
            input.put(KeyStroke.getKeyStroke("ENTER"), "restart");
            actions.put("restart", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    if (gameOver) {
                        resetMatch();
                        timer.start();
                    }
                }
            });
        }

        private static void bindKey(InputMap input, ActionMap actions, String keyStroke,
                                    String actionName, Runnable action) {
            input.put(KeyStroke.getKeyStroke(keyStroke), actionName);
            actions.put(actionName, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    action.run();
                }
            });
        }

        private void resetMatch() {
            leftScore = 0;
            rightScore = 0;
            gameOver = false;
            leftPaddleY = (getHeight() - PADDLE_HEIGHT) / 2.0;
            rightPaddleY = leftPaddleY;
            matchStartedAt = System.currentTimeMillis();
            resetBall(ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        }

        private void resetBall(int horizontalDirection) {
            ballX = (getWidth() - BALL_SIZE) / 2.0;
            ballY = (getHeight() - BALL_SIZE) / 2.0;
            ballVelocityX = horizontalDirection * currentSpeed();
            ballVelocityY = (ThreadLocalRandom.current().nextDouble() - 0.5) * currentSpeed();
        }

        private double currentSpeed() {
            long elapsedSeconds = (System.currentTimeMillis() - matchStartedAt) / 1000;
            return BASE_SPEED + (elapsedSeconds / 10) * 1.0;
        }

        private void updateGame(ActionEvent event) {
            if (gameOver) {
                repaint();
                return;
            }

            double scale = 16.0 / 16.0;
            double paddleSpeed = 8.0 * scale;
            if (wPressed) {
                leftPaddleY -= paddleSpeed;
            }
            if (sPressed) {
                leftPaddleY += paddleSpeed;
            }
            if (upPressed) {
                rightPaddleY -= paddleSpeed;
            }
            if (downPressed) {
                rightPaddleY += paddleSpeed;
            }
            leftPaddleY = clampPaddle(leftPaddleY);
            rightPaddleY = clampPaddle(rightPaddleY);

            double speed = currentSpeed();
            double directionLength = Math.hypot(ballVelocityX, ballVelocityY);
            if (directionLength > 0) {
                ballVelocityX = ballVelocityX / directionLength * speed;
                ballVelocityY = ballVelocityY / directionLength * speed;
            }
            ballX += ballVelocityX;
            ballY += ballVelocityY;

            if (ballY <= 0 || ballY + BALL_SIZE >= getHeight()) {
                ballY = clamp(ballY, 0, getHeight() - BALL_SIZE);
                ballVelocityY *= -1;
            }

            Rectangle2D ball = new Rectangle2D.Double(ballX, ballY, BALL_SIZE, BALL_SIZE);
            Rectangle2D leftPaddle = paddleBounds(28, leftPaddleY);
            Rectangle2D rightPaddle = paddleBounds(getWidth() - 28 - PADDLE_WIDTH, rightPaddleY);
            if (ball.intersects(leftPaddle) && ballVelocityX < 0) {
                bounceFromPaddle(leftPaddle, 1);
            } else if (ball.intersects(rightPaddle) && ballVelocityX > 0) {
                bounceFromPaddle(rightPaddle, -1);
            }

            if (ballX + BALL_SIZE < 0) {
                rightScore++;
                scoreOrReset(-1);
            } else if (ballX > getWidth()) {
                leftScore++;
                scoreOrReset(1);
            }
            repaint();
        }

        private void bounceFromPaddle(Rectangle2D paddle, int horizontalDirection) {
            double paddleCenter = paddle.getY() + paddle.getHeight() / 2;
            double ballCenter = ballY + BALL_SIZE / 2;
            double verticalInfluence = (ballCenter - paddleCenter) / (paddle.getHeight() / 2);
            double speed = currentSpeed();
            ballVelocityX = horizontalDirection * speed;
            ballVelocityY = verticalInfluence * speed * 0.8;
            ballX = horizontalDirection > 0
                    ? paddle.getMaxX()
                    : paddle.getX() - BALL_SIZE;
        }

        private void scoreOrReset(int nextDirection) {
            if (leftScore >= WINNING_SCORE || rightScore >= WINNING_SCORE) {
                gameOver = true;
                timer.stop();
            } else {
                resetBall(nextDirection);
            }
        }

        private double clampPaddle(double y) {
            return clamp(y, 0, Math.max(0, getHeight() - PADDLE_HEIGHT));
        }

        private static double clamp(double value, double minimum, double maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }

        private Rectangle2D paddleBounds(double x, double y) {
            return new Rectangle2D.Double(x, y, PADDLE_WIDTH, PADDLE_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintCourt(g);
            paintBackboards(g);
            paintPaddles(g);
            paintBall(g);
            paintScore(g);
            if (gameOver) {
                paintGameOver(g);
            }
            g.dispose();
        }

        private void paintCourt(Graphics2D g) {
            g.setPaint(new GradientPaint(0, 0, new Color(31, 82, 106), 0, getHeight(), new Color(11, 42, 59)));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(235, 174, 76, 80));
            g.fillOval(getWidth() / 2 - 150, getHeight() / 2 - 150, 300, 300);
            g.setColor(new Color(255, 222, 150));
            g.setStroke(new BasicStroke(3));
            g.drawLine(getWidth() / 2, 0, getWidth() / 2, getHeight());
            g.drawOval(getWidth() / 2 - 90, getHeight() / 2 - 90, 180, 180);
        }

        private void paintBackboards(Graphics2D g) {
            int boardWidth = 72;
            int boardHeight = 120;
            int boardY = getHeight() / 2 - boardHeight / 2;
            g.setColor(new Color(238, 241, 232));
            g.fillRect(0, boardY, boardWidth, boardHeight);
            g.fillRect(getWidth() - boardWidth, boardY, boardWidth, boardHeight);
            g.setColor(new Color(194, 47, 42));
            g.setStroke(new BasicStroke(5));
            g.drawRect(10, boardY + 28, 52, 48);
            g.drawRect(getWidth() - 62, boardY + 28, 52, 48);
            g.setColor(new Color(104, 57, 34));
            g.fillRect(0, boardY + 52, 8, 8);
            g.fillRect(getWidth() - 8, boardY + 52, 8, 8);
        }

        private void paintPaddles(Graphics2D g) {
            paintPaddle(g, 28, leftPaddleY, new Color(239, 93, 43));
            paintPaddle(g, getWidth() - 28 - PADDLE_WIDTH, rightPaddleY, new Color(76, 180, 120));
        }

        private void paintPaddle(Graphics2D g, double x, double y, Color color) {
            g.setColor(new Color(0, 0, 0, 80));
            g.fillRoundRect((int) x + 4, (int) y + 5, PADDLE_WIDTH, PADDLE_HEIGHT, 12, 12);
            g.setColor(color);
            g.fillRoundRect((int) x, (int) y, PADDLE_WIDTH, PADDLE_HEIGHT, 12, 12);
        }

        private void paintBall(Graphics2D g) {
            g.setColor(new Color(217, 105, 35));
            g.fill(new Ellipse2D.Double(ballX, ballY, BALL_SIZE, BALL_SIZE));
            g.setColor(new Color(72, 35, 21));
            g.setStroke(new BasicStroke(2));
            g.drawArc((int) ballX + 4, (int) ballY - 2, BALL_SIZE - 8, BALL_SIZE + 4, 70, 220);
            g.drawArc((int) ballX - 2, (int) ballY + 4, BALL_SIZE + 4, BALL_SIZE - 8, 250, 220);
        }

        private void paintScore(Graphics2D g) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
            g.setColor(Color.WHITE);
            String score = leftScore + "     " + rightScore;
            int scoreWidth = g.getFontMetrics().stringWidth(score);
            g.drawString(score, (getWidth() - scoreWidth) / 2, 62);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.drawString("W / S", 24, 28);
            String rightControls = "UP / DOWN";
            g.drawString(rightControls, getWidth() - g.getFontMetrics().stringWidth(rightControls) - 24, 28);
        }

        private void paintGameOver(Graphics2D g) {
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
            String winner = leftScore > rightScore ? "PLAYER 1 WINS!" : "PLAYER 2 WINS!";
            int winnerWidth = g.getFontMetrics().stringWidth(winner);
            g.drawString(winner, (getWidth() - winnerWidth) / 2, getHeight() / 2 - 10);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
            String prompt = "Press ENTER to play again";
            int promptWidth = g.getFontMetrics().stringWidth(prompt);
            g.drawString(prompt, (getWidth() - promptWidth) / 2, getHeight() / 2 + 32);
        }
    }
}
