package com.jrobertgardzinski;

import io.micronaut.runtime.Micronaut;

public class App {
    public static void main(String[] args) {
        // before the context exists: an undeclared start must be refused by name, not die on
        // whichever eager bean happens to miss its DataSource first (see ProfileGuard)
        ProfileGuard.requireDeclaredProfile(System.getProperty("micronaut.environments",
                System.getenv().getOrDefault("MICRONAUT_ENVIRONMENTS", "")));
        Micronaut.run(App.class, args);
    }
}
