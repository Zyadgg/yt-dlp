import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YtDlpGUI extends JFrame {

    private JTextField urlField;
    private JComboBox<FormatItem> formatComboBox;
    private JTextArea logArea;
    private JButton fetchButton;
    private JButton downloadButton;

    private JRadioButton videoAudioBtn;
    private JRadioButton videoOnlyBtn;
    private JRadioButton audioOnlyBtn;

    // Playlist UI
    private JLabel playlistLabel;
    private JPanel playlistPanel;
    private JRadioButton downloadAllBtn;
    private JRadioButton downloadOneBtn;
    private JTextField playlistRangeField;

    private final String YTDLP_EXE = "yt-dlp.exe";
    private boolean isPlaylist = false;

    public YtDlpGUI() {
        setTitle("zyad yt-dlp Pro Downloader");
        setSize(900, 680);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // 1. URL Panel
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("1. Video / Playlist URL"));
        urlField = new JTextField();
        fetchButton = new JButton("Fetch Formats");
        topPanel.add(urlField, BorderLayout.CENTER);
        topPanel.add(fetchButton, BorderLayout.EAST);

        // 2. Playlist Detection Panel (hidden by default)
        playlistPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        playlistPanel.setBorder(BorderFactory.createTitledBorder("Playlist Detected"));
        playlistPanel.setBackground(new Color(255, 243, 205));
        playlistLabel = new JLabel("Playlist detected!");
        playlistLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        playlistLabel.setForeground(new Color(120, 80, 0));

        downloadAllBtn = new JRadioButton("Download All Videos", true);
        downloadOneBtn = new JRadioButton("Download Specific Range:");
        playlistRangeField = new JTextField("1-5", 8);
        playlistRangeField.setEnabled(false);
        playlistRangeField.setToolTipText("Example: 1-5  or  2,4,6  or  1:2:10");

        ButtonGroup playlistGroup = new ButtonGroup();
        playlistGroup.add(downloadAllBtn);
        playlistGroup.add(downloadOneBtn);

        downloadOneBtn.addActionListener(e -> playlistRangeField.setEnabled(true));
        downloadAllBtn.addActionListener(e -> playlistRangeField.setEnabled(false));

        playlistPanel.add(playlistLabel);
        playlistPanel.add(Box.createHorizontalStrut(20));
        playlistPanel.add(downloadAllBtn);
        playlistPanel.add(downloadOneBtn);
        playlistPanel.add(playlistRangeField);
        playlistPanel.setVisible(false);

        // 3. Mode Panel
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.setBorder(BorderFactory.createTitledBorder("Download Mode"));
        videoAudioBtn = new JRadioButton("Video + Audio (Full)", true);
        videoOnlyBtn = new JRadioButton("Video Only");
        audioOnlyBtn = new JRadioButton("Audio Only");
        ButtonGroup group = new ButtonGroup();
        group.add(videoAudioBtn);
        group.add(videoOnlyBtn);
        group.add(audioOnlyBtn);
        filterPanel.add(videoAudioBtn);
        filterPanel.add(videoOnlyBtn);
        filterPanel.add(audioOnlyBtn);

        // 4. Middle Panel
        JPanel middlePanel = new JPanel(new BorderLayout(5, 5));
        middlePanel.setBorder(BorderFactory.createTitledBorder("2. Select Quality & Size"));
        formatComboBox = new JComboBox<>();
        formatComboBox.setEnabled(false);
        logArea = new JTextArea();
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        middlePanel.add(formatComboBox, BorderLayout.NORTH);
        middlePanel.add(scrollPane, BorderLayout.CENTER);

        // 5. Download Button
        downloadButton = new JButton("Start Download to Videos Folder");
        downloadButton.setEnabled(false);
        downloadButton.setFont(new Font("Segoe UI", Font.BOLD, 13));

        // North group: URL + playlist + mode
        JPanel northGroup = new JPanel(new GridLayout(3, 1));
        northGroup.add(topPanel);
        northGroup.add(playlistPanel);
        northGroup.add(filterPanel);

        add(northGroup, BorderLayout.NORTH);
        add(middlePanel, BorderLayout.CENTER);
        add(downloadButton, BorderLayout.SOUTH);

        fetchButton.addActionListener(e -> checkAndFetch());
        downloadButton.addActionListener(e -> startDownload());
    }

    // ─────────────────────────────────────────────
    // Detect playlist from URL
    // ─────────────────────────────────────────────
    private boolean detectPlaylist(String url) {
        return url.contains("playlist?list=") || url.contains("&list=") || url.contains("?list=");
    }

    private void checkAndFetch() {
        String url = urlField.getText().trim();
        if (url.isEmpty())
            return;

        isPlaylist = detectPlaylist(url);
        SwingUtilities.invokeLater(() -> {
            playlistPanel.setVisible(isPlaylist);
            if (isPlaylist) {
                playlistLabel.setText("Playlist URL detected: " + shortenUrl(url));
                logArea.setText("Playlist URL detected...\n");
            } else {
                logArea.setText("Starting engine...\n");
            }
            revalidate();
            repaint();
        });

        fetchButton.setEnabled(false);
        new Thread(() -> {
            if (!isYtDlpInstalled())
                downloadYtDlp();
            if (!isFfmpegInstalled()) {
                SwingUtilities
                        .invokeLater(() -> logArea.append("WARNING: ffmpeg not found! Video+Audio merging may fail.\n" +
                                "Please install ffmpeg and add it to your PATH.\n"));
            }
            fetchVideoInfo(url);
        }).start();
    }

    private String shortenUrl(String url) {
        return url.length() > 55 ? url.substring(0, 52) + "..." : url;
    }

    // ─────────────────────────────────────────────
    // Tool checks
    // ─────────────────────────────────────────────
    private boolean isYtDlpInstalled() {
        try {
            return new ProcessBuilder(getYtDlpCommand(), "--version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isFfmpegInstalled() {
        try {
            return new ProcessBuilder("ffmpeg", "-version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String getYtDlpCommand() {
        File localExe = new File(YTDLP_EXE);
        return localExe.exists() ? localExe.getAbsolutePath() : "yt-dlp";
    }

    private void downloadYtDlp() {
        SwingUtilities.invokeLater(() -> logArea.append("yt-dlp missing. Downloading...\n"));
        try (BufferedInputStream in = new BufferedInputStream(
                new URL("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe").openStream());
                FileOutputStream fos = new FileOutputStream(YTDLP_EXE)) {
            byte[] data = new byte[1024];
            int count;
            while ((count = in.read(data, 0, 1024)) != -1)
                fos.write(data, 0, count);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────
    // Fetch formats (router)
    // ─────────────────────────────────────────────
    private void fetchVideoInfo(String url) {
        SwingUtilities.invokeLater(() -> logArea.append("Analyzing stream data...\n"));

        if (videoAudioBtn.isSelected() && isPlaylist) {
            // Playlist + Video+Audio: predefined options (sizes vary per video)
            showPredefinedFormats(true);
            return;
        }

        if (videoAudioBtn.isSelected() && !isPlaylist) {
            // Single video + Video+Audio: fetch real sizes
            fetchSizesForVideoAudio(url);
            return;
        }

        // Video Only / Audio Only — fetch real format list
        try {
            ArrayList<String> cmd = new ArrayList<>();
            cmd.add(getYtDlpCommand());
            if (isPlaylist) {
                cmd.add("--playlist-items");
                cmd.add("1");
            }
            cmd.add("-F");
            cmd.add(url);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            ArrayList<FormatItem> items = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("[") || line.startsWith("ID") || line.startsWith("-")
                        || line.trim().isEmpty() || line.contains("mhtml"))
                    continue;

                String[] parts = line.split("\\s+");
                if (parts.length < 2)
                    continue;

                String id = parts[0];
                String ext = parts[1];
                String size = "Unknown";
                String quality = "";

                Matcher sM = Pattern.compile("~?\\d+(\\.\\d+)?(MiB|GiB|KiB|B)").matcher(line);
                if (sM.find())
                    size = sM.group();

                Matcher qM = Pattern.compile("(\\d{3,4}p|\\d+x\\d+)").matcher(line);
                if (qM.find()) {
                    quality = qM.group();
                } else {
                    Matcher bM = Pattern.compile("\\d+k").matcher(line);
                    if (bM.find())
                        quality = bM.group();
                    else
                        quality = "Default";
                }

                boolean isAudio = line.contains("audio only");
                boolean isVideo = line.contains("video only");

                if (audioOnlyBtn.isSelected() && isAudio) {
                    items.add(new FormatItem(id,
                            String.format("[AUDIO] %-10s | %-5s | Size: %s", quality, ext, size)));
                } else if (videoOnlyBtn.isSelected() && isVideo) {
                    items.add(new FormatItem(id,
                            String.format("[VIDEO] %-10s | %-5s | Size: %s", quality, ext, size)));
                }
            }

            process.waitFor();
            SwingUtilities.invokeLater(() -> {
                formatComboBox.removeAllItems();
                for (FormatItem item : items)
                    formatComboBox.addItem(item);
                formatComboBox.setEnabled(!items.isEmpty());
                downloadButton.setEnabled(!items.isEmpty());
                fetchButton.setEnabled(true);
                logArea.append(items.isEmpty() ? "No results found for this mode.\n" : "Ready to download.\n");
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────
    // Fetch actual sizes for single video (Video+Audio)
    // ─────────────────────────────────────────────
    private void fetchSizesForVideoAudio(String url) {
        SwingUtilities.invokeLater(() -> logArea.append("Fetching resolutions & sizes...\n"));
        try {
            ProcessBuilder pb = new ProcessBuilder(getYtDlpCommand(), "-F", url);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            // Best video per height, best audio overall
            java.util.TreeMap<Integer, String[]> videoFormats = new java.util.TreeMap<>(
                    java.util.Collections.reverseOrder());
            String bestAudioId = null;
            String bestAudioSize = "?";
            int bestAudioBr = 0;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("[") || line.startsWith("ID") || line.startsWith("-")
                        || line.trim().isEmpty() || line.contains("mhtml"))
                    continue;

                String[] parts = line.split("\\s+");
                if (parts.length < 2)
                    continue;
                String id = parts[0];

                String size = "?";
                Matcher sM = Pattern.compile("~?\\d+(\\.\\d+)?(MiB|GiB|KiB|B)").matcher(line);
                if (sM.find())
                    size = sM.group();

                boolean isAudio = line.contains("audio only");
                boolean isVideo = line.contains("video only");

                if (isAudio) {
                    Matcher bM = Pattern.compile("(\\d+)k").matcher(line);
                    int br = 0;
                    if (bM.find())
                        br = Integer.parseInt(bM.group(1));
                    if (br > bestAudioBr) {
                        bestAudioBr = br;
                        bestAudioId = id;
                        bestAudioSize = size + (br > 0 ? " (" + br + "k)" : "");
                    }
                } else if (isVideo) {
                    Matcher hM = Pattern.compile("(\\d{3,4})p").matcher(line);
                    if (hM.find()) {
                        int h = Integer.parseInt(hM.group(1));
                        if (!videoFormats.containsKey(h)) {
                            videoFormats.put(h, new String[] { id, size });
                        }
                    }
                }
            }
            process.waitFor();

            final String audioId = bestAudioId;
            final String audioSize = bestAudioSize;
            final java.util.TreeMap<Integer, String[]> vf = videoFormats;

            SwingUtilities.invokeLater(() -> {
                formatComboBox.removeAllItems();
                formatComboBox.addItem(new FormatItem("bestvideo+bestaudio/best",
                        "[FULL] Best Quality (Auto)  |  Video: auto  +  Audio: auto"));

                for (java.util.Map.Entry<Integer, String[]> entry : vf.entrySet()) {
                    int h = entry.getKey();
                    String vid = entry.getValue()[0];
                    String vsz = entry.getValue()[1];
                    String fmtId = (audioId != null) ? vid + "+" + audioId : vid + "+bestaudio";
                    formatComboBox.addItem(new FormatItem(fmtId,
                            String.format("[FULL] %4dp  |  Video: %-12s + Audio: %s", h, vsz, audioSize)));
                }

                formatComboBox.setEnabled(true);
                downloadButton.setEnabled(true);
                fetchButton.setEnabled(true);
                logArea.append("Ready to download. (ffmpeg needed for merging)\n");
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────
    // Predefined formats (playlist Video+Audio)
    // ─────────────────────────────────────────────
    private void showPredefinedFormats(boolean playlistMode) {
        String note = playlistMode ? " (size varies per video)" : "";
        ArrayList<FormatItem> items = new ArrayList<>();
        items.add(new FormatItem("bestvideo+bestaudio/best", "[FULL] Best Quality (Auto)" + note));
        items.add(new FormatItem("bestvideo[height<=2160]+bestaudio/best", "[FULL] 4K  (2160p)" + note));
        items.add(new FormatItem("bestvideo[height<=1440]+bestaudio/best", "[FULL] 1440p" + note));
        items.add(new FormatItem("bestvideo[height<=1080]+bestaudio/best", "[FULL] 1080p" + note));
        items.add(new FormatItem("bestvideo[height<=720]+bestaudio/best", "[FULL] 720p" + note));
        items.add(new FormatItem("bestvideo[height<=480]+bestaudio/best", "[FULL] 480p" + note));
        items.add(new FormatItem("bestvideo[height<=360]+bestaudio/best", "[FULL] 360p" + note));

        SwingUtilities.invokeLater(() -> {
            formatComboBox.removeAllItems();
            for (FormatItem item : items)
                formatComboBox.addItem(item);
            formatComboBox.setEnabled(true);
            downloadButton.setEnabled(true);
            fetchButton.setEnabled(true);
            logArea.append(playlistMode
                    ? "Playlist: sizes vary per video and cannot be shown. Ready.\n"
                    : "Ready to download. (ffmpeg required for merging)\n");
        });
    }

    // ─────────────────────────────────────────────
    // Start Download
    // ─────────────────────────────────────────────
    private void startDownload() {
        FormatItem selected = (FormatItem) formatComboBox.getSelectedItem();
        if (selected == null)
            return;

        final String fId = selected.id;
        final String url = urlField.getText().trim();
        final boolean isA = audioOnlyBtn.isSelected();
        final boolean isVA = videoAudioBtn.isSelected();

        downloadButton.setEnabled(false);
        logArea.setText("Starting download...\n");

        new Thread(() -> {
            try {
                String basePath = System.getProperty("user.home") + File.separator + "Videos";
                String outTemplate;
                if (isPlaylist) {
                    // Save to subfolder named after the playlist
                    outTemplate = basePath + File.separator + "%(playlist_title)s"
                            + File.separator + "%(playlist_index)02d - %(title)s.%(ext)s";
                } else {
                    outTemplate = basePath + File.separator + "%(title)s.%(ext)s";
                }

                ArrayList<String> cmd = new ArrayList<>();
                cmd.add(getYtDlpCommand());
                cmd.add("-f");
                cmd.add(fId);
                cmd.add("-o");
                cmd.add(outTemplate);

                // Playlist range filter
                if (isPlaylist && downloadOneBtn.isSelected()) {
                    String range = playlistRangeField.getText().trim();
                    if (!range.isEmpty()) {
                        cmd.add("--playlist-items");
                        cmd.add(range);
                    }
                }

                if (isA) {
                    cmd.add("-x");
                    cmd.add("--audio-format");
                    cmd.add("mp3");
                } else if (isVA) {
                    cmd.add("--merge-output-format");
                    cmd.add("mp4");
                }

                cmd.add("--progress");
                cmd.add(url);

                Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String l;
                while ((l = r.readLine()) != null) {
                    String fl = l;
                    SwingUtilities.invokeLater(() -> {
                        logArea.append(fl + "\n");
                        logArea.setCaretPosition(logArea.getDocument().getLength());
                    });
                }
                p.waitFor();
                SwingUtilities.invokeLater(() -> {
                    downloadButton.setEnabled(true);
                    JOptionPane.showMessageDialog(this,
                            "Download Finished Successfully!\nSaved to: ~/Videos/",
                            "Done", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> downloadButton.setEnabled(true));
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new YtDlpGUI().setVisible(true);
        });
    }
}