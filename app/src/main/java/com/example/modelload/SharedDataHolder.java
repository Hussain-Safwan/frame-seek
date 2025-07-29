package com.example.modelload;

import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

public class SharedDataHolder {
    public static final SharedDataHolder instance = new SharedDataHolder();
    private Map<Long, float[]> dataMap = new HashMap<>();
    private Uri uploadedVideoUri = null;
    private SharedDataHolder() {};

    public static SharedDataHolder getInstance()
    {
        return instance;
    }
    public Map<Long, float[]> getDataMap()
    {
        return dataMap;
    }
    public Uri getUploadedVideoUri()
    {
        return uploadedVideoUri;
    }
    public void setDataMap(Map<Long, float[]> dataMap)
    {
        this.dataMap = dataMap;
    }
    public void setUploadedVideoUri(Uri uri)
    {
        this.uploadedVideoUri = uri;
    }
}
