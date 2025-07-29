package com.example.modelload;

import android.net.Uri;

public class TimeFrame {
    public String query;
    public long timeMillis;
    public Uri queryImageUri;

    public TimeFrame(String query, long timeMillis, Uri queryImageUri)
    {
        this.query = query;
        this.timeMillis = timeMillis;
        this.queryImageUri = queryImageUri;
    }
}
