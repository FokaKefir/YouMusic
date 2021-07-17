package com.fokakefir.musicplayer.gui.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.SparseArray;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.fokakefir.musicplayer.R;
import com.fokakefir.musicplayer.gui.fragment.ChoosePlaylistFragment;
import com.fokakefir.musicplayer.logic.database.MusicPlayerContract.*;
import com.fokakefir.musicplayer.logic.background.RequestDownloadMusicStream;
import com.fokakefir.musicplayer.logic.background.RequestDownloadThumbnailStream;
import com.fokakefir.musicplayer.gui.fragment.MusicsFragment;
import com.fokakefir.musicplayer.gui.fragment.PlaylistsFragment;
import com.fokakefir.musicplayer.gui.fragment.SearchFragment;
import com.fokakefir.musicplayer.logic.background.RequestDownloadMusicStreamResponse;
import com.fokakefir.musicplayer.logic.database.MusicPlayerDBHelper;
import com.fokakefir.musicplayer.model.Music;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import java.io.File;
import java.io.IOException;

import at.huber.youtubeExtractor.YouTubeUriExtractor;
import at.huber.youtubeExtractor.YtFile;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener, SlidingUpPanelLayout.PanelSlideListener, View.OnClickListener, MediaPlayer.OnCompletionListener, RequestDownloadMusicStreamResponse {

    // region 0. Constants

    private static final int READ_STORAGE_PERMISSION_REQUEST_CODE = 1;

    private static final int YOUTUBE_ITAG_VIDEO_480P = 18;
    private static final int YOUTUBE_ITAG_AUDIO_50K = 249;
    private static final int YOUTUBE_ITAG_AUDIO_160K = 251;
    private static final int YOUTUBE_ITAG_AUDIO_128K = 140;

    public static final int DEFAULT_PLAYLIST_ID = 1;

    // endregion

    // region 1. Decl and Init

    private SQLiteDatabase database;

    private SearchFragment searchFragment;
    private PlaylistsFragment playlistsFragment;
    private MusicsFragment musicsFragment;
    private ChoosePlaylistFragment choosePlaylistFragment;

    private BottomNavigationView bottomNav;
    private SlidingUpPanelLayout layout;

    private MediaPlayer mediaPlayer;

    private ImageButton btnPlay;

    // endregion

    // region 2. Lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MusicPlayerDBHelper dbHelper = new MusicPlayerDBHelper(this);
        this.database = dbHelper.getWritableDatabase();

        this.searchFragment = new SearchFragment(this);
        this.playlistsFragment = new PlaylistsFragment(this);
        this.musicsFragment = null;
        this.choosePlaylistFragment = null;

        this.bottomNav = findViewById(R.id.bottom_navigation);
        this.layout = findViewById(R.id.sliding_up_panel);
        this.btnPlay = findViewById(R.id.btn_play_music);

        this.bottomNav.setOnNavigationItemSelectedListener(this);
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, this.searchFragment).commit();
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, this.playlistsFragment).hide(this.playlistsFragment).commit();

        this.btnPlay.setImageResource(R.drawable.ic_baseline_play_music);
        this.btnPlay.setOnClickListener(this);

        this.layout.addPanelSlideListener(this);

        if (!checkPermissionForReadExternalStorage()) {
            try {
                requestPermissionForReadExternalStorage();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public void onStop() {
        super.onStop();
        stopMediaPlayer();
    }

    @Override
    public void onBackPressed() {
        if (this.bottomNav.getSelectedItemId() == R.id.nav_playlists) {
            if (this.choosePlaylistFragment != null) {
                this.musicsFragment = null;
                super.onBackPressed();
            } else if (this.musicsFragment != null) {
                this.musicsFragment = null;
                super.onBackPressed();
            }
        }
    }

    // endregion

    // region 3. Fragments

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (item.isChecked()) {
            return true;
        } else {
            switch (item.getItemId()) {
                case R.id.nav_search:
                    getSupportFragmentManager().beginTransaction().hide(this.playlistsFragment).commit();
                    getSupportFragmentManager().beginTransaction().show(this.searchFragment).commit();
                    if (this.musicsFragment != null) {
                        getSupportFragmentManager().beginTransaction().hide(this.musicsFragment).commit();
                    }
                    if (this.choosePlaylistFragment != null) {
                        closeChoosePlaylistFragment();
                    }
                    return true;
                case R.id.nav_playlists:
                    getSupportFragmentManager().beginTransaction().hide(this.searchFragment).commit();
                    getSupportFragmentManager().beginTransaction().show(this.playlistsFragment).commit();
                    if (this.musicsFragment != null) {
                        getSupportFragmentManager().beginTransaction().show(this.musicsFragment).commit();
                    }
                    return true;
            }
        }
        return false;
    }

    public void addMusicsFragment(int playlistId) {
        this.musicsFragment = new MusicsFragment(this, playlistId);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, this.musicsFragment).addToBackStack(null).commit();
    }

    public void addChoosePlaylistFragment(int musicId) {
        this.choosePlaylistFragment = new ChoosePlaylistFragment(this, musicId);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, this.choosePlaylistFragment).addToBackStack(null).commit();
    }

    public void closeChoosePlaylistFragment() {
        getSupportFragmentManager().popBackStack();
    }

    // endregion

    // region 4. Button listener

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_play_music) {
            if (this.mediaPlayer != null) {
                if (this.mediaPlayer.isPlaying()) {
                    pauseMusic();
                } else {
                    playMusic();
                }
            }
        }
    }

    // endregion

    // region 5. MediaPlayer

    public void playMusicUri(Uri uri) {
        //Uri uri = Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)+"/YoutubeMusics/" + name);
        if (this.mediaPlayer == null) {
            this.mediaPlayer = new MediaPlayer();
            this.mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
            );
            this.mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    stopMediaPlayer();
                }
            });
        }

        try {
            this.mediaPlayer.setDataSource(this, uri);
            this.mediaPlayer.prepare();
            this.mediaPlayer.start();

            this.btnPlay.setImageResource(R.drawable.ic_baseline_pause_music);
        } catch (IOException e) {
            e.printStackTrace();
            this.mediaPlayer = null;
        }
    }

    public void playMusic() {
        if (this.mediaPlayer != null) {
            this.mediaPlayer.start();
            this.btnPlay.setImageResource(R.drawable.ic_baseline_pause_music);
        }
    }

    public void pauseMusic() {
        if (this.mediaPlayer != null) {
            this.mediaPlayer.pause();
            this.btnPlay.setImageResource(R.drawable.ic_baseline_play_music);
        }
    }

    public void stopMediaPlayer() {
        if (this.mediaPlayer != null) {
            this.mediaPlayer.release();;
            this.mediaPlayer = null;

            this.btnPlay.setImageResource(R.drawable.ic_baseline_play_music);
        }
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        stopMediaPlayer();
    }

    // endregion

    // region 6. SlidingUpPanel

    @Override
    public void onPanelSlide(View panel, float slideOffset) {
        findViewById(R.id.layout_down).setAlpha(1 - slideOffset);
        findViewById(R.id.layout_up).setAlpha(slideOffset);
    }

    @Override
    public void onPanelStateChanged(View panel, SlidingUpPanelLayout.PanelState previousState, SlidingUpPanelLayout.PanelState newState) {

    }

    // endregion

    // region 7. Download and delete music

    public void downloadMusic(String url, String videoArtist) {
        YouTubeUriExtractor youTubeUriExtractor = new YouTubeUriExtractor(this) {
            @Override
            public void onUrisAvailable(String videoId, String videoTitle, SparseArray<YtFile> ytFiles) {
                if (ytFiles != null) {
                    try {
                        String downloadUrl = ytFiles.get(YOUTUBE_ITAG_AUDIO_128K).getUrl();
                        RequestDownloadMusicStream requestDownloadMusicStream = new RequestDownloadMusicStream(MainActivity.this, MainActivity.this);
                        requestDownloadMusicStream.execute(downloadUrl, videoId, videoTitle, videoArtist);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, String.valueOf(e), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };

        youTubeUriExtractor.execute(url);
    }

    public void downloadThumbnail(String url, String name) {
        new RequestDownloadThumbnailStream(this).execute(url, name);
    }

    public void deleteMusicFromStorage(Music music) {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)+"/YoutubeMusics");
        File file = new File(dir, music.getVideoId() + ".m4a");
        boolean deleted = file.delete();

        if (deleted)
            deleteMusicFromDatabase(music);
    }

    public void requestPermissionForReadExternalStorage() throws Exception {
        try {
            ActivityCompat.requestPermissions((Activity) this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    READ_STORAGE_PERMISSION_REQUEST_CODE);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public boolean checkPermissionForReadExternalStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int result = this.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
            int result2 = this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);

            return (result == PackageManager.PERMISSION_GRANTED && result2 == PackageManager.PERMISSION_GRANTED);
        }
        return false;
    }

    // endregion

    // region 8. Database

    @Override
    public void onMusicDownloaded(String videoId, String title, String artist, int length) {
        ContentValues cvMusic = new ContentValues();
        cvMusic.put(MusicEntry.COLUMN_VIDEO_ID, videoId);
        cvMusic.put(MusicEntry.COLUMN_TITLE, title);
        cvMusic.put(MusicEntry.COLUMN_ARTIST, artist);
        cvMusic.put(MusicEntry.COLUMN_LENGTH, length);

        long id = this.database.insert(MusicEntry.TABLE_NAME, null, cvMusic);

        ContentValues cvConnect = new ContentValues();
        cvConnect.put(ConnectEntry.COLUMN_PLAYLIST_ID, DEFAULT_PLAYLIST_ID);
        cvConnect.put(ConnectEntry.COLUMN_MUSIC_ID, id);

        this.database.insert(ConnectEntry.TABLE_NAME, null, cvConnect);

        this.playlistsFragment.swapCursor(getAllPlaylists());
        if (this.musicsFragment != null)
            this.musicsFragment.swapCursor(getAllMusic(this.musicsFragment.getPlaylistId()));

        Toast.makeText(this, "Downloaded", Toast.LENGTH_SHORT).show();
    }

    public void insertPlaylist(String name, String color) {
        ContentValues cv = new ContentValues();
        cv.put(PlaylistEntry.COLUMN_NAME, name);
        cv.put(PlaylistEntry.COLUMN_COLOR, color);

        this.database.insert(PlaylistEntry.TABLE_NAME, null, cv);

        this.playlistsFragment.swapCursor(getAllPlaylists());
    }

    public void insertConnection(int playlistId, int musicId) {
        ContentValues cv = new ContentValues();
        cv.put(ConnectEntry.COLUMN_PLAYLIST_ID, playlistId);
        cv.put(ConnectEntry.COLUMN_MUSIC_ID, musicId);

        this.database.insert(ConnectEntry.TABLE_NAME, null, cv);

        this.playlistsFragment.swapCursor(getAllPlaylists());
        if (this.musicsFragment != null)
            this.musicsFragment.swapCursor(getAllMusic(this.musicsFragment.getPlaylistId()));

        closeChoosePlaylistFragment();

        Toast.makeText(this, "Music added to playlist", Toast.LENGTH_SHORT).show();
    }

    public void deleteMusicFromDatabase(Music music) {
        this.database.delete(MusicEntry.TABLE_NAME, MusicEntry._ID + "=?", new String[]{String.valueOf(music.getId())});
        this.database.delete(ConnectEntry.TABLE_NAME, ConnectEntry.COLUMN_MUSIC_ID + "=?", new String[]{String.valueOf(music.getId())});

        this.playlistsFragment.swapCursor(getAllPlaylists());

        if (this.musicsFragment != null)
            this.musicsFragment.swapCursor(getAllMusic(this.musicsFragment.getPlaylistId()));

        Toast.makeText(this, "Music deleted", Toast.LENGTH_SHORT).show();
    }

    // endregion

    // region 9. Getters and Setters

    public Cursor getAllPlaylists() {
        return this.database.rawQuery(
                "SELECT " + PlaylistEntry._ID + ", " + PlaylistEntry.COLUMN_NAME + ", " + PlaylistEntry.COLUMN_COLOR + ", " +
                        "(SELECT COUNT(" + ConnectEntry.COLUMN_MUSIC_ID + ") FROM " + ConnectEntry.TABLE_NAME + ", " + MusicEntry.TABLE_NAME +
                        " WHERE " + PlaylistEntry.TABLE_NAME + "." + PlaylistEntry._ID + "=" + ConnectEntry.COLUMN_PLAYLIST_ID +
                        " AND " + ConnectEntry.COLUMN_MUSIC_ID + "=" + MusicEntry.TABLE_NAME + "." + MusicEntry._ID +
                        ") AS " + PlaylistEntry.COLUMN_MUSICS +
                        " FROM " + PlaylistEntry.TABLE_NAME +
                        " ORDER BY " + PlaylistEntry.COLUMN_TIMESTAMP + " ASC;",
                null
        );
    }

    public Cursor getAllChoosePlaylists(int musicId) {
        final String SQL_SELECT_ALL_CHOOSE_PLAYLISTS = "SELECT DISTINCT " + PlaylistEntry._ID + ", " + PlaylistEntry.COLUMN_NAME + ", " + PlaylistEntry.COLUMN_COLOR + ", " +
                "(SELECT COUNT(" + ConnectEntry.COLUMN_MUSIC_ID + ") FROM " + ConnectEntry.TABLE_NAME + ", " + MusicEntry.TABLE_NAME +
                    " WHERE " + PlaylistEntry.TABLE_NAME + "." + PlaylistEntry._ID + "=" + ConnectEntry.COLUMN_PLAYLIST_ID +
                    " AND " + ConnectEntry.COLUMN_MUSIC_ID + "=" + MusicEntry.TABLE_NAME + "." + MusicEntry._ID +
                ") AS " + PlaylistEntry.COLUMN_MUSICS +
                " FROM " + PlaylistEntry.TABLE_NAME +
                " WHERE " + PlaylistEntry._ID + " NOT IN " +
                "(SELECT " + ConnectEntry.COLUMN_PLAYLIST_ID +
                    " FROM " + ConnectEntry.TABLE_NAME +
                    " WHERE " + ConnectEntry.COLUMN_MUSIC_ID + "=?)" +
                " ORDER BY " + PlaylistEntry.TABLE_NAME + "." + PlaylistEntry.COLUMN_TIMESTAMP + " ASC;";

        return this.database.rawQuery(
                SQL_SELECT_ALL_CHOOSE_PLAYLISTS,
                new String[]{String.valueOf(musicId)}
        );
    }

    public Cursor getAllMusic() {
        return this.database.query(
                MusicEntry.TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                MusicEntry.COLUMN_TIMESTAMP + " ASC"
        );
    }

    public Cursor getAllMusic(int playlistId) {
        if (playlistId == DEFAULT_PLAYLIST_ID)
            return getAllMusic();

        final String SQL_SELECT_ALL_MUSIC = "SELECT " + MusicEntry.TABLE_NAME + ".*" + " FROM " +
                MusicEntry.TABLE_NAME + ", " + PlaylistEntry.TABLE_NAME + ", " + ConnectEntry.TABLE_NAME +
                " WHERE " + MusicEntry.TABLE_NAME + "." + MusicEntry._ID + "=" + ConnectEntry.COLUMN_MUSIC_ID +
                " AND " + PlaylistEntry.TABLE_NAME + "." + PlaylistEntry._ID + "=" + ConnectEntry.COLUMN_PLAYLIST_ID +
                " AND " + PlaylistEntry.TABLE_NAME + "." + PlaylistEntry._ID + "=?" +
                " ORDER BY " + ConnectEntry.COLUMN_TIMESTAMP + " ASC;";

        return this.database.rawQuery(
                SQL_SELECT_ALL_MUSIC,
                new String[]{String.valueOf(playlistId)}
        );
    }

    // endregion

}