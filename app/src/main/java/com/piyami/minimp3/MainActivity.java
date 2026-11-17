package com.piyami.minimp3;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends Activity {
    private static final int AUDIO_PERMISSION = 1;
    private static final int PICK_AUDIO = 2;
    private static final String EMPTY = "Telefonda mp3 bulunamadı";
    private final ArrayList<Song> songs = new ArrayList<>();
    private final ArrayList<Song> picked = new ArrayList<>();
    private final ExecutorService scanner = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler();
    private MediaPlayer player;
    private int current = -1;
    private int generation;
    private int scanGeneration;
    private int attemptsLeft;
    private boolean preparing;
    private boolean playWhenReady;
    private boolean scanning;
    private boolean destroyed;
    private boolean lastPermissionGranted;
    private Future<?> scanTask;
    private TextView currentTitle;
    private TextView status;
    private TextView permissionStatus;
    private Button playButton;
    private Button settingsButton;
    private ArrayAdapter<Song> adapter;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (8 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        currentTitle = new TextView(this);
        currentTitle.setText("Bir parça seçin");
        currentTitle.setTextSize(18);
        layout.addView(currentTitle);
        status = new TextView(this);
        layout.addView(status);
        permissionStatus = new TextView(this);
        layout.addView(permissionStatus);
        LinearLayout permissionActions = new LinearLayout(this);
        Button requestPermission = button("İzin iste");
        requestPermission.setOnClickListener(view -> requestAudioPermission());
        settingsButton = button("Uygulama ayarlarını aç");
        settingsButton.setOnClickListener(view -> openAppSettings());
        permissionActions.addView(requestPermission, new LinearLayout.LayoutParams(0, -2, 1));
        permissionActions.addView(settingsButton, new LinearLayout.LayoutParams(0, -2, 2));
        layout.addView(permissionActions);
        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, songs);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> play(position));
        layout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        Button previous = button("⏮");
        playButton = button("▶");
        Button next = button("⏭");
        previous.setContentDescription("Önceki");
        playButton.setContentDescription("Oynat veya duraklat");
        next.setContentDescription("Sonraki");
        previous.setOnClickListener(view -> step(-1));
        playButton.setOnClickListener(view -> toggle());
        next.setOnClickListener(view -> step(1));
        controls.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(playButton, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
        layout.addView(controls);
        LinearLayout actions = new LinearLayout(this);
        Button refresh = button("Yenile");
        Button pick = button("Dosya seç");
        Button about = button("Hakkında");
        refresh.setOnClickListener(view -> checkPermission());
        pick.setOnClickListener(view -> pickAudio());
        about.setOnClickListener(view -> new AlertDialog.Builder(this).setTitle("Hakkında")
                .setMessage("Piyami'den Kardeşine hediye\n\n© Piyami Polat")
                .setPositiveButton("Tamam", null).show());
        actions.addView(refresh, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(pick, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(about, new LinearLayout.LayoutParams(0, -2, 1));
        layout.addView(actions);
        setContentView(layout);
        lastPermissionGranted = hasPermission();
        updatePermissionStatus();
        checkPermission();
    }

    private Button button(String text) { Button button = new Button(this); button.setText(text); return button; }

    @Override protected void onResume() {
        super.onResume();
        boolean granted = hasPermission();
        updatePermissionStatus();
        if (!scanning) updateStatus();
        if (granted != lastPermissionGranted) scanSongs();
        else if (songs.isEmpty() && !scanning) scanSongs();
        lastPermissionGranted = granted;
    }

    private String audioPermission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private boolean hasPermission() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    private void checkPermission() {
        scanSongs();
        if (!hasPermission()) requestAudioPermission();
    }

    private void requestAudioPermission() {
        if (hasPermission()) { updatePermissionStatus(); scanSongs(); return; }
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{audioPermission()}, AUDIO_PERMISSION);
    }

    private void updatePermissionStatus() {
        boolean granted = hasPermission();
        boolean blocked = !granted && Build.VERSION.SDK_INT >= 23 &&
                getPreferences(MODE_PRIVATE).getBoolean("audio_permission_requested", false) &&
                !shouldShowRequestPermissionRationale(audioPermission());
        permissionStatus.setText("İzinler: " + (granted ? "İzin verildi" : "İzin YOK") +
                (blocked ? " — uygulama ayarlarından izin verin" : ""));
        settingsButton.setVisibility(granted ? View.GONE : View.VISIBLE);
        settingsButton.setTypeface(null, blocked ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception error) { toast("Uygulama ayarları açılamadı: " + error.getMessage()); }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == AUDIO_PERMISSION) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("audio_permission_requested", true).apply();
            lastPermissionGranted = hasPermission();
            updatePermissionStatus();
            scanSongs();
        }
    }

    private void scanSongs() {
        scanning = true;
        status.setText("Müzikler aranıyor…");
        int scan = ++scanGeneration;
        if (scanTask != null) scanTask.cancel(true);
        scanTask = scanner.submit(() -> {
            ArrayList<Song> found;
            try { found = MusicLibrary.scan(getApplicationContext()); }
            catch (Exception error) { found = new ArrayList<>(); }
            ArrayList<Song> result = found;
            handler.post(() -> {
                if (destroyed || scan != scanGeneration) return;
                scanning = false;
                Song playing = current >= 0 && current < songs.size() ? songs.get(current) : null;
                for (Song song : picked) addUnique(result, song);
                songs.clear();
                songs.addAll(result);
                current = indexOf(playing);
                adapter.notifyDataSetChanged();
                updateStatus();
            });
        });
    }

    private int indexOf(Song target) {
        if (target == null) return -1;
        for (int position = 0; position < songs.size(); position++) {
            Song song = songs.get(position);
            if (song.uri.equals(target.uri) || MusicLibrary.key(song).equals(MusicLibrary.key(target))) return position;
        }
        return -1;
    }

    private void addUnique(ArrayList<Song> list, Song target) {
        for (Song song : list) {
            if (song.uri.equals(target.uri) || MusicLibrary.key(song).equals(MusicLibrary.key(target))) return;
        }
        list.add(target);
    }

    private void updateStatus() {
        String text = songs.size() + " parça bulundu";
        if (songs.isEmpty()) text += ". " + EMPTY + ". Dosya seç veya Yenile.";
        if (!hasPermission()) text += " Depolama izni verilmedi; yalnızca erişilebilir dosyalar gösteriliyor.";
        status.setText(text);
    }

    private void pickAudio() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= 18) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { startActivityForResult(Intent.createChooser(intent, "Dosya seç"), PICK_AUDIO); }
        catch (Exception error) { toast("Dosya seçici açılamadı: " + error.getMessage()); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_AUDIO || result != RESULT_OK || data == null) return;
        ArrayList<Uri> selected = new ArrayList<>();
        if (data.getData() != null) selected.add(data.getData());
        if (Build.VERSION.SDK_INT >= 18) {
            ClipData clips = data.getClipData();
            if (clips != null) for (int item = 0; item < clips.getItemCount(); item++) {
                Uri uri = clips.getItemAt(item).getUri();
                if (uri != null && !selected.contains(uri)) selected.add(uri);
            }
        }
        Song first = null;
        for (Uri uri : selected) {
            if (Build.VERSION.SDK_INT >= 19) {
                try { getContentResolver().takePersistableUriPermission(uri, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                catch (Exception ignored) { }
            }
            Song song = MusicLibrary.picked(this, uri);
            addUnique(picked, song);
            addUnique(songs, song);
            if (first == null) first = song;
        }
        adapter.notifyDataSetChanged();
        updateStatus();
        int position = indexOf(first);
        if (position >= 0) play(position);
    }

    private void play(int position) {
        if (songs.isEmpty()) { empty(); return; }
        if (position < 0 || position >= songs.size()) return;
        generation++;
        attemptsLeft = songs.size();
        attempt(position, generation);
    }

    private void attempt(int position, int token) {
        if (destroyed || token != generation || songs.isEmpty()) return;
        releasePlayer();
        current = position % songs.size();
        attemptsLeft--;
        Song song = songs.get(current);
        currentTitle.setText(song.title);
        try {
            player = new MediaPlayer();
            MusicLibrary.setDataSource(this, player, song);
            preparing = true;
            playWhenReady = true;
            player.setOnPreparedListener(ready -> {
                if (ready != player || token != generation) return;
                preparing = false;
                try { if (playWhenReady) ready.start(); }
                catch (Exception error) { failed(token, error.toString()); return; }
                updateButton();
            });
            player.setOnCompletionListener(done -> { if (done == player && token == generation) step(1); });
            player.setOnErrorListener((failedPlayer, what, extra) -> {
                if (failedPlayer == player && token == generation) failed(token, "Medya hatası " + what + "/" + extra);
                return true;
            });
            player.prepareAsync();
            updateButton();
        } catch (Exception error) { failed(token, error.toString()); }
    }

    private void failed(int token, String reason) {
        if (token != generation) return;
        String title = current >= 0 && current < songs.size() ? songs.get(current).title : "Parça";
        toast(title + " açılamadı: " + reason);
        releasePlayer();
        if (attemptsLeft > 0 && !songs.isEmpty()) {
            int next = (current + 1) % songs.size();
            handler.post(() -> attempt(next, token));
        } else currentTitle.setText("Hiçbir parça açılamadı. Dosya seç veya Yenile.");
    }

    private void step(int delta) {
        if (songs.isEmpty()) { empty(); return; }
        int position = current < 0 ? 0 : (current + delta + songs.size()) % songs.size();
        play(position);
    }

    private void toggle() {
        if (songs.isEmpty()) { empty(); return; }
        if (player == null) { play(current < 0 ? 0 : current); return; }
        try {
            if (preparing) playWhenReady = !playWhenReady;
            else if (player.isPlaying()) { player.pause(); playWhenReady = false; }
            else { player.start(); playWhenReady = true; }
            updateButton();
        } catch (Exception error) { failed(generation, error.toString()); }
    }

    private void empty() { updateStatus(); toast(EMPTY + ". Dosya seç veya Yenile."); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private void updateButton() { playButton.setText(player != null && playWhenReady ? "❚❚" : "▶"); }
    private void releasePlayer() {
        if (player != null) { player.release(); player = null; }
        preparing = false;
        playWhenReady = false;
        if (playButton != null) updateButton();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        generation++;
        scanner.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onDestroy();
    }
}
