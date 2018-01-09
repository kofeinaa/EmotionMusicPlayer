package com.wigdis.player;

public class Mood {
    private String name;
    private Double valence;
    private Double arousal;

    public Mood(String name, Double valence, Double arousal) {
        this.name = name;
        this.valence = valence;
        this.arousal = arousal;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getValence() {
        return valence;
    }

    public void setValence(Double valence) {
        this.valence = valence;
    }

    public Double getArousal() {
        return arousal;
    }

    public void setArousal(Double arousal) {
        this.arousal = arousal;
    }
}
