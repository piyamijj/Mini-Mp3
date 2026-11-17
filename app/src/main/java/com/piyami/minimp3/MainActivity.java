package com.piyami.minimp3;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private final ArrayList<Uri> uris = new ArrayList<>();
    private final ArrayList<String> titles = new ArrayList<>();
    private MediaPlayer player;
    private int current = -1;
    private boolean preparing;
    private boolean playWhenReady;
    private TextView currentTitle;
    private TextView status;
    private Button playButton;
    private ArrayAdapter<String> adapter;

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
        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titles);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> play(position));
        layout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        Button previous = button("⏮");
        playButton = button("▶");
        Button next = button("⏭");
        Button about = button("Hakkında");
        previous.setContentDescription("Önceki");
        playButton.setContentDescription("Oynat veya duraklat");
        next.setContentDescription("Sonraki");
        previous.setOnClickListener(view -> step(-1));
        playButton.setOnClickListener(view -> toggle());
        next.setOnClickListener(view -> step(1));
        about.setOnClickListener(view -> new AlertDialog.Builder(this).setTitle("Hakkında")
                .setMessage("Piyami'den Kardeşine hediye\n\n© Piyami Polat")
                .setPositiveButton("Tamam", null).show());
        buttons.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(playButton, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(about, new LinearLayout.LayoutParams(0, -2, 2));
        layout.addView(buttons);
        setContentView(layout);
        checkPermission();
    }

    private Button button(String text) { Button button = new Button(this); button.setText(text); return button; }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        } else loadSongs();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 1) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadSongs();
            else status.setText("Müzikleri göstermek için depolama izni gerekli.");
        }
    }

    private void loadSongs() {
        uris.clear();
        titles.clear();
        String[] columns = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE};
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 OR " + MediaStore.Audio.Media.MIME_TYPE + " LIKE ?";
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns,
                selection, new String[]{"audio/%"}, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    uris.add(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)));
                    String title = cursor.getString(1);
                    titles.add(title == null || title.isEmpty() ? "Bilinmeyen parça" : title);
                }
                status.setText(titles.isEmpty() ? "Ses dosyası bulunamadı." : titles.size() + " parça bulundu");
            } else status.setText("Müzik listesine erişilemedi.");
        } catch (Exception error) { status.setText("Müzik listesine erişilemedi: " + error.getMessage()); }
        finally { if (cursor != null) cursor.close(); }
        adapter.notifyDataSetChanged();
    }

    private void play(int position) {
        if (position < 0 || position >= uris.size()) return;
        releasePlayer();
        current = position;
        currentTitle.setText(titles.get(current));
        try {
            player = new MediaPlayer();
            player.setDataSource(this, uris.get(current));
            preparing = true;
            playWhenReady = true;
            player.setOnPreparedListener(ready -> {
                if (ready != player) return;
                preparing = false;
                if (playWhenReady) ready.start();
                updateButton();
            });
            player.setOnCompletionListener(done -> step(1));
            player.setOnErrorListener((failed, what, extra) -> {
                if (failed == player) { releasePlayer(); Toast.makeText(this, "Parça çalınamadı", Toast.LENGTH_SHORT).show(); }
                return true;
            });
            player.prepareAsync();
            updateButton();
        } catch (Exception error) {
            releasePlayer();
            Toast.makeText(this, "Parça açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    private void step(int delta) {
        if (uris.isEmpty()) return;
        int position = current < 0 ? 0 : (current + delta + uris.size()) % uris.size();
        play(position);
    }

    private void toggle() {
        if (uris.isEmpty()) { Toast.makeText(this, "Ses dosyası bulunamadı", Toast.LENGTH_SHORT).show(); return; }
        if (player == null) { play(current < 0 ? 0 : current); return; }
        if (preparing) playWhenReady = !playWhenReady;
        else if (player.isPlaying()) { player.pause(); playWhenReady = false; }
        else { player.start(); playWhenReady = true; }
        updateButton();
    }

    private void updateButton() { playButton.setText(player != null && playWhenReady ? "❚❚" : "▶"); }

    private void releasePlayer() {
        if (player != null) { player.release(); player = null; }
        preparing = false;
        playWhenReady = false;
        updateButton();
    }

    @Override protected void onDestroy() { releasePlayer(); super.onDestroy(); }
}
