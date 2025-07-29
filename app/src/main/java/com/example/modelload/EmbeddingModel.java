package com.example.modelload;

import java.lang.reflect.Array;
import java.util.Arrays;

public class EmbeddingModel {
    private String filename;
    private long[] timestamps;
    private float[][] embeddings;
    private int id;

    public String getFilename() { return filename; }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long[] getTimestamps() { return timestamps; }

    public void setTimestamps(long[] timestamps) {
        this.timestamps = timestamps;
    }

    public float[][] getEmbedding() {
        return embeddings;
    }

    public void setEmbedding(float[][] embeddings) {
        this.embeddings = embeddings;
    }

    public int getId() { return id; }

    public void setId(int id) { this.id = id; }

    public String toString() {
        return "id: "+id+"\nfilename: "+filename+"\ntimestamps: "+ Arrays.toString(timestamps)+"\nembeddings: "+Arrays.deepToString(embeddings);
    }
    public EmbeddingModel(String filename, long[] timestamps, float[][] embeddings) {
        this.filename = filename;
        this.timestamps = timestamps;
        this.embeddings = embeddings;
    }
}