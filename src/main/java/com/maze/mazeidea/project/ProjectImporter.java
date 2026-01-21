package com.maze.mazeidea.project;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Project importer using DOM XML parsing to read pom.xml and recursively resolve parent POMs via relativePath.
 * This implementation avoids compile-time dependency on Maven model classes so it builds without network access.
 */
public class ProjectImporter {
    public ProjectModel importProjectModel(Path root) {
        ProjectModel model = new ProjectModel(root);
        Path pom = root.resolve("pom.xml");
        if (!Files.exists(pom)) return model;
        try {
            Set<String> seen = new HashSet<>();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder db = dbf.newDocumentBuilder();

            // Walk up parent chain and collect dependencies/modules
            Path currentPom = pom;
            while (currentPom != null && Files.exists(currentPom) && seen.add(currentPom.toAbsolutePath().toString())) {
                Document doc = db.parse(currentPom.toFile());
                Element rootEl = doc.getDocumentElement();

                // modules (only from top-level pom ideally)
                if (currentPom.equals(pom)) {
                    NodeList modules = rootEl.getElementsByTagName("modules");
                    if (modules.getLength() > 0) {
                        Node modulesNode = modules.item(0);
                        NodeList children = modulesNode.getChildNodes();
                        for (int i = 0; i < children.getLength(); i++) {
                            Node n = children.item(i);
                            if (n.getNodeType() == Node.ELEMENT_NODE && "module".equals(n.getNodeName())) {
                                String m = n.getTextContent().trim();
                                if (!m.isEmpty()) model.addModule(m);
                            }
                        }
                    }
                }

                // dependencies
                NodeList deps = rootEl.getElementsByTagName("dependency");
                for (int i = 0; i < deps.getLength(); i++) {
                    Node d = deps.item(i);
                    if (d.getNodeType() == Node.ELEMENT_NODE) {
                        Element de = (Element) d;
                        String g = getChildText(de, "groupId");
                        String a = getChildText(de, "artifactId");
                        if (g != null && a != null) model.addDependency(g + ":" + a);
                    }
                }

                // detect source roots
                if (Files.exists(root.resolve("src/main/java"))) model.addSourceRoot(root.resolve("src/main/java"));
                if (Files.exists(root.resolve("src/test/java"))) model.addSourceRoot(root.resolve("src/test/java"));

                // resolve parent relativePath and continue loop
                NodeList parents = rootEl.getElementsByTagName("parent");
                Path next = null;
                if (parents.getLength() > 0) {
                    Element p = (Element) parents.item(0);
                    String rel = getChildText(p, "relativePath");
                    if (rel == null || rel.isBlank()) rel = "../pom.xml";
                    Path candidate = currentPom.getParent().resolve(rel).normalize();
                    if (Files.exists(candidate)) next = candidate;
                }
                currentPom = next;
            }

        } catch (Exception e) {
            // parsing failed; return minimal model
        }
        return model;
    }

    private String getChildText(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) return null;
        return nl.item(0).getTextContent().trim();
    }

    public List<Path> importWorkspace(Path root) {
        List<Path> items = new ArrayList<>();
        try {
            if (Files.exists(root.resolve("pom.xml"))) {
                // Use ProjectModel to build a nicer list
                ProjectModel model = importProjectModel(root);
                items.add(root.resolve("pom.xml"));
                model.getSourceRoots().forEach(items::add);
                for (String m : model.getModules()) items.add(root.resolve(m));
            } else if (Files.exists(root.resolve("package.json"))) {
                items.add(root.resolve("package.json"));
                if (Files.exists(root.resolve("src"))) items.add(root.resolve("src"));
            } else {
                Files.list(root).limit(200).forEach(items::add);
            }
        } catch (Exception ignored) {}
        return items;
    }
}
