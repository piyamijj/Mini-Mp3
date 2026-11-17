package com.piyami.minimp3;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;

final class MusicLibrary {
    private static final int MAX_DEPTH = 6;

    static ArrayList<Song> scan(Context context) {
        LinkedHashMap<String, Song> songs = new LinkedHashMap<>();
        if (!scanMedia(context, songs, true)) scanMedia(context, songs, false);
        ArrayList<File> roots = new ArrayList<>();
        File primary = Environment.getExternalStorageDirectory();
        String[] folders = {"Music", "Download", "Downloads", "DCIM", "Documents"};
        for (String folder : folders) roots.add(new File(primary, folder));
        roots.add(primary);
        addEnvironmentRoots(roots, "EXTERNAL_STORAGE");
        addEnvironmentRoots(roots, "SECONDARY_STORAGE");
        File[] external = null;
        try { external = Build.VERSION.SDK_INT >= 19 ? context.getExternalFilesDirs(null)
                : new File[]{context.getExternalFilesDir(null)}; } catch (Exception ignored) { }
        if (external != null) {
            for (File directory : external) {
                if (directory == null) continue;
                roots.add(directory);
                String path = directory.getAbsolutePath();
                int androidDir = path.indexOf("/Android/data/");
                if (androidDir > 0) roots.add(new File(path.substring(0, androidDir)));
            }
        }
        addChildren(roots, new File("/storage"));
        addChildren(roots, new File("/mnt"));
        HashMap<String, Integer> visited = new HashMap<>();
        for (File root : roots) {
            if (Thread.currentThread().isInterrupted()) break;
            scanDirectory(root, MAX_DEPTH, visited, songs);
        }
        ArrayList<Song> result = new ArrayList<>(songs.values());
        Collections.sort(result, (first, second) -> String.CASE_INSENSITIVE_ORDER.compare(first.title, second.title));
        return result;
    }

    private static boolean scanMedia(Context context, LinkedHashMap<String, Song> songs, boolean withPath) {
        String[] columns = withPath
                ? new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA}
                : new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE};
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    columns, null, null, null);
            if (cursor == null) return false;
            while (!Thread.currentThread().isInterrupted() && cursor.moveToNext()) {
                Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0));
                String path = withPath ? cursor.getString(2) : null;
                if (path != null && !path.isEmpty()) path = canonical(new File(path));
                else path = null;
                String title = cursor.getString(1);
                if (title == null || title.trim().isEmpty() || "<unknown>".equals(title)) {
                    title = path == null ? "Bilinmeyen parça" : new File(path).getName();
                }
                Song song = new Song(uri, title, path);
                songs.put(key(song), song);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private static void addEnvironmentRoots(ArrayList<File> roots, String variable) {
        String value = System.getenv(variable);
        if (value != null) for (String path : value.split(File.pathSeparator)) if (!path.isEmpty()) roots.add(new File(path));
    }

    private static void addChildren(ArrayList<File> roots, File parent) {
        try {
            File[] children = parent.listFiles();
            if (children != null) Collections.addAll(roots, children);
        } catch (SecurityException ignored) { }
    }

    private static void scanDirectory(File directory, int depth, HashMap<String, Integer> visited,
                                      LinkedHashMap<String, Song> songs) {
        if (Thread.currentThread().isInterrupted() || depth < 0 || directory == null) return;
        try { if (!directory.isDirectory()) return; } catch (SecurityException ignored) { return; }
        String path = canonical(directory);
        Integer previousDepth = visited.get(path);
        if (previousDepth != null && previousDepth >= depth) return;
        visited.put(path, depth);
        File[] children;
        try { children = directory.listFiles(); } catch (SecurityException ignored) { return; }
        if (children == null) return;
        for (File child : children) {
            if (Thread.currentThread().isInterrupted()) return;
            String name = child.getName();
            if (name.startsWith(".")) continue;
            try {
                if (child.isDirectory()) {
                    if ("Android".equalsIgnoreCase(directory.getName()) &&
                            ("data".equalsIgnoreCase(name) || "obb".equalsIgnoreCase(name))) continue;
                    scanDirectory(child, depth - 1, visited, songs);
                } else if (child.isFile() && isAudio(name)) {
                    String filePath = canonical(child);
                    Song song = new Song(Uri.fromFile(new File(filePath)), name, filePath);
                    if (!songs.containsKey(key(song))) songs.put(key(song), song);
                }
            } catch (SecurityException ignored) { }
        }
    }

    private static boolean isAudio(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav") ||
                lower.endsWith(".ogg") || lower.endsWith(".aac") || lower.endsWith(".flac");
    }

    static String canonical(File file) {
        try { return file.getCanonicalPath(); } catch (IOException | SecurityException ignored) { return file.getAbsolutePath(); }
    }

    static String key(Song song) { return song.path != null ? "file:" + song.path : song.uri.toString(); }

    static Song picked(Context context, Uri uri) {
        String path = "file".equals(uri.getScheme()) && uri.getPath() != null ? canonical(new File(uri.getPath())) : null;
        String title = uri.getLastPathSegment();
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null) title = cursor.getString(0);
        } catch (Exception ignored) { }
        finally { if (cursor != null) cursor.close(); }
        return new Song(uri, title == null || title.isEmpty() ? "Seçilen dosya" : title, path);
    }

    static void setDataSource(Context context, MediaPlayer player, Song song) throws Exception {
        Exception last = null;
        if (song.path != null) {
            try {
                player.reset();
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                player.setDataSource(song.path);
                return;
            } catch (Exception error) { last = error; }
            FileInputStream input = null;
            try {
                input = new FileInputStream(song.path);
                player.reset();
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                player.setDataSource(input.getFD());
                return;
            } catch (Exception error) { last = error; }
            finally { if (input != null) try { input.close(); } catch (IOException ignored) { } }
        }
        try {
            player.reset();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(context, song.uri);
            return;
        } catch (Exception error) { last = error; }
        ParcelFileDescriptor descriptor = null;
        try {
            descriptor = context.getContentResolver().openFileDescriptor(song.uri, "r");
            if (descriptor == null) throw new IOException("Dosya açılamadı");
            player.reset();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(descriptor.getFileDescriptor());
            return;
        } catch (Exception error) { last = error; }
        finally { if (descriptor != null) try { descriptor.close(); } catch (IOException ignored) { } }
        throw new IOException(last == null ? "Dosya bulunamadı" : last.toString(), last);
    }
}
