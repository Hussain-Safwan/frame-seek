package com.example.modelload;

public enum ErrorCode {
    MULTIPLE_FACES("Multiple faces found in the image"),
    NO_FACE("No faces were in the image"),
    FACE_DETECTOR_FAILURE("Face detection failed");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
