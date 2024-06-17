package com.welljoint.constant;

public enum AudioFormat {
    MP3(".mp3"),
    V3(".v3"),
    AAC(".aac"),
    NMF(".nmf"),
    WAV(".wav");
    private final String fileExtension;

    AudioFormat(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public static AudioFormat fromString(String text) {
        for (AudioFormat format : AudioFormat.values()) {
            if (format.fileExtension.equalsIgnoreCase(text)) {
                return format;
            }
        }
        throw new IllegalArgumentException("No constant with text " + text + " found");
    }
}
