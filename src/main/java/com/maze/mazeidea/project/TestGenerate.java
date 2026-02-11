package com.maze.mazeidea.project;

import java.nio.file.Path;

public class TestGenerate {
    public static void main(String[] args) throws Exception {
        Path tmp = Path.of(System.getProperty("user.home"), "tmp-gen-test");
        ProjectGenerator pg = new ProjectGenerator();
        pg.generateSpringBootProject(tmp, "com.example", "demo", "com.example.demo", "0.0.1-SNAPSHOT", "jar", "17", true, false, false, false);
        System.out.println("Generated at: " + tmp.toAbsolutePath());
    }
}
