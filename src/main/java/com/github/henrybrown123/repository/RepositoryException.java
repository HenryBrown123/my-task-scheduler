package com.github.henrybrown123.repository;


public class RepositoryException extends RuntimeException {
    public RepositoryException(String message, Exception e) {
        super(message, e);
    }
}
