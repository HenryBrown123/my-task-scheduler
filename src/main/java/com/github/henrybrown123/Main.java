package com.github.henrybrown123;

import com.github.henrybrown123.app.AppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            new AppContext().start();
        } catch (Exception e) {
            log.error("Fatal: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}