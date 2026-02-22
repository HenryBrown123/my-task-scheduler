package com.github.henrybrown123;

import com.github.henrybrown123.app.AppContext;

public class Main {

    public static void main(String[] args) {
        try {
            new AppContext().start();
        } catch (Exception e) {
            System.err.println("Fatal: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}