package com.jrobertgardzinski.security.system.testkit;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guardrail for the Javadoc &harr; Allure navigation: every {@link Concept} slug used by a test
 * must resolve to an anchor in {@code glossary.html}. A typo becomes a red build, not a dead link.
 */
@Epic("Use case")
@Feature("Concept glossary")
class ConceptGlossaryTest {

    @Example
    @Label("Every @Concept slug resolves to an anchor in glossary.html")
    void every_concept_slug_has_a_glossary_anchor() throws Exception {
        Set<String> slugs = conceptSlugsInTestSuite();
        Set<String> anchors = glossaryAnchors();

        Set<String> missing = new TreeSet<>(slugs);
        missing.removeAll(anchors);

        assertFalse(slugs.isEmpty(), "no @Concept usages found — the classpath scan is broken");
        assertTrue(missing.isEmpty(),
                "@Concept slugs without a glossary anchor: " + missing + " (anchors present: " + anchors + ")");
    }

    private Set<String> conceptSlugsInTestSuite() throws Exception {
        Path root = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        ClassLoader loader = getClass().getClassLoader();
        Set<String> slugs = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> classFiles = paths.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList());
            for (Path classFile : classFiles) {
                String fqn = root.relativize(classFile).toString()
                        .replace(File.separatorChar, '.')
                        .replaceAll("\\.class$", "");
                Class<?> clazz;
                try {
                    clazz = Class.forName(fqn, false, loader);
                } catch (Throwable ignored) {
                    continue; // unloadable artifact (e.g. tooling stub) — not our concern
                }
                Concept onClass = clazz.getAnnotation(Concept.class);
                if (onClass != null) {
                    slugs.add(onClass.value());
                }
                for (Method method : clazz.getDeclaredMethods()) {
                    Concept onMethod = method.getAnnotation(Concept.class);
                    if (onMethod != null) {
                        slugs.add(onMethod.value());
                    }
                }
            }
        }
        return slugs;
    }

    private Set<String> glossaryAnchors() throws Exception {
        String html = Files.readString(Path.of(getClass().getResource("/glossary.html").toURI()));
        Matcher matcher = Pattern.compile("id=\"([^\"]+)\"").matcher(html);
        Set<String> anchors = new TreeSet<>();
        while (matcher.find()) {
            anchors.add(matcher.group(1));
        }
        return anchors;
    }
}
