package com.maze.mazeidea.project;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProjectGeneratorTest {
    @Test
    public void generateSpringBoot() throws Exception {
        Path tmp = Files.createTempDirectory("pg-spring-");
        ProjectGenerator g = new ProjectGenerator();
        Path target = tmp.resolve("demo-spring");
        g.generate("Spring Boot", target);
        assertTrue(Files.exists(target.resolve("pom.xml")));
        assertTrue(Files.exists(target.resolve("src/main/java/com/example/demo/DemoApplication.java")));
    }

    @Test
    public void generateReact() throws Exception {
        Path tmp = Files.createTempDirectory("pg-react-");
        ProjectGenerator g = new ProjectGenerator();
        Path target = tmp.resolve("demo-react");
        g.generate("React", target);
        assertTrue(Files.exists(target.resolve("package.json")));
        assertTrue(Files.exists(target.resolve("src/index.js")));
    }

    @Test
    public void generateAngular() throws Exception {
        Path tmp = Files.createTempDirectory("pg-angular-");
        ProjectGenerator g = new ProjectGenerator();
        Path target = tmp.resolve("demo-angular");
        g.generate("Angular", target);
        assertTrue(Files.exists(target.resolve("package.json")));
        assertTrue(Files.exists(target.resolve("src/app/app.module.ts")));
    }
}
