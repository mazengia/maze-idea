package com.maze.mazeidea.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class ProjectGenerator {
    public void generate(String templateName, Path targetDir) throws IOException {
        generateAndCollect(templateName, targetDir, false, false, false, false);
    }

    public List<Path> generateAndCollect(String templateName, Path targetDir, boolean depWeb, boolean depDataJpa, boolean depSecurity, boolean depLombok) throws IOException {
        if (Files.exists(targetDir)) throw new IOException("Target already exists: " + targetDir);
        List<Path> created = new ArrayList<>();
        Files.createDirectories(targetDir);
        created.add(targetDir);
        switch (templateName) {
            case "Spring Boot":
                created.addAll(generateSpringBoot(targetDir, depWeb, depDataJpa, depSecurity, depLombok));
                break;
            case "React":
                created.addAll(generateReact(targetDir));
                break;
            case "Angular":
                created.addAll(generateAngular(targetDir));
                break;
            default:
                throw new IllegalArgumentException("Unknown template: " + templateName);
        }
        return created;
    }

    // Backwards-compatible simple spring generator
    private List<Path> generateSpringBoot(Path dir, boolean depWeb, boolean depDataJpa, boolean depSecurity, boolean depLombok) throws IOException {
        return generateSpringBootProject(dir, "com.example", "demo", "com.example.demo", "0.0.1-SNAPSHOT", "jar", "17", depWeb, depDataJpa, depSecurity, depLombok);
    }

    // New more flexible generator used by the wizard
    public List<Path> generateSpringBootProject(Path dir, String groupId, String artifactId, String packageName, String version, String packaging, String javaVersion, boolean depWeb, boolean depDataJpa, boolean depSecurity, boolean depLombok) throws IOException {
        List<Path> created = new ArrayList<>();
        // create standard maven layout
        String packagePath = packageName.replace('.', '/');
        Files.createDirectories(dir.resolve("src/main/java/" + packagePath));
        Files.createDirectories(dir.resolve("src/main/resources"));
        Files.createDirectories(dir.resolve("src/test/java/" + packagePath));
        created.add(dir.resolve("src/main/java/" + packagePath));
        created.add(dir.resolve("src/main/resources"));
        created.add(dir.resolve("src/test/java/" + packagePath));

        StringBuilder deps = new StringBuilder();
        if (depWeb) {
            deps.append("    <dependency>\n      <groupId>org.springframework.boot</groupId>\n      <artifactId>spring-boot-starter-web</artifactId>\n    </dependency>\n");
        }
        if (depDataJpa) {
            deps.append("    <dependency>\n      <groupId>org.springframework.boot</groupId>\n      <artifactId>spring-boot-starter-data-jpa</artifactId>\n    </dependency>\n");
        }
        if (depSecurity) {
            deps.append("    <dependency>\n      <groupId>org.springframework.boot</groupId>\n      <artifactId>spring-boot-starter-security</artifactId>\n    </dependency>\n");
        }
        if (depLombok) {
            deps.append("    <dependency>\n      <groupId>org.projectlombok</groupId>\n      <artifactId>lombok</artifactId>\n      <scope>provided</scope>\n    </dependency>\n");
        }
        if (deps.length() == 0) {
            deps.append("    <dependency>\n      <groupId>org.springframework.boot</groupId>\n      <artifactId>spring-boot-starter-web</artifactId>\n    </dependency>\n");
        }

        String pom = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n  <modelVersion>4.0.0</modelVersion>\n  <groupId>" + groupId + "</groupId>\n  <artifactId>" + artifactId + "</artifactId>\n  <version>" + version + "</version>\n  <packaging>" + packaging + "</packaging>\n  <parent>\n    <groupId>org.springframework.boot</groupId>\n    <artifactId>spring-boot-starter-parent</artifactId>\n    <version>3.1.0</version>\n  </parent>\n  <properties>\n    <java.version>" + javaVersion + "</java.version>\n  </properties>\n  <dependencies>\n" + deps.toString() + "  </dependencies>\n" +
                "  <repositories>\n" +
                "    <repository>\n" +
                "      <id>central</id>\n" +
                "      <url>https://repo1.maven.org/maven2</url>\n" +
                "    </repository>\n" +
                "  </repositories>\n" +
                "  <build>\n" +
                "    <plugins>\n" +
                "      <plugin>\n" +
                "        <groupId>org.springframework.boot</groupId>\n" +
                "        <artifactId>spring-boot-maven-plugin</artifactId>\n" +
                "        <version>3.1.0</version>\n" +
                "      </plugin>\n" +
                "    </plugins>\n" +
                "  </build>\n" +
                 "</project>";
        Path p = dir.resolve("pom.xml");
        Files.writeString(p, pom, StandardCharsets.UTF_8);
        created.add(p);

        String mainClass = packageName + ".Application";
        String app = "package " + packageName + ";\n\nimport org.springframework.boot.SpringApplication;\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n\n@SpringBootApplication\npublic class Application {\n    public static void main(String[] args) {\n        SpringApplication.run(Application.class, args);\n    }\n}\n";
        Path appPath = dir.resolve("src/main/java/" + packagePath + "/Application.java");
        Files.writeString(appPath, app, StandardCharsets.UTF_8);
        created.add(appPath);
        return created;
    }

    private List<Path> generateReact(Path dir) throws IOException {
        List<Path> created = new ArrayList<>();
        Files.createDirectories(dir.resolve("src"));
        created.add(dir.resolve("src"));
        String pkg = "{\n  \"name\": \"react-app\",\n  \"version\": \"0.1.0\",\n  \"private\": true,\n  \"dependencies\": {\n    \"react\": \"^18.0.0\",\n    \"react-dom\": \"^18.0.0\"\n  }\n}";
        Path pkgPath = dir.resolve("package.json");
        Files.writeString(pkgPath, pkg, StandardCharsets.UTF_8);
        created.add(pkgPath);
        String index = "import React from 'react';\nimport { createRoot } from 'react-dom/client';\nconst App = () => <div>Hello React</div>;\ncreateRoot(document.getElementById('root')).render(<App/>);";
        Path indexPath = dir.resolve("src/index.js");
        Files.writeString(indexPath, index, StandardCharsets.UTF_8);
        created.add(indexPath);
        String html = "<!doctype html>\n<html>\n  <head>\n    <meta charset=\"utf-8\">\n    <title>React App</title>\n  </head>\n  <body>\n    <div id=\"root\"></div>\n    <script src=\"src/index.js\"></script>\n  </body>\n</html>";
        Path htmlPath = dir.resolve("index.html");
        Files.writeString(htmlPath, html, StandardCharsets.UTF_8);
        created.add(htmlPath);
        return created;
    }

    private List<Path> generateAngular(Path dir) throws IOException {
        List<Path> created = new ArrayList<>();
        Files.createDirectories(dir.resolve("src/app"));
        created.add(dir.resolve("src/app"));
        String pkg = "{\n  \"name\": \"angular-app\",\n  \"version\": \"0.0.0\",\n  \"private\": true\n}";
        Path pkgPath = dir.resolve("package.json");
        Files.writeString(pkgPath, pkg, StandardCharsets.UTF_8);
        created.add(pkgPath);
        String main = "import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';\nimport { AppModule } from './app/app.module';\nplatformBrowserDynamic().bootstrapModule(AppModule)\n  .catch(err => console.error(err));";
        Path mainPath = dir.resolve("src/main.ts");
        Files.writeString(mainPath, main, StandardCharsets.UTF_8);
        created.add(mainPath);
        String appModule = "import { NgModule } from '@angular/core';\nimport { BrowserModule } from '@angular/platform-browser';\nimport { AppComponent } from './app.component';\n\n@NgModule({\n  declarations: [AppComponent],\n  imports: [BrowserModule],\n  bootstrap: [AppComponent]\n})\nexport class AppModule { }";
        Path appModulePath = dir.resolve("src/app/app.module.ts");
        Files.writeString(appModulePath, appModule, StandardCharsets.UTF_8);
        created.add(appModulePath);
        String appComp = "import { Component } from '@angular/core';\n@Component({ selector: 'app-root', template: '<h1>Hello Angular</h1>' })\nexport class AppComponent { }";
        Path appCompPath = dir.resolve("src/app/app.component.ts");
        Files.writeString(appCompPath, appComp, StandardCharsets.UTF_8);
        created.add(appCompPath);
        return created;
    }
}
