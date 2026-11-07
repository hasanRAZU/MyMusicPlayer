package com.example.mymusicplayer;

// MainActivity controls the shared player in MusicPlaybackService through MediaController.

import android.Manifest;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int AUDIO_PERMISSION_CODE = 100;

    private static final String PREFS_NAME =
            "music_player_settings";

    private static final String HIDDEN_FOLDERS_KEY =
            "hidden_folders";

    private LinearLayout folderContainer;

    private TextView tvStatus;
    private TextView tvCurrentSong;
    private TextView tvPlayerStatus;

    private TextView btnPlayPause;
    private TextView btnPrevious;
    private TextView btnNext;
    private TextView btnRepeat;
    private TextView btnShuffle;
    private TextView btnSettings;

    private View miniPlayer;

    // This controller talks to the ExoPlayer owned by MusicPlaybackService.
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;

    private final List<MusicSong> currentPlaylist =
            new ArrayList<>();

    private final Map<String, MusicFolder> musicFolders =
            new LinkedHashMap<>();

    private final Set<String> hiddenFolders =
            new LinkedHashSet<>();

    private int repeatMode = Player.REPEAT_MODE_OFF;

    private boolean shuffleEnabled = false;

    private String currentFolderPath = "";
    private String currentFolderName = "";

    // Expanded player
    private AlertDialog expandedPlayerDialog;

    private LinearLayout expandedSongContainer;

    private TextView expandedSongTitle;
    private TextView expandedPlayerStatus;
    private TextView expandedPlayPauseButton;
    private TextView expandedRepeatButton;
    private TextView expandedShuffleButton;

    private TextView currentTimeText;
    private TextView totalTimeText;

    private SeekBar progressSeekBar;

    private boolean isUserSeeking = false;

    // Progress updater
    private final Handler progressHandler =
            new Handler(Looper.getMainLooper());

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {

            updateProgress();

            progressHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initializeViews();

        loadFolderSettings();

        initializePlayer();

        setupButtonListeners();

        progressHandler.post(progressRunnable);

        checkAudioPermission();
    }

    // =========================================================
    // INITIALIZATION
    // =========================================================

    private void initializeViews() {

        folderContainer = findViewById(R.id.folderContainer);

        tvStatus = findViewById(R.id.tvStatus);
        tvCurrentSong = findViewById(R.id.tvCurrentSong);
        tvPlayerStatus = findViewById(R.id.tvPlayerStatus);

        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPrevious = findViewById(R.id.btnPrevious);
        btnNext = findViewById(R.id.btnNext);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnSettings = findViewById(R.id.btnSettings);

        miniPlayer = findViewById(R.id.miniPlayer);
    }

    private void initializePlayer() {

        SessionToken sessionToken = new SessionToken(
                this,
                new ComponentName(this, MusicPlaybackService.class)
        );

        controllerFuture = new MediaController.Builder(this, sessionToken)
                .buildAsync();

        controllerFuture.addListener(() -> {
            try {
                player = controllerFuture.get();

                player.addListener(new Player.Listener() {
                    @Override
                    public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                        updateCurrentSong();
                        updateExpandedPlayer();
                    }

                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        updatePlayPauseButton();
                        updatePlayerStatus();
                        updateExpandedPlayer();
                    }

                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        updatePlayerStatus();
                        updateProgress();
                    }

                    @Override
                    public void onRepeatModeChanged(int newRepeatMode) {
                        repeatMode = newRepeatMode;
                        updateRepeatButton();
                    }

                    @Override
                    public void onShuffleModeEnabledChanged(boolean enabled) {
                        shuffleEnabled = enabled;
                        updateShuffleButton();
                    }
                });

                // Synchronize the UI with any music already playing in the service.
                repeatMode = player.getRepeatMode();
                shuffleEnabled = player.getShuffleModeEnabled();
                updateCurrentSong();
                updatePlayPauseButton();
                updatePlayerStatus();
                updateRepeatButton();
                updateShuffleButton();
                updateProgress();

            } catch (ExecutionException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                Toast.makeText(
                        this,
                        "Could not connect to the playback service: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void setupButtonListeners() {

        btnPlayPause.setOnClickListener(v -> togglePlayPause());

        btnPrevious.setOnClickListener(v -> playPrevious());

        btnNext.setOnClickListener(v -> playNext());

        btnRepeat.setOnClickListener(v -> toggleRepeat());

        btnShuffle.setOnClickListener(v -> toggleShuffle());

        btnSettings.setOnClickListener(v -> showFolderSettings());

        miniPlayer.setOnClickListener(v -> showExpandedPlayer());

        updatePlayPauseButton();
        updateRepeatButton();
        updateShuffleButton();
    }

    // =========================================================
    // AUDIO PERMISSION
    // =========================================================

    private void checkAudioPermission() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_AUDIO
        ) == PackageManager.PERMISSION_GRANTED) {

            loadMusicFiles();

        } else {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.READ_MEDIA_AUDIO
                    },
                    AUDIO_PERMISSION_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == AUDIO_PERMISSION_CODE) {

            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                loadMusicFiles();

            } else {

                tvStatus.setText(
                        "Audio permission is required to show music."
                );

                Toast.makeText(
                        this,
                        "Permission denied",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    // =========================================================
    // LOAD MUSIC FILES
    // =========================================================

    private void loadMusicFiles() {

        musicFolders.clear();

        Uri musicUri =
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DURATION
        };

        String selection =
                MediaStore.Audio.Media.IS_MUSIC + " = 1";

        String sortOrder =
                MediaStore.Audio.Media.DISPLAY_NAME + " ASC";

        try (Cursor cursor = getContentResolver().query(
                musicUri,
                projection,
                selection,
                null,
                sortOrder
        )) {

            if (cursor != null) {

                int idColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media._ID
                );

                int nameColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.DISPLAY_NAME
                );

                int pathColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.RELATIVE_PATH
                );

                int durationColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.DURATION
                );

                while (cursor.moveToNext()) {

                    long id = cursor.getLong(idColumn);

                    String name = cursor.getString(nameColumn);

                    String relativePath =
                            cursor.getString(pathColumn);

                    long duration =
                            cursor.getLong(durationColumn);

                    if (relativePath == null) {
                        relativePath = "";
                    }

                    if (isExcludedFolder(relativePath)) {
                        continue;
                    }

                    Uri contentUri = Uri.withAppendedPath(
                            musicUri,
                            String.valueOf(id)
                    );

                    MusicSong song = new MusicSong(
                            name,
                            contentUri,
                            duration,
                            relativePath
                    );

                    MusicFolder folder =
                            musicFolders.get(relativePath);

                    if (folder == null) {

                        folder = new MusicFolder(
                                getFolderName(relativePath),
                                relativePath
                        );

                        musicFolders.put(relativePath, folder);
                    }

                    folder.songs.add(song);
                }
            }

            displayFolders();

        } catch (Exception e) {

            tvStatus.setText("Unable to load music files.");

            Toast.makeText(
                    this,
                    "Error loading music: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private boolean isExcludedFolder(String relativePath) {

        String path = relativePath.toLowerCase(Locale.ROOT);

        String[] excludedNames = {
                "recordings",
                "call recordings",
                "callrecordings",
                "voice recorder",
                "voice recordings",
                "voicerecorder",
                "audiorecorder",
                "audio recorder",
                "sound recorder",
                "soundrecorder"
        };

        for (String folderName : excludedNames) {

            if (path.contains(folderName)) {
                return true;
            }
        }

        return false;
    }

    private String getFolderName(String relativePath) {

        String path = relativePath;

        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        int lastSlash = path.lastIndexOf("/");

        if (lastSlash >= 0) {
            return path.substring(lastSlash + 1);
        }

        return path.isEmpty() ? "Unknown Folder" : path;
    }

    // =========================================================
    // FOLDER DISPLAY
    // =========================================================

    private void displayFolders() {

        folderContainer.removeAllViews();

        int visibleFolderCount = 0;
        int visibleSongCount = 0;

        for (MusicFolder folder : musicFolders.values()) {

            if (hiddenFolders.contains(folder.path)) {
                continue;
            }

            visibleFolderCount++;
            visibleSongCount += folder.songs.size();

            TextView folderView = new TextView(this);

            folderView.setText(
                    "📁  " + folder.name
                            + "\n      " + folder.songs.size() + " songs"
            );

            folderView.setTextColor(0xFFFFFFFF);
            folderView.setTextSize(16);
            folderView.setPadding(
                    dpToPx(18),
                    dpToPx(20),
                    dpToPx(18),
                    dpToPx(20)
            );

            folderView.setBackgroundColor(0xFF202635);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            params.bottomMargin = dpToPx(10);

            folderContainer.addView(folderView, params);

            folderView.setOnClickListener(
                    v -> showFolderSongs(folder)
            );
        }

        if (visibleFolderCount == 0) {

            TextView emptyView = new TextView(this);

            emptyView.setText(
                    musicFolders.isEmpty()
                            ? "No music folders found."
                            : "No folders visible. Open Settings to show folders."
            );

            emptyView.setTextColor(0xFFAAB4C8);
            emptyView.setTextSize(14);
            emptyView.setPadding(
                    dpToPx(12),
                    dpToPx(20),
                    dpToPx(12),
                    dpToPx(20)
            );

            folderContainer.addView(emptyView);
        }

        tvStatus.setText(
                visibleFolderCount + " folders • "
                        + visibleSongCount + " songs"
        );
    }

    private void showFolderSongs(MusicFolder folder) {

        LinearLayout songList = new LinearLayout(this);

        songList.setOrientation(LinearLayout.VERTICAL);

        ScrollView scrollView = new ScrollView(this);

        scrollView.addView(songList);

        for (int i = 0; i < folder.songs.size(); i++) {

            MusicSong song = folder.songs.get(i);

            LinearLayout songRow = new LinearLayout(this);

            songRow.setOrientation(LinearLayout.HORIZONTAL);
            songRow.setGravity(Gravity.CENTER_VERTICAL);

            songRow.setPadding(
                    dpToPx(16),
                    dpToPx(18),
                    dpToPx(16),
                    dpToPx(18)
            );

            TextView songName = new TextView(this);

            songName.setText(song.name);
            songName.setTextColor(0xFFFFFFFF);
            songName.setTextSize(14);
            songName.setMaxLines(2);

            LinearLayout.LayoutParams nameParams =
                    new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1
                    );

            songRow.addView(songName, nameParams);

            TextView duration = new TextView(this);

            duration.setText(formatDuration(song.duration));
            duration.setTextColor(0xFFAAB4C8);
            duration.setTextSize(12);

            songRow.addView(duration);

            songList.addView(songRow);

            View divider = new View(this);

            divider.setBackgroundColor(0xFF394256);

            songList.addView(
                    divider,
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dpToPx(1)
                    )
            );

            final int position = i;

            songRow.setOnClickListener(v -> {

                playFolderSong(folder, position);

                Toast.makeText(
                        this,
                        "Playing: " + song.name,
                        Toast.LENGTH_SHORT
                ).show();
            });
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        folder.name + " (" + folder.songs.size() + " songs)"
                )
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .show();
    }

    // =========================================================
    // FOLDER SETTINGS
    // =========================================================

    private void loadFolderSettings() {

        SharedPreferences preferences =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        Set<String> savedFolders = preferences.getStringSet(
                HIDDEN_FOLDERS_KEY,
                new LinkedHashSet<>()
        );

        hiddenFolders.clear();

        if (savedFolders != null) {
            hiddenFolders.addAll(savedFolders);
        }
    }

    private void saveFolderSettings() {

        SharedPreferences preferences =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        preferences.edit()
                .putStringSet(
                        HIDDEN_FOLDERS_KEY,
                        new LinkedHashSet<>(hiddenFolders)
                )
                .apply();
    }

    private void showFolderSettings() {

        if (musicFolders.isEmpty()) {

            Toast.makeText(
                    this,
                    "No music folders found.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        List<MusicFolder> folders =
                new ArrayList<>(musicFolders.values());

        String[] folderNames = new String[folders.size()];

        boolean[] checkedItems = new boolean[folders.size()];

        for (int i = 0; i < folders.size(); i++) {

            MusicFolder folder = folders.get(i);

            folderNames[i] =
                    folder.name + " (" + folder.songs.size() + " songs)";

            checkedItems[i] =
                    !hiddenFolders.contains(folder.path);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Manage Music Folders")
                .setMultiChoiceItems(
                        folderNames,
                        checkedItems,
                        (dialogInterface, which, isChecked) -> {

                            MusicFolder folder = folders.get(which);

                            if (isChecked) {
                                hiddenFolders.remove(folder.path);
                            } else {
                                hiddenFolders.add(folder.path);
                            }
                        }
                )
                .setPositiveButton("Save", (dialogInterface, which) -> {

                    saveFolderSettings();

                    displayFolders();

                    Toast.makeText(
                            this,
                            "Folder settings saved",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .setNegativeButton("Cancel", (dialogInterface, which) -> {
                    loadFolderSettings();
                })
                .setNeutralButton("Show All", (dialogInterface, which) -> {

                    hiddenFolders.clear();

                    saveFolderSettings();

                    displayFolders();
                })
                .create();

        dialog.setOnCancelListener(d -> loadFolderSettings());

        dialog.show();
    }

    // =========================================================
    // PLAYBACK
    // =========================================================

    private void playFolderSong(
            MusicFolder folder,
            int selectedPosition
    ) {

        if (folder.songs.isEmpty()) {
            return;
        }

        currentFolderPath = folder.path;
        currentFolderName = folder.name;

        currentPlaylist.clear();
        currentPlaylist.addAll(folder.songs);

        if (player == null) {
            Toast.makeText(this, "Playback service is still connecting. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<MediaItem> mediaItems = new ArrayList<>();

        for (MusicSong song : currentPlaylist) {
            MediaMetadata metadata = new MediaMetadata.Builder()
                    .setTitle(song.name)
                    .setDisplayTitle(song.name)
                    .build();

            mediaItems.add(
                    new MediaItem.Builder()
                            .setUri(song.uri)
                            .setMediaMetadata(metadata)
                            .build()
            );
        }

        player.setMediaItems(
                mediaItems,
                selectedPosition,
                0
        );

        player.prepare();
        player.play();

        updateCurrentSong();
        updatePlayPauseButton();
        updatePlayerStatus();
        updateProgress();
    }

    private void togglePlayPause() {

        if (player == null || player.getMediaItemCount() == 0) {

            Toast.makeText(
                    this,
                    "Select a song first",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }

        updatePlayPauseButton();
    }

    private void playNext() {

        if (player == null || player.getMediaItemCount() == 0) {
            return;
        }

        if (player.hasNextMediaItem()) {

            player.seekToNext();

        } else if (repeatMode == Player.REPEAT_MODE_OFF) {

            Toast.makeText(
                    this,
                    "This is the last song",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void playPrevious() {

        if (player == null || player.getMediaItemCount() == 0) {
            return;
        }

        if (player.getCurrentPosition() > 3000) {

            player.seekTo(0);

        } else if (player.hasPreviousMediaItem()) {

            player.seekToPrevious();

        } else {

            player.seekTo(0);
        }
    }

    // =========================================================
    // SEEK FORWARD / BACKWARD
    // =========================================================

    private void seekBackward(long seconds) {

        if (player == null || player.getMediaItemCount() == 0) {
            return;
        }

        long currentPosition = player.getCurrentPosition();

        long newPosition =
                Math.max(0, currentPosition - seconds * 1000);

        player.seekTo(newPosition);

        updateProgress();
    }

    private void seekForward(long seconds) {

        if (player == null || player.getMediaItemCount() == 0) {
            return;
        }

        long currentPosition = player.getCurrentPosition();

        long duration = player.getDuration();

        long newPosition = currentPosition + seconds * 1000;

        if (duration != androidx.media3.common.C.TIME_UNSET && duration > 0) {
            newPosition = Math.min(newPosition, duration);
        }

        player.seekTo(newPosition);

        updateProgress();
    }

    // =========================================================
    // REPEAT / SHUFFLE
    // =========================================================

    private void toggleRepeat() {

        if (player == null) return;

        if (repeatMode == Player.REPEAT_MODE_OFF) {

            repeatMode = Player.REPEAT_MODE_ALL;

        } else if (repeatMode == Player.REPEAT_MODE_ALL) {

            repeatMode = Player.REPEAT_MODE_ONE;

        } else {

            repeatMode = Player.REPEAT_MODE_OFF;
        }

        player.setRepeatMode(repeatMode);

        updateRepeatButton();
    }

    private void toggleShuffle() {

        if (player == null) return;

        shuffleEnabled = !shuffleEnabled;

        player.setShuffleModeEnabled(shuffleEnabled);

        updateShuffleButton();
    }

    private void updateRepeatButton() {

        String text;

        if (repeatMode == Player.REPEAT_MODE_ALL) {
            text = "Repeat: All";
        } else if (repeatMode == Player.REPEAT_MODE_ONE) {
            text = "Repeat: One";
        } else {
            text = "Repeat: Off";
        }

        btnRepeat.setText(text);

        if (expandedRepeatButton != null) {
            expandedRepeatButton.setText(text);
        }
    }

    private void updateShuffleButton() {

        String text = shuffleEnabled
                ? "Shuffle: On"
                : "Shuffle: Off";

        btnShuffle.setText(text);

        if (expandedShuffleButton != null) {
            expandedShuffleButton.setText(text);
        }
    }

    // =========================================================
    // MINI PLAYER UPDATES
    // =========================================================

    private void updateCurrentSong() {

        if (player == null
                || player.getCurrentMediaItem() == null) {
            return;
        }

        int index = player.getCurrentMediaItemIndex();

        if (index >= 0 && index < currentPlaylist.size()) {

            MusicSong song = currentPlaylist.get(index);

            tvCurrentSong.setText(song.name);

            updateExpandedPlayer();
        }
    }

    private void updatePlayPauseButton() {

        boolean playing =
                player != null && player.isPlaying();

        btnPlayPause.setText(playing ? "⏸" : "▶");

        if (expandedPlayPauseButton != null) {
            expandedPlayPauseButton.setText(
                    playing ? "⏸" : "▶"
            );
        }
    }

    private void updatePlayerStatus() {

        if (player == null || player.getMediaItemCount() == 0) {

            tvPlayerStatus.setText("Tap here to open player");

            if (expandedPlayerStatus != null) {
                expandedPlayerStatus.setText("No song selected");
            }

            return;
        }

        String status = player.isPlaying()
                ? "Playing"
                : "Paused";

        tvPlayerStatus.setText(
                status + " • " + currentFolderName
        );

        if (expandedPlayerStatus != null) {
            expandedPlayerStatus.setText(status);
        }
    }

    // =========================================================
    // EXPANDED PLAYER
    // =========================================================

    private void showExpandedPlayer() {

        if (player == null) {
            Toast.makeText(this, "Playback service is still connecting.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentPlaylist.isEmpty()) {

            Toast.makeText(
                    this,
                    "Select a song first",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (expandedPlayerDialog != null
                && expandedPlayerDialog.isShowing()) {
            return;
        }

        LinearLayout root = new LinearLayout(this);

        root.setOrientation(LinearLayout.VERTICAL);

        root.setPadding(
                dpToPx(18),
                dpToPx(16),
                dpToPx(18),
                dpToPx(12)
        );

        root.setBackgroundColor(0xFF151B29);

        // Heading
        TextView heading = new TextView(this);

        heading.setText("Now Playing");
        heading.setTextColor(0xFFFFFFFF);
        heading.setTextSize(22);
        heading.setGravity(Gravity.CENTER);
        heading.setTypeface(null, Typeface.BOLD);

        heading.setPadding(0, 0, 0, dpToPx(12));

        root.addView(heading);

        // Current song
        expandedSongTitle = new TextView(this);

        expandedSongTitle.setTextColor(0xFFFFFFFF);
        expandedSongTitle.setTextSize(17);
        expandedSongTitle.setGravity(Gravity.CENTER);
        expandedSongTitle.setMaxLines(2);

        expandedSongTitle.setPadding(
                dpToPx(8),
                dpToPx(10),
                dpToPx(8),
                dpToPx(6)
        );

        root.addView(expandedSongTitle);

        // Playback status
        expandedPlayerStatus = new TextView(this);

        expandedPlayerStatus.setTextColor(0xFFAAB4C8);
        expandedPlayerStatus.setTextSize(13);
        expandedPlayerStatus.setGravity(Gravity.CENTER);

        root.addView(expandedPlayerStatus);

        // Seek bar time labels
        LinearLayout timeRow = new LinearLayout(this);

        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);

        currentTimeText = new TextView(this);

        currentTimeText.setText("0:00");
        currentTimeText.setTextColor(0xFFAAB4C8);
        currentTimeText.setTextSize(12);

        totalTimeText = new TextView(this);

        totalTimeText.setText("0:00");
        totalTimeText.setTextColor(0xFFAAB4C8);
        totalTimeText.setTextSize(12);
        totalTimeText.setGravity(Gravity.END);

        LinearLayout.LayoutParams timeParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );

        timeRow.addView(currentTimeText, timeParams);

        timeRow.addView(totalTimeText, timeParams);

        root.addView(timeRow);

        // Seek bar
        progressSeekBar = new SeekBar(this);

        progressSeekBar.setMax(1000);

        root.addView(
                progressSeekBar,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(38)
                )
        );

        progressSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {

                        if (fromUser && player != null) {

                            long duration = player.getDuration();

                            if (duration > 0
                                    && duration != androidx.media3.common.C.TIME_UNSET) {

                                long position =
                                        duration * progress / 1000;

                                currentTimeText.setText(
                                        formatDuration(position)
                                );
                            }
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        isUserSeeking = true;
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {

                        if (player != null) {

                            long duration = player.getDuration();

                            if (duration > 0
                                    && duration != androidx.media3.common.C.TIME_UNSET) {

                                long position =
                                        duration * seekBar.getProgress() / 1000;

                                player.seekTo(position);
                            }
                        }

                        isUserSeeking = false;

                        updateProgress();
                    }
                }
        );

        // 15 / 30 second controls
        LinearLayout seekControls = new LinearLayout(this);

        seekControls.setOrientation(LinearLayout.HORIZONTAL);
        seekControls.setGravity(Gravity.CENTER);
        seekControls.setPadding(
                0,
                dpToPx(8),
                0,
                dpToPx(8)
        );

        TextView back30 = createControlButton("−30s", 0, 42);
        TextView back15 = createControlButton("−15s", 0, 42);
        TextView forward15 = createControlButton("+15s", 0, 42);
        TextView forward30 = createControlButton("+30s", 0, 42);

        back30.setTextSize(14);
        back15.setTextSize(14);
        forward15.setTextSize(14);
        forward30.setTextSize(14);

        back30.setOnClickListener(v -> seekBackward(30));
        back15.setOnClickListener(v -> seekBackward(15));
        forward15.setOnClickListener(v -> seekForward(15));
        forward30.setOnClickListener(v -> seekForward(30));

        LinearLayout.LayoutParams seekButtonParams =
                new LinearLayout.LayoutParams(
                        0,
                        dpToPx(42),
                        1
                );

        seekButtonParams.setMargins(
                dpToPx(3),
                0,
                dpToPx(3),
                0
        );

        seekControls.addView(back30, seekButtonParams);
        seekControls.addView(back15, seekButtonParams);
        seekControls.addView(forward15, seekButtonParams);
        seekControls.addView(forward30, seekButtonParams);

        root.addView(seekControls);

        // Previous / Play / Next
        LinearLayout playbackControls = new LinearLayout(this);

        playbackControls.setGravity(Gravity.CENTER);
        playbackControls.setOrientation(LinearLayout.HORIZONTAL);

        playbackControls.setPadding(
                0,
                dpToPx(6),
                0,
                dpToPx(12)
        );

        TextView previousButton =
                createControlButton("⏮", 58, 48);

        expandedPlayPauseButton =
                createControlButton("▶", 68, 52);

        TextView nextButton =
                createControlButton("⏭", 58, 48);

        previousButton.setOnClickListener(v -> playPrevious());

        expandedPlayPauseButton.setOnClickListener(
                v -> togglePlayPause()
        );

        nextButton.setOnClickListener(v -> playNext());

        LinearLayout.LayoutParams playParams =
                new LinearLayout.LayoutParams(
                        dpToPx(68),
                        dpToPx(52)
                );

        playParams.setMargins(
                dpToPx(18),
                0,
                dpToPx(18),
                0
        );

        playbackControls.addView(previousButton);
        playbackControls.addView(
                expandedPlayPauseButton,
                playParams
        );
        playbackControls.addView(nextButton);

        root.addView(playbackControls);

        // Repeat / Shuffle
        LinearLayout modeControls = new LinearLayout(this);

        modeControls.setOrientation(LinearLayout.HORIZONTAL);
        modeControls.setGravity(Gravity.CENTER);

        modeControls.setPadding(
                0,
                dpToPx(4),
                0,
                dpToPx(14)
        );

        expandedRepeatButton =
                createControlButton("Repeat: Off", 0, 44);

        expandedShuffleButton =
                createControlButton("Shuffle: Off", 0, 44);

        expandedRepeatButton.setTextSize(13);
        expandedShuffleButton.setTextSize(13);

        expandedRepeatButton.setOnClickListener(
                v -> toggleRepeat()
        );

        expandedShuffleButton.setOnClickListener(
                v -> toggleShuffle()
        );

        LinearLayout.LayoutParams modeParams =
                new LinearLayout.LayoutParams(
                        0,
                        dpToPx(44),
                        1
                );

        modeParams.setMargins(
                dpToPx(4),
                0,
                dpToPx(4),
                0
        );

        modeControls.addView(expandedRepeatButton, modeParams);
        modeControls.addView(expandedShuffleButton, modeParams);

        root.addView(modeControls);

        // Current folder heading
        TextView folderHeading = new TextView(this);

        folderHeading.setText(
                "Songs in " + currentFolderName
        );

        folderHeading.setTextColor(0xFFFFFFFF);
        folderHeading.setTextSize(16);
        folderHeading.setTypeface(null, Typeface.BOLD);

        folderHeading.setPadding(
                0,
                dpToPx(8),
                0,
                dpToPx(10)
        );

        root.addView(folderHeading);

        // Song list
        ScrollView songScrollView = new ScrollView(this);

        expandedSongContainer = new LinearLayout(this);

        expandedSongContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        songScrollView.addView(expandedSongContainer);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                );

        root.addView(songScrollView, scrollParams);

        expandedPlayerDialog = new AlertDialog.Builder(this)
                .setView(root)
                .setNegativeButton("Close", null)
                .create();

        expandedPlayerDialog.setOnDismissListener(dialog -> {

            expandedSongContainer = null;

            expandedSongTitle = null;
            expandedPlayerStatus = null;

            expandedPlayPauseButton = null;
            expandedRepeatButton = null;
            expandedShuffleButton = null;

            currentTimeText = null;
            totalTimeText = null;

            progressSeekBar = null;

            isUserSeeking = false;

            expandedPlayerDialog = null;
        });

        expandedPlayerDialog.show();

        if (expandedPlayerDialog.getWindow() != null) {

            expandedPlayerDialog.getWindow().setLayout(
                    (int) (
                            getResources()
                                    .getDisplayMetrics()
                                    .widthPixels * 0.94
                    ),
                    (int) (
                            getResources()
                                    .getDisplayMetrics()
                                    .heightPixels * 0.85
                    )
            );
        }

        updateExpandedPlayer();
        updateProgress();
    }

    // =========================================================
    // EXPANDED PLAYER UPDATES
    // =========================================================

    private void updateExpandedPlayer() {

        if (expandedSongContainer == null
                || expandedSongTitle == null
                || currentPlaylist.isEmpty()
                || player == null) {
            return;
        }

        int currentIndex =
                player.getCurrentMediaItemIndex();

        if (currentIndex >= 0
                && currentIndex < currentPlaylist.size()) {

            expandedSongTitle.setText(
                    currentPlaylist.get(currentIndex).name
            );
        }

        updatePlayPauseButton();
        updatePlayerStatus();
        updateRepeatButton();
        updateShuffleButton();

        expandedSongContainer.removeAllViews();

        for (int i = 0; i < currentPlaylist.size(); i++) {

            final int position = i;

            MusicSong song = currentPlaylist.get(i);

            LinearLayout row = new LinearLayout(this);

            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            row.setPadding(
                    dpToPx(12),
                    dpToPx(13),
                    dpToPx(12),
                    dpToPx(13)
            );

            boolean isCurrentSong = (i == currentIndex);

            row.setBackgroundColor(
                    isCurrentSong
                            ? 0xFF35425C
                            : 0xFF202635
            );

            TextView songName = new TextView(this);

            songName.setText(
                    (isCurrentSong ? "▶  " : "") + song.name
            );

            songName.setTextColor(
                    isCurrentSong
                            ? 0xFF8FC7FF
                            : 0xFFFFFFFF
            );

            songName.setTextSize(14);
            songName.setMaxLines(2);

            LinearLayout.LayoutParams nameParams =
                    new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1
                    );

            row.addView(songName, nameParams);

            TextView duration = new TextView(this);

            duration.setText(formatDuration(song.duration));
            duration.setTextColor(0xFFAAB4C8);
            duration.setTextSize(12);

            row.addView(duration);

            expandedSongContainer.addView(row);

            View divider = new View(this);

            divider.setBackgroundColor(0xFF394256);

            expandedSongContainer.addView(
                    divider,
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dpToPx(1)
                    )
            );

            row.setOnClickListener(v -> {

                if (position >= 0
                        && position < currentPlaylist.size()) {

                    player.seekTo(position, 0);
                    player.play();

                    updateExpandedPlayer();
                }
            });
        }
    }

    // =========================================================
    // PROGRESS BAR
    // =========================================================

    private void updateProgress() {

        if (player == null
                || player.getMediaItemCount() == 0) {
            return;
        }

        long position = player.getCurrentPosition();

        long duration = player.getDuration();

        if (duration == androidx.media3.common.C.TIME_UNSET || duration <= 0) {
            duration = 0;
        }

        if (currentTimeText != null) {
            currentTimeText.setText(
                    formatDuration(position)
            );
        }

        if (totalTimeText != null) {
            totalTimeText.setText(
                    formatDuration(duration)
            );
        }

        if (progressSeekBar != null && !isUserSeeking) {

            int progress = 0;

            if (duration > 0) {

                progress = (int) (
                        position * 1000 / duration
                );
            }

            progressSeekBar.setProgress(progress);
        }
    }

    // =========================================================
    // UI HELPERS
    // =========================================================

    private TextView createControlButton(
            String text,
            int widthDp,
            int heightDp
    ) {

        TextView button = new TextView(this);

        button.setText(text);
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(20);
        button.setGravity(Gravity.CENTER);

        button.setBackgroundColor(0xFF2B3549);

        button.setClickable(true);
        button.setFocusable(true);

        if (widthDp > 0) {

            button.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            dpToPx(widthDp),
                            dpToPx(heightDp)
                    )
            );

        } else {

            button.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dpToPx(heightDp)
                    )
            );
        }

        return button;
    }

    private String formatDuration(long duration) {

        if (duration < 0) {
            duration = 0;
        }

        long totalSeconds = duration / 1000;

        long minutes = totalSeconds / 60;

        long seconds = totalSeconds % 60;

        long hours = minutes / 60;

        minutes = minutes % 60;

        if (hours > 0) {

            return String.format(
                    Locale.getDefault(),
                    "%d:%02d:%02d",
                    hours,
                    minutes,
                    seconds
            );
        }

        return String.format(
                Locale.getDefault(),
                "%d:%02d",
                minutes,
                seconds
        );
    }

    private int dpToPx(int dp) {

        return (int) (
                dp * getResources()
                        .getDisplayMetrics()
                        .density + 0.5f
        );
    }

    // =========================================================
    // DATA CLASSES
    // =========================================================

    private static class MusicFolder {

        String name;

        String path;

        List<MusicSong> songs = new ArrayList<>();

        MusicFolder(String name, String path) {

            this.name = name;

            this.path = path;
        }
    }

    private static class MusicSong {

        String name;

        Uri uri;

        long duration;

        String folderPath;

        MusicSong(
                String name,
                Uri uri,
                long duration,
                String folderPath
        ) {

            this.name = name;

            this.uri = uri;

            this.duration = duration;

            this.folderPath = folderPath;
        }
    }

    // =========================================================
    // ACTIVITY DESTROY
    // =========================================================

    @Override
    protected void onDestroy() {

        progressHandler.removeCallbacks(progressRunnable);

        if (expandedPlayerDialog != null
                && expandedPlayerDialog.isShowing()) {

            expandedPlayerDialog.dismiss();
        }

        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
            player = null;
        }

        super.onDestroy();
    }
}