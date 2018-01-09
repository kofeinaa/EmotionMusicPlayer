package com.wigdis.player;

import java.io.Serializable;

public class Audio implements Serializable {

    private String data;
    private String title;
    private String album;
    private String artist;
    private Integer id;


    public Audio(String data, String title, String album, String artist, Integer id) {
        this.data = data;
        this.title = title;
        this.album = album;
        this.artist = artist;
        this.id = id;

        if (this.title == null) {
            this.title = "Unknown";
        }
        if (this.album == null) {
            this.title = "Unknown";
        }
        if (this.artist == null) {
            this.artist = "Unknown";
        }
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}
