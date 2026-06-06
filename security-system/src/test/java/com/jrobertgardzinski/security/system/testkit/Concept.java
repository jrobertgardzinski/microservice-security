package com.jrobertgardzinski.security.system.testkit;

import io.qameta.allure.LinkAnnotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Links a test to a term from the ubiquitous language by its stable kebab-slug
 * (e.g. {@code "brute-force-guard"}).
 *
 * <p>Renders in Allure as a custom link of type {@code concept}; the slug is substituted
 * into the {@code allure.link.concept.pattern} (see {@code src/test/resources/allure.properties}),
 * pointing at the matching anchor in the glossary. The same slug is the join key for
 * Javadoc &harr; Allure navigation.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@LinkAnnotation(type = "concept")
public @interface Concept {
    String value();
}
