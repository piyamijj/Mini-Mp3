package com.piyami.minimp3;

import android.net.Uri;

final class Song {
    final Uri uri;
    final String title;
    final String path;

    Song(Uri uri, String title) {
        this(uri, title, null);
    }

    Song(Uri uri, String title, String path) {
        this.uri = uri;
        this.title = title;
        this.path = path;
    }

    @Override
    public String toString() {
        return title;
    }
}
