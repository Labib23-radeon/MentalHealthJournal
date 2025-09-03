package com.example.mentalhealthjournal;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters; // <<< --- ADDED IMPORT
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.effect.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class HelloApplication extends Application {

    // --- User Class ---
    static class User implements Serializable {
        @Serial
        private static final long serialVersionUID = 101L; // Unique for User class
        String username;
        String hashedPassword;
        String profilePicturePath; // Relative path like "profile.png" within user's folder
        String role; // Added role for patient or doctor
        String themePreference = "dark"; // New: theme preference

        public User(String username, String hashedPassword, String role) {
            this.username = username;
            this.hashedPassword = hashedPassword;
            this.role = role;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            User user = (User) o;
            return username.equals(user.username);
        }

        @Override
        public int hashCode() {
            return Objects.hash(username);
        }
    }


    // --- Data Classes (Defined as static nested classes) ---
    static class JournalEntry implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        LocalDate date;
        String content;
        String mood;

        public JournalEntry(LocalDate date, String content, String mood) {
            this.date = date;
            this.content = content != null ? content : "";
            this.mood = mood != null ? mood : "Unknown";
        }

        @Override
        public String toString() {
            return "Date: " + date.format(DateTimeFormatter.ISO_DATE) + ", Mood: " + mood + "\nEntry: " + content.substring(0, Math.min(100, content.length())) + (content.length() > 100 ? "..." : "");
        }
    }

    static class MoodEntry implements Serializable {
        @Serial
        private static final long serialVersionUID = 2L;
        LocalDate date;
        String mood;
        String notes;

        public MoodEntry(LocalDate date, String mood, String notes) {
            this.date = date;
            this.mood = mood != null ? mood : "Unknown";
            this.notes = notes != null ? notes : "";
        }

        @Override
        public String toString() {
            return "Date: " + date.format(DateTimeFormatter.ISO_DATE) + ", Mood: " + mood + (notes.isEmpty() ? "" : "\nNotes: " + notes);
        }
    }

    // --- Friend System Data Classes ---
    static class MyInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 4L;
        String uid;
        int tcpPort;

        public MyInfo(String uid, int tcpPort) {
            this.uid = uid;
            this.tcpPort = tcpPort;
        }
    }

    static class Friend implements Serializable {
        @Serial
        private static final long serialVersionUID = 301L;
        String uid;
        String nickname;
        private List<ChatMessage> storedChatMessages;

        transient String ipAddress;
        transient int tcpPort;
        transient boolean isOnline = false;
        transient LocalDateTime lastSeen;
        transient ObservableList<ChatMessage> chatMessages;


        public Friend(String uid, String nickname) {
            this.uid = uid;
            this.nickname = nickname;
            this.chatMessages = FXCollections.observableArrayList();
            this.storedChatMessages = new ArrayList<>();
        }

        public ObservableList<ChatMessage> getChatMessages() {
            if (this.chatMessages == null) {
                this.chatMessages = FXCollections.observableArrayList(
                        this.storedChatMessages != null ? this.storedChatMessages : new ArrayList<>()
                );
            }
            return this.chatMessages;
        }

        @Serial
        private void writeObject(ObjectOutputStream out) throws IOException {
            if (this.chatMessages != null) {
                this.storedChatMessages = new ArrayList<>(this.chatMessages);
            } else {
                this.storedChatMessages = new ArrayList<>();
            }
            out.defaultWriteObject();
        }

        @Serial
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            if (this.storedChatMessages == null) {
                this.storedChatMessages = new ArrayList<>();
            }
            this.chatMessages = FXCollections.observableArrayList(this.storedChatMessages);
        }


        public String getDisplayStatus() {
            return isOnline ? "Online" : "Offline";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Friend friend = (Friend) o;
            return Objects.equals(uid, friend.uid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uid);
        }

        @Override
        public String toString() {
            return nickname + " (" + uid.substring(0, Math.min(8,uid.length())) + ") - " + getDisplayStatus();
        }
    }

    static class ChatMessage implements Serializable {
        @Serial
        private static final long serialVersionUID = 6L;
        String senderUid;
        String content;
        LocalDateTime timestamp;
        boolean isMe;

        public ChatMessage(String senderUid, String content, LocalDateTime timestamp, boolean isMe) {
            this.senderUid = senderUid;
            this.content = content;
            this.timestamp = timestamp;
            this.isMe = isMe;
        }

        @Override
        public String toString() {
            String prefix = isMe ? "You: " : "";
            Optional<HelloApplication> appInstanceOpt = HelloApplication.getInstance();
            if (!isMe && appInstanceOpt.isPresent()) {
                HelloApplication appInstance = appInstanceOpt.get();
                if (appInstance.friendsList != null) {
                    Optional<Friend> senderFriend = appInstance.friendsList.stream()
                            .filter(f -> f.uid.equals(senderUid)).findFirst();
                    prefix = senderFriend.map(f -> f.nickname + ": ")
                            .orElse(senderUid.substring(0, Math.min(6,senderUid.length())) + "...: ");
                } else {
                    prefix = senderUid.substring(0, Math.min(6,senderUid.length())) + "...: ";
                }
            }
            return String.format("[%s] %s%s", timestamp.format(DateTimeFormatter.ofPattern("HH:mm")), prefix, content);
        }
    }

    // --- Prescription Data Class ---
    static class PrescriptionEntry implements Serializable {
        @Serial
        private static final long serialVersionUID = 7L;
        LocalDate date;
        String doctorName;
        String prescriptionText;
        String filePath; // Path to uploaded prescription file (image or PDF)

        public PrescriptionEntry(LocalDate date, String doctorName, String prescriptionText, String filePath) {
            this.date = date;
            this.doctorName = doctorName;
            this.prescriptionText = prescriptionText;
            this.filePath = filePath;
        }

        @Override
        public String toString() {
            return "Date: " + date.format(DateTimeFormatter.ISO_DATE) + ", Doctor: " + doctorName +
                    "\nPrescription: " + prescriptionText.substring(0, Math.min(100, prescriptionText.length())) +
                    (prescriptionText.length() > 100 ? "..." : "") +
                    (filePath != null ? "\nFile: " + filePath : "");
        }
    }

    // --- End Data Classes ---

    // --- Suggestion Engine Data Structures ---
    static class AnalysisData {
        Map<String, Long> moodCountsLast7Days;
        List<String> keywordsInJournalLast7Days;
        int consecutiveLowMoodDays;

        public AnalysisData(Map<String, Long> moodCountsLast7Days, List<String> keywordsInJournalLast7Days, int consecutiveLowMoodDays) {
            this.moodCountsLast7Days = moodCountsLast7Days != null ? moodCountsLast7Days : new HashMap<>();
            this.keywordsInJournalLast7Days = keywordsInJournalLast7Days != null ? keywordsInJournalLast7Days : new ArrayList<>();
            this.consecutiveLowMoodDays = consecutiveLowMoodDays;
        }
    }

    @FunctionalInterface
    interface SuggestionCondition {
        boolean test(AnalysisData data, List<JournalEntry> recentJournalEntries, List<MoodEntry> recentMoodEntries);
    }

    public static class SuggestionRule {
        String id;
        String conditionDescription;
        SuggestionCondition condition;
        String suggestionText;
        String detailedExplanation;
        int priority;

        public SuggestionRule(String id, String conditionDescription, SuggestionCondition condition, String suggestionText, String detailedExplanation, int priority) {
            this.id = id;
            this.conditionDescription = conditionDescription;
            this.condition = condition;
            this.suggestionText = suggestionText;
            this.detailedExplanation = detailedExplanation;
            this.priority = priority;
        }
    }
    // --- End Suggestion Engine Data Structures ---

    // --- Well Being Feature Data Structures ---
    static class AnalysisDataForWellBeing {
        Set<String> recentNegativeMoods;
        Set<String> recentPositiveMoods;
        Set<String> recentKeywordsFromJournals;
        boolean lowEngagementJournal;
        boolean lowEngagementMood;

        public AnalysisDataForWellBeing(Set<String> recentNegativeMoods,
                                        Set<String> recentPositiveMoods,
                                        Set<String> recentKeywordsFromJournals,
                                        boolean lowEngagementJournal,
                                        boolean lowEngagementMood) {
            this.recentNegativeMoods = recentNegativeMoods != null ? recentNegativeMoods : new HashSet<>();
            this.recentPositiveMoods = recentPositiveMoods != null ? recentPositiveMoods : new HashSet<>();
            this.recentKeywordsFromJournals = recentKeywordsFromJournals != null ? recentKeywordsFromJournals : new HashSet<>();
            this.lowEngagementJournal = lowEngagementJournal;
            this.lowEngagementMood = lowEngagementMood;
        }
    }

    @FunctionalInterface
    interface WellBeingCondition {
        boolean test(AnalysisDataForWellBeing data);
    }

    static class WellBeingTask {
        String title;
        String description;
        String category;
        WellBeingCondition condition;
        String estimatedTime;

        public WellBeingTask(String title, String description, String category, String estimatedTime, WellBeingCondition condition) {
            this.title = title;
            this.description = description;
            this.category = category;
            this.estimatedTime = estimatedTime;
            this.condition = condition;
        }
    }
    // --- End Well-being Feature Data Structures ---


    // --- User and Session Data ---
    private User currentUser;
    private List<User> allUsers = new ArrayList<>();

    // Data for the current logged-in user
    private List<JournalEntry> journalEntries;
    private List<MoodEntry> moodEntries;
    ObservableList<Friend> friendsList;
    private MyInfo myApplicationInfo;

    private final List<SuggestionRule> suggestionRules = new ArrayList<>();
    private final List<WellBeingTask> wellBeingTaskList = new ArrayList<>();
    private VBox wellBeingViewContent;

    // Prescription data
    private List<PrescriptionEntry> prescriptionEntries;

    // --- File Paths ---
    private static final String DATA_DIR = System.getProperty("user.home") + File.separator + ".neurodevelopmental";
    private static final String USERS_DATA_FILE = DATA_DIR + File.separator + "users.dat";
    private static final String USER_JOURNAL_FILE_NAME = "journalEntries.dat";
    private static final String USER_MOOD_FILE_NAME = "moodEntries.dat";
    private static final String USER_MYINFO_FILE_NAME = "myAppInfo.dat";
    private static final String USER_FRIENDS_FILE_NAME = "friends.dat";
    private static final String USER_PROFILE_PIC_FILE_NAME = "profile.png";
    private static final String USER_PRESCRIPTIONS_FILE_NAME = "prescriptions.dat"; // New file for prescriptions
    private static final String BACKUP_SUFFIX = "_backup.dat";


    private final Random random = new Random();
    private StackPane contentArea;
    private Stage mainApplicationStage;

    private static final List<String> VALID_MOODS = Arrays.asList(
            "Very Happy", "Happy", "Content", "Neutral", "Sad", "Very Sad",
            "Anxious", "Stressed", "Calm", "Energetic", "Tired"
    );

    private NetworkManager networkManager;
    private static final int DEFAULT_TCP_PORT = 25566;

    private ListView<Friend> friendsListViewForConnect;
    private TextArea chatDisplayArea;
    private TextField messageInputField;
    private Button sendMessageButton;
    private Label chatWithLabel;
    private Friend currentChatFriend;
    private ImageView sidebarProfileImageView;

    private static HelloApplication instance;
    public static Optional<HelloApplication> getInstance() {
        return Optional.ofNullable(instance);
    }

    private static final String DARK_CSS_STYLES = """
            .root {
                -fx-background-color: #121212;
            }
            .sidebar {
                -fx-background-color: #1A1A1A;
                -fx-padding: 20px;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);
            }
            .sidebar-button {
                -fx-background-color: transparent;
                -fx-text-fill: #B3B3B3;
                -fx-font-family: 'Arial';
                -fx-font-size: 16px;
                -fx-padding: 10px 20px;
                -fx-alignment: center-left;
                -fx-cursor: hand;
            }
            .sidebar-button:hover {
                -fx-background-color: #2A2A2A;
                -fx-text-fill: #FFFFFF;
                -fx-background-radius: 5px;
            }
            .sidebar-button:selected {
                -fx-background-color: #1DB954; /* Brighter selection color */
                -fx-text-fill: #FFFFFF;
                -fx-font-weight: bold;
                -fx-background-radius: 5px;
                 -fx-effect: innershadow(gaussian, rgba(0,0,0,0.2), 5, 0, 0, 1);
            }
            .sidebar-profile-image {
                -fx-padding: 5px;
                -fx-border-color: #3A3A3A;
                -fx-border-width: 1px;
                -fx-border-radius: 5px; /* For square-ish with rounded corners */
            }
            .header {
                -fx-background-color: transparent;
                -fx-padding: 15px 30px;
            }
            .header-title {
                -fx-font-family: 'Arial';
                -fx-font-size: 28px;
                -fx-font-weight: bold;
                -fx-text-fill: #FFFFFF;
            }
            .header-subtitle {
                -fx-font-family: 'Arial';
                -fx-font-size: 14px;
                -fx-text-fill: #B3B3B3;
            }
            .content-area {
                -fx-background-color: #181818;
                -fx-background-radius: 10px;
                -fx-padding: 20px;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);
            }
            .primary-button {
                -fx-background-color: #1DB954;
                -fx-text-fill: #FFFFFF;
                -fx-font-family: 'Arial';
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-padding: 10px 20px;
                -fx-background-radius: 25px;
                -fx-cursor: hand;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);
            }
            .primary-button:hover {
                -fx-background-color: #1ED760;
                -fx-effect: dropshadow(gaussian, #1DB954, 15, 0.7, 0, 0);
            }
            .primary-button:focused {
                -fx-border-color: #FFFFFF;
                -fx-border-width: 2px;
                -fx-border-radius: 25px;
                 -fx-effect: dropshadow(gaussian, #1DB954, 10, 0.5, 0, 0);
            }
            .mood-button {
                -fx-background-color: #2A2A2A;
                -fx-text-fill: #B3B3B3;
                -fx-font-family: 'Arial';
                -fx-font-size: 14px;
                -fx-background-radius: 10px;
                -fx-padding: 12px;
                -fx-cursor: hand;
                -fx-effect: innershadow(gaussian, rgba(0,0,0,0.2), 5, 0, 0, 2);
            }
            .mood-button:hover {
                -fx-background-color: #3A3A3A;
                -fx-text-fill: #FFFFFF;
            }
            .mood-button:focused { /* More subtle focus for mood buttons, selection is key */
                -fx-border-color: #1DB954;
                -fx-border-width: 1px;
                -fx-border-radius: 10px;
            }
            .mood-button-selected { /* Class for the selected mood button */
                 -fx-background-color: #1DB954; /* Example primary selection color */
                 -fx-text-fill: #FFFFFF;
                 -fx-font-weight: bold;
                 -fx-effect: innershadow(gaussian, rgba(0,0,0,0.3), 8, 0, 1, 1);
            }

            .mood-button-happy { /* For mood toggle buttons (not separate styles unless distinct coloring) */
                -fx-background-color: #1DB954;
                -fx-text-fill: #FFFFFF;
            }
            .mood-button-sad {
                -fx-background-color: #FF5555;
                -fx-text-fill: #FFFFFF;
            }
            .mood-button-neutral {
                -fx-background-color: #505050;
                -fx-text-fill: #FFFFFF;
            }
            .prompt-label {
                -fx-background-color: #2A2A2A;
                -fx-padding: 15px;
                -fx-background-radius: 10px;
                -fx-font-family: 'Arial';
                -fx-font-size: 14px;
                -fx-text-fill: #B3B3B3;
                -fx-border-color: #3A3A3A;
                -fx-border-width: 1px;
                -fx-border-radius: 10px;
            }
            .title-label {
                -fx-font-family: 'Arial';
                -fx-font-size: 18px;
                -fx-font-weight: bold;
                -fx-text-fill: #FFFFFF;
            }
            .chart {
                -fx-background-color: #2A2A2A;
                -fx-background-radius: 10px;
            }
            .chart-plot-background { -fx-background-color: transparent; }
            .axis { -fx-tick-label-fill: #B3B3B3; }
            .axis-label { -fx-text-fill: #FFFFFF; }
            .chart-title { -fx-text-fill: #FFFFFF; -fx-font-size: 16px;}
            .chart-line-symbol { -fx-background-radius: 5px; -fx-padding: 5px; }
            .chart-series-line { -fx-stroke-width: 2px; }
            .default-color0.chart-line-symbol { -fx-background-color: #1DB954, #181818; -fx-background-insets: 0, 2; }
            .default-color0.chart-series-line { -fx-stroke: #1DB954; }


            .text-area, .combo-box, .date-picker, .text-field, .password-field, .list-view {
                -fx-font-family: 'Arial';
                -fx-font-size: 14px;
                -fx-background-color: #2A2A2A;
                -fx-text-fill: #FFFFFF;
                -fx-prompt-text-fill: #B3B3B3;
                -fx-border-color: #3A3A3A;
                -fx-border-width: 1px;
                -fx-border-radius: 5px;
            }
            .text-area .content, .list-view .list-cell {
                -fx-background-color: #2A2A2A; /* Ensures content area also has the dark bg */
                -fx-text-fill: #FFFFFF; /* Ensure text is white on dark bg */
            }
             .list-view .list-cell:filled:selected, .list-view .list-cell:filled:selected:focused {
                -fx-background-color: #1DB954;
                -fx-text-fill: white;
            }
            .list-view .list-cell:filled:hover {
                -fx-background-color: #3A3A3A;
            }
             .list-view .placeholder .label { /* Placeholder text styling */
                -fx-text-fill: #B3B3B3;
            }

            .text-area:focused, .combo-box:focused, .date-picker:focused, .text-field:focused, .password-field:focused, .list-view:focused {
                -fx-border-color: #1DB954;
                -fx-border-width: 2px;
                 -fx-effect: dropshadow(gaussian, #1DB954, 5, 0.2, 0, 0);
            }
            .insights-title {
                -fx-font-family: 'Arial';
                -fx-font-size: 24px;
                -fx-font-weight: bold;
                -fx-text-fill: #FFFFFF;
            }
            .summary-pane { /* TitledPane itself or a VBox */
                -fx-background-color: #2A2A2A;
                -fx-background-radius: 10px;
                -fx-padding: 15px;
            }
            .summary-label { /* General text within summaries */
                -fx-font-family: 'Arial';
                -fx-font-size: 14px;
                -fx-text-fill: #B3B3B3;
            }
            .tooltip {
                -fx-background-color: #1A1A1A;
                -fx-text-fill: #FFFFFF;
                -fx-font-family: 'Arial';
                -fx-font-size: 12px;
                -fx-background-radius: 5px;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 5, 0, 0, 1);
            }
            .login-pane { /* Also used for registration pane */
                -fx-background-color: #181818;
                -fx-background-radius: 10px;
                -fx-padding: 40px;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);
                -fx-max-width: 450px; /* Slightly wider for registration hints */
                -fx-max-height: 600px; /* Taller for more fields if needed */
            }
            .login-title {
                -fx-font-family: 'Arial';
                -fx-font-size: 28px;
                -fx-font-weight: bold;
                -fx-text-fill: #FFFFFF;
                -fx-alignment: center;
            }
            .history-filter-label {
                 -fx-font-family: 'Arial';
                 -fx-font-size: 14px;
                 -fx-text-fill: #B3B3B3;
                 -fx-padding: 5px 0 0 0;
            }
            .hyperlink {
                -fx-text-fill: #1DB954;
                -fx-border-color: transparent;
            }
            .hyperlink:hover {
                -fx-underline: true;
                -fx-text-fill: #1ED760;
            }
            .dialog-pane {
                -fx-background-color: #181818;
            }
            .dialog-pane .label {
                 -fx-text-fill: #FFFFFF;
            }
            .dialog-pane .button { /* Style buttons inside dialogs too */
                 -fx-background-color: #2A2A2A;
                 -fx-text-fill: #FFFFFF;
                 -fx-border-color: #3A3A3A;
            }
            .dialog-pane .button:hover {
                -fx-background-color: #3A3A3A;
            }
            .dialog-pane .header-panel {
                 -fx-background-color: #1A1A1A;
            }
            .dialog-pane .header-panel .label {
                 -fx-text-fill: #FFFFFF;
                 -fx-font-style: italic;
            }
            .chat-message-me {
                -fx-text-fill: #1DB954; /* Brighter green for 'me' messages */
                -fx-font-weight: bold;
            }
            .chat-message-them {
                -fx-text-fill: #B3B3B3; /* Standard text for 'them' */
            }
            /* Ensure selected chat messages retain color contrast */
            .list-cell:filled:selected .chat-message-me,
            .list-cell:filled:selected .chat-message-them {
                -fx-text-fill: white;
            }

            .split-pane {
                -fx-background-color: #181818; /* Match content area background */
            }
            .split-pane > .split-pane-divider {
                -fx-background-color: #3A3A3A;
                -fx-padding: 0 1px 0 1px; /* Slimmer divider */
            }
            .connect-view-left-pane, .connect-view-right-pane {
                -fx-background-color: #181818; /* Match overall content area background */
                -fx-padding: 10px;
            }
            .connect-view-left-pane > .label, /* Labels directly in these panes */
            .connect-view-right-pane > .label {
                -fx-text-fill: #FFFFFF; /* Ensure titles like "Friends" or "Chat with" are white */
            }
             .connect-view-add-friend-pane .label { /* Labels inside the "Add Friend" VBox */
                -fx-text-fill: #B3B3B3; /* Sub-labels should be standard text color */
             }

            .titled-pane {
                -fx-text-fill: #FFFFFF;
            }
            .titled-pane > .title {
                -fx-background-color: #1A1A1A;
                -fx-background-insets: 0;
                -fx-background-radius: 8 8 0 0; /* Slightly rounded top corners */
                -fx-padding: 8px 10px;
            }
            .titled-pane > .title > .arrow-button .arrow {
                -fx-background-color: #FFFFFF;
            }
            .titled-pane > .content {
                -fx-background-color: #2A2A2A;
                -fx-border-color: #1A1A1A;
                -fx-border-width: 0 1px 1px 1px;
                -fx-padding: 10px;
                -fx-background-radius: 0 0 8 8; /* Slightly rounded bottom corners */
            }
            /* General label styling inside TitledPane content */
            .titled-pane > .content .label {
                -fx-text-fill: #B3B3B3;
            }
            /* Specific override for labels styled as .title-label inside TitledPane content */
            .titled-pane > .content .label.title-label {
                 -fx-text-fill: #FFFFFF;
            }
            .titled-pane > .content .list-view,
            .titled-pane > .content .text-area {
                -fx-background-color: #2A2A2A; /* Content background */
            }
            .titled-pane > .content .list-view .list-cell {
                -fx-background-color: #2A2A2A;
                -fx-text-fill: #FFFFFF;
            }
            .titled-pane > .content .list-view .list-cell:filled:selected {
                 -fx-background-color: #1DB954;
                 -fx-text-fill: white;
            }
            .titled-pane > .content .list-view .list-cell:filled:hover {
                 -fx-background-color: #3A3A3A;
            }
             .titled-pane > .content .list-view .placeholder .label {
                -fx-text-fill: #B3B3B3;
            }
             .titled-pane > .content .text-area .content {
                 -fx-background-color: #2A2A2A; /* Ensure text area actual content bg is consistent */
            }

            /* ScrollPane styling */
            .scroll-pane {
               -fx-background-color: #181818; /* Match content area */
               -fx-padding: 1px; /* Minor padding to prevent content touching edge of scrollbar */
            }
            .scroll-pane > .viewport { /* Viewport should be transparent to show scroll-pane bg */
               -fx-background-color: transparent;
            }
            .scroll-bar:horizontal .track,
            .scroll-bar:vertical .track{
                -fx-background-color: transparent;
                -fx-border-color: transparent;
                -fx-background-radius: 0em;
                -fx-border-radius:2em;
            }
            .scroll-bar:horizontal .increment-button ,
            .scroll-bar:horizontal .decrement-button {
                -fx-background-color:transparent;
                -fx-background-radius: 0em;
                -fx-padding:0 0 10 0;
            }
            .scroll-bar:vertical .increment-button ,
            .scroll-bar:vertical .decrement-button {
                -fx-background-color:transparent;
                -fx-background-radius: 0em;
                -fx-padding:0 10 0 0;
            }
            .scroll-bar .increment-arrow,
            .scroll-bar .decrement-arrow{
                -fx-shape: " "; /* Hide default arrows */
                -fx-padding:0;
            }
            .scroll-bar:horizontal .thumb,
            .scroll-bar:vertical .thumb {
                -fx-background-color: #3A3A3A; /* Darker thumb */
                -fx-background-insets: 2, 0, 0;
                -fx-background-radius: 2em; /* Rounded thumb */
            }
            .scroll-bar:horizontal .thumb:hover,
            .scroll-bar:vertical .thumb:hover {
                -fx-background-color: #505050; /* Lighter thumb on hover */
            }
             .profile-picture-chooser {
                 -fx-alignment: center-left;
                 -fx-padding: 10px;
                 -fx-border-color: #3A3A3A;
                 -fx-border-width: 1px;
                 -fx-border-radius: 5px;
                 -fx-background-color: #2A2A2A;
                 -fx-background-radius: 5px;
             }
             .profile-picture-chooser .label {
                 -fx-text-fill: #B3B3B3;
             }
            """;

    private static final String LIGHT_CSS_STYLES = """
            .root {
                -fx-background-color: #FFFFFF;
            }
            .sidebar {
                -fx-background-color: #F0F0F0;
                -fx-padding: 20px;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);
            }
            .sidebar-button {
                -fx-background-color: transparent;
                -fx-text-fill: #333333;
                -fx-font-family: 'Arial';
                -fx-font-size: 16px;
                -fx-padding: 10px 20px;
                -fx-alignment: center-left;
                -fx-cursor: hand;
            }
            .sidebar-button:hover {
                -fx-background-color: #E0E0E0;
                -fx-text-fill: #000000;
                -fx-background-radius: 5px;
            }
            .sidebar-button:selected {
                -fx-background-color: #1DB954; /* Keep green for selection */
                -fx-text-fill: #FFFFFF;
                -fx-font-weight: bold;
                -fx-background-radius: 5px;
                 -fx-effect: innershadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 1);
            }
            .sidebar-profile-image {
                -fx-padding: 5px;
                -fx-border-color: #D0D0D0;
                -fx-border-width: 1px;
                -fx-border-radius: 5px;
            }
            .header {
                -fx-background-color: transparent;
                -fx-padding: 15px 30px;
            }
            .header-title {
                -fx-font-family: 'Arial';
                -fx-font-size: 28px;
                -fx-font-weight: bold;
                -fx-text-fill: #000000;
            }
            .header-subtitle {
                -fx-font-family: 'Arial';
                -fx-font-size: 14px;
                -fx-text-fill: #666666;
            }
            .content-area {
                -fx-background-color: #F8F8F8;
                -fx-background-radius: 10px;
                -fx-padding: 20px;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);
            }
            .primary-button {
                -fx-background-color: #1DB954;
                -fx-text-fill: #FFFFFF;
                -fx-font-family: 'Arial';
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-padding: 10px 20px;
                -fx-background-radius: 25px;
                -fx-cursor: hand;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);
            }
            .primary-button:hover {
                -fx-background-color: #1ED760;
                -fx-effect: dropshadow(gaussian, #1DB954, 15, 0.7, 0, 0);
            }
            .primary-button:focused {
                -fx-border-color: #000000;
                -fx-border-width: 2px;
                -fx-border-radius: 25px;
                 -fx-effect: dropshadow(gaussian, #1DB954, 10, 0.5, 0, 0);
            }
            .mood-button {
                -fx-background-color: #E0E0E0;
                -fx-text-fill: #333333;
                -fx-font-family: 'Arial';
                -fx-font-size: 14px;
                -fx-background-radius: 10px;
                -fx-padding: 12px;
                -fx-cursor: hand;
                -fx-effect: innershadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);
            }
            .mood-button:hover {
                -fx-background-color: #D0D0D0;
                -fx-text-fill: #000000;
            }
            .mood-button:focused {
                -fx-border-color: #1DB954;
                -fx-border-width: 1px;
                -fx-border-radius: 10px;
            }
            .mood-button-selected {
                 -fx-background-color: #1DB954;
                 -fx-text-fill: #FFFFFF;
                 -fx-font-weight: bold;
                 -fx-effect: innershadow(gaussian, rgba(0,0,0,0.3), 8, 0, 1, 1);
            }

            .mood-button-happy {
                -fx-background-color: #1DB954;
                -fx-text-fill: #FFFFFF;
            }
            .mood-button-sad {
                -fx-background-color: #FF5555;
                -fx-text-fill: #FFFFFF;
            }
            .mood-button-neutral {
                -fx-background-color: #A0A0A0;
                -fx-text-fill: #FFFFFF;
            }
            .prompt-label {
                -fx-background-color: #F0F0F0;
                -fx-padding: 15px;
                -fx-background-radius: 10px;
                -fx-font-family: 'Arial';
                -fx-font-size: 14px;
                -fx-text-fill: #666666;
                -fx-border-color: #D0D0D0;
                -fx-border-width: 1px;
                -fx-border-radius: 10px;
            }
            .title-label {
                -fx-font-family: 'Arial';
                -fx-font-size: 18px;
                -fx-font-weight: bold;
                -fx-text-fill: #000000;
            }
            .chart {
                -fx-background-color: #F0F0F0;
                -fx-background-radius: 10px;
            }
            .chart-plot-background { -fx-background-color: transparent; }
            .axis { -fx-tick-label-fill: #666666; }
            .axis-label { -fx-text-fill: #000000; }
            .chart-title { -fx-text-fill: #000000; -fx-font-size: 16px;}
            .chart-line-symbol { -fx-background-radius: 5px; -fx-padding: 5px; }
            .chart-series-line { -fx-stroke-width: 2px; }
            .default-color0.chart-line-symbol { -fx-background-color: #1DB954, #F8F8F8; -fx-background-insets: 0, 2; }
            .default-color0.chart-series-line { -fx-stroke: #1DB954; }


            .text-area, .combo-box, .date-picker, .text-field, .password-field, .list-view {
                -fx-font-family: 'Arial';
                -fx-font-size: 14px;
                -fx-background-color: #F0F0F0;
                -fx-text-fill: #000000;
                -fx-prompt-text-fill: #999999;
                -fx-border-color: #D0D0D0;
                -fx-border-width: 1px;
                -fx-border-radius: 5px;
            }
            .text-area .content, .list-view .list-cell {
                -fx-background-color: #F0F0F0;
                -fx-text-fill: #000000;
            }
             .list-view .list-cell:filled:selected, .list-view .list-cell:filled:selected:focused {
                -fx-background-color: #1DB954;
                -fx-text-fill: white;
            }
            .list-view .list-cell:filled:hover {
                -fx-background-color: #E0E0E0;
            }
             .list-view .placeholder .label {
                -fx-text-fill: #999999;
            }

            .text-area:focused, .combo-box:focused, .date-picker:focused, .text-field:focused, .password-field:focused, .list-view:focused {
                -fx-border-color: #1DB954;
                -fx-border-width: 2px;
                 -fx-effect: dropshadow(gaussian, #1DB954, 5, 0.2, 0, 0);
            }
            .insights-title {
                -fx-font-family: 'Arial';
                -fx-font-size: 24px;
                -fx-font-weight: bold;
                -fx-text-fill: #000000;
            }
            .summary-pane {
                -fx-background-color: #F0F0F0;
                -fx-background-radius: 10px;
                -fx-padding: 15px;
            }
            .summary-label {
                -fx-font-family: 'Arial';
                -fx-font-size: 14px;
                -fx-text-fill: #666666;
            }
            .tooltip {
                -fx-background-color: #FFFFFF;
                -fx-text-fill: #000000;
                -fx-font-family: 'Arial';
                -fx-font-size: 12px;
                -fx-background-radius: 5px;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 5, 0, 0, 1);
            }
            .login-pane {
                -fx-background-color: #F8F8F8;
                -fx-background-radius: 10px;
                -fx-padding: 40px;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);
                -fx-max-width: 450px;
                -fx-max-height: 600px;
            }
            .login-title {
                -fx-font-family: 'Arial';
                -fx-font-size: 28px;
                -fx-font-weight: bold;
                -fx-text-fill: #000000;
                -fx-alignment: center;
            }
            .history-filter-label {
                 -fx-font-family: 'Arial';
                 -fx-font-size: 14px;
                 -fx-text-fill: #666666;
                 -fx-padding: 5px 0 0 0;
            }
            .hyperlink {
                -fx-text-fill: #1DB954;
                -fx-border-color: transparent;
            }
            .hyperlink:hover {
                -fx-underline: true;
                -fx-text-fill: #1ED760;
            }
            .dialog-pane {
                -fx-background-color: #F8F8F8;
            }
            .dialog-pane .label {
                 -fx-text-fill: #000000;
            }
            .dialog-pane .button {
                 -fx-background-color: #E0E0E0;
                 -fx-text-fill: #000000;
                 -fx-border-color: #D0D0D0;
            }
            .dialog-pane .button:hover {
                -fx-background-color: #D0D0D0;
            }
            .dialog-pane .header-panel {
                 -fx-background-color: #F0F0F0;
            }
            .dialog-pane .header-panel .label {
                 -fx-text-fill: #000000;
                 -fx-font-style: italic;
            }
            .chat-message-me {
                -fx-text-fill: #1DB954;
                -fx-font-weight: bold;
            }
            .chat-message-them {
                -fx-text-fill: #666666;
            }
            .list-cell:filled:selected .chat-message-me,
            .list-cell:filled:selected .chat-message-them {
                -fx-text-fill: white;
            }

            .split-pane {
                -fx-background-color: #F8F8F8;
            }
            .split-pane > .split-pane-divider {
                -fx-background-color: #D0D0D0;
                -fx-padding: 0 1px 0 1px;
            }
            .connect-view-left-pane, .connect-view-right-pane {
                -fx-background-color: #F8F8F8;
                -fx-padding: 10px;
            }
            .connect-view-left-pane > .label,
            .connect-view-right-pane > .label {
                -fx-text-fill: #000000;
            }
             .connect-view-add-friend-pane .label {
                -fx-text-fill: #666666;
             }

            .titled-pane {
                -fx-text-fill: #000000;
            }
            .titled-pane > .title {
                -fx-background-color: #F0F0F0;
                -fx-background-insets: 0;
                -fx-background-radius: 8 8 0 0;
                -fx-padding: 8px 10px;
            }
            .titled-pane > .title > .arrow-button .arrow {
                -fx-background-color: #000000;
            }
            .titled-pane > .content {
                -fx-background-color: #FFFFFF;
                -fx-border-color: #F0F0F0;
                -fx-border-width: 0 1px 1px 1px;
                -fx-padding: 10px;
                -fx-background-radius: 0 0 8 8;
            }
            .titled-pane > .content .label {
                -fx-text-fill: #666666;
            }
            .titled-pane > .content .label.title-label {
                 -fx-text-fill: #000000;
            }
            .titled-pane > .content .list-view,
            .titled-pane > .content .text-area {
                -fx-background-color: #FFFFFF;
            }
            .titled-pane > .content .list-view .list-cell {
                -fx-background-color: #FFFFFF;
                -fx-text-fill: #000000;
            }
            .titled-pane > .content .list-view .list-cell:filled:selected {
                 -fx-background-color: #1DB954;
                 -fx-text-fill: white;
            }
            .titled-pane > .content .list-view .list-cell:filled:hover {
                 -fx-background-color: #E0E0E0;
            }
             .titled-pane > .content .list-view .placeholder .label {
                -fx-text-fill: #999999;
            }
             .titled-pane > .content .text-area .content {
                 -fx-background-color: #FFFFFF;
            }

            .scroll-pane {
               -fx-background-color: #F8F8F8;
               -fx-padding: 1px;
            }
            .scroll-pane > .viewport {
               -fx-background-color: transparent;
            }
            .scroll-bar:horizontal .track,
            .scroll-bar:vertical .track{
                -fx-background-color: transparent;
                -fx-border-color: transparent;
                -fx-background-radius: 0em;
                -fx-border-radius:2em;
            }
            .scroll-bar:horizontal .increment-button ,
            .scroll-bar:horizontal .decrement-button {
                -fx-background-color:transparent;
                -fx-background-radius: 0em;
                -fx-padding:0 0 10 0;
            }
            .scroll-bar:vertical .increment-button ,
            .scroll-bar:vertical .decrement-button {
                -fx-background-color:transparent;
                -fx-background-radius: 0em;
                -fx-padding:0 10 0 0;
            }
            .scroll-bar .increment-arrow,
            .scroll-bar .decrement-arrow{
                -fx-shape: " ";
                -fx-padding:0;
            }
            .scroll-bar:horizontal .thumb,
            .scroll-bar:vertical .thumb {
                -fx-background-color: #D0D0D0;
                -fx-background-insets: 2, 0, 0;
                -fx-background-radius: 2em;
            }
            .scroll-bar:horizontal .thumb:hover,
            .scroll-bar:vertical .thumb:hover {
                -fx-background-color: #A0A0A0;
            }
             .profile-picture-chooser {
                 -fx-alignment: center-left;
                 -fx-padding: 10px;
                 -fx-border-color: #D0D0D0;
                 -fx-border-width: 1px;
                 -fx-border-radius: 5px;
                 -fx-background-color: #F0F0F0;
                 -fx-background-radius: 5px;
             }
             .profile-picture-chooser .label {
                 -fx-text-fill: #666666;
             }
            """;
    private String tempDarkCssFileUrl = null;
    private String tempLightCssFileUrl = null;


    @Override
    public void start(Stage primaryStage) {
        instance = this;
        this.mainApplicationStage = primaryStage;

        // Listener to handle maximization correctly and avoid taskbar overlap
        primaryStage.maximizedProperty().addListener((obs, wasMaximized, isMaximized) -> {
            if (isMaximized) {
                // When maximized, adjust the stage to fit the visual bounds of the screen
                Screen screen = Screen.getPrimary();
                Rectangle2D bounds = screen.getVisualBounds();
                primaryStage.setX(bounds.getMinX());
                primaryStage.setY(bounds.getMinY());
                primaryStage.setWidth(bounds.getWidth());
                primaryStage.setHeight(bounds.getHeight());
            }
        });

        Stage loadingStage = new Stage(StageStyle.UNDECORATED);
        VBox loadingLayout = new VBox(20);
        loadingLayout.setAlignment(Pos.CENTER);
        loadingLayout.setStyle("-fx-background-color: #181818; -fx-border-color: #1DB954; -fx-border-width: 2; -fx-padding: 30; -fx-background-radius: 10; -fx-border-radius: 10;");

        Label loadingLabel = new Label("Mind Matters is loading...");
        loadingLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 18px;");
        ProgressIndicator progressIndicator = new ProgressIndicator();
        loadingLayout.getChildren().addAll(loadingLabel, progressIndicator);
        Scene loadingScene = new Scene(loadingLayout, 350, 200);
        loadingStage.setScene(loadingScene);
        loadingStage.show();

        Task<Void> startupTask = getVoidTask(loadingStage);

        new Thread(startupTask).start();

        mainApplicationStage.setOnCloseRequest(event -> {
            if (currentUser != null) {
                try {
                    saveDataForCurrentUser();
                } catch (IOException ex) {
                    showAlert("Save Error", "Failed to save data for " + currentUser.username + " on exit: " + ex.getMessage(), Alert.AlertType.ERROR);
                }
            }
            try {
                saveUserProfiles();
            } catch (IOException ex) {
                showAlert("Save Error", "Failed to save user profiles on exit: " + ex.getMessage(), Alert.AlertType.ERROR);
            }
            if (networkManager != null) {
                networkManager.shutdown();
            }
            if (tempDarkCssFileUrl != null) {
                try {
                    Files.deleteIfExists(Paths.get(new URI(tempDarkCssFileUrl)));
                } catch (IOException | URISyntaxException e) {
                    System.err.println("Warning: Failed to delete temporary dark CSS file: " + e.getMessage());
                }
            }
            if (tempLightCssFileUrl != null) {
                try {
                    Files.deleteIfExists(Paths.get(new URI(tempLightCssFileUrl)));
                } catch (IOException | URISyntaxException e) {
                    System.err.println("Warning: Failed to delete temporary light CSS file: " + e.getMessage());
                }
            }
            Platform.exit();
            System.exit(0);
        });
    }

    private Task<Void> getVoidTask(Stage loadingStage) {
        Task<Void> startupTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Initializing core systems...");
                updateProgress(1, 5);
                Thread.sleep(300);

                updateMessage("Ensuring data directory...");
                ensureDataDirectory();
                updateProgress(2, 5);
                Thread.sleep(300);

                updateMessage("Loading user profiles...");
                loadUserProfiles();
                updateProgress(3, 5);
                Thread.sleep(300);

                updateMessage("Preparing suggestions engine...");
                initializeSuggestionRules();
                updateProgress(4, 5);
                Thread.sleep(300);

                updateMessage("Setting up well-being tasks...");
                initializeWellBeingTasks();
                updateProgress(5, 5);
                Thread.sleep(300);
                return null;
            }
        };

        startupTask.setOnSucceeded(e -> {
            loadingStage.close();
            setupInitialAuthScene(mainApplicationStage);
            mainApplicationStage.setMinWidth(900);
            mainApplicationStage.setMinHeight(700);
            mainApplicationStage.show();
        });

        startupTask.setOnFailed(e -> {
            loadingStage.close();
            Throwable exception = startupTask.getException();
            String errorMessage = "Could not initialize the application.";
            if (exception != null) {
                errorMessage += ": " + exception.getMessage();
                System.err.println("Fatal Startup Error encountered: " + exception.getMessage() + ". Full stack trace follows for diagnosis:");
                exception.printStackTrace(System.err);
            } else {
                System.err.println("Fatal Startup Error with no exception details.");
            }
            showAlert("Fatal Startup Error", errorMessage, Alert.AlertType.ERROR);
            Platform.exit();
        });
        return startupTask;
    }


    private void setupInitialAuthScene(Stage stage) {
        StackPane authRoot = new StackPane();
        authRoot.getStyleClass().add("root");
        Scene authScene = new Scene(authRoot);
        applyStylesToScene(authScene, "dark"); // Default to dark


        if (allUsers.isEmpty()) {
            VBox registrationPage = createRegistrationPage(stage, authScene);
            authRoot.getChildren().add(registrationPage);
            stage.setTitle("Mind Matters - Create Account");
        } else {
            VBox loginPage = createLoginPage(stage, authScene);
            authRoot.getChildren().add(loginPage);
            stage.setTitle("Mind Matters - Login");
        }
        authRoot.setAlignment(Pos.CENTER);
        stage.setScene(authScene);
    }


    private void applyStylesToScene(Scene scene, String theme) {
        if (scene == null) return;
        String cssUrl = getThemeCssUrl(theme);
        if (cssUrl != null && !scene.getStylesheets().contains(cssUrl)) {
            scene.getStylesheets().clear();
            scene.getStylesheets().add(cssUrl);
        }
    }

    private String getThemeCssUrl(String theme) {
        if ("light".equals(theme)) {
            if (tempLightCssFileUrl == null) {
                try {
                    File cssFile = File.createTempFile("styles-neuro-light", ".css");
                    Files.writeString(cssFile.toPath(), LIGHT_CSS_STYLES);
                    tempLightCssFileUrl = cssFile.toURI().toString();
                    cssFile.deleteOnExit();
                } catch (IOException e) {
                    System.err.println("FATAL: Failed to create temporary light CSS file: " + e.getMessage());
                    showAlert("CSS Error", "Critical error applying light theme styles. UI may not appear correctly.", Alert.AlertType.ERROR);
                    return null;
                }
            }
            return tempLightCssFileUrl;
        } else {
            if (tempDarkCssFileUrl == null) {
                try {
                    File cssFile = File.createTempFile("styles-neuro-dark", ".css");
                    Files.writeString(cssFile.toPath(), DARK_CSS_STYLES);
                    tempDarkCssFileUrl = cssFile.toURI().toString();
                    cssFile.deleteOnExit();
                } catch (IOException e) {
                    System.err.println("FATAL: Failed to create temporary dark CSS file: " + e.getMessage());
                    showAlert("CSS Error", "Critical error applying dark theme styles. UI may not appear correctly.", Alert.AlertType.ERROR);
                    return null;
                }
            }
            return tempDarkCssFileUrl;
        }
    }


    private VBox createLoginPage(Stage stage, Scene scene) {
        VBox loginBox = new VBox(20);
        loginBox.getStyleClass().add("login-pane");
        loginBox.setAlignment(Pos.CENTER);

        Label title = new Label("Mind Matters");
        title.getStyleClass().add("login-title");
        Label subtitle = new Label("Log in to your journal");
        subtitle.getStyleClass().add("summary-label");
        subtitle.setAlignment(Pos.CENTER);

        Label usernameLabel = new Label("Username");
        usernameLabel.getStyleClass().add("title-label");
        TextField usernameField = new TextField();
        usernameField.getStyleClass().add("text-field");
        usernameField.setPromptText("Enter username");
        usernameField.setMaxWidth(300);

        Label passwordLabel = new Label("Password");
        passwordLabel.getStyleClass().add("title-label");
        PasswordField passwordField = new PasswordField();
        passwordField.getStyleClass().add("password-field");
        passwordField.setPromptText("Enter password");
        passwordField.setMaxWidth(300);

        Button loginButton = new Button("Log In");
        loginButton.getStyleClass().add("primary-button");
        applyButtonAnimations(loginButton);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("summary-label");
        errorLabel.setTextFill(Color.web("#FF5555"));
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(300);
        errorLabel.setVisible(false);

        Hyperlink createAccountLink = new Hyperlink("Don't have an account? Create one");
        createAccountLink.setOnAction(e -> {
            VBox registrationPage = createRegistrationPage(stage, scene);
            if (scene.getRoot() instanceof Pane) {
                ((Pane) scene.getRoot()).getChildren().setAll(registrationPage);
            } else {
                scene.setRoot(registrationPage);
            }
            stage.setTitle("Mind Matters - Create Account");
        });


        loginButton.setOnAction(e -> handleLogin(stage, scene, usernameField, passwordField, errorLabel, loginBox));
        passwordField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleLogin(stage, scene, usernameField, passwordField, errorLabel, loginBox);
            }
        });
        usernameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) passwordField.requestFocus();
        });

        loginBox.getChildren().addAll(
                title, subtitle,
                usernameLabel, usernameField,
                passwordLabel, passwordField, errorLabel, loginButton, createAccountLink
        );
        return loginBox;
    }

    private VBox createRegistrationPage(Stage stage, Scene scene) {
        VBox regBox = new VBox(15);
        regBox.getStyleClass().add("login-pane");
        regBox.setAlignment(Pos.CENTER);

        Label title = new Label("Create Your Account");
        title.getStyleClass().add("login-title");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Choose a username");
        usernameField.setMaxWidth(300);
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Create a password");
        passwordField.setMaxWidth(300);
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm password");
        confirmPasswordField.setMaxWidth(300);

        // Added role selection
        Label roleLabel = new Label("Role:");
        roleLabel.getStyleClass().add("title-label");
        ComboBox<String> roleCombo = new ComboBox<>(FXCollections.observableArrayList("Patient", "Doctor"));
        roleCombo.setValue("Patient");
        roleCombo.getStyleClass().add("combo-box");

        Label profilePicLabel = new Label("Profile Picture (Optional):");
        profilePicLabel.getStyleClass().add("title-label");
        ImageView tempProfileImageView = new ImageView();
        tempProfileImageView.setFitHeight(80); tempProfileImageView.setFitWidth(80);
        tempProfileImageView.setPreserveRatio(true);
        setDefaultProfileImage(tempProfileImageView);
        final File[] selectedProfilePicFile = {null};

        Button choosePicButton = new Button("Choose Image");
        choosePicButton.getStyleClass().add("sidebar-button");
        choosePicButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Profile Picture");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
            );
            File file = fileChooser.showOpenDialog(stage);
            if (file != null) {
                try {
                    Image image = new Image(file.toURI().toString(), 80, 80, true, true);
                    if (!image.isError()) {
                        tempProfileImageView.setImage(image);
                        selectedProfilePicFile[0] = file;
                    } else {
                        showAlert("Image Error", "Could not load selected image.", Alert.AlertType.WARNING);
                    }
                } catch (Exception ex) {
                    showAlert("Image Error", "Could not load selected image: " + ex.getMessage(), Alert.AlertType.WARNING);
                }
            }
        });
        Label picNote = new Label("Passport size recommended (will be displayed as square).");
        picNote.getStyleClass().add("summary-label");


        HBox picBox = new HBox(10, tempProfileImageView, new VBox(5, choosePicButton, picNote));
        picBox.setAlignment(Pos.CENTER_LEFT);
        picBox.getStyleClass().add("profile-picture-chooser");
        picBox.setMaxWidth(300);


        Button registerButton = new Button("Register");
        registerButton.getStyleClass().add("primary-button");
        applyButtonAnimations(registerButton);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("summary-label");
        errorLabel.setTextFill(Color.web("#FF5555"));
        errorLabel.setMaxWidth(300); errorLabel.setWrapText(true);
        errorLabel.setVisible(false);

        Hyperlink loginLink = new Hyperlink("Already have an account? Log in");
        loginLink.setOnAction(e -> {
            VBox loginPage = createLoginPage(stage, scene);
            if (scene.getRoot() instanceof Pane) {
                ((Pane) scene.getRoot()).getChildren().setAll(loginPage);
            } else {
                scene.setRoot(loginPage);
            }
            stage.setTitle("Mind Matters - Login");
        });

        registerButton.setOnAction(e -> handleRegistration(
                stage, scene, usernameField, passwordField, confirmPasswordField,
                selectedProfilePicFile[0], errorLabel, regBox, roleCombo.getValue()));

        regBox.getChildren().addAll(
                title,
                new Label("Username:"), usernameField,
                new Label("Password:"), passwordField,
                new Label("Confirm Password:"), confirmPasswordField,
                roleLabel, roleCombo,
                profilePicLabel, picBox,
                errorLabel, registerButton, loginLink
        );
        regBox.getChildren().forEach(node -> {
            if(node instanceof Label && regBox.getChildren().indexOf(node) > 1 && !node.getStyleClass().contains("login-title") && !node.getStyleClass().contains("summary-label") && !node.getStyleClass().contains("title-label")){
                node.getStyleClass().add("title-label");
            }
        });
        return regBox;
    }


    private void handleLogin(Stage stage, Scene scene, TextField usernameField, PasswordField passwordField, Label errorLabel, Node loginBoxNode) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.trim().isEmpty()) {
            showError(errorLabel, "Username cannot be empty."); applyShakeAnimation(usernameField); usernameField.requestFocus(); return;
        }
        if (password.trim().isEmpty()) {
            showError(errorLabel, "Password cannot be empty."); applyShakeAnimation(passwordField); passwordField.requestFocus(); return;
        }

        Optional<User> foundUserOpt = allUsers.stream().filter(u -> u.username.equalsIgnoreCase(username)).findFirst();

        if (foundUserOpt.isPresent()) {
            User userToLogin = foundUserOpt.get();
            String hashedInputPassword;
            try {
                hashedInputPassword = hashPassword(password);
            } catch (Exception e) {
                showError(errorLabel, "Error processing password."); applyShakeAnimation(loginBoxNode); return;
            }

            if (userToLogin.hashedPassword.equals(hashedInputPassword)) {
                this.currentUser = userToLogin;
                errorLabel.setVisible(false);
                applyStylesToScene(scene, currentUser.themePreference);
                loadAndTransitionToMainApp(stage, scene);
            } else {
                showError(errorLabel, "Invalid username or password."); applyShakeAnimation(loginBoxNode); passwordField.clear(); passwordField.requestFocus();
            }
        } else {
            showError(errorLabel, "User not found."); applyShakeAnimation(loginBoxNode); usernameField.clear(); passwordField.clear(); usernameField.requestFocus();
        }
    }

    private void handleRegistration(Stage stage, Scene scene, TextField usernameField, PasswordField passwordField, PasswordField confirmField, File profilePicFile, Label errorLabel, Node regBoxNode, String role) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmField.getText();

        if (username.length() < 3) {
            showError(errorLabel, "Username must be at least 3 characters."); applyShakeAnimation(usernameField); usernameField.requestFocus(); return;
        }
        if (password.length() < 5) {
            showError(errorLabel, "Password must be at least 5 characters."); applyShakeAnimation(passwordField); passwordField.requestFocus(); return;
        }
        if (!password.equals(confirmPassword)) {
            showError(errorLabel, "Passwords do not match."); applyShakeAnimation(confirmField); confirmField.requestFocus(); return;
        }
        if (allUsers.stream().anyMatch(u -> u.username.equalsIgnoreCase(username))) {
            showError(errorLabel, "Username already exists. Please choose another."); applyShakeAnimation(usernameField); usernameField.requestFocus(); return;
        }

        try {
            String hashedPassword = hashPassword(password);
            User newUser = new User(username, hashedPassword, role);

            File userDir = new File(getUserDataDir(username));
            if (!userDir.exists()) {
                if (!userDir.mkdirs()) {
                    showError(errorLabel, "Could not create user data directory."); return;
                }
            }

            if (profilePicFile != null) {
                Path sourcePath = profilePicFile.toPath();
                Path targetPath = Paths.get(getUserSpecificFilePath(username, USER_PROFILE_PIC_FILE_NAME));
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                newUser.profilePicturePath = USER_PROFILE_PIC_FILE_NAME;
            }

            allUsers.add(newUser);
            saveUserProfiles();

            this.currentUser = newUser;
            showError(errorLabel, ""); errorLabel.setVisible(false);
            showAlert("Registration Successful", "Welcome, " + username + "! Your account has been created.", Alert.AlertType.INFORMATION);
            loadAndTransitionToMainApp(stage, scene);

        } catch (Exception e) {
            showError(errorLabel, "Registration failed: " + e.getMessage());
            System.err.println("Registration failure occurred: " + e.getMessage() + ". Full stack trace for diagnosis:");
            e.printStackTrace(System.err);
            applyShakeAnimation(regBoxNode);
        }
    }

    private void loadAndTransitionToMainApp(Stage stage, Scene scene) {
        try {
            loadDataForCurrentUser();
            initializeNetworkManagerForCurrentUser();
            transitionToMainApp(stage, scene);
        } catch (IOException e) {
            String usernameForError = (currentUser != null ? currentUser.username : "unknown user");
            showAlert("Data Load Error", "Failed to load data for " + usernameForError + ": " + e.getMessage(), Alert.AlertType.ERROR);
            System.err.println("Data Load Error for " + usernameForError + ": " + e.getMessage() + ". Stack trace for details:");
            e.printStackTrace(System.err);
            logoutUser(stage, scene);
        }
    }

    private void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    private void logoutUser(Stage stage, Scene scene) {
        if (currentUser != null) {
            try {
                saveDataForCurrentUser();
            } catch (IOException e) {
                showAlert("Save Error", "Failed to save data for " + currentUser.username + " during logout: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }

        if (networkManager != null) {
            networkManager.shutdown();
            networkManager = null;
        }

        currentUser = null;
        journalEntries = null;
        moodEntries = null;
        friendsList = null;
        myApplicationInfo = null;
        currentChatFriend = null;
        prescriptionEntries = null; // Clear prescriptions

        StackPane authRoot = new StackPane();
        authRoot.getStyleClass().add("root");
        authRoot.setAlignment(Pos.CENTER);

        VBox loginPage = createLoginPage(stage, scene);
        authRoot.getChildren().add(loginPage);

        scene.setRoot(authRoot);
        applyStylesToScene(scene, "dark"); // Reset to dark on logout
        stage.setTitle("Mind Matters - Login");
    }


    private void transitionToMainApp(Stage stage, Scene scene) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setPadding(new Insets(10,10,0,10)); // Padding for top, right, and left. Bottom is handled by margin on contentArea

        ScrollPane sidebar = createSidebar();
        root.setLeft(sidebar);
        applySlideInAnimation(sidebar, -60, 0);

        HBox header = createHeader();
        root.setTop(header);
        applySlideInAnimation(header, 0, -30);

        contentArea = new StackPane();
        contentArea.getStyleClass().add("content-area");
        // Margin on content area to create space from the bottom of the window
        BorderPane.setMargin(contentArea, new Insets(10, 0, 10, 10));

        // Create content views
        VBox journalViewContent = createJournalView();
        VBox moodTrackerViewContent = createMoodTrackerView();
        LineChart<String, Number> moodChart = createMoodChart();
        VBox insightsViewContent = createInsightsView(moodChart);
        VBox exportViewContent = createExportView();
        VBox historyViewContent = createHistoryView();
        VBox connectViewContent = createConnectView();
        VBox wellBeingViewContent = createWellBeingView();
        VBox profileViewContent = createProfileView(stage);
        VBox prescriptionsViewContent = createPrescriptionsView(); // New view
        VBox helpViewContent = createHelpView(); // Added help view

        // Wrap each view's content in a configured ScrollPane
        Node journalView = createConfiguredScrollPane(journalViewContent);
        Node moodTrackerView = createConfiguredScrollPane(moodTrackerViewContent);
        Node insightsView = createConfiguredScrollPane(insightsViewContent);
        Node exportView = createConfiguredScrollPane(exportViewContent);
        Node historyView = createConfiguredScrollPane(historyViewContent);
        Node connectView = createConfiguredScrollPane(connectViewContent);
        Node wellBeingView = createConfiguredScrollPane(wellBeingViewContent);
        Node profileView = createConfiguredScrollPane(profileViewContent);
        Node prescriptionsView = createConfiguredScrollPane(prescriptionsViewContent); // New view
        Node helpView = createConfiguredScrollPane(helpViewContent); // Added

        contentArea.getChildren().addAll(journalView, moodTrackerView, insightsView, exportView, historyView, connectView, wellBeingView, profileView, prescriptionsView, helpView);
        showView(journalView);

        root.setCenter(contentArea);

        scene.setRoot(root);
        applyStylesToScene(scene, currentUser.themePreference);
        stage.setTitle("Mind Matters - " + (currentUser != null ? currentUser.username : "Guest"));

        if ("Doctor".equals(currentUser.role)) {
            showDoctorDashboard(root);
        }
    }

    private void showDoctorDashboard(BorderPane root) {
        // Simple doctor dashboard: list patients and view their data
        VBox doctorBox = new VBox(20);
        doctorBox.setPadding(new Insets(10));

        Label doctorTitle = new Label("Doctor Dashboard");
        doctorTitle.getStyleClass().add("insights-title");

        ListView<User> patientsListView = new ListView<>(FXCollections.observableArrayList(allUsers.stream().filter(u -> "Patient".equals(u.role)).toList()));
        patientsListView.getStyleClass().add("list-view");
        VBox.setVgrow(patientsListView, Priority.ALWAYS);

        patientsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                setText(empty ? null : user.username);
            }
        });

        TabPane patientDataTabs = new TabPane();
        patientDataTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(patientDataTabs, Priority.ALWAYS);

        patientsListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newPatient) -> {
            if (newPatient != null) {
                loadPatientDataAndPopulateTabs(patientDataTabs, newPatient);
            }
        });

        SplitPane doctorSplit = new SplitPane();
        doctorSplit.setDividerPositions(0.3);

        doctorBox.getChildren().addAll(doctorTitle, new Label("Patients:"), patientsListView);
        doctorSplit.getItems().addAll(doctorBox, patientDataTabs);

        root.setCenter(doctorSplit);
    }

    private void loadPatientDataAndPopulateTabs(TabPane tabs, User patient) {
        tabs.getTabs().clear();

        try {
            List<JournalEntry> patientJournals = loadSpecificUserData(patient.username, USER_JOURNAL_FILE_NAME, List.class, new ArrayList<>());
            List<MoodEntry> patientMoods = loadSpecificUserData(patient.username, USER_MOOD_FILE_NAME, List.class, new ArrayList<>());
            List<PrescriptionEntry> patientPrescriptions = loadSpecificUserData(patient.username, USER_PRESCRIPTIONS_FILE_NAME, List.class, new ArrayList<>());

            Tab journalTab = new Tab("Journals", createConfiguredScrollPane(createPatientJournalView(patientJournals)));
            Tab moodTab = new Tab("Moods", createConfiguredScrollPane(createPatientMoodView(patientMoods)));
            Tab prescriptionTab = new Tab("Prescriptions", createConfiguredScrollPane(createPatientPrescriptionsView(patientPrescriptions)));

            tabs.getTabs().addAll(journalTab, moodTab, prescriptionTab);
        } catch (Exception e) {
            showAlert("Load Error", "Failed to load data for " + patient.username + ": " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private VBox createPatientJournalView(List<JournalEntry> journals) {
        VBox box = new VBox(10);
        ListView<JournalEntry> list = new ListView<>(FXCollections.observableArrayList(journals));
        list.setPlaceholder(new Label("No journals."));
        TextArea contentArea = new TextArea();
        list.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> contentArea.setText(n != null ? n.content : ""));
        box.getChildren().addAll(list, contentArea);
        return box;
    }

    private VBox createPatientMoodView(List<MoodEntry> moods) {
        VBox box = new VBox(10);
        ListView<MoodEntry> list = new ListView<>(FXCollections.observableArrayList(moods));
        list.setPlaceholder(new Label("No moods."));
        TextArea notesArea = new TextArea();
        list.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> notesArea.setText(n != null ? n.notes : ""));
        box.getChildren().addAll(list, notesArea);
        return box;
    }

    private VBox createPatientPrescriptionsView(List<PrescriptionEntry> prescriptions) {
        VBox box = new VBox(10);
        ListView<PrescriptionEntry> list = new ListView<>(FXCollections.observableArrayList(prescriptions));
        list.setPlaceholder(new Label("No prescriptions."));
        TextArea detailsArea = new TextArea();
        list.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> detailsArea.setText(n != null ? n.prescriptionText : ""));
        box.getChildren().addAll(list, detailsArea);
        return box;
    }

    private VBox createHelpView() {
        VBox helpBox = new VBox(20);
        helpBox.setPadding(new Insets(10));

        Label title = new Label("Help & Tutorial");
        title.getStyleClass().add("insights-title");

        Accordion accordion = new Accordion();
        VBox.setVgrow(accordion, Priority.ALWAYS);

        TitledPane generalPane = new TitledPane("General Usage", new TextArea("This app helps track mental health. Use journal for entries, mood for daily feelings."));
        TitledPane doctorPane = new TitledPane("For Doctors", new TextArea("Log in as Doctor to view patient data."));
        TitledPane patientPane = new TitledPane("For Patients", new TextArea("Track your moods and journals daily."));

        accordion.getPanes().addAll(generalPane, doctorPane, patientPane);

        helpBox.getChildren().addAll(title, accordion);
        return helpBox;
    }

    private ScrollPane createSidebar() {
        VBox sidebarContent = new VBox(10);
        sidebarContent.getStyleClass().add("sidebar");
        sidebarContent.setPrefWidth(220);
        sidebarContent.setAlignment(Pos.TOP_CENTER);

        sidebarProfileImageView = new ImageView();
        sidebarProfileImageView.setFitHeight(100);
        sidebarProfileImageView.setFitWidth(100);
        sidebarProfileImageView.setPreserveRatio(false);
        sidebarProfileImageView.getStyleClass().add("sidebar-profile-image");
        Rectangle clip = new Rectangle(100, 100);
        clip.setArcWidth(20); clip.setArcHeight(20);
        sidebarProfileImageView.setClip(clip);
        if (currentUser != null) {
            loadAndSetUserProfileImage(currentUser, sidebarProfileImageView);
        } else {
            setDefaultProfileImage(sidebarProfileImageView);
        }


        Label usernameLabel = new Label(currentUser != null ? currentUser.username : "Guest");
        usernameLabel.getStyleClass().add("title-label");
        usernameLabel.setStyle("-fx-font-size: 16px;");
        VBox.setMargin(sidebarProfileImageView, new Insets(0,0,5,0));
        VBox.setMargin(usernameLabel, new Insets(0,0,15,0));


        Button journalBtn = createSidebarButton("Journal Entry", 0);
        Button moodBtn = createSidebarButton("Mood Tracker", 1);
        Button insightsBtn = createSidebarButton("Insights", 2);
        Button historyBtn = createSidebarButton("History", 4);
        Button wellBeingBtn = createSidebarButton("Well Being", 6);
        Button connectBtn = createSidebarButton("Connect", 5);
        Button exportBtn = createSidebarButton("Export Data", 3);
        Button profileBtn = createSidebarButton("Profile", 7);
        Button prescriptionsBtn = createSidebarButton("Pharmacy", 8); // New button
        Button helpBtn = createSidebarButton("Help", 9); // Added help button
        Button logoutBtn = new Button("Logout");
        logoutBtn.getStyleClass().addAll("sidebar-button", "primary-button");
        logoutBtn.setStyle("-fx-background-color: #c94f4f;");
        logoutBtn.setPrefWidth(Double.MAX_VALUE);
        applyButtonAnimations(logoutBtn);
        logoutBtn.setOnAction(e -> logoutUser(mainApplicationStage, mainApplicationStage.getScene()));


        journalBtn.getStyleClass().add("selected");

        sidebarContent.getChildren().addAll(sidebarProfileImageView, usernameLabel,
                journalBtn, moodBtn, insightsBtn, historyBtn, wellBeingBtn, connectBtn, exportBtn, profileBtn, prescriptionsBtn, helpBtn);
        VBox spacer = new VBox(); VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebarContent.getChildren().add(spacer);
        sidebarContent.getChildren().add(logoutBtn);

        ScrollPane sidebar = createConfiguredScrollPane(sidebarContent);
        sidebar.setFitToWidth(true);

        return sidebar;
    }

    private VBox createProfileView(Stage stage) {
        VBox profileBox = new VBox(20);
        profileBox.setAlignment(Pos.TOP_CENTER);
        profileBox.setPadding(new Insets(30));

        Label title = new Label((currentUser != null ? currentUser.username : "User") + "'s Profile");
        title.getStyleClass().add("insights-title");

        ImageView currentProfileImageView = new ImageView();
        currentProfileImageView.setFitHeight(150); currentProfileImageView.setFitWidth(150);
        currentProfileImageView.setPreserveRatio(false);
        Rectangle clip = new Rectangle(150,150); clip.setArcHeight(30); clip.setArcWidth(30);
        currentProfileImageView.setClip(clip);
        if (currentUser != null) {
            loadAndSetUserProfileImage(currentUser, currentProfileImageView);
        } else {
            setDefaultProfileImage(currentProfileImageView);
        }


        Button changePicButton = new Button("Change Profile Picture");
        changePicButton.getStyleClass().add("primary-button");
        applyButtonAnimations(changePicButton);
        changePicButton.setOnAction(e -> {
            if (currentUser == null) {
                showAlert("Error", "No user logged in.", Alert.AlertType.WARNING);
                return;
            }
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select New Profile Picture");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
            File newPicFile = fileChooser.showOpenDialog(stage);
            if (newPicFile != null) {
                try {
                    Path sourcePath = newPicFile.toPath();
                    Path targetDir = Paths.get(getUserDataDir(currentUser.username));
                    if (!Files.exists(targetDir)) Files.createDirectories(targetDir);
                    Path targetPath = targetDir.resolve(USER_PROFILE_PIC_FILE_NAME);

                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    currentUser.profilePicturePath = USER_PROFILE_PIC_FILE_NAME;
                    saveUserProfiles();

                    loadAndSetUserProfileImage(currentUser, currentProfileImageView);
                    if (sidebarProfileImageView != null) {
                        loadAndSetUserProfileImage(currentUser, sidebarProfileImageView);
                    }
                    showAlert("Success", "Profile picture updated.", Alert.AlertType.INFORMATION);
                } catch (IOException ex) {
                    showAlert("Error", "Could not save profile picture: " + ex.getMessage(), Alert.AlertType.ERROR);
                    System.err.println("Error saving profile picture for " + currentUser.username + ": " + ex.getMessage() + ". See logs if persistent.");
                }
            }
        });
        Label picNoteLabel = new Label("Passport size recommended (will be displayed as square).");
        picNoteLabel.getStyleClass().add("summary-label");

        // New: Theme selection
        Label themeLabel = new Label("Theme Preference:");
        themeLabel.getStyleClass().add("title-label");
        ComboBox<String> themeCombo = new ComboBox<>(FXCollections.observableArrayList("dark", "light"));
        themeCombo.setValue(currentUser.themePreference);
        themeCombo.getStyleClass().add("combo-box");
        themeCombo.setOnAction(e -> {
            String newTheme = themeCombo.getValue();
            currentUser.themePreference = newTheme;
            try {
                saveUserProfiles();
                applyStylesToScene(mainApplicationStage.getScene(), newTheme);
            } catch (IOException ex) {
                showAlert("Save Error", "Failed to save theme preference: " + ex.getMessage(), Alert.AlertType.ERROR);
            }
        });

        profileBox.getChildren().addAll(title, currentProfileImageView, changePicButton, picNoteLabel, themeLabel, themeCombo);
        return profileBox;
    }


    private void loadAndSetUserProfileImage(User user, ImageView imageView) {
        if (user == null || imageView == null) return;
        Image profileImage = null;
        if (user.profilePicturePath != null && !user.profilePicturePath.isEmpty()) {
            try {
                File picFile = new File(getUserSpecificFilePath(user.username, user.profilePicturePath));
                if (picFile.exists()) {
                    profileImage = new Image(picFile.toURI().toString(), imageView.getFitWidth(), imageView.getFitHeight(), false, true);
                }
            } catch (Exception e) {
                System.err.println("Error loading profile image for " + user.username + ": " + e.getMessage());
            }
        }
        if (profileImage != null && !profileImage.isError()) {
            imageView.setImage(profileImage);
        } else {
            setDefaultProfileImage(imageView);
        }
    }

    private void setDefaultProfileImage(ImageView imageView) {
        try (InputStream defaultStream = getClass().getResourceAsStream("default-profile.png")) {
            if (defaultStream != null) {
                imageView.setImage(new Image(defaultStream, imageView.getFitWidth(), imageView.getFitHeight(), false, true));
            } else {
                throw new FileNotFoundException("'default-profile.png' not found in resources. Expected at: src/main/resources/com/example/mentalhealthjournal/default-profile.png");
            }
        } catch(Exception e){
            System.err.println("Default profile image error: " + e.getMessage() + ". Using placeholder.");
            String initial = "U";
            if (currentUser != null && currentUser.username != null && !currentUser.username.isEmpty()) {
                initial = currentUser.username.substring(0,1).toUpperCase();
            }

            try {
                Label placeholderLabel = getLabel(imageView, initial);


                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.TRANSPARENT);
                // If not in a scene, temporarily add to a new Scene to ensure layout pass for snapshot
                if (placeholderLabel.getScene() == null) {
                    new Scene(new StackPane(placeholderLabel)); // Temporary scene for layout pass
                }
                Image snapshotImage = placeholderLabel.snapshot(params, null);
                imageView.setImage(snapshotImage);

            } catch (Exception snapshotEx) {
                System.err.println("Failed to create snapshot placeholder: " + snapshotEx.getMessage() + ". Using via.placeholder.com as final fallback.");
                snapshotEx.printStackTrace(System.err);
                Image defaultImg = new Image("https://via.placeholder.com/" + (int)imageView.getFitWidth() + "/CCCCCC/FFFFFF?Text="+ initial, imageView.getFitWidth(),imageView.getFitHeight(), false, true);
                imageView.setImage(defaultImg);
            }
        }
    }

    private static Label getLabel(ImageView imageView, String initial) {
        Label placeholderLabel = new Label(initial);
        placeholderLabel.setStyle(
                "-fx-font-size: " + (imageView.getFitHeight()*0.6) +"px; " +
                        "-fx-text-fill: #FFFFFF; " +
                        "-fx-background-color: #505050; " +
                        "-fx-alignment: center; " +
                        "-fx-pref-width: " + imageView.getFitWidth() + "px; " +
                        "-fx-pref-height: " + imageView.getFitHeight() + "px;"
        );
        // Ensure the label has dimensions before snapshotting if it's not in a scene
        placeholderLabel.setMinWidth(imageView.getFitWidth());
        placeholderLabel.setMinHeight(imageView.getFitHeight());
        placeholderLabel.setPrefSize(imageView.getFitWidth(), imageView.getFitHeight());
        placeholderLabel.setMaxSize(imageView.getFitWidth(), imageView.getFitHeight());
        return placeholderLabel;
    }

    private Button createSidebarButton(String text, int viewIndex) {
        Button button = new Button(text);
        button.getStyleClass().add("sidebar-button");
        button.setPrefWidth(Double.MAX_VALUE);
        Tooltip.install(button, new Tooltip("Switch to " + text + " view"));

        button.setOnAction(e -> {
            if (contentArea == null || viewIndex >= contentArea.getChildren().size()) {
                showAlert("Navigation Error", "View index out of bounds or content area not initialized: " + viewIndex, Alert.AlertType.ERROR);
                return;
            }
            Node viewNode = contentArea.getChildren().get(viewIndex);

            // Get the actual content from the ScrollPane wrapper
            Node actualContentNode = (viewNode instanceof ScrollPane) ? ((ScrollPane) viewNode).getContent() : viewNode;


            if (actualContentNode instanceof VBox || actualContentNode instanceof BorderPane) {
                switch (text) {
                    case "Insights":
                        if (actualContentNode instanceof VBox insightsVBox) {
                            LineChart<String, Number> chart = (LineChart<String, Number>) insightsVBox.lookup(".chart");
                            if (chart != null) {
                                updateInsights(insightsVBox, chart);
                            } else {
                                showAlert("Error", "Insights chart not found. Cannot update.", Alert.AlertType.ERROR);
                            }
                        } else {
                            showAlert("Navigation Error", "Insights view expects a VBox container.", Alert.AlertType.ERROR);
                        }
                        break;
                    case "Well Being":
                        updateWellBeingView();
                        break;
                    case "History":
                        if (actualContentNode instanceof VBox historyVBoxNode) {
                            Platform.runLater(() -> {
                                ComboBox<String> viewTypeCombo = (ComboBox<String>) historyVBoxNode.lookup("#historyViewTypeCombo");
                                DatePicker startDatePicker = (DatePicker) historyVBoxNode.lookup("#historyStartDatePicker");
                                DatePicker endDatePicker = (DatePicker) historyVBoxNode.lookup("#historyEndDatePicker");
                                TextField searchField = (TextField) historyVBoxNode.lookup("#historySearchField");
                                ListView<JournalEntry> journalListView = (ListView<JournalEntry>) historyVBoxNode.lookup("#journalHistoryListView");
                                ListView<MoodEntry> moodHistoryListView = (ListView<MoodEntry>) historyVBoxNode.lookup("#moodHistoryListView");
                                TextArea selectedJournalArea = (TextArea) historyVBoxNode.lookup("#selectedJournalContentArea");
                                TextArea selectedMoodArea = (TextArea) historyVBoxNode.lookup("#selectedMoodNotesArea");
                                Label statusLabel = (Label) historyVBoxNode.lookup("#historyResultsStatusLabel");

                                if (viewTypeCombo != null && startDatePicker != null && endDatePicker != null && searchField != null &&
                                        journalListView != null && moodHistoryListView != null && selectedJournalArea != null && selectedMoodArea != null && statusLabel != null) {
                                    populateHistoryView(viewTypeCombo, startDatePicker, endDatePicker, searchField, journalListView, moodHistoryListView, selectedJournalArea, selectedMoodArea, statusLabel);
                                } else {
                                    showAlert("History View Error", "Could not fully initialize history view components. Some may be null.", Alert.AlertType.WARNING);
                                    System.err.println("One or more History view components are null during lookup.");
                                }
                            });
                        } else {
                            showAlert("Navigation Error", "History view expects a VBox container.", Alert.AlertType.ERROR);
                        }
                        break;
                    case "Pharmacy":
                        if (actualContentNode instanceof VBox prescriptionsVBox) {
                            updatePrescriptionsView(prescriptionsVBox);
                        }
                        break;
                    default:
                        // No special action needed for other views before showing
                        break;
                }
                showView(viewNode);
                updateSidebarSelection(button);
            } else {
                showAlert("Navigation Error", "Cannot switch to the selected view (node type mismatch or null for index " + viewIndex + "). Node is: " + (actualContentNode == null ? "null" : actualContentNode.getClass().getName()), Alert.AlertType.ERROR);
            }
        });
        button.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                button.fire();
            }
        });
        return button;
    }

    private void updateSidebarSelection(Button selectedBtn) {
        if (selectedBtn.getParent() == null) return;
        selectedBtn.getParent().getChildrenUnmodifiable().stream()
                .filter(node -> node instanceof Button && node.getStyleClass().contains("sidebar-button"))
                .forEach(node -> node.getStyleClass().remove("selected"));
        selectedBtn.getStyleClass().add("selected");
    }


    private void showView(Node view) {
        if (contentArea == null) return;
        final double ANIMATION_DURATION = 250;
        List<Animation> fadeOuts = new ArrayList<>();
        for (Node child : contentArea.getChildren()) {
            if (child.isVisible() && child != view) {
                FadeTransition ft = new FadeTransition(Duration.millis(ANIMATION_DURATION / 2), child);
                ft.setToValue(0);
                ft.setOnFinished(e -> child.setVisible(false));
                fadeOuts.add(ft);
            }
        }

        ParallelTransition parallelFadeOut = new ParallelTransition(fadeOuts.toArray(new Animation[0]));
        parallelFadeOut.setOnFinished(event -> {
            view.setOpacity(0);
            view.setTranslateY(15);
            view.setVisible(true);

            FadeTransition ftIn = new FadeTransition(Duration.millis(ANIMATION_DURATION), view);
            ftIn.setFromValue(0.0);
            ftIn.setToValue(1.0);
            ftIn.setInterpolator(Interpolator.EASE_OUT);

            TranslateTransition ttIn = new TranslateTransition(Duration.millis(ANIMATION_DURATION), view);
            ttIn.setFromY(15);
            ttIn.setToY(0);
            ttIn.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition parallelFadeIn = new ParallelTransition(ftIn, ttIn);
            parallelFadeIn.play();
        });

        if (!fadeOuts.isEmpty()) {
            parallelFadeOut.play();
        } else {
            if (!view.isVisible()) {
                view.setOpacity(0);
                view.setTranslateY(15);
            }
            view.setVisible(true);
            FadeTransition ftIn = new FadeTransition(Duration.millis(ANIMATION_DURATION), view);
            ftIn.setFromValue(view.getOpacity());
            ftIn.setToValue(1.0);
            ftIn.setInterpolator(Interpolator.EASE_OUT);

            TranslateTransition ttIn = new TranslateTransition(Duration.millis(ANIMATION_DURATION), view);
            ttIn.setFromY(view.getTranslateY());
            ttIn.setToY(0);
            ttIn.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition parallelFadeIn = new ParallelTransition(ftIn, ttIn);
            parallelFadeIn.play();
        }
    }


    private HBox createHeader() {
        HBox header = new HBox(15);
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Mind Matters");
        title.getStyleClass().add("header-title");
        Tooltip.install(title, new Tooltip("Application Title"));
        String subTitleText = "Your Personal Mental Wellness Journal";
        if (currentUser != null) {
            subTitleText = currentUser.username + "'s Wellness Journal";
        }
        Label subtitle = new Label(subTitleText);
        subtitle.getStyleClass().add("header-subtitle");
        Tooltip.install(subtitle, new Tooltip("A space for reflection and insight"));
        header.getChildren().addAll(title, subtitle);
        return header;
    }
    private VBox createJournalView() {
        VBox journalBox = new VBox(15);
        journalBox.setPadding(new Insets(10));

        Label dateTitle = new Label("Select Date");
        dateTitle.getStyleClass().add("title-label");
        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.getStyleClass().add("date-picker");
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isAfter(LocalDate.now()));
            }
        });

        String[] prompts = {
                "What accomplishment, big or small, are you proud of today?",
                "Describe a moment today when you felt truly present. What did you notice?",
                "If you could offer advice to yourself at the start of today, what would it be?",
                "What is one thing weighing on your mind? Can you break it down into smaller parts?",
                "Reflect on an interaction today. What did you learn from it, or how did it make you feel?",
                "What small act of kindness (for yourself or others) could you do tomorrow?",
                "How are your energy levels today, and what might be influencing them?"
        };
        Label promptTitle = new Label("Today's Reflection Prompt");
        promptTitle.getStyleClass().add("title-label");
        Label promptLabel = new Label(getRandomPrompt(prompts));
        promptLabel.getStyleClass().add("prompt-label");
        promptLabel.setWrapText(true);
        promptLabel.setPrefWidth(Double.MAX_VALUE);

        Button newPromptBtn = new Button("New Prompt");
        newPromptBtn.getStyleClass().add("primary-button");
        applyButtonAnimations(newPromptBtn);
        newPromptBtn.setOnAction(e -> {
            applyShakeAnimation(promptLabel);
            FadeTransition ftOut = new FadeTransition(Duration.millis(150), promptLabel);
            ftOut.setToValue(0);
            ftOut.setOnFinished(event -> {
                promptLabel.setText(getRandomPrompt(prompts));
                FadeTransition ftIn = new FadeTransition(Duration.millis(250), promptLabel);
                ftIn.setToValue(1);
                ftIn.play();
            });
            ftOut.play();
        });

        Label journalTitle = new Label("Your Journal Entry");
        journalTitle.getStyleClass().add("title-label");
        TextArea journalEntryArea = new TextArea();
        journalEntryArea.getStyleClass().add("text-area");
        journalEntryArea.setWrapText(true);
        journalEntryArea.setPromptText("Start writing your thoughts, feelings, and reflections here...");
        VBox.setVgrow(journalEntryArea, Priority.ALWAYS);
        journalEntryArea.setPrefHeight(300); // Give it a preferred size

        Label journalMoodLabel = new Label("Overall Mood Today:");
        journalMoodLabel.getStyleClass().add("title-label");
        ComboBox<String> moodCombo = new ComboBox<>(FXCollections.observableList(VALID_MOODS));
        moodCombo.getStyleClass().add("combo-box");
        moodCombo.setValue("Neutral");

        Button saveButton = new Button("Save Entry");
        saveButton.getStyleClass().add("primary-button");
        applyButtonAnimations(saveButton);
        saveButton.setOnAction(e -> handleSaveJournalEntry(datePicker, journalEntryArea, moodCombo, promptLabel, prompts, saveButton));


        HBox controlBox = new HBox(15, journalMoodLabel, moodCombo);
        controlBox.setAlignment(Pos.CENTER_LEFT);

        HBox bottomBar = new HBox(saveButton);
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(bottomBar, new Insets(10,0,0,0));


        journalBox.getChildren().addAll(
                dateTitle, datePicker,
                promptTitle, promptLabel, newPromptBtn,
                journalTitle, journalEntryArea,
                controlBox, bottomBar
        );
        return journalBox;
    }

    private void handleSaveJournalEntry(DatePicker datePicker, TextArea journalEntryArea, ComboBox<String> moodCombo, Label promptLabel, String[] prompts, Button saveButton) {
        LocalDate date = datePicker.getValue();
        String entryText = journalEntryArea.getText();
        String mood = moodCombo.getValue();

        if (date == null || date.isAfter(LocalDate.now())) {
            showAlert("Invalid Date", "Please select a valid date (today or past).", Alert.AlertType.WARNING); return;
        }
        if (entryText == null || entryText.trim().isEmpty()) {
            showAlert("Missing Entry", "Please write something in your journal before saving.", Alert.AlertType.WARNING); return;
        }
        if (mood == null || !VALID_MOODS.contains(mood)) {
            showAlert("Invalid Mood", "Please select a valid mood from the list.", Alert.AlertType.WARNING); return;
        }
        if (journalEntries == null) {
            showAlert("Error", "Journal entries not initialized. Please restart.", Alert.AlertType.ERROR); return;
        }

        String originalText = saveButton.getText();
        saveButton.setText("Saving...");
        saveButton.setDisable(true);

        PauseTransition pause = new PauseTransition(Duration.millis(300));
        pause.setOnFinished(event -> {
            try {
                journalEntries.add(new JournalEntry(date, entryText, mood));
                String moodNote = "From journal entry (summary): " + entryText.substring(0, Math.min(50, entryText.length())) + (entryText.length() > 50 ? "..." : "");
                if (moodEntries != null) moodEntries.add(new MoodEntry(date, mood, moodNote));
                saveDataForCurrentUser();

                showAlert("Journal Entry Saved", "Your entry for " + date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")) + " has been saved successfully!", Alert.AlertType.INFORMATION);
                journalEntryArea.clear();
                moodCombo.setValue("Neutral");
                promptLabel.setText(getRandomPrompt(prompts));
            } catch (Exception ex) {
                showAlert("Save Error", "Failed to save journal entry: " + ex.getMessage(), Alert.AlertType.ERROR);
                System.err.println("Error saving journal entry: " + ex.getMessage());
            } finally {
                saveButton.setText(originalText);
                saveButton.setDisable(false);
            }
        });
        pause.play();
    }

    private VBox createMoodTrackerView() {
        VBox moodBox = new VBox(15);
        moodBox.setPadding(new Insets(10));
        moodBox.setAlignment(Pos.TOP_CENTER);

        Label dateTitle = new Label("Select Date");
        dateTitle.getStyleClass().add("title-label");
        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.getStyleClass().add("date-picker");
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isAfter(LocalDate.now()));
            }
        });

        Label moodTitleLabel = new Label("How are you feeling today?");
        moodTitleLabel.getStyleClass().add("title-label");

        FlowPane moodFlowPane = new FlowPane(Orientation.HORIZONTAL, 10, 10);
        moodFlowPane.setAlignment(Pos.CENTER);
        moodFlowPane.setPadding(new Insets(5));
        moodFlowPane.setMaxWidth(Region.USE_PREF_SIZE);


        ToggleGroup moodToggle = new ToggleGroup();
        String[] moods = {"Very Happy", "Happy", "Content", "Neutral", "Sad", "Very Sad", "Anxious", "Stressed", "Calm", "Energetic", "Tired"};
        String[] emojis = {"😄", "🙂", "😌", "😐", "😔", "😢", "😰", "😫", "🧘", "⚡", "😴"};

        int moodButtonWidth = 160;
        int moodButtonHeight = 55;

        for (int i = 0; i < moods.length; i++) {
            ToggleButton moodBtn = new ToggleButton(emojis[i] + " " + moods[i]);
            moodBtn.getStyleClass().add("mood-button");
            moodBtn.setToggleGroup(moodToggle);
            moodBtn.setUserData(moods[i]);
            moodBtn.setPrefSize(moodButtonWidth, moodButtonHeight);
            moodBtn.setAlignment(Pos.CENTER_LEFT);
            moodBtn.setPadding(new Insets(0,0,0,10));
            applyMoodButtonEffects(moodBtn);
            moodFlowPane.getChildren().add(moodBtn);
        }

        if (!moodToggle.getToggles().isEmpty() && moodToggle.getToggles().size() > 3) {
            moodToggle.getToggles().get(3).setSelected(true);
            ((ToggleButton)moodToggle.getToggles().get(3)).getStyleClass().add("mood-button-selected");
        }


        moodToggle.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (oldToggle != null) {
                ((ToggleButton) oldToggle).getStyleClass().remove("mood-button-selected");
            }
            if (newToggle != null) {
                ToggleButton selectedBtn = (ToggleButton) newToggle;
                selectedBtn.getStyleClass().add("mood-button-selected");
            }
        });

        Label notesTitle = new Label("Additional Notes (Optional)");
        notesTitle.getStyleClass().add("title-label");
        TextArea notesArea = new TextArea();
        notesArea.getStyleClass().add("text-area");
        notesArea.setWrapText(true);
        notesArea.setPromptText("What's contributing to your mood? Any specific thoughts or events?");
        notesArea.setPrefHeight(200); // Set preferred height
        VBox.setVgrow(notesArea, Priority.ALWAYS);

        Button saveMoodBtn = new Button("Save Mood");
        saveMoodBtn.getStyleClass().add("primary-button");
        applyButtonAnimations(saveMoodBtn);
        saveMoodBtn.setOnAction(e -> handleSaveMoodEntry(datePicker, moodToggle, notesArea, saveMoodBtn));

        HBox saveBox = new HBox(saveMoodBtn);
        saveBox.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(saveBox, new Insets(10,0,0,0));

        moodBox.getChildren().addAll(
                dateTitle, datePicker, moodTitleLabel, moodFlowPane,
                notesTitle, notesArea, saveBox
        );
        return moodBox;
    }

    private void handleSaveMoodEntry(DatePicker datePicker, ToggleGroup moodToggle, TextArea notesArea, Button saveButton) {
        LocalDate date = datePicker.getValue();
        if (date == null || date.isAfter(LocalDate.now())) {
            showAlert("Invalid Date", "Please select a valid date (today or past).", Alert.AlertType.WARNING); return;
        }
        if (moodToggle.getSelectedToggle() == null) {
            showAlert("Missing Selection", "Please select a mood.", Alert.AlertType.WARNING); return;
        }
        String selectedMood = (String) moodToggle.getSelectedToggle().getUserData();
        if (!VALID_MOODS.contains(selectedMood)) {
            showAlert("Invalid Mood", "The selected mood is not recognized.", Alert.AlertType.WARNING); return;
        }
        String notes = notesArea.getText() != null ? notesArea.getText().trim() : "";

        if (moodEntries == null) {
            showAlert("Error", "Mood entries not initialized. Please restart.", Alert.AlertType.ERROR); return;
        }

        String originalText = saveButton.getText();
        saveButton.setText("Saving...");
        saveButton.setDisable(true);

        PauseTransition pause = new PauseTransition(Duration.millis(300));
        pause.setOnFinished(event -> {
            try {
                moodEntries.add(new MoodEntry(date, selectedMood, notes));
                saveDataForCurrentUser();
                showAlert("Mood Recorded", "Your mood for " + date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")) + " has been recorded!", Alert.AlertType.INFORMATION);
                if (!moodToggle.getToggles().isEmpty() && moodToggle.getToggles().size() > 3) {
                    moodToggle.getToggles().get(3).setSelected(true);
                }
                notesArea.clear();
            } catch (Exception ex) {
                showAlert("Save Error", "Failed to save mood entry: " + ex.getMessage(), Alert.AlertType.ERROR);
                System.err.println("Error saving mood entry: " + ex.getMessage());
            } finally {
                saveButton.setText(originalText);
                saveButton.setDisable(false);
            }
        });
        pause.play();
    }

    private LineChart<String, Number> createMoodChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Date");
        NumberAxis yAxis = new NumberAxis("Mood Level (1-10)",0, 10, 1);
        yAxis.setMinorTickCount(0);
        yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis) {
            @Override public String toString(Number object) { return String.format("%d", object.intValue()); }
        });

        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.getStyleClass().add("chart");
        lineChart.setTitle("Mood Trends Over Time");
        lineChart.setAnimated(true);
        lineChart.setCreateSymbols(true);
        lineChart.setLegendVisible(false);
        VBox.setVgrow(lineChart, Priority.ALWAYS);
        return lineChart;
    }

    private VBox createInsightsView(LineChart<String, Number> lineChart) {
        VBox insightsBox = new VBox(20);
        insightsBox.setPadding(new Insets(10));
        insightsBox.setId("insightsBoxContent");

        Label insightsTitle = new Label("Your Mood Insights & Patterns");
        insightsTitle.getStyleClass().add("insights-title");

        Label filterLabel = new Label("Time Period:");
        filterLabel.getStyleClass().add("title-label");
        ComboBox<String> periodCombo = new ComboBox<>(FXCollections.observableList(
                Arrays.asList("Last 7 Days", "Last 14 Days", "Last 30 Days", "This Month", "Last Month", "All Time")
        ));
        periodCombo.getStyleClass().add("combo-box");
        periodCombo.setValue("Last 7 Days");
        periodCombo.setId("periodComboBox");
        HBox filterBox = new HBox(10, filterLabel, periodCombo);
        filterBox.setAlignment(Pos.CENTER_LEFT);

        TitledPane summaryPane = new TitledPane("Mood Summary", new VBox());
        summaryPane.setCollapsible(false);
        summaryPane.getStyleClass().add("summary-pane");
        summaryPane.setId("summaryPane");

        TitledPane suggestionsPane = new TitledPane("Personalized Suggestions & Reflections", new VBox());
        suggestionsPane.setCollapsible(true);
        suggestionsPane.setExpanded(true);
        suggestionsPane.setId("suggestionsPane");

        periodCombo.setOnAction(e -> updateInsights(insightsBox, lineChart));
        insightsBox.getChildren().addAll(insightsTitle, filterBox, lineChart, summaryPane, suggestionsPane);
        VBox.setVgrow(insightsBox, Priority.ALWAYS);
        VBox.setVgrow(lineChart, Priority.ALWAYS);
        VBox.setVgrow(suggestionsPane, Priority.SOMETIMES);


        return insightsBox;
    }


    private VBox createExportView() {
        VBox exportBox = new VBox(20);
        exportBox.setPadding(new Insets(10));

        Label exportTitle = new Label("Export Your Journal Data");
        exportTitle.getStyleClass().add("insights-title");
        Label exportDesc = new Label("Securely export your journal and mood entries. Select date range, content type, and preferred format.");
        exportDesc.getStyleClass().add("summary-label");
        exportDesc.setWrapText(true);

        Label dateRangeLabel = new Label("Select Date Range:");
        dateRangeLabel.getStyleClass().add("title-label");
        DatePicker startDatePicker = new DatePicker(getMinDateFromEntries());
        DatePicker endDatePicker = new DatePicker(LocalDate.now());
        startDatePicker.getStyleClass().add("date-picker");
        endDatePicker.getStyleClass().add("date-picker");
        HBox dateRangeBox = new HBox(10, new Label("From:"), startDatePicker, new Label("To:"), endDatePicker);
        dateRangeBox.setAlignment(Pos.CENTER_LEFT);


        Label exportOptionsLabel = new Label("Content to Include:");
        exportOptionsLabel.getStyleClass().add("title-label");
        CheckBox includeJournalCheck = new CheckBox("Journal Entries (Full Text & Mood)");
        includeJournalCheck.setSelected(true);
        CheckBox includeMoodCheck = new CheckBox("Mood Tracker Entries (Mood & Notes)");
        includeMoodCheck.setSelected(true);
        for(CheckBox cb : List.of(includeJournalCheck, includeMoodCheck)) {
            cb.getStyleClass().add("summary-label");
        }
        VBox exportOptions = new VBox(10, includeJournalCheck, includeMoodCheck);
        exportOptions.setPadding(new Insets(0, 0, 0, 10));

        Label formatLabel = new Label("Export Format:");
        formatLabel.getStyleClass().add("title-label");
        ToggleGroup formatToggle = new ToggleGroup();
        RadioButton txtOption = new RadioButton("Plain Text File (.txt)");
        txtOption.setUserData("TXT"); txtOption.setSelected(true);
        RadioButton csvOption = new RadioButton("CSV File (.csv - for spreadsheets)");
        csvOption.setUserData("CSV");
        for(RadioButton rb : List.of(txtOption, csvOption)) {
            rb.getStyleClass().add("summary-label"); rb.setToggleGroup(formatToggle);
        }
        HBox formatBox = new HBox(15, txtOption, csvOption);
        formatBox.setAlignment(Pos.CENTER_LEFT);

        Button exportBtn = new Button("Export Journal Data");
        exportBtn.getStyleClass().add("primary-button");
        applyButtonAnimations(exportBtn);
        Circle spinner = new Circle(8, Color.TRANSPARENT);
        spinner.setStroke(Color.WHITE); spinner.setStrokeWidth(2); spinner.setVisible(false);
        SVGPath checkMark = new SVGPath();
        checkMark.setContent("M6 12 L10 16 L18 8");
        checkMark.setStroke(Color.WHITE); checkMark.setStrokeWidth(2); checkMark.setFill(Color.TRANSPARENT);
        checkMark.setVisible(false);

        StackPane exportBtnGraphicPane = new StackPane(spinner, checkMark);
        exportBtnGraphicPane.setPrefSize(20,20);
        exportBtn.setGraphic(exportBtnGraphicPane);
        exportBtn.setContentDisplay(ContentDisplay.RIGHT);
        exportBtn.setGraphicTextGap(10);

        exportBtn.setOnAction(e -> handleExport(startDatePicker, endDatePicker, includeJournalCheck, includeMoodCheck, formatToggle, exportBtn, spinner, checkMark));

        TextArea previewArea = new TextArea();
        previewArea.getStyleClass().add("text-area");
        previewArea.setEditable(false);
        previewArea.setWrapText(true);
        previewArea.setPromptText("Summary of export settings will appear here.");
        previewArea.setMinHeight(100);
        VBox.setVgrow(previewArea, Priority.ALWAYS); // Ensure it can grow.

        Button generatePreviewBtn = new Button("Update Export Summary");
        generatePreviewBtn.getStyleClass().add("primary-button");
        applyButtonAnimations(generatePreviewBtn);
        generatePreviewBtn.setOnAction(e -> updateExportPreview(previewArea, startDatePicker, endDatePicker, formatToggle, includeJournalCheck, includeMoodCheck));

        VBox previewContentBox = new VBox(10, generatePreviewBtn, previewArea);
        VBox.setVgrow(previewArea, Priority.ALWAYS);
        TitledPane previewPane = new TitledPane("Export Settings Summary", previewContentBox);
        previewPane.setCollapsible(false);
        VBox.setVgrow(previewPane, Priority.ALWAYS);

        HBox exportActionBox = new HBox(exportBtn);
        exportActionBox.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(exportActionBox, new Insets(10,0,0,0));


        exportBox.getChildren().addAll(
                exportTitle, exportDesc,
                new Separator(),
                dateRangeLabel, dateRangeBox,
                new Separator(),
                exportOptionsLabel, exportOptions,
                new Separator(),
                formatLabel, formatBox,
                new Separator(),
                previewPane,
                exportActionBox
        );
        VBox.setVgrow(exportBox, Priority.ALWAYS);
        Platform.runLater(() -> updateExportPreview(previewArea, startDatePicker, endDatePicker, formatToggle, includeJournalCheck, includeMoodCheck));
        return exportBox;
    }

    private VBox createHistoryView() {
        VBox historyBox = new VBox(15);
        historyBox.setPadding(new Insets(10));
        VBox.setVgrow(historyBox, Priority.ALWAYS);

        Label historyTitle = new Label("Review Your Entry History");
        historyTitle.getStyleClass().add("insights-title");

        GridPane filterPane = new GridPane();
        filterPane.setHgap(15); filterPane.setVgap(10); filterPane.setPadding(new Insets(10,0,10,0));
        ColumnConstraints col1Filter = new ColumnConstraints(); col1Filter.setHgrow(Priority.NEVER);
        ColumnConstraints col2Filter = new ColumnConstraints(); col2Filter.setHgrow(Priority.ALWAYS);
        ColumnConstraints col3Filter = new ColumnConstraints(); col3Filter.setHgrow(Priority.NEVER);
        ColumnConstraints col4Filter = new ColumnConstraints(); col4Filter.setHgrow(Priority.ALWAYS);
        filterPane.getColumnConstraints().addAll(col1Filter, col2Filter, col3Filter, col4Filter);


        ComboBox<String> viewTypeCombo = new ComboBox<>(FXCollections.observableArrayList("All Entries", "Journal Entries Only", "Mood Entries Only"));
        viewTypeCombo.setValue("All Entries"); viewTypeCombo.getStyleClass().add("combo-box");
        viewTypeCombo.setId("historyViewTypeCombo");
        viewTypeCombo.setMaxWidth(Double.MAX_VALUE);
        filterPane.add(new Label("View Type:"), 0, 0); filterPane.add(viewTypeCombo, 1, 0);

        TextField searchField = new TextField();
        searchField.setPromptText("Search content/notes..."); searchField.getStyleClass().add("text-field");
        searchField.setId("historySearchField");
        searchField.setMaxWidth(Double.MAX_VALUE);
        filterPane.add(new Label("Keyword Search:"), 2, 0); filterPane.add(searchField, 3, 0);

        DatePicker startDatePicker = new DatePicker(getMinDateFromEntries());
        startDatePicker.getStyleClass().add("date-picker");
        startDatePicker.setId("historyStartDatePicker");
        startDatePicker.setMaxWidth(Double.MAX_VALUE);
        filterPane.add(new Label("From Date:"), 0, 1); filterPane.add(startDatePicker, 1, 1);

        DatePicker endDatePicker = new DatePicker(getMaxDateFromEntries());
        endDatePicker.getStyleClass().add("date-picker");
        endDatePicker.setId("historyEndDatePicker");
        endDatePicker.setMaxWidth(Double.MAX_VALUE);
        filterPane.add(new Label("To Date:"), 2, 1); filterPane.add(endDatePicker, 3, 1);

        filterPane.getChildren().stream()
                .filter(node -> node instanceof Label && GridPane.getColumnIndex(node) % 2 == 0)
                .forEach(node -> node.getStyleClass().add("history-filter-label"));

        Button applyFiltersButton = new Button("Apply Filters & Refresh");
        applyFiltersButton.getStyleClass().add("primary-button");
        applyButtonAnimations(applyFiltersButton);
        GridPane.setColumnSpan(applyFiltersButton, 4);
        GridPane.setHalignment(applyFiltersButton, HPos.CENTER);
        GridPane.setMargin(applyFiltersButton, new Insets(10,0,0,0));
        filterPane.add(applyFiltersButton, 0, 2);

        Label resultsStatusLabel = new Label("Filter and search your past entries.");
        resultsStatusLabel.getStyleClass().add("summary-label");
        resultsStatusLabel.setId("historyResultsStatusLabel");

        ListView<JournalEntry> journalHistoryListView = new ListView<>();
        journalHistoryListView.setPlaceholder(new Label("No journal entries match your current filters."));
        VBox.setVgrow(journalHistoryListView, Priority.ALWAYS);
        journalHistoryListView.setId("journalHistoryListView");
        TextArea selectedJournalContentArea = new TextArea("Select a journal entry from the list above to view its full content.");
        selectedJournalContentArea.setEditable(false); VBox.setVgrow(selectedJournalContentArea, Priority.SOMETIMES);
        selectedJournalContentArea.setId("selectedJournalContentArea"); selectedJournalContentArea.setMinHeight(100);
        Label journalContentLabel = new Label("Full Journal Entry Content:"); journalContentLabel.getStyleClass().add("title-label");

        VBox journalDisplayBox = new VBox(5, journalHistoryListView, journalContentLabel, selectedJournalContentArea);
        VBox.setVgrow(journalHistoryListView, Priority.ALWAYS); VBox.setVgrow(selectedJournalContentArea, Priority.ALWAYS);
        TitledPane journalPane = new TitledPane("Filtered Journal Entries", journalDisplayBox);

        ListView<MoodEntry> moodHistoryListView = new ListView<>();
        moodHistoryListView.setPlaceholder(new Label("No mood entries match your current filters."));
        VBox.setVgrow(moodHistoryListView, Priority.ALWAYS);
        moodHistoryListView.setId("moodHistoryListView");
        TextArea selectedMoodNotesArea = new TextArea("Select a mood entry from the list above to view its notes.");
        selectedMoodNotesArea.setEditable(false); VBox.setVgrow(selectedMoodNotesArea, Priority.SOMETIMES);
        selectedMoodNotesArea.setId("selectedMoodNotesArea"); selectedMoodNotesArea.setMinHeight(100);
        Label moodNotesLabel = new Label("Full Mood Entry Notes:"); moodNotesLabel.getStyleClass().add("title-label");

        VBox moodDisplayBox = new VBox(5, moodHistoryListView, moodNotesLabel, selectedMoodNotesArea);
        VBox.setVgrow(moodHistoryListView, Priority.ALWAYS); VBox.setVgrow(selectedMoodNotesArea, Priority.ALWAYS);
        TitledPane moodPane = new TitledPane("Filtered Mood Entries", moodDisplayBox);


        for(ListView<?> lv : List.of(journalHistoryListView, moodHistoryListView)) lv.getStyleClass().add("list-view");
        for(TextArea ta : List.of(selectedJournalContentArea, selectedMoodNotesArea)) {
            ta.getStyleClass().add("text-area"); ta.setWrapText(true);
        }

        for(TitledPane tp : List.of(journalPane, moodPane)) {
            tp.setCollapsible(true); tp.setExpanded(true);
            VBox.setVgrow(tp, Priority.ALWAYS);
            if (tp.getContent() instanceof VBox contentVBox) {
                contentVBox.setPadding(new Insets(10));
                VBox.setVgrow(contentVBox, Priority.ALWAYS);
                contentVBox.getChildren().stream()
                        .filter(n -> n instanceof Label && !n.getStyleClass().contains("title-label"))
                        .forEach(lbl -> lbl.getStyleClass().add("summary-label"));
            }
        }


        journalHistoryListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(JournalEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) { setText(null); setGraphic(null); }
                else {
                    VBox cellContent = new VBox(3);
                    Label dateMoodLabel = new Label(String.format("Date: %s  |  Mood: %s", entry.date.format(DateTimeFormatter.ISO_DATE), entry.mood));
                    dateMoodLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #FFFFFF;");
                    Label previewLabel = new Label("Entry Preview: " + entry.content.substring(0, Math.min(entry.content.length(), 70)) + (entry.content.length() > 70 ? "..." : ""));
                    previewLabel.setWrapText(true); previewLabel.getStyleClass().add("summary-label");
                    cellContent.getChildren().addAll(dateMoodLabel, previewLabel);
                    setGraphic(cellContent); Tooltip.install(this, new Tooltip("Click to view full entry below"));
                }
            }
        });
        moodHistoryListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(MoodEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) { setText(null); setGraphic(null); }
                else {
                    VBox cellContent = new VBox(3);
                    Label dateMoodLabel = new Label(String.format("Date: %s  |  Mood: %s", entry.date.format(DateTimeFormatter.ISO_DATE), entry.mood));
                    dateMoodLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #FFFFFF;");
                    String notesPreview = entry.notes.isEmpty() ? "No additional notes." :
                            "Notes: " + entry.notes.substring(0, Math.min(entry.notes.length(), 70)) + (entry.notes.length() > 70 ? "..." : "");
                    Label notesPreviewLabel = new Label(notesPreview);
                    notesPreviewLabel.setWrapText(true); notesPreviewLabel.getStyleClass().add("summary-label");
                    cellContent.getChildren().addAll(dateMoodLabel, notesPreviewLabel);
                    setGraphic(cellContent); Tooltip.install(this, new Tooltip("Click to view full notes below"));
                }
            }
        });

        journalHistoryListView.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> selectedJournalContentArea.setText(n != null ? n.content : "Select a journal entry to view its full content."));
        moodHistoryListView.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> selectedMoodNotesArea.setText(n != null ? n.notes : "Select a mood entry to view its notes."));


        SplitPane dataDisplayPane = new SplitPane(journalPane, moodPane);
        dataDisplayPane.setOrientation(Orientation.VERTICAL); dataDisplayPane.setDividerPositions(0.5);
        VBox.setVgrow(dataDisplayPane, Priority.ALWAYS);

        applyFiltersButton.setOnAction(e -> populateHistoryView(viewTypeCombo, startDatePicker, endDatePicker, searchField, journalHistoryListView, moodHistoryListView, selectedJournalContentArea, selectedMoodNotesArea, resultsStatusLabel));
        historyBox.getChildren().addAll(historyTitle, filterPane, resultsStatusLabel, dataDisplayPane);
        return historyBox;
    }


    /**
     * FIX: Added the missing populateHistoryView method.
     * This method filters and displays journal and mood entries based on UI controls.
     */
    private void populateHistoryView(ComboBox<String> viewTypeCombo, DatePicker startDatePicker, DatePicker endDatePicker,
                                     TextField searchField, ListView<JournalEntry> journalListView,
                                     ListView<MoodEntry> moodListView, TextArea selectedJournalArea,
                                     TextArea selectedMoodArea, Label statusLabel) {

        if (journalEntries == null || moodEntries == null || viewTypeCombo == null || journalListView == null || moodListView == null) {
            statusLabel.setText("Error: History view components not ready.");
            return;
        }

        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();
        String searchTerm = searchField.getText().toLowerCase().trim();
        String viewType = viewTypeCombo.getValue();

        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            statusLabel.setText("Invalid date range selected.");
            journalListView.getItems().clear();
            moodListView.getItems().clear();
            return;
        }

        List<JournalEntry> filteredJournals = new ArrayList<>();
        if ("All Entries".equals(viewType) || "Journal Entries Only".equals(viewType)) {
            filteredJournals = journalEntries.stream()
                    .filter(entry -> entry.date != null && !entry.date.isBefore(startDate) && !entry.date.isAfter(endDate))
                    .filter(entry -> searchTerm.isEmpty() || entry.content.toLowerCase().contains(searchTerm))
                    .sorted(Comparator.comparing(JournalEntry::toString).reversed())
                    .toList();
        }

        List<MoodEntry> filteredMoods = new ArrayList<>();
        if ("All Entries".equals(viewType) || "Mood Entries Only".equals(viewType)) {
            filteredMoods = moodEntries.stream()
                    .filter(entry -> entry.date != null && !entry.date.isBefore(startDate) && !entry.date.isAfter(endDate))
                    .filter(entry -> searchTerm.isEmpty() || entry.notes.toLowerCase().contains(searchTerm))
                    .sorted(Comparator.comparing(MoodEntry::toString).reversed())
                    .toList();
        }

        // Clear previous content
        journalListView.getItems().clear();
        moodListView.getItems().clear();
        selectedJournalArea.clear();
        selectedMoodArea.clear();

        // Populate lists
        journalListView.setItems(FXCollections.observableArrayList(filteredJournals));
        moodListView.setItems(FXCollections.observableArrayList(filteredMoods));

        // Manage visibility
        TitledPane journalPane = (TitledPane) journalListView.getParent().getParent();
        TitledPane moodPane = (TitledPane) moodListView.getParent().getParent();
        journalPane.setVisible(!"Mood Entries Only".equals(viewType));
        journalPane.setManaged(!"Mood Entries Only".equals(viewType));
        moodPane.setVisible(!"Journal Entries Only".equals(viewType));
        moodPane.setManaged(!"Journal Entries Only".equals(viewType));


        // Update status label
        String journalStatus = String.format("%d journal entries found.", filteredJournals.size());
        String moodStatus = String.format("%d mood entries found.", filteredMoods.size());

        switch (viewType) {
            case "All Entries":
                statusLabel.setText(journalStatus + " " + moodStatus);
                break;
            case "Journal Entries Only":
                statusLabel.setText(journalStatus);
                break;
            case "Mood Entries Only":
                statusLabel.setText(moodStatus);
                break;
            default:
                statusLabel.setText("Filter applied.");
        }
    }


    private VBox createConnectView() {
        VBox connectBox = new VBox(15);
        connectBox.setPadding(new Insets(10));
        VBox.setVgrow(connectBox, Priority.ALWAYS);


        Label connectTitle = new Label("Connect with Peers");
        connectTitle.getStyleClass().add("insights-title");

        HBox myUidBox = new HBox(10);
        myUidBox.setAlignment(Pos.CENTER_LEFT);
        Label myUidLabelPrefix = new Label("Your Secure ID (Share for peer connection):");
        myUidLabelPrefix.getStyleClass().add("title-label");
        myUidLabelPrefix.setWrapText(true);
        String uidText = (myApplicationInfo != null && myApplicationInfo.uid != null) ? myApplicationInfo.uid : "N/A - Check Profile";
        TextField myUidValueField = new TextField(uidText);
        myUidValueField.getStyleClass().add("text-field");
        myUidValueField.setEditable(false);
        HBox.setHgrow(myUidValueField, Priority.ALWAYS);


        Button copyUidButton = new Button("Copy ID");
        copyUidButton.getStyleClass().add("sidebar-button");
        copyUidButton.setStyle("-fx-padding: 5px 10px; -fx-font-size: 12px;");
        applyButtonAnimations(copyUidButton);
        Tooltip.install(copyUidButton, new Tooltip("Copy your Secure ID to clipboard"));
        copyUidButton.setOnAction(e -> {
            if (myApplicationInfo != null && myApplicationInfo.uid != null) {
                final Clipboard clipboard = Clipboard.getSystemClipboard();
                final ClipboardContent content = new ClipboardContent();
                content.putString(myApplicationInfo.uid);
                clipboard.setContent(content);
                showAlert("ID Copied", "Your Secure ID has been copied to the clipboard.", Alert.AlertType.INFORMATION);
            } else {
                showAlert("Error", "Your Secure ID is not available. Please check your profile or restart the application.", Alert.AlertType.WARNING);
            }
        });
        myUidBox.getChildren().addAll(myUidLabelPrefix, myUidValueField, copyUidButton);

        VBox addFriendContent = new VBox(10);
        addFriendContent.getStyleClass().add("connect-view-add-friend-pane");
        addFriendContent.setPadding(new Insets(10));
        Label friendUidLabel = new Label("Peer's Secure ID:");
        friendUidLabel.getStyleClass().add("summary-label");
        TextField friendUidField = new TextField();
        friendUidField.setPromptText("Enter peer's Secure ID");
        friendUidField.getStyleClass().add("text-field");

        Label friendNicknameLabel = new Label("Nickname (Optional):");
        friendNicknameLabel.getStyleClass().add("summary-label");
        TextField friendNicknameField = new TextField();
        friendNicknameField.setPromptText("How you'll identify this peer");
        friendNicknameField.getStyleClass().add("text-field");

        Button addFriendButton = new Button("Add Peer");
        addFriendButton.getStyleClass().add("primary-button");
        applyButtonAnimations(addFriendButton);
        addFriendButton.setOnAction(e -> handleAddFriend(friendUidField, friendNicknameField));
        addFriendContent.getChildren().addAll(friendUidLabel, friendUidField, friendNicknameLabel, friendNicknameField, addFriendButton);

        TitledPane addFriendPane = new TitledPane("Add New Peer Connection", addFriendContent);
        addFriendPane.setCollapsible(true);
        addFriendPane.setExpanded(false);

        SplitPane friendsChatSplit = new SplitPane();
        friendsChatSplit.setOrientation(Orientation.HORIZONTAL);
        friendsChatSplit.setDividerPositions(0.35);
        VBox.setVgrow(friendsChatSplit, Priority.ALWAYS);

        VBox friendsListPane = new VBox(10);
        friendsListPane.getStyleClass().add("connect-view-left-pane");
        Label friendsListTitle = new Label("Connected Peers");
        friendsListTitle.getStyleClass().add("title-label");
        friendsListViewForConnect = new ListView<>(friendsList != null ? friendsList : FXCollections.observableArrayList());
        friendsListViewForConnect.getStyleClass().add("list-view");
        VBox.setVgrow(friendsListViewForConnect, Priority.ALWAYS);
        friendsListViewForConnect.setPlaceholder(new Label("No peers added or online.\nAdd peers using their Secure ID."));
        friendsListViewForConnect.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Friend friend, boolean empty) {
                super.updateItem(friend, empty);
                if (empty || friend == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    setText(friend.toString());
                    setTooltip(new Tooltip(friend.nickname + "\nUID: " + friend.uid + "\nStatus: " + friend.getDisplayStatus()));
                }
            }
        });
        friendsListViewForConnect.getSelectionModel().selectedItemProperty().addListener((obs, oldFriend, newFriend) -> {
            currentChatFriend = newFriend;
            updateChatUIForSelectedFriend();
        });
        friendsListPane.getChildren().addAll(friendsListTitle, friendsListViewForConnect);

        VBox chatPane = new VBox(10);
        chatPane.getStyleClass().add("connect-view-right-pane");
        chatWithLabel = new Label("Chat: (Select a peer from the list)");
        chatWithLabel.getStyleClass().add("title-label");
        chatDisplayArea = new TextArea();
        chatDisplayArea.getStyleClass().add("text-area");
        chatDisplayArea.setEditable(false);
        chatDisplayArea.setWrapText(true);
        VBox.setVgrow(chatDisplayArea, Priority.ALWAYS);

        HBox messageInputBox = new HBox(10);
        messageInputBox.setAlignment(Pos.CENTER_LEFT);
        messageInputField = new TextField();
        messageInputField.getStyleClass().add("text-field");
        messageInputField.setPromptText("Type your message here...");
        HBox.setHgrow(messageInputField, Priority.ALWAYS);
        sendMessageButton = new Button("Send");
        sendMessageButton.getStyleClass().add("primary-button");
        applyButtonAnimations(sendMessageButton);
        sendMessageButton.setOnAction(e -> handleSendMessage());
        messageInputField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) handleSendMessage();
        });

        messageInputBox.getChildren().addAll(messageInputField, sendMessageButton);
        chatPane.getChildren().addAll(chatWithLabel, chatDisplayArea, messageInputBox);

        friendsChatSplit.getItems().addAll(friendsListPane, chatPane);

        connectBox.getChildren().addAll(connectTitle, myUidBox, addFriendPane, friendsChatSplit);
        updateChatUIForSelectedFriend();
        return connectBox;
    }

    private void handleAddFriend(TextField uidField, TextField nicknameField) {
        String uid = uidField.getText().trim();
        String nickname = nicknameField.getText().trim();

        if (uid.isEmpty()) {
            showAlert("Invalid Input", "Peer's Secure ID cannot be empty.", Alert.AlertType.WARNING);
            return;
        }
        if (myApplicationInfo == null || friendsList == null) {
            showAlert("Error", "User session data not fully loaded. Please try again or restart.", Alert.AlertType.ERROR);
            return;
        }

        if (uid.equals(myApplicationInfo.uid)) {
            showAlert("Invalid Input", "You cannot add yourself as a peer.", Alert.AlertType.WARNING);
            return;
        }
        if (nickname.isEmpty()) {
            nickname = "Peer (" + uid.substring(0, Math.min(uid.length(), 8)) + "...)";
        }

        Friend newFriend = new Friend(uid, nickname);
        if (friendsList.contains(newFriend)) {
            showAlert("Peer Exists", "This Secure ID is already in your peer list.", Alert.AlertType.INFORMATION);
        } else {
            friendsList.add(newFriend);
            showAlert("Peer Added", nickname + " has been added to your peer list.", Alert.AlertType.INFORMATION);
            uidField.clear();
            nicknameField.clear();
            try {
                saveDataForCurrentUser();
            } catch (IOException e) {
                showAlert("Save Error", "Could not save updated peer list: " + e.getMessage(), Alert.AlertType.ERROR);
                System.err.println("Error saving updated peer list: " + e.getMessage());
            }
        }
    }

    private void handleSendMessage() {
        if (currentChatFriend == null) {
            showAlert("No Peer Selected", "Please select a peer from the list to start a chat.", Alert.AlertType.WARNING);
            if(messageInputField != null) messageInputField.requestFocus();
            return;
        }
        if (!currentChatFriend.isOnline) {
            showAlert("Peer Offline", currentChatFriend.nickname + " is currently offline. Messages cannot be sent.", Alert.AlertType.WARNING);
            return;
        }
        String messageContent = messageInputField.getText().trim();
        if (messageContent.isEmpty()) {
            return;
        }

        if (networkManager != null && myApplicationInfo != null) {
            ChatMessage msgObject = new ChatMessage(myApplicationInfo.uid, messageContent, LocalDateTime.now(), true);
            currentChatFriend.getChatMessages().add(msgObject);
            refreshChatDisplay();
            networkManager.sendMessage(currentChatFriend, messageContent);
            messageInputField.clear();
            messageInputField.requestFocus();
            try {
                saveDataForCurrentUser();
            } catch (IOException e) {
                System.err.println("Failed to save chat message with friend data: " + e.getMessage());
            }
        } else {
            showAlert("Network Error", "Network service is not available or your user ID is missing. Cannot send message.", Alert.AlertType.ERROR);
        }
    }

    public void receiveMessage(String senderUid, String messageContent) {
        Platform.runLater(() -> {
            if (friendsList == null) return;

            Friend senderFriend = friendsList.stream()
                    .filter(f -> f.uid.equals(senderUid))
                    .findFirst().orElse(null);

            if (senderFriend == null) {
                System.out.println("Received message from unknown UID: " + senderUid + ". Not processing further.");
                return;
            }

            ChatMessage msgObject = new ChatMessage(senderUid, messageContent, LocalDateTime.now(), false);
            senderFriend.getChatMessages().add(msgObject);

            if (currentChatFriend != null && currentChatFriend.uid.equals(senderUid)) {
                refreshChatDisplay();
            } else {
                showAlert("New Message", "You have a new message from " + senderFriend.nickname + ".", Alert.AlertType.INFORMATION);
                if (friendsListViewForConnect != null) friendsListViewForConnect.refresh();
            }
            try {
                saveDataForCurrentUser();
            } catch (IOException e) {
                System.err.println("Failed to save received chat message for " + senderFriend.nickname + ": " + e.getMessage());
            }
        });
    }


    private void updateChatUIForSelectedFriend() {
        if (chatWithLabel == null || chatDisplayArea == null || messageInputField == null || sendMessageButton == null) {
            return;
        }
        if (currentChatFriend == null) {
            chatWithLabel.setText("Chat: (Select a peer to begin)");
            chatDisplayArea.clear();
            chatDisplayArea.setPromptText("Select a peer from the list on the left to view chat history or send messages.");
            chatDisplayArea.setDisable(true);
            messageInputField.setDisable(true);
            sendMessageButton.setDisable(true);
        } else {
            chatWithLabel.setText("Chatting with: " + currentChatFriend.nickname + (currentChatFriend.isOnline ? " (Online)" : " (Offline)"));
            chatDisplayArea.setDisable(false);
            messageInputField.setDisable(!currentChatFriend.isOnline);
            sendMessageButton.setDisable(!currentChatFriend.isOnline);
            chatDisplayArea.setPromptText(currentChatFriend.isOnline ? "Type a message below..." : currentChatFriend.nickname + " is offline. You can view past messages.");
            refreshChatDisplay();
        }
    }

    private void refreshChatDisplay() {
        if (chatDisplayArea == null || currentChatFriend == null ) return;

        chatDisplayArea.clear();
        if (currentChatFriend.getChatMessages().isEmpty()) {
            chatDisplayArea.setPromptText("No messages with " + currentChatFriend.nickname + " yet. Send one if they are online!");
        } else {
            currentChatFriend.getChatMessages().forEach(msg -> {
                String formattedMsg = msg.toString() + "\n";
                chatDisplayArea.appendText(formattedMsg);
            });
        }
        chatDisplayArea.setScrollTop(Double.MAX_VALUE);
    }


    public void updateFriendStatus(String uid, String ipAddress, int tcpPort, boolean isOnline) {
        Platform.runLater(() -> {
            if (friendsList == null) return;

            friendsList.stream()
                    .filter(f -> f.uid.equals(uid))
                    .findFirst()
                    .ifPresent(friend -> {
                        boolean statusChanged = friend.isOnline != isOnline;
                        friend.isOnline = isOnline;
                        friend.ipAddress = ipAddress;
                        friend.tcpPort = tcpPort;
                        if (isOnline) friend.lastSeen = LocalDateTime.now();

                        if (friendsListViewForConnect != null) friendsListViewForConnect.refresh();

                        if (currentChatFriend != null && currentChatFriend.uid.equals(uid)) {
                            updateChatUIForSelectedFriend();
                            if (statusChanged) {
                                String status = isOnline ? "online." : "offline.";
                                chatDisplayArea.appendText("\n--- " + friend.nickname + " is now " + status + " ---\n");
                                chatDisplayArea.setScrollTop(Double.MAX_VALUE);
                            }
                        } else if (statusChanged && isOnline) {
                            System.out.println("Friend status update: " + friend.nickname + " is now " + "online.");
                        }
                    });
        });
    }

    private VBox createWellBeingView() {
        VBox wellBeingOuterBox = new VBox(15);
        wellBeingOuterBox.setPadding(new Insets(10));
        VBox.setVgrow(wellBeingOuterBox, Priority.ALWAYS);

        Label titleLabel = new Label("Personal Well-Being Toolkit");
        titleLabel.getStyleClass().add("insights-title");

        Label descriptionLabel = new Label(
                "This section offers personalized activities and reflections based on your recent entries. " +
                        "These are suggestions to support your well-being, not replacements for professional advice."
        );
        descriptionLabel.getStyleClass().add("summary-label");
        descriptionLabel.setWrapText(true);

        Button refreshButton = new Button("Refresh Suggestions");
        refreshButton.getStyleClass().add("primary-button");
        applyButtonAnimations(refreshButton);
        refreshButton.setOnAction(e -> {
            applyShakeAnimation(refreshButton);
            updateWellBeingView();
        });
        HBox buttonBox = new HBox(refreshButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        wellBeingViewContent = new VBox(20);
        wellBeingViewContent.setPadding(new Insets(10));

        // Note: this view manages its own internal ScrollPane
        ScrollPane scrollPane = new ScrollPane(wellBeingViewContent);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        wellBeingOuterBox.getChildren().addAll(titleLabel, descriptionLabel, buttonBox, scrollPane);
        return wellBeingOuterBox;
    }

    // New View for Pharmacy & Prescriptions
    private VBox createPrescriptionsView() {
        VBox prescriptionsBox = new VBox(15);
        prescriptionsBox.setPadding(new Insets(10));
        prescriptionsBox.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("Pharmacy & Prescriptions");
        title.getStyleClass().add("insights-title");

        // Form to add new prescription
        Label dateLabel = new Label("Date:");
        dateLabel.getStyleClass().add("title-label");
        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.getStyleClass().add("date-picker");

        Label doctorLabel = new Label("Doctor's Name:");
        doctorLabel.getStyleClass().add("title-label");
        TextField doctorField = new TextField();
        doctorField.getStyleClass().add("text-field");
        doctorField.setPromptText("Enter doctor's name");

        Label prescriptionLabel = new Label("Prescription Details:");
        prescriptionLabel.getStyleClass().add("title-label");
        TextArea prescriptionArea = new TextArea();
        prescriptionArea.getStyleClass().add("text-area");
        prescriptionArea.setWrapText(true);
        prescriptionArea.setPromptText("Enter prescription text");
        VBox.setVgrow(prescriptionArea, Priority.ALWAYS);

        Label fileLabel = new Label("Upload Prescription File (Optional):");
        fileLabel.getStyleClass().add("title-label");
        Button uploadButton = new Button("Choose File");
        uploadButton.getStyleClass().add("primary-button");
        applyButtonAnimations(uploadButton);
        Label fileStatus = new Label("No file selected.");
        fileStatus.getStyleClass().add("summary-label");
        final File[] selectedFile = {null};
        uploadButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Prescription File");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"),
                    new FileChooser.ExtensionFilter("PDF", "*.pdf")
            );
            File file = chooser.showOpenDialog(mainApplicationStage);
            if (file != null) {
                selectedFile[0] = file;
                fileStatus.setText("Selected: " + file.getName());
            }
        });

        Button saveButton = new Button("Save Prescription");
        saveButton.getStyleClass().add("primary-button");
        applyButtonAnimations(saveButton);
        saveButton.setOnAction(e -> handleSavePrescription(datePicker, doctorField, prescriptionArea, selectedFile[0], fileStatus, saveButton));

        HBox uploadBox = new HBox(10, uploadButton, fileStatus);
        uploadBox.setAlignment(Pos.CENTER_LEFT);

        // List of existing prescriptions
        ListView<PrescriptionEntry> prescriptionsListView = new ListView<>(FXCollections.observableArrayList(prescriptionEntries));
        prescriptionsListView.getStyleClass().add("list-view");
        VBox.setVgrow(prescriptionsListView, Priority.ALWAYS);
        prescriptionsListView.setPlaceholder(new Label("No prescriptions saved yet."));

        prescriptionsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(PrescriptionEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setText(null);
                } else {
                    setText(entry.toString());
                }
            }
        });

        // Send to pharmacy button
        Button sendButton = new Button("Send Selected to Pharmacy");
        sendButton.getStyleClass().add("primary-button");
        applyButtonAnimations(sendButton);
        sendButton.setOnAction(e -> handleSendToPharmacy(prescriptionsListView.getSelectionModel().getSelectedItem()));

        HBox actionsBox = new HBox(10, saveButton, sendButton);
        actionsBox.setAlignment(Pos.CENTER_RIGHT);

        prescriptionsBox.getChildren().addAll(
                title,
                dateLabel, datePicker,
                doctorLabel, doctorField,
                prescriptionLabel, prescriptionArea,
                fileLabel, uploadBox,
                actionsBox,
                new Separator(),
                new Label("Saved Prescriptions:"),
                prescriptionsListView
        );

        return prescriptionsBox;
    }

    private void handleSavePrescription(DatePicker datePicker, TextField doctorField, TextArea prescriptionArea, File selectedFile, Label fileStatus, Button saveButton) {
        LocalDate date = datePicker.getValue();
        String doctorName = doctorField.getText().trim();
        String prescriptionText = prescriptionArea.getText().trim();
        String filePath = null;

        if (date == null) {
            showAlert("Invalid Date", "Please select a date.", Alert.AlertType.WARNING);
            return;
        }
        if (doctorName.isEmpty()) {
            showAlert("Missing Doctor Name", "Please enter the doctor's name.", Alert.AlertType.WARNING);
            return;
        }
        if (prescriptionText.isEmpty()) {
            showAlert("Missing Prescription", "Please enter prescription details.", Alert.AlertType.WARNING);
            return;
        }

        if (selectedFile != null) {
            try {
                String username = currentUser.username;
                String targetFileName = "prescription_" + date.format(DateTimeFormatter.ISO_DATE) + "_" + selectedFile.getName();
                Path targetPath = Paths.get(getUserSpecificFilePath(username, targetFileName));
                Files.copy(selectedFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                filePath = targetFileName;
            } catch (IOException ex) {
                showAlert("File Save Error", "Could not save prescription file: " + ex.getMessage(), Alert.AlertType.ERROR);
                return;
            }
        }

        PrescriptionEntry newEntry = new PrescriptionEntry(date, doctorName, prescriptionText, filePath);
        prescriptionEntries.add(newEntry);

        try {
            saveDataForCurrentUser();
            showAlert("Success", "Prescription saved successfully.", Alert.AlertType.INFORMATION);
            doctorField.clear();
            prescriptionArea.clear();
            fileStatus.setText("No file selected.");
            updatePrescriptionsView((VBox) contentArea.getChildren().get(8).getUserData()); // Refresh view
        } catch (IOException ex) {
            showAlert("Save Error", "Failed to save prescription: " + ex.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void handleSendToPharmacy(PrescriptionEntry selectedEntry) {
        if (selectedEntry == null) {
            showAlert("No Selection", "Please select a prescription to send.", Alert.AlertType.WARNING);
            return;
        }

        // Simulate sending to pharmacy (educational purpose)
        ComboBox<String> pharmacyCombo = new ComboBox<>(FXCollections.observableArrayList("Pharmacy A", "Pharmacy B", "Pharmacy C"));
        pharmacyCombo.setValue("Pharmacy A");

        Alert sendAlert = new Alert(Alert.AlertType.CONFIRMATION);
        sendAlert.setTitle("Send Prescription");
        sendAlert.setHeaderText("Send to Pharmacy");
        sendAlert.setContentText("Select pharmacy to send the prescription to:");

        VBox content = new VBox(10);
        content.getChildren().add(pharmacyCombo);
        sendAlert.getDialogPane().setContent(content);

        if (sendAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            String selectedPharmacy = pharmacyCombo.getValue();
            showAlert("Sent", "Prescription sent to " + selectedPharmacy + " (simulated for educational purposes).", Alert.AlertType.INFORMATION);
        }
    }

    private void updatePrescriptionsView(VBox prescriptionsVBox) {
        ListView<PrescriptionEntry> listView = (ListView<PrescriptionEntry>) prescriptionsVBox.lookup(".list-view");
        if (listView != null) {
            listView.setItems(FXCollections.observableArrayList(prescriptionEntries));
        }
    }

    private String getUserDataDir(String username) {
        return DATA_DIR + File.separator + username;
    }

    private String getUserSpecificFilePath(String username, String fileName) {
        return getUserDataDir(username) + File.separator + fileName;
    }

    private String getUserSpecificBackupPath(String username, String fileName) {
        return getUserDataDir(username) + File.separator + fileName.replace(".dat", "") + BACKUP_SUFFIX;
    }


    private void loadDataForCurrentUser() throws IOException {
        if (currentUser == null) {
            throw new IllegalStateException("No current user set. Cannot load data.");
        }
        String username = currentUser.username;
        ensureUserDirectory(username);

        journalEntries = loadSpecificUserData(username, USER_JOURNAL_FILE_NAME, List.class, new ArrayList<>());
        moodEntries = loadSpecificUserData(username, USER_MOOD_FILE_NAME, List.class, new ArrayList<>());
        myApplicationInfo = loadSpecificUserData(username, USER_MYINFO_FILE_NAME, MyInfo.class, null);

        List<Friend> loadedFriends = loadSpecificUserData(username, USER_FRIENDS_FILE_NAME, List.class, new ArrayList<>());
        friendsList = FXCollections.observableArrayList();
        if (loadedFriends != null) {
            for(Friend f : loadedFriends) {
                f.getChatMessages(); // Initialize transient field
                f.isOnline = false; // Default to offline until discovery
                friendsList.add(f);
            }
        }

        prescriptionEntries = loadSpecificUserData(username, USER_PRESCRIPTIONS_FILE_NAME, List.class, new ArrayList<>()); // Load prescriptions


        if (myApplicationInfo == null) {
            String newUID = UUID.randomUUID().toString();
            myApplicationInfo = new MyInfo(newUID, DEFAULT_TCP_PORT);
            System.out.println("New application info (UID, Port) created for user " + username);
        }
        if (journalEntries == null) journalEntries = new ArrayList<>();
        if (moodEntries == null) moodEntries = new ArrayList<>();
        if (prescriptionEntries == null) prescriptionEntries = new ArrayList<>();
    }

    private void saveDataForCurrentUser() throws IOException {
        if (currentUser == null) {
            return;
        }
        String username = currentUser.username;
        ensureUserDirectory(username);

        saveSpecificUserData(username, USER_JOURNAL_FILE_NAME, journalEntries);
        saveSpecificUserData(username, USER_MOOD_FILE_NAME, moodEntries);
        saveSpecificUserData(username, USER_MYINFO_FILE_NAME, myApplicationInfo);

        List<Friend> serializableFriends = (friendsList != null) ? new ArrayList<>(friendsList) : new ArrayList<>();
        saveSpecificUserData(username, USER_FRIENDS_FILE_NAME, serializableFriends);

        saveSpecificUserData(username, USER_PRESCRIPTIONS_FILE_NAME, prescriptionEntries); // Save prescriptions
    }

    private void loadUserProfiles() {
        allUsers = loadGenericData(USERS_DATA_FILE, USERS_DATA_FILE.replace(".dat", BACKUP_SUFFIX), List.class, new ArrayList<>());
        if (allUsers == null) allUsers = new ArrayList<>();
    }

    private void saveUserProfiles() throws IOException {
        saveGenericData(USERS_DATA_FILE, USERS_DATA_FILE.replace(".dat", BACKUP_SUFFIX), new ArrayList<>(allUsers));
    }


    private <T> T loadGenericData(String dataFilePath, String backupFilePath, Class<?> expectedPrimaryType, T defaultValue) {
        File dataFile = new File(dataFilePath);
        File backupFile = new File(backupFilePath);
        Object loadedObject = null;

        if (dataFile.exists() && dataFile.length() > 0) {
            try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(dataFile)))) {
                loadedObject = ois.readObject();
            } catch (ClassNotFoundException | InvalidClassException | StreamCorruptedException e) {
                System.err.println("Error: " + dataFile.getName() + " is corrupt or class structure changed: " + e.getMessage() + ". Attempting backup.");
                loadedObject = null;
            } catch (IOException e) {
                System.err.println("IOException reading " + dataFile.getName() + ": " + e.getMessage() + ". Attempting backup.");
                loadedObject = null;
            }
        }

        if (loadedObject == null && backupFile.exists() && backupFile.length() > 0) {
            System.out.println("Attempting to load from backup: " + backupFile.getName());
            try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(backupFile)))) {
                loadedObject = ois.readObject();
                System.out.println(backupFile.getName() + " (backup) loaded successfully.");
                Files.copy(backupFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                System.err.println("Error reading backup file " + backupFile.getName() + ": " + e.getMessage());
                loadedObject = null;
            }
        }

        if (expectedPrimaryType.isInstance(loadedObject)) {
            try {
                return (T) loadedObject;
            } catch (ClassCastException cce) {
                System.err.println("ClassCastException for " + dataFile.getName() + ". Type mismatch. Using default. Error: " + cce.getMessage());
                return defaultValue;
            }
        } else if (loadedObject != null) {
            System.err.println(dataFile.getName() + " contained an object of unexpected type (" + loadedObject.getClass().getName() + " vs " + expectedPrimaryType.getName() +"). Using default value.");
        }
        return defaultValue;
    }

    private void saveGenericData(String dataFilePath, String backupFilePath, Object dataToSave) throws IOException {
        File dataFile = new File(dataFilePath);
        File backupFile = new File(backupFilePath);

        ensureDataDirectory();

        if (dataFile.exists() && dataFile.length() > 0) {
            Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(dataFile)))) {
            oos.writeObject(dataToSave);
        } catch (IOException e) {
            System.err.println("Critical Error saving " + dataFile.getName() + ".");
            if (backupFile.exists()) {
                Files.copy(backupFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.err.println("Restored " + dataFile.getName() + " from backup.");
            }
            throw e;
        }
    }


    private <T> T loadSpecificUserData(String username, String fileName, Class<?> expectedPrimaryType, T defaultValue) {
        String filePath = getUserSpecificFilePath(username, fileName);
        String backupPath = getUserSpecificBackupPath(username, fileName);
        return loadGenericData(filePath, backupPath, expectedPrimaryType, defaultValue);
    }

    private void saveSpecificUserData(String username, String fileName, Object dataToSave) throws IOException {
        String filePath = getUserSpecificFilePath(username, fileName);
        String backupPath = getUserSpecificBackupPath(username, fileName);
        saveGenericData(filePath, backupPath, dataToSave);
    }

    private void ensureDataDirectory() throws IOException {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Could not create main data directory: " + dir.getAbsolutePath());
            }
        }
    }
    private void ensureUserDirectory(String username) throws IOException {
        File userDir = new File(getUserDataDir(username));
        if (!userDir.exists()) {
            if (!userDir.mkdirs()) {
                throw new IOException("Could not create data directory for user " + username + ": " + userDir.getAbsolutePath());
            }
        }
    }


    private void initializeNetworkManagerForCurrentUser() {
        if (networkManager != null) {
            networkManager.shutdown();
        }
        if (currentUser != null && myApplicationInfo != null) {
            networkManager = new NetworkManager(myApplicationInfo.uid, myApplicationInfo.tcpPort, this);
            networkManager.startServices();
        } else {
            System.err.println("Cannot initialize NetworkManager: currentUser or myApplicationInfo is null.");
            if (currentUser != null) {
                showAlert("Network Error", "Could not initialize network services for " + currentUser.username + ". User network ID missing.", Alert.AlertType.WARNING);
            }
        }
    }


    private LocalDate getMinDateFromEntries() {
        if (journalEntries == null && moodEntries == null) return LocalDate.now().minusYears(1);
        Optional<LocalDate> minJournalDate = (journalEntries != null) ? journalEntries.stream().map(e -> e.date).filter(Objects::nonNull).min(LocalDate::compareTo) : Optional.empty();
        Optional<LocalDate> minMoodDate = (moodEntries != null) ? moodEntries.stream().map(e -> e.date).filter(Objects::nonNull).min(LocalDate::compareTo) : Optional.empty();
        LocalDate defaultMin = LocalDate.now().minusYears(1);
        return minJournalDate
                .flatMap(jDate -> minMoodDate.map(mDate -> jDate.isBefore(mDate) ? jDate : mDate))
                .orElseGet(() -> minJournalDate.orElseGet(() -> minMoodDate.orElse(defaultMin)));
    }

    private LocalDate getMaxDateFromEntries() {
        if (journalEntries == null && moodEntries == null) return LocalDate.now();
        Optional<LocalDate> maxJournalDate = (journalEntries != null) ? journalEntries.stream().map(e -> e.date).filter(Objects::nonNull).max(LocalDate::compareTo) : Optional.empty();
        Optional<LocalDate> maxMoodDate = (moodEntries != null) ? moodEntries.stream().map(e -> e.date).filter(Objects::nonNull).max(LocalDate::compareTo) : Optional.empty();
        LocalDate defaultMax = LocalDate.now();
        return maxJournalDate
                .flatMap(jDate -> maxMoodDate.map(mDate -> jDate.isAfter(mDate) ? jDate : mDate))
                .orElseGet(() -> maxJournalDate.orElseGet(() -> maxMoodDate.orElse(defaultMax)));
    }

    private void updateInsights(VBox insightsBox, LineChart<String, Number> lineChart) {
        if (moodEntries == null || journalEntries == null) {
            showAlert("Data Missing", "User data (moods/journals) not loaded. Cannot generate insights.", Alert.AlertType.WARNING);
            return;
        }
        ComboBox<String> periodCombo = (ComboBox<String>) insightsBox.lookup("#periodComboBox");
        TitledPane summaryPane = (TitledPane) insightsBox.lookup("#summaryPane");
        TitledPane suggestionsPane = (TitledPane) insightsBox.lookup("#suggestionsPane");

        if (periodCombo == null || summaryPane == null || lineChart == null || suggestionsPane == null) {
            showAlert("Insights Error", "Key UI components for insights are missing. Cannot update.", Alert.AlertType.ERROR); return;
        }
        String selectedPeriod = periodCombo.getValue();
        if (selectedPeriod == null) {
            showAlert("Invalid Selection", "Please select a time period for insights.", Alert.AlertType.WARNING); return;
        }

        updateMoodChart(lineChart, selectedPeriod);
        List<MoodEntry> filteredEntries = filterMoodEntries(selectedPeriod);

        VBox summaryContentBox = new VBox(10);
        summaryContentBox.setPadding(new Insets(10));
        Label mostCommonMood = new Label("Most Common Mood: " + getMostCommonMood(filteredEntries));
        Label averageMood = new Label("Average Mood Level: " + String.format("%.1f", getAverageMoodLevel(filteredEntries)) + " / 10");
        Label moodVariability = new Label("Mood Fluctuation: " + getMoodVariabilityDescription(filteredEntries));
        Label entryCount = new Label("Total Mood Entries This Period: " + filteredEntries.size());

        for(Label l : List.of(mostCommonMood, averageMood, moodVariability, entryCount)) {
            l.getStyleClass().add("summary-label"); l.setWrapText(true);
        }
        summaryContentBox.getChildren().addAll(mostCommonMood, averageMood, moodVariability, entryCount);
        summaryPane.setContent(summaryContentBox);

        updateSuggestions(suggestionsPane);
    }

    private List<MoodEntry> filterMoodEntries(String selectedPeriod) {
        if (moodEntries == null) return Collections.emptyList();

        LocalDate endDateQuery = LocalDate.now();
        LocalDate startDateQuery;

        switch (selectedPeriod) {
            case "Last 7 Days": startDateQuery = endDateQuery.minusDays(6); break;
            case "Last 14 Days": startDateQuery = endDateQuery.minusDays(13); break;
            case "Last 30 Days": startDateQuery = endDateQuery.minusDays(29); break;
            case "This Month": startDateQuery = endDateQuery.withDayOfMonth(1); break;
            case "Last Month":
                LocalDate lastMonthDay = endDateQuery.minusMonths(1);
                startDateQuery = lastMonthDay.withDayOfMonth(1);
                endDateQuery = lastMonthDay.withDayOfMonth(lastMonthDay.lengthOfMonth());
                break;
            case "All Time":
            default:
                startDateQuery = getMinDateFromEntries();
                endDateQuery = getMaxDateFromEntries();
                break;
        }

        final LocalDate finalStartDate = startDateQuery;
        final LocalDate finalEndDate = endDateQuery;

        return moodEntries.stream()
                .filter(e -> e.date != null && !e.date.isBefore(finalStartDate) && !e.date.isAfter(finalEndDate))
                .sorted(Comparator.comparing(e -> e.date))
                .toList();
    }


    private void updateMoodChart(LineChart<String, Number> chart, String selectedPeriod) {
        if (chart == null || moodEntries == null) return;
        chart.getData().clear();
        List<MoodEntry> filteredEntries = filterMoodEntries(selectedPeriod);

        if (filteredEntries.isEmpty()) {
            chart.setTitle("Mood Trends (No data for " + selectedPeriod + ")");
            return;
        }
        chart.setTitle("Mood Trends (" + selectedPeriod + ")");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Average Mood");

        Map<LocalDate, Double> avgMoodsByDate = filteredEntries.stream()
                .collect(Collectors.groupingBy(e -> e.date,
                        Collectors.averagingInt(e -> getMoodValue(e.mood))));

        avgMoodsByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    LocalDate date = entry.getKey();
                    double avgMood = entry.getValue();
                    String moodDesc = avgMood >= 7.5 ? "Positive" : avgMood >= 4.5 ? "Neutral" : "Negative";

                    XYChart.Data<String, Number> dataPoint = new XYChart.Data<>(date.format(DateTimeFormatter.ofPattern("MMM d")), avgMood);
                    series.getData().add(dataPoint);

                    dataPoint.nodeProperty().addListener((obs, oldNode, newNode) -> {
                        if (newNode != null) {
                            Tooltip.install(newNode, new Tooltip(String.format("Date: %s\nAvg. Mood: %.1f (%s)",
                                    date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")), avgMood, moodDesc)));

                            newNode.setOnMouseEntered(me -> {
                                newNode.setStyle("-fx-background-color: #1ED760, #121212; -fx-cursor: hand;");
                                ScaleTransition scaleIn = new ScaleTransition(Duration.millis(150), newNode);
                                scaleIn.setToX(1.6); scaleIn.setToY(1.6);
                                scaleIn.play();
                            });
                            newNode.setOnMouseExited(me -> {
                                newNode.setStyle("");
                                ScaleTransition scaleOut = new ScaleTransition(Duration.millis(150), newNode);
                                scaleOut.setToX(1.0); scaleOut.setToY(1.0);
                                scaleOut.play();
                            });
                        }
                    });
                });

        chart.getData().add(series);
    }
    private String getMostCommonMood(List<MoodEntry> entries) {
        if (entries == null || entries.isEmpty()) return "N/A (No entries)";
        Map<String, Long> moodCounts = entries.stream()
                .filter(e -> e.mood != null)
                .collect(Collectors.groupingBy(e -> e.mood, Collectors.counting()));

        if (moodCounts.isEmpty()) return "N/A (No moods recorded)";

        long maxCount = moodCounts.values().stream().max(Long::compare).orElse(0L);
        if (maxCount == 0) return "N/A";

        List<String> mostCommon = moodCounts.entrySet().stream()
                .filter(e -> e.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        return String.join(" / ", mostCommon) + String.format(" (%d occurrence%s)", maxCount, maxCount == 1 ? "" : "s");
    }

    private double getAverageMoodLevel(List<MoodEntry> entries) {
        if (entries == null || entries.isEmpty()) return 0.0;
        return entries.stream()
                .filter(e -> e.mood != null)
                .mapToInt(e -> getMoodValue(e.mood))
                .average()
                .orElse(0.0);
    }

    private String getMoodVariabilityDescription(List<MoodEntry> entries) {
        if (entries == null || entries.size() < 2) return "N/A (Insufficient data)";
        List<Integer> moodValues = entries.stream()
                .filter(e -> e.mood != null)
                .map(e -> getMoodValue(e.mood))
                .toList();

        if (moodValues.size() < 2) return "N/A (Insufficient data points)";

        double mean = moodValues.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        double stdDev = Math.sqrt(moodValues.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / moodValues.size());

        if (stdDev < 1.2) return String.format("Low (Fairly Stable, SD: %.2f)", stdDev);
        if (stdDev < 2.8) return String.format("Moderate (Some Fluctuation, SD: %.2f)", stdDev);
        return String.format("High (Significant Changes, SD: %.2f)", stdDev);
    }

    private void handleExport(DatePicker startDatePicker, DatePicker endDatePicker, CheckBox includeJournalCheck, CheckBox includeMoodCheck, ToggleGroup formatToggle, Button exportBtn, Circle spinner, SVGPath checkMark) {
        if (journalEntries == null || moodEntries == null) {
            showAlert("Data Not Loaded", "Cannot export, user data is not available.", Alert.AlertType.WARNING);
            return;
        }
        if (formatToggle.getSelectedToggle() == null) {
            showAlert("Missing Format", "Please select an export format.", Alert.AlertType.WARNING); return;
        }
        String format = (String) formatToggle.getSelectedToggle().getUserData();
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();

        if (start == null || end == null) {
            showAlert("Invalid Date Range", "Please select valid start and end dates.", Alert.AlertType.WARNING); return;
        }
        if (end.isBefore(start)) {
            showAlert("Invalid Date Range", "End date cannot be before start date.", Alert.AlertType.WARNING); return;
        }
        if (!includeJournalCheck.isSelected() && !includeMoodCheck.isSelected()) {
            showAlert("Export Options", "Please select at least one type of content to export.", Alert.AlertType.WARNING); return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Exported Journal Data");
        String defaultFileName = String.format("MindMatters_Export_%s_%s_to_%s.%s",
                (currentUser != null ? currentUser.username : "User"),
                start.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                end.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                "CSV".equalsIgnoreCase(format) ? "csv" : "txt");
        fileChooser.setInitialFileName(defaultFileName);
        String extDesc = "CSV".equalsIgnoreCase(format) ? "CSV Files (*.csv)" : "Text Files (*.txt)";
        String extPattern = "CSV".equalsIgnoreCase(format) ? "*.csv" : "*.txt";
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(extDesc, extPattern));
        File file = fileChooser.showSaveDialog(mainApplicationStage);
        if (file == null) return;

        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationAlert.setTitle("Confirm Export Destination");
        confirmationAlert.setHeaderText("You are about to export data to:");
        confirmationAlert.setContentText(file.getAbsolutePath() + "\n\nProceed with export?");
        applyDialogStyles(confirmationAlert.getDialogPane());
        if (confirmationAlert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;


        String originalBtnText = exportBtn.getText();
        exportBtn.setText("Exporting...");
        exportBtn.setDisable(true);
        spinner.setVisible(true);
        checkMark.setVisible(false);
        RotateTransition spinnerAnim = new RotateTransition(Duration.seconds(1), spinner);
        spinnerAnim.setByAngle(360);
        spinnerAnim.setCycleCount(Animation.INDEFINITE);
        spinnerAnim.setInterpolator(Interpolator.LINEAR);
        spinnerAnim.play();

        new Thread(() -> {
            try {
                performExport(file, format, start, end, includeJournalCheck.isSelected(), includeMoodCheck.isSelected());
                Platform.runLater(() -> {
                    spinnerAnim.stop(); spinner.setVisible(false);
                    checkMark.setVisible(true);
                    ScaleTransition checkScale = new ScaleTransition(Duration.millis(200), checkMark);
                    checkScale.setFromX(0); checkScale.setFromY(0);
                    checkScale.setToX(1); checkScale.setToY(1);
                    checkScale.play();
                    exportBtn.setText("Export Successful!");
                });
            } catch (Exception expEx) {
                Platform.runLater(() -> {
                    showAlert("Export Error", "Failed to export journal data: " + expEx.getMessage(), Alert.AlertType.ERROR);
                    System.err.println("Error during data export: " + expEx.getMessage());
                });
            } finally {
                Platform.runLater(() -> {
                    PauseTransition pause = new PauseTransition(Duration.seconds(2));
                    pause.setOnFinished(event -> {
                        exportBtn.setText(originalBtnText);
                        exportBtn.setDisable(false);
                        checkMark.setVisible(false);
                    });
                    pause.play();
                });
            }
        }).start();
    }
    private void updateExportPreview(TextArea previewArea, DatePicker startDatePicker, DatePicker endDatePicker, ToggleGroup formatToggle, CheckBox includeJournalCheck, CheckBox includeMoodCheck) {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        String format = "None";
        if(formatToggle.getSelectedToggle() != null) {
            format = (String) formatToggle.getSelectedToggle().getUserData();
        }


        if (start == null || end == null) {
            previewArea.setText("Please select valid start and end dates for the export."); return;
        }
        if (end.isBefore(start)) {
            previewArea.setText("Error: End date cannot be before start date."); return;
        }
        if (!includeJournalCheck.isSelected() && !includeMoodCheck.isSelected()) {
            previewArea.setText("Please select content (Journal and/or Mood) to include in the export."); return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("EXPORT SUMMARY FOR USER: %s\n", (currentUser != null ? currentUser.username : "N/A")));
        sb.append("==========================\n");
        sb.append(String.format("Date Range: %s to %s\n", start.format(DateTimeFormatter.ISO_DATE), end.format(DateTimeFormatter.ISO_DATE)));
        sb.append(String.format("Export Format: %s\n", format));
        sb.append("Content to be Included:\n");
        if (includeJournalCheck.isSelected()) sb.append("  - Journal Entries (Full Text & Associated Mood)\n");
        if (includeMoodCheck.isSelected()) sb.append("  - Mood Tracker (Mood & Optional Notes)\n");
        sb.append("==========================");
        previewArea.setText(sb.toString());
    }


    private static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Critical Error: SHA-256 algorithm not found for password hashing.", e);
        }
    }
    void showAlert(String title, String message, Alert.AlertType alertType) {
        Platform.runLater(() -> {
            Alert alert = new Alert(alertType);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            applyDialogStyles(alert.getDialogPane());
            alert.showAndWait();
        });
    }

    private void applyDialogStyles(DialogPane dialogPane) {
        if (dialogPane == null) return;
        if (dialogPane.getScene() != null) {
            applyStylesToScene(dialogPane.getScene(), currentUser != null ? currentUser.themePreference : "dark");
        } else {
            dialogPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    applyStylesToScene(newScene, currentUser != null ? currentUser.themePreference : "dark");
                }
            });
        }
        if(!dialogPane.getStyleClass().contains("dialog-pane")){
            dialogPane.getStyleClass().add("dialog-pane");
        }
    }


    private String getRandomPrompt(String[] prompts) {
        return (prompts == null || prompts.length == 0) ? "How was your day in general?" : prompts[random.nextInt(prompts.length)];
    }

    private void applySlideInAnimation(Node node, double offsetX, double offsetY) {
        if (node == null) return;
        final double duration = 800;
        node.setOpacity(0);
        TranslateTransition tt = new TranslateTransition(Duration.millis(duration), node);
        if (offsetX != 0) {
            tt.setFromX(offsetX);
            tt.setToX(0);
        }
        if (offsetY != 0) {
            tt.setFromY(offsetY);
            tt.setToY(0);
        }
        tt.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition ft = new FadeTransition(Duration.millis(duration * 0.8), node);
        ft.setFromValue(0.0); ft.setToValue(1.0);
        ft.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition pt = new ParallelTransition(node, tt, ft);
        pt.play();
    }
    private void applyButtonAnimations(ButtonBase button) {
        if (button == null) return;

        Effect originalEffect = button.getEffect();
        boolean isPrimaryButton = button.getStyleClass().contains("primary-button");

        button.setOnMouseEntered(e -> {
            ScaleTransition scaleIn = new ScaleTransition(Duration.millis(100), button);
            scaleIn.setToX(1.05); scaleIn.setToY(1.05);
            scaleIn.setInterpolator(Interpolator.EASE_OUT);
            scaleIn.play();

            if (!isPrimaryButton && !button.getStyleClass().contains("sidebar-button")) {
                DropShadow hoverShadow = new DropShadow(BlurType.GAUSSIAN, Color.rgb(0,0,0,0.25), 8, 0, 1, 1);
                button.setEffect(hoverShadow);
            }
        });

        button.setOnMouseExited(e -> {
            ScaleTransition scaleOut = new ScaleTransition(Duration.millis(100), button);
            scaleOut.setToX(1.0); scaleOut.setToY(1.0);
            scaleOut.setInterpolator(Interpolator.EASE_OUT);
            scaleOut.play();

            if (!isPrimaryButton && !button.getStyleClass().contains("sidebar-button")) {
                button.setEffect(originalEffect);
            }
        });
    }
    private void applyShakeAnimation(Node node) {
        if (node == null) return;
        TranslateTransition tt = new TranslateTransition(Duration.millis(60), node);
        tt.setCycleCount(6);
        tt.setAutoReverse(true);
        tt.setFromX(-4); tt.setToX(4);
        tt.setInterpolator(Interpolator.EASE_BOTH);
        tt.play();
    }
    private void applyMoodButtonEffects(ToggleButton button) {
        button.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), button);
            if (isSelected) {
                st.setToX(1.05); st.setToY(1.05);
            } else {
                st.setToX(1.0);
            }
            st.play();
        });
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(150), button);
        scaleIn.setToX(1.03); scaleIn.setToY(1.03); scaleIn.setInterpolator(Interpolator.EASE_BOTH);
        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(150), button);
        scaleOut.setToX(1.0); scaleOut.setToY(1.0); scaleOut.setInterpolator(Interpolator.EASE_BOTH);

        button.setOnMouseEntered(e -> scaleIn.play());
        button.setOnMouseExited(e -> scaleOut.play());
    }

    private void initializeSuggestionRules() {
        suggestionRules.clear();

        suggestionRules.add(new SuggestionRule(
                "PERSISTENT_LOW_MOOD",
                "Experiencing low mood (Sad, Very Sad, Anxious, Stressed, Tired) for 3 or more of the last 7 days with entries.",
                (data, journalEs, moodEs) -> data.consecutiveLowMoodDays >= 2 ||
                        data.moodCountsLast7Days.entrySet().stream()
                                .filter(e -> Arrays.asList("Sad", "Very Sad", "Anxious", "Stressed", "Tired").contains(e.getKey()))
                                .mapToLong(Map.Entry::getValue).sum() >= 3,
                "It appears you've had several challenging mood days recently. Remember to be kind to yourself. " +
                        "Consider what small act of self-care might feel supportive right now (e.g., a short break, calming music, talking to someone).",
                "Persistent low moods can be taxing. Acknowledging them is the first step. " +
                        "Self-compassion involves treating yourself with the same kindness you'd offer a friend. If these feelings are ongoing or intense, " +
                        "reflecting on potential triggers or speaking with a trusted peer or professional could be beneficial.",
                10
        ));


        suggestionRules.add(new SuggestionRule(
                "HIGH_STRESS_ANXIETY_KEYWORDS",
                "Journal entries in the last 7 days frequently mention themes of stress or anxiety.",
                (data, journalEs, moodEs) -> {
                    long stressKeywordCount = data.keywordsInJournalLast7Days.stream()
                            .filter(k -> k.matches("stress|anxious|overwhelm|worried|panic|pressure|tense|burnout"))
                            .count();
                    return stressKeywordCount >= 3;
                },
                "Your recent writings suggest you might be under significant pressure or experiencing anxiety. " +
                        "Are there grounding techniques (like deep breathing or the 5-4-3-2-1 method) that could offer some immediate relief?",
                "When stress or anxiety is high, our thoughts can race. Grounding techniques help bring focus to the present moment. " +
                        "Consider also if there are any manageable changes to your environment or schedule that could reduce immediate stressors. " +
                        "It's okay to set boundaries or delegate if possible.",
                20
        ));

        suggestionRules.add(new SuggestionRule(
                "SLEEP_CONCERNS_EVIDENT",
                "Reports 'Tired' mood and journal entries mention sleep difficulties or fatigue in the last 7 days.",
                (data, journalEs, moodEs) -> {
                    boolean tiredReported = data.moodCountsLast7Days.getOrDefault("Tired", 0L) >= 1;
                    long sleepKeywordCount = data.keywordsInJournalLast7Days.stream()
                            .filter(kw -> kw.matches("sleep|insomnia|awake|restless|fatigue|exhausted|nosleep|cantsleep"))
                            .count();
                    return tiredReported && sleepKeywordCount >= 1;
                },
                "Feeling tired, and your entries mention sleep-related themes? Quality rest is crucial. " +
                        "Could reviewing your sleep hygiene (consistent schedule, calm pre-sleep routine, minimizing screens) be helpful?",
                "Consistent, restorative sleep underpins mental well-being. Sleep hygiene practices aim to create optimal conditions for sleep. " +
                        "This includes a cool, dark, quiet bedroom; avoiding caffeine/heavy meals before bed; and creating a wind-down period. " +
                        "If sleep issues persist, discussing them with a healthcare provider is important.",
                30
        ));

        suggestionRules.add(new SuggestionRule(
                "POSITIVE_MOOD_PATTERN",
                "Consistently positive or calm moods reported over several days in the last week.",
                (data, journalEs, moodEs) -> {
                    if (data.moodCountsLast7Days == null || moodEs == null) return false;
                    long positiveAndCalmMoodCount = data.moodCountsLast7Days.getOrDefault("Very Happy", 0L) +
                            data.moodCountsLast7Days.getOrDefault("Happy", 0L) +
                            data.moodCountsLast7Days.getOrDefault("Content", 0L) +
                            data.moodCountsLast7Days.getOrDefault("Calm", 0L) +
                            data.moodCountsLast7Days.getOrDefault("Energetic", 0L);
                    long totalMoodEntriesInPeriod = moodEs.stream()
                            .filter(me -> me.date.isAfter(LocalDate.now().minusDays(8)))
                            .count();
                    return totalMoodEntriesInPeriod >= 2 && positiveAndCalmMoodCount >= 2 && (totalMoodEntriesInPeriod <=3 || ((double)positiveAndCalmMoodCount / totalMoodEntriesInPeriod) >= 0.6);
                },
                "It's great to see a pattern of positive or calm moods recently! " +
                        "Perhaps take a moment to acknowledge what might be contributing to this period of well-being.",
                "Recognizing and understanding periods of positive well-being is valuable. " +
                        "Reflecting on activities, relationships, mindsets, or circumstances that align with these good feelings can help you consciously cultivate more of them. " +
                        "Savor these moments and learn from them.",
                40
        ));

        suggestionRules.add(new SuggestionRule(
                "LOW_ENGAGEMENT_RECENTLY",
                "Very few or no journal/mood entries in the past 7 days.",
                (data, journalEs, moodEs) -> {
                    if (journalEs == null || moodEs == null) return false;
                    boolean noRecentJournal = journalEs.stream()
                            .filter(je -> je.date.isAfter(LocalDate.now().minusDays(8)))
                            .findAny().isEmpty();
                    long recentMoodsCount = moodEs.stream()
                            .filter(me -> me.date.isAfter(LocalDate.now().minusDays(8)))
                            .count();
                    return noRecentJournal && recentMoodsCount <= 1;
                },
                "It's been a little while since your last entry. Even a brief check-in with yourself can be insightful. " +
                        "No pressure for a long entry – just a moment of reflection.",
                "Regular self-reflection, even if short, helps maintain awareness of your emotional landscape. " +
                        "If full journaling feels daunting, a quick mood log or noting one thought/feeling can still be beneficial. " +
                        "Consider what might make it easier to engage (e.g., setting a reminder, choosing a specific time).",
                5
        ));
    }
    private AnalysisData gatherAnalysisData(LocalDate analysisEndDate) {
        if (this.journalEntries == null || this.moodEntries == null) {
            return new AnalysisData(new HashMap<>(), new ArrayList<>(), 0);
        }

        int daysToAnalyze = 7;
        LocalDate analysisStartDate = analysisEndDate.minusDays(daysToAnalyze - 1);

        List<MoodEntry> recentMoods = this.moodEntries.stream()
                .filter(e -> e.date != null && !e.date.isBefore(analysisStartDate) && !e.date.isAfter(analysisEndDate))
                .sorted(Comparator.comparing(e -> e.date))
                .toList();

        List<JournalEntry> recentJournals = this.journalEntries.stream()
                .filter(e -> e.date != null && !e.date.isBefore(analysisStartDate) && !e.date.isAfter(analysisEndDate))
                .toList();

        Map<String, Long> moodCounts = recentMoods.stream()
                .filter(e -> e.mood != null)
                .collect(Collectors.groupingBy(e -> e.mood, Collectors.counting()));

        List<String> keywords = recentJournals.stream()
                .filter(e -> e.content != null && !e.content.isBlank())
                .flatMap(e -> Arrays.stream(e.content.toLowerCase().split("\\W+")))
                .filter(word -> word.length() > 3)
                .distinct()
                .toList();


        int maxConsecutiveLowMoods = 0;
        if (!recentMoods.isEmpty()) {
            Map<LocalDate, Boolean> dailyLowMoodStatus = new TreeMap<>();
            List<String> lowMoodStrings = Arrays.asList("Sad", "Very Sad", "Anxious", "Stressed", "Tired");

            for (LocalDate date = analysisStartDate; !date.isAfter(analysisEndDate); date = date.plusDays(1)) {
                final LocalDate currentDate = date;
                List<MoodEntry> entriesForDate = recentMoods.stream().filter(e -> e.date.isEqual(currentDate)).toList();
                if (!entriesForDate.isEmpty()) {
                    boolean hasLowMood = entriesForDate.stream().anyMatch(e -> lowMoodStrings.contains(e.mood));
                    dailyLowMoodStatus.put(currentDate, hasLowMood);
                }
            }


            int currentStreak = 0;
            for (LocalDate dateKey = analysisStartDate; !dateKey.isAfter(analysisEndDate); dateKey = dateKey.plusDays(1)) {
                if (dailyLowMoodStatus.getOrDefault(dateKey, false)) {
                    currentStreak++;
                } else {
                    maxConsecutiveLowMoods = Math.max(maxConsecutiveLowMoods, currentStreak);
                    currentStreak = 0;
                }
            }
            maxConsecutiveLowMoods = Math.max(maxConsecutiveLowMoods, currentStreak);
        }
        return new AnalysisData(moodCounts, keywords, maxConsecutiveLowMoods);
    }
    public List<SuggestionRule> generateSuggestions() {
        if (this.journalEntries == null || this.moodEntries == null) {
            return Collections.emptyList();
        }
        AnalysisData data = gatherAnalysisData(LocalDate.now());
        LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);

        List<JournalEntry> recentJournalEntriesForRules = this.journalEntries.stream()
                .filter(e -> e.date != null && !e.date.isBefore(sevenDaysAgo) && !e.date.isAfter(LocalDate.now()))
                .toList();
        List<MoodEntry> recentMoodEntriesForRules = this.moodEntries.stream()
                .filter(e -> e.date != null && !e.date.isBefore(sevenDaysAgo) && !e.date.isAfter(LocalDate.now()))
                .toList();


        return suggestionRules.stream()
                .filter(rule -> rule.condition.test(data, recentJournalEntriesForRules, recentMoodEntriesForRules))
                .sorted(Comparator.comparingInt(rule -> rule.priority))
                .toList();
    }

    private void updateSuggestions(TitledPane suggestionsPane) {
        List<SuggestionRule> activeSuggestions = generateSuggestions();
        VBox suggestionsContent = new VBox(15);
        suggestionsContent.setPadding(new Insets(10));

        if (activeSuggestions.isEmpty()) {
            Label noSuggestionsLabel = new Label("No specific suggestions at this moment. Keep up with your entries to receive tailored insights!");
            noSuggestionsLabel.getStyleClass().add("summary-label");
            suggestionsContent.getChildren().add(noSuggestionsLabel);
        } else {
            Label intro = new Label("Reflections & Suggestions Based on Your Recent Entries:");
            intro.getStyleClass().add("title-label");
            intro.setStyle("-fx-font-size: 16px;");
            suggestionsContent.getChildren().add(intro);

            int suggestionCount = 0;
            for (SuggestionRule rule : activeSuggestions) {
                // Limit displayed suggestions if many apply, but always show at least one if available
                if (suggestionCount++ >= 3 && activeSuggestions.size() > 3) {
                    Label seeMoreLabel = new Label("More suggestions available based on deeper analysis...");
                    seeMoreLabel.getStyleClass().add("summary-label");
                    seeMoreLabel.setStyle("-fx-font-style: italic;");
                    suggestionsContent.getChildren().add(seeMoreLabel);
                    break;
                }

                VBox suggestionBox = new VBox(8);
                suggestionBox.setPadding(new Insets(12));
                suggestionBox.getStyleClass().add("summary-pane");
                String originalStyle = suggestionBox.getStyle(); // May be null if not set by CSS initially
                suggestionBox.setOnMouseEntered(e -> suggestionBox.setStyle((originalStyle != null ? originalStyle : "") + "-fx-background-color: #333333; -fx-background-radius: 10px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);"));
                suggestionBox.setOnMouseExited(e -> suggestionBox.setStyle(originalStyle));


                Label suggestionTextLabel = new Label(rule.suggestionText);
                suggestionTextLabel.getStyleClass().add("summary-label");
                suggestionTextLabel.setWrapText(true);
                suggestionBox.getChildren().add(suggestionTextLabel);

                if (rule.detailedExplanation != null && !rule.detailedExplanation.isEmpty()) {
                    Hyperlink learnMoreLink = new Hyperlink("Learn More / Explore Further...");
                    learnMoreLink.getStyleClass().add("hyperlink");
                    learnMoreLink.setOnAction(e -> {
                        Alert detailAlert = new Alert(Alert.AlertType.INFORMATION);
                        detailAlert.setTitle("Suggestion Details: " + rule.conditionDescription);
                        detailAlert.setHeaderText(rule.suggestionText.length() > 100 ? rule.suggestionText.substring(0,100)+"..." : rule.suggestionText);

                        TextArea detailArea = new TextArea(rule.detailedExplanation);
                        detailArea.setWrapText(true);
                        detailArea.setEditable(false);
                        detailArea.setPrefRowCount(10);
                        detailArea.getStyleClass().add("text-area");

                        ScrollPane detailScrollPane = new ScrollPane(detailArea);
                        detailScrollPane.setFitToWidth(true);
                        detailScrollPane.setPrefHeight(200);
                        detailScrollPane.getStyleClass().add("scroll-pane");

                        applyDialogStyles(detailAlert.getDialogPane());
                        detailAlert.getDialogPane().setContent(detailScrollPane);
                        detailAlert.getDialogPane().setPrefWidth(600);
                        detailAlert.showAndWait();
                    });
                    HBox linkBox = new HBox(learnMoreLink);
                    linkBox.setAlignment(Pos.CENTER_RIGHT);
                    linkBox.setPadding(new Insets(5,0,0,0));
                    suggestionBox.getChildren().add(linkBox);
                }
                suggestionsContent.getChildren().add(suggestionBox);

                suggestionBox.setOpacity(0);
                FadeTransition ft = new FadeTransition(Duration.millis(300 + suggestionCount * 100), suggestionBox);
                ft.setToValue(1);
                TranslateTransition tt = new TranslateTransition(Duration.millis(300 + suggestionCount * 100), suggestionBox);
                tt.setFromY(10); tt.setToY(0);
                ParallelTransition pt = new ParallelTransition(suggestionBox, ft, tt);
                pt.play();
            }
        }
        ScrollPane suggestionsScrollPane = new ScrollPane(suggestionsContent);
        suggestionsScrollPane.setFitToWidth(true);
        suggestionsScrollPane.setFitToHeight(false);
        suggestionsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        suggestionsScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        suggestionsScrollPane.getStyleClass().add("scroll-pane");

        suggestionsPane.setContent(suggestionsScrollPane);
        VBox.setVgrow(suggestionsScrollPane, Priority.ALWAYS);

        suggestionsPane.setExpanded(!activeSuggestions.isEmpty());
    }
    private void initializeWellBeingTasks() {
        wellBeingTaskList.clear();

        wellBeingTaskList.add(new WellBeingTask(
                "Guided Mindful Breathing (5 min)",
                "Find a quiet space. Sit comfortably. Close your eyes or soften your gaze. Gently bring your attention to your breath. Notice the sensation of air entering your nostrils, filling your lungs, and the release as you exhale. Don't try to change your breath, just observe its natural rhythm. If your mind wanders, gently guide it back. Continue for 5 minutes.\n\nBenefits: Calms the nervous system, reduces acute stress, improves focus.",
                "Mindfulness & Relaxation", "5 mins",
                data -> data.recentNegativeMoods.contains("Stressed") || data.recentNegativeMoods.contains("Anxious") || data.recentKeywordsFromJournals.stream().anyMatch(k -> k.matches("stress|anxiety|overwhelm|panic|tense"))
        ));
        wellBeingTaskList.add(new WellBeingTask(
                "Body Scan Meditation (10 min)",
                "Lie down or sit comfortably. Bring awareness to your feet, noticing any sensations without judgment. Slowly move your attention up through your legs, torso, arms, neck, and head, pausing at each part to observe sensations (tingling, warmth, tension). If you find tension, try to breathe into it and soften. The goal is observation, not necessarily relaxation, though it may occur.\n\nBenefits: Increases body awareness, can release physical tension, promotes calm.",
                "Mindfulness & Relaxation", "10 mins",
                data -> data.recentNegativeMoods.contains("Stressed") || data.recentKeywordsFromJournals.stream().anyMatch(k -> k.matches("tense|ache|pain|restless"))
        ));

        wellBeingTaskList.add(new WellBeingTask(
                "Three Good Things / Gratitude Journaling",
                "At the end of your day, write down three things that went well and briefly explain why they went well or what you're grateful for regarding them. They can be small (a good cup of coffee) or large (a personal achievement).\n\nBenefits: Shifts focus to positive experiences, boosts optimism, improves mood over time.",
                "Positive Psychology", "5-7 mins",
                data -> true
        ));
        wellBeingTaskList.add(new WellBeingTask(
                "Savoring a Positive Memory",
                "Bring to mind a recent positive experience. Close your eyes and relive it in detail: what did you see, hear, smell, feel (emotionally and physically)? Try to prolong the positive feelings associated with this memory for a few minutes.\n\nBenefits: Amplifies positive emotions, builds resilience.",
                "Positive Psychology", "3-5 mins",
                data -> data.recentPositiveMoods.isEmpty() && !data.recentNegativeMoods.isEmpty()
        ));


        wellBeingTaskList.add(new WellBeingTask(
                "Mindful Walking (10-15 min)",
                "If possible, go for a short walk. Pay attention to the sensation of your feet on the ground, the movement of your body, the sights, sounds, and smells around you. If indoors, walk slowly from one end of a room to another.\n\nBenefits: Combines light physical activity with mindfulness, can boost energy and clear the mind.",
                "Gentle Activity", "10-15 mins",
                data -> data.recentNegativeMoods.contains("Tired") || data.recentKeywordsFromJournals.stream().anyMatch(k -> k.matches("fatigue|lethargic|stuck|restless"))
        ));
        wellBeingTaskList.add(new WellBeingTask(
                "Creative Outlet (15-20 min)",
                "Engage in a simple creative activity you enjoy: sketching, coloring, writing for pleasure (not journaling), playing an instrument, listening attentively to new music. Focus on the process, not the outcome.\n\nBenefits: Can be a form of emotional expression, stress relief, and flow state induction.",
                "Creative Engagement", "15-20 mins",
                data -> data.lowEngagementJournal || data.recentKeywordsFromJournals.stream().anyMatch(k -> k.matches("bored|uninspired|stuck"))
        ));

        wellBeingTaskList.add(new WellBeingTask(
                "Journal Prompt: Exploring a Challenge",
                "Pick one challenge you're currently facing. Write about it from different perspectives: \n1. What are the objective facts? \n2. What are your thoughts and feelings about it? \n3. What is one small, actionable step you could take related to this challenge, or what's one aspect you can control?\n\nBenefits: Helps in problem-solving, reduces feeling overwhelmed, promotes agency.",
                "Problem Solving & Reflection", "10-15 mins",
                data -> data.recentNegativeMoods.contains("Stressed") || data.recentNegativeMoods.contains("Anxious") || data.recentKeywordsFromJournals.stream().anyMatch(k -> k.matches("overwhelm|problem|stuck"))
        ));

    }
    private AnalysisDataForWellBeing gatherWellBeingAnalysisData() {
        if (this.journalEntries == null || this.moodEntries == null) {
            return new AnalysisDataForWellBeing(new HashSet<>(), new HashSet<>(), new HashSet<>(), true, true);
        }
        LocalDate sevenDaysAgo = LocalDate.now().minusDays(6);

        Set<String> negativeMoodsCollector = new HashSet<>();
        Set<String> positiveMoodsCollector = new HashSet<>();
        Set<String> keywordsCollector = new HashSet<>();

        List<String> lowMoodMarkers = Arrays.asList("Sad", "Very Sad", "Anxious", "Stressed", "Tired");
        List<String> positiveMoodMarkers = Arrays.asList("Very Happy", "Happy", "Content", "Calm", "Energetic");

        long journalCountLast7Days = 0;
        for (JournalEntry entry : this.journalEntries) {
            if (entry.date != null && !entry.date.isBefore(sevenDaysAgo) && !entry.date.isAfter(LocalDate.now())) {
                journalCountLast7Days++;
                if (entry.mood != null) {
                    if (lowMoodMarkers.contains(entry.mood)) negativeMoodsCollector.add(entry.mood);
                    if (positiveMoodMarkers.contains(entry.mood)) positiveMoodsCollector.add(entry.mood);
                }
                if (entry.content != null && !entry.content.isBlank()) {
                    keywordsCollector.addAll(Arrays.stream(entry.content.toLowerCase().split("\\W+"))
                            .filter(s -> s.length() > 3).collect(Collectors.toSet()));
                }
            }
        }

        long moodEntryCountLast7Days = 0;
        for (MoodEntry entry : this.moodEntries) {
            if (entry.date != null && !entry.date.isBefore(sevenDaysAgo) && !entry.date.isAfter(LocalDate.now())) {
                moodEntryCountLast7Days++;
                if (entry.mood != null) {
                    if (lowMoodMarkers.contains(entry.mood)) negativeMoodsCollector.add(entry.mood);
                    if (positiveMoodMarkers.contains(entry.mood)) positiveMoodsCollector.add(entry.mood);
                }
                if (entry.notes != null && !entry.notes.isBlank()) {
                    keywordsCollector.addAll(Arrays.stream(entry.notes.toLowerCase().split("\\W+"))
                            .filter(s -> s.length() > 3).collect(Collectors.toSet()));
                }
            }
        }

        boolean lowJournalEngagement = journalCountLast7Days < 2;
        boolean lowMoodLogEngagement = moodEntryCountLast7Days < 2;

        return new AnalysisDataForWellBeing(negativeMoodsCollector, positiveMoodsCollector, keywordsCollector, lowJournalEngagement, lowMoodLogEngagement);
    }
    private List<WellBeingTask> generateWellBeingTasks() {
        AnalysisDataForWellBeing analysisData = gatherWellBeingAnalysisData();
        List<WellBeingTask> allApplicableTasks = wellBeingTaskList.stream()
                .filter(task -> task.condition.test(analysisData))
                .toList();

        List<WellBeingTask> shuffledTasks = new ArrayList<>(allApplicableTasks);
        Collections.shuffle(shuffledTasks, random);
        return shuffledTasks.stream().limit(5).toList();
    }
    private void updateWellBeingView() {
        if (wellBeingViewContent == null) return;

        wellBeingViewContent.getChildren().clear();
        if (this.journalEntries == null || this.moodEntries == null) {
            Label dataMissingLabel = new Label("Please log in or ensure your data is loaded to see well-being tasks.");
            dataMissingLabel.getStyleClass().add("summary-label");
            wellBeingViewContent.getChildren().add(dataMissingLabel);
            return;
        }
        populateWellBeingContent();
    }

    private void populateWellBeingContent() {
        if (wellBeingViewContent == null) return;
        List<WellBeingTask> suggestedTasks = generateWellBeingTasks();

        if (suggestedTasks.isEmpty()) {
            Label noTasksLabel = new Label("No specific well-being tasks suggested at this moment. " +
                    "Keep journaling to receive personalized tips and activities! Your insights help tailor these suggestions.");
            noTasksLabel.getStyleClass().add("summary-label");
            noTasksLabel.setWrapText(true);
            noTasksLabel.setPadding(new Insets(20));
            noTasksLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            wellBeingViewContent.getChildren().add(noTasksLabel);
            wellBeingViewContent.setOpacity(1);
            wellBeingViewContent.setTranslateY(0);
            return;
        }

        Map<String, List<WellBeingTask>> tasksByCategory = suggestedTasks.stream()
                .collect(Collectors.groupingBy(task -> task.category, LinkedHashMap::new, Collectors.toList()));

        int delay = 0;
        for (Map.Entry<String, List<WellBeingTask>> entry : tasksByCategory.entrySet()) {
            String category = entry.getKey();
            List<WellBeingTask> tasks = entry.getValue();

            VBox categoryBox = new VBox(10);
            categoryBox.setOpacity(0);
            Label categoryLabel = new Label(category);
            categoryLabel.getStyleClass().add("title-label");
            categoryLabel.setStyle("-fx-font-size: 20px; -fx-padding: 15px 0 5px 0;");
            categoryBox.getChildren().add(categoryLabel);

            for (WellBeingTask task : tasks) {
                VBox taskItemBox = new VBox(8);
                taskItemBox.setPadding(new Insets(15));
                taskItemBox.getStyleClass().add("summary-pane");
                DropShadow hoverShadow = new DropShadow(BlurType.GAUSSIAN, Color.rgb(0,0,0,0.3), 15, 0.5, 2, 2);
                Effect originalEffect = taskItemBox.getEffect();
                taskItemBox.setOnMouseEntered(e -> {
                    taskItemBox.setEffect(hoverShadow);
                    ScaleTransition st = new ScaleTransition(Duration.millis(150), taskItemBox);
                    st.setToX(1.02); st.setToY(1.02);
                    st.play();
                });
                taskItemBox.setOnMouseExited(e -> {
                    taskItemBox.setEffect(originalEffect);
                    ScaleTransition st = new ScaleTransition(Duration.millis(150), taskItemBox);
                    st.setToX(1.0); st.setToY(1.0);
                    st.play();
                });


                Label taskTitle = new Label(task.title);
                taskTitle.getStyleClass().add("title-label");
                taskTitle.setStyle("-fx-font-size: 16px;");

                Label timeEstimateLabel = new Label("Est. Time: " + task.estimatedTime);
                timeEstimateLabel.getStyleClass().add("summary-label");
                timeEstimateLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #A0A0A0;");

                Label taskDescriptionLabel = new Label(task.description);
                taskDescriptionLabel.getStyleClass().add("summary-label");
                taskDescriptionLabel.setWrapText(true);

                taskItemBox.getChildren().addAll(taskTitle, timeEstimateLabel, taskDescriptionLabel);
                categoryBox.getChildren().add(taskItemBox);
            }
            wellBeingViewContent.getChildren().add(categoryBox);

            FadeTransition ft = new FadeTransition(Duration.millis(400), categoryBox);
            ft.setFromValue(0); ft.setToValue(1);
            ft.setDelay(Duration.millis(delay));
            ft.play();
            delay += 100;
        }
    }
    private int getMoodValue(String mood) {
        if (mood == null) return 5; // Neutral as default
        return switch (mood.toLowerCase()) {
            case "very happy" -> 10;
            case "happy" -> 8;
            case "energetic", "content" -> 7;
            case "calm" -> 6;
            case "neutral" -> 5;
            case "tired" -> 4;
            case "sad", "anxious" -> 3;
            case "stressed" -> 2;
            case "very sad" -> 1;
            default -> 5; // Fallback for any unknown mood string
        };
    }
    private void performExport(File file, String format, LocalDate startDate, LocalDate endDate, boolean includeJournal, boolean includeMood) throws IOException {
        if (this.journalEntries == null || this.moodEntries == null) {
            throw new IOException("User data not loaded. Cannot perform export.");
        }

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8)))) {
            writer.printf("MENTAL HEALTH JOURNAL EXPORT FOR USER: %s\n=================================\nDate Range: %s to %s\nExport Generated: %s\nFormat: %s\nContent Included: %s%s\n=================================\n\n",
                    (currentUser != null ? currentUser.username : "N/A"),
                    startDate.format(DateTimeFormatter.ISO_DATE), endDate.format(DateTimeFormatter.ISO_DATE),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    format,
                    (includeJournal ? "Journal Entries " : ""),
                    (includeMood ? (includeJournal ? "& Mood Data" : "Mood Data") : ""));

            if ("CSV".equalsIgnoreCase(format)) {
                exportAsCsv(writer, startDate, endDate, includeJournal, includeMood);
            } else {
                exportAsTxt(writer, startDate, endDate, includeJournal, includeMood);
            }
        }
    }
    private void exportAsTxt(PrintWriter writer, LocalDate startDate, LocalDate endDate, boolean includeJournal, boolean includeMood) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
        if (includeJournal) {
            writer.println("--- JOURNAL ENTRIES ---\n");
            journalEntries.stream()
                    .filter(e -> e.date != null && !e.date.isBefore(startDate) && !e.date.isAfter(endDate))
                    .sorted(Comparator.comparing(e -> e.date))
                    .forEach(e -> writer.printf("Date: %s\nMood Reported: %s\n\nEntry Content:\n%s\n\n-----\n\n",
                            e.date.format(dtf), e.mood, e.content.trim()));
            writer.println("--- END JOURNAL ENTRIES ---\n");
        }
        if (includeMood) {
            writer.println("--- MOOD TRACKER ENTRIES ---\n");
            moodEntries.stream()
                    .filter(e -> e.date != null && !e.date.isBefore(startDate) && !e.date.isAfter(endDate))
                    .sorted(Comparator.comparing(e -> e.date))
                    .forEach(e -> writer.printf("Date: %s\nMood: %s\n%s\n-----\n",
                            e.date.format(dtf), e.mood,
                            e.notes.isEmpty() ? "No additional notes." : "Notes:\n" + e.notes.trim()));
            writer.println("\n--- END MOOD TRACKER ENTRIES ---");
        }
    }

    private void exportAsCsv(PrintWriter writer, LocalDate startDate, LocalDate endDate, boolean includeJournal, boolean includeMood) {
        DateTimeFormatter dtf = DateTimeFormatter.ISO_DATE;
        writer.println("EntryType,Date,Mood,ContentOrNotes,SourceAppTab");

        if (includeJournal) {
            journalEntries.stream()
                    .filter(e -> e.date != null && !e.date.isBefore(startDate) && !e.date.isAfter(endDate))
                    .sorted(Comparator.comparing(e -> e.date))
                    .forEach(e -> writer.printf("JournalEntry,%s,%s,%s,JournalTab\n",
                            e.date.format(dtf),
                            escapeCsv(e.mood),
                            escapeCsv(e.content)));
        }
        if (includeMood) {
            moodEntries.stream()
                    .filter(e -> e.date != null && !e.date.isBefore(startDate) && !e.date.isAfter(endDate))
                    .sorted(Comparator.comparing(e -> e.date))
                    .forEach(e -> writer.printf("MoodTrackerEntry,%s,%s,%s,MoodTrackerTab\n",
                            e.date.format(dtf),
                            escapeCsv(e.mood),
                            escapeCsv(e.notes)));
        }
    }

    private String escapeCsv(String value) {
        if (value == null || value.isEmpty()) return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\r") || escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private ScrollPane createConfiguredScrollPane(Node content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("scroll-pane");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // Add a listener to set the scroll speed once the skin is applied and the scrollbar exists
        scrollPane.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                ScrollBar verticalScrollBar = (ScrollBar) scrollPane.lookup(".scroll-bar:vertical");
                if (verticalScrollBar != null) {
                    // Increase the vertical scroll speed for easier navigation.
                    // This value represents the distance to scroll per mouse wheel 'tick'.
                    verticalScrollBar.setUnitIncrement(20);
                }
            }
        });

        return scrollPane;
    }
}


class NetworkManager {
    private final String myUid;
    private final int myTcpPort;
    private final HelloApplication app;
    private ScheduledExecutorService scheduler;
    private DatagramSocket udpSocket;
    private ServerSocket tcpServerSocket;
    private volatile boolean running = true;

    private static final int UDP_BROADCAST_PORT = 25565;
    private static final int PRESENCE_INTERVAL_SECONDS = 15;
    private static final int FRIEND_TIMEOUT_SECONDS = PRESENCE_INTERVAL_SECONDS * 3 + 5;


    public NetworkManager(String myUid, int myTcpPort, HelloApplication app) {
        this.myUid = myUid;
        this.myTcpPort = myTcpPort;
        this.app = app;
        System.out.println("NetworkManager initialized for UID: " + this.myUid + " on TCP Port: " + this.myTcpPort);
    }

    public void startServices() {
        running = true;
        scheduler = Executors.newScheduledThreadPool(3);

        scheduler.execute(this::listenForUdpBroadcasts);
        scheduler.scheduleAtFixedRate(this::broadcastPresence, 5, PRESENCE_INTERVAL_SECONDS, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkFriendTimeouts, FRIEND_TIMEOUT_SECONDS, FRIEND_TIMEOUT_SECONDS / 2, TimeUnit.SECONDS);
        scheduler.execute(this::startTcpServer);

        System.out.println("Network services initiated. User UID: " + myUid + ". Listening on TCP Port: " + myTcpPort + ". UDP Discovery on: " + UDP_BROADCAST_PORT);
    }

    private void checkFriendTimeouts() {
        if (!running || app.friendsList == null) return;
        LocalDateTime now = LocalDateTime.now();
        app.friendsList.forEach(friend -> {
            if (friend.isOnline && friend.lastSeen != null &&
                    java.time.Duration.between(friend.lastSeen, now).getSeconds() > FRIEND_TIMEOUT_SECONDS) {
                System.out.println("Peer " + friend.uid + " (" + friend.nickname + ") timed out.");
                app.updateFriendStatus(friend.uid, friend.ipAddress, friend.tcpPort, false);
            }
        });
    }


    private void broadcastPresence() {
        if (!running || myUid == null) return;
        try (DatagramSocket broadcastSocket = new DatagramSocket()){
            broadcastSocket.setBroadcast(true);
            String message = String.format("ALIVE:%s:%d", myUid, myTcpPort);
            byte[] sendData = message.getBytes(StandardCharsets.UTF_8);

            List<InetAddress> broadcastAddresses = new ArrayList<>();
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                        continue;
                    }
                    for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                        InetAddress broadcast = interfaceAddress.getBroadcast();
                        if (broadcast != null) {
                            broadcastAddresses.add(broadcast);
                        }
                    }
                }
                if (broadcastAddresses.isEmpty()) {
                    System.err.println("Warning: No broadcast addresses found for network interfaces. Using 255.255.255.255.");
                    broadcastAddresses.add(InetAddress.getByName("255.255.255.255"));
                }
            } catch (SocketException | UnknownHostException e) {
                System.err.println("Error getting broadcast addresses: " + e.getMessage() + ". Using 255.255.255.255.");
                broadcastAddresses.clear();
                try {
                    broadcastAddresses.add(InetAddress.getByName("255.255.255.255"));
                } catch (UnknownHostException ignored) { /* Should not happen for 255.255.255.255 */ }
            }


            for (InetAddress address : broadcastAddresses) {
                try {
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, UDP_BROADCAST_PORT);
                    broadcastSocket.send(sendPacket);
                } catch (IOException e) {
                    System.err.println("Network Error: Could not send broadcast to " + address + " for UID " + myUid + ". Error: " + e.getMessage());
                }
            }
        } catch (SocketException e) {
            System.err.println("Network Error: Could not create broadcast socket: " + e.getMessage());
        }
    }

    private void listenForUdpBroadcasts() {
        try {
            udpSocket = new DatagramSocket(null);
            udpSocket.setReuseAddress(true);
            udpSocket.bind(new InetSocketAddress(UDP_BROADCAST_PORT));

            byte[] receiveData = new byte[1024];
            System.out.println("UDP Discovery Listener active on port " + UDP_BROADCAST_PORT);

            while (running) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                try {
                    udpSocket.receive(receivePacket);
                    String message = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8);
                    InetAddress senderIp = receivePacket.getAddress();

                    String[] parts = message.split(":");
                    if (parts.length == 3 && "ALIVE".equals(parts[0])) {
                        String senderUid = parts[1];
                        int senderTcpPort;
                        try {
                            senderTcpPort = Integer.parseInt(parts[2]);
                        } catch (NumberFormatException e) {
                            System.err.println("Received ALIVE message with invalid port: " + message + " from " + senderIp.getHostAddress());
                            continue;
                        }

                        if (!senderUid.equals(myUid)) {
                            Optional<HelloApplication.Friend> knownFriendOpt = (app.friendsList != null) ?
                                    app.friendsList.stream().filter(f -> f.uid.equals(senderUid)).findFirst() : Optional.empty();

                            if (knownFriendOpt.isPresent()) {
                                app.updateFriendStatus(senderUid, senderIp.getHostAddress(), senderTcpPort, true);
                            }
                        }
                    }
                } catch (SocketException se) {
                    if (running) System.err.println("UDP SocketException in listener (potentially during shutdown): " + se.getMessage());
                    else break;
                } catch (IOException e) {
                    if (running) System.err.println("IOException in UDP listener: " + e.getMessage());
                }
            }
        } catch (SocketException e) {
            System.err.println("FATAL: Could not bind UDP listener to port " + UDP_BROADCAST_PORT + ". Error: " + e.getMessage());
        } finally {
            if (udpSocket != null && !udpSocket.isClosed()) {
                udpSocket.close();
            }
            System.out.println("UDP Discovery Listener stopped for UID " + myUid);
        }
    }

    private void startTcpServer() {
        try {
            tcpServerSocket = new ServerSocket(myTcpPort);
            System.out.println("TCP Message Server active for UID " + myUid + " on port " + myTcpPort);
            while (running) {
                try {
                    Socket clientSocket = tcpServerSocket.accept();
                    if (!running) {
                        clientSocket.close();
                        break;
                    }
                    System.out.println("TCP connection accepted from: " + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort() + " for user " + myUid);
                    scheduler.execute(() -> handleTcpClient(clientSocket));
                } catch (SocketException se) {
                    if (running) System.err.println("TCP Server SocketException (potentially during shutdown accept): " + se.getMessage());
                    else break;
                }
                catch (IOException e) {
                    if (running) System.err.println("IOException in TCP server accept loop for UID " + myUid + ": " + e.getMessage());
                }
            }
        } catch (BindException e) {
            System.err.println("FATAL: Could not start TCP server for UID " + myUid + " on port " + myTcpPort + ". Port might be in use. Error: " + e.getMessage());
            app.showAlert("Network Error", "Could not start TCP server on port " + myTcpPort + ". It might be in use. Connect feature will be limited.", Alert.AlertType.ERROR);
        } catch (IOException e) {
            System.err.println("FATAL: General IOException starting TCP server for UID " + myUid + " on port " + myTcpPort + ". Error: " + e.getMessage());
        } finally {
            if (tcpServerSocket != null && !tcpServerSocket.isClosed()) {
                try {
                    tcpServerSocket.close();
                } catch (IOException e) { System.err.println("Error closing TCP server socket during shutdown for UID " + myUid + ": "+e.getMessage()); }
            }
            System.out.println("TCP Message Server stopped for UID " + myUid);
        }
    }

    private void handleTcpClient(Socket clientSocket) {
        String remoteClientUid = "Unknown";
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))
        ) {
            String line;
            line = reader.readLine();
            if (line != null && line.startsWith("INITMSG:")) {
                remoteClientUid = line.substring("INITMSG:".length()).trim();
                System.out.println("TCP Handshake successful with peer UID: " + remoteClientUid + " on connection for user " + myUid);

                String clientIp = clientSocket.getInetAddress().getHostAddress();
                final String finalRemoteClientUid = remoteClientUid;
                if (app.friendsList != null) {
                    app.friendsList.stream()
                            .filter(f -> f.uid.equals(finalRemoteClientUid))
                            .findFirst()
                            .ifPresent(friend -> app.updateFriendStatus(friend.uid, clientIp, friend.tcpPort, true));
                }


            } else {
                System.err.println("TCP Connection from " + clientSocket.getInetAddress().getHostAddress() +
                        " - Invalid or missing INITMSG for user " + myUid + ". Closing connection.");
                clientSocket.close();
                return;
            }

            while (running && (line = reader.readLine()) != null) {
                if (line.startsWith("MESSAGE:")) {
                    String messageContent = line.substring("MESSAGE:".length());
                    System.out.println("TCP Message received from UID " + remoteClientUid + " for user " + myUid + ": " + messageContent);
                    app.receiveMessage(remoteClientUid, messageContent);
                } else if (line.equalsIgnoreCase("BYE")) {
                    System.out.println("Peer " + remoteClientUid + " sent BYE to user " + myUid + ". Closing connection.");
                    break;
                }else {
                    System.out.println("TCP Received unhandled line from " + remoteClientUid + " for user " + myUid + ": " + line);
                }
            }
        } catch (SocketException se) {
            System.out.println("TCP Connection with " + remoteClientUid + " ("+clientSocket.getInetAddress().getHostAddress() +") for user "+ myUid +" ended: " + se.getMessage());
        } catch (IOException e) {
            System.err.println("IOException handling TCP client " + remoteClientUid + " ("+clientSocket.getInetAddress().getHostAddress() +") for user "+myUid+": " + e.getMessage());
        } finally {
            try {
                if (!clientSocket.isClosed()) clientSocket.close();
            } catch (IOException e) { System.err.println("Error closing client socket for " + remoteClientUid +" on user "+myUid+"'s server: " + e.getMessage());}
            System.out.println("Closed TCP connection with " + remoteClientUid + " for user " + myUid);
        }
    }

    public void sendMessage(HelloApplication.Friend friend, String message) {
        if (myUid == null) {
            System.err.println("Cannot send message: Current user's UID is not set in NetworkManager.");
            app.showAlert("Network Error", "Your network identity is not set. Cannot send message.", Alert.AlertType.ERROR);
            return;
        }
        if (!friend.isOnline || friend.ipAddress == null || friend.tcpPort <= 0) {
            System.err.println("Cannot send message to " + friend.uid + " ("+friend.nickname+"): peer is offline or connection info is missing.");
            app.showAlert("Send Error", "Peer " + friend.nickname + " is offline or connection details are unknown. Cannot send message.", Alert.AlertType.WARNING);
            return;
        }

        scheduler.execute(() -> {
            try (Socket socket = new Socket()) {
                System.out.println("Attempting TCP connection from UID " + myUid + " to " + friend.ipAddress + ":" + friend.tcpPort + " (for peer " + friend.uid + ")");
                socket.connect(new InetSocketAddress(friend.ipAddress, friend.tcpPort), 5000);
                System.out.println("TCP Connection established from UID " + myUid + " to " + friend.nickname);

                try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                    writer.println("INITMSG:" + myUid);
                    writer.println("MESSAGE:" + message);
                    System.out.println("Message sent from UID " + myUid + " to " + friend.uid + " ("+friend.nickname+"): " + message);
                }

            } catch (ConnectException e) {
                System.err.println("TCP Connection refused or timed out for " + friend.nickname + " at " + friend.ipAddress + ":" + friend.tcpPort + " when sending from " + myUid + ". Marking as offline.");
                app.updateFriendStatus(friend.uid, friend.ipAddress, friend.tcpPort, false);
                Platform.runLater(() -> app.showAlert("Connection Error", "Could not connect to " + friend.nickname + ". They may have gone offline or a firewall is blocking.", Alert.AlertType.ERROR));
            }
            catch (UnknownHostException e) {
                System.err.println("TCP Error: Unknown host " + friend.ipAddress + " for peer " + friend.nickname + " (sending from " + myUid + ").");
                Platform.runLater(() ->app.showAlert("Network Error", "Cannot resolve hostname for " + friend.nickname + ". Check network configuration.", Alert.AlertType.ERROR));
            }
            catch (IOException e) {
                String errorContext = "IOException sending TCP message from " + myUid + " to " + friend.uid + " ("+friend.nickname+")";
                System.err.println(errorContext + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                Platform.runLater(() -> app.showAlert("Send Error", "Failed to send message to " + friend.nickname + ". Error: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    public void shutdown() {
        System.out.println("NetworkManager (UID: " + myUid + ", Port: " + myTcpPort + "): Initiating shutdown...");
        running = false;

        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        if (tcpServerSocket != null && !tcpServerSocket.isClosed()) {
            try {
                tcpServerSocket.close();
            } catch (IOException e) { System.err.println("Error closing TCP server socket during shutdown for UID " + myUid + ": " + e.getMessage()); }
        }

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS))
                        System.err.println("Network scheduler (UID: " + myUid + ") did not terminate cleanly.");
                }
            } catch (InterruptedException ie) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("NetworkManager (UID: " + myUid + "): Services shut down complete.");
    }
}