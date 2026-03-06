package com.example.partyplanner.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppExecutors {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    public static ExecutorService io() { return IO; }
}
