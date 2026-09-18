package com.example.demo.service;

/** Thrown when a sign-up requests a username that is already registered. */
public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(String username) {
        super("Username already taken: " + username);
    }
}
