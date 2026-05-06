package com.thomazcollet;

import com.thomazcollet.infra.database.DatabaseInitializer;

/**
 * Hello world!
 */
public class PomodoroApplication {
    public static void main(String[] args) {
        System.out.println("Hello World!");

        DatabaseInitializer.initialize();
    }
}
