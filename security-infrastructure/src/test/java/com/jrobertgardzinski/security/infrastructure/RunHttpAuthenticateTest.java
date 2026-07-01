package com.jrobertgardzinski.security.infrastructure;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Drives the shared {@code authenticate.feature} (pulled from the top-level {@code specs/} dir onto
 * the classpath by build-helper) through the HTTP entry point — a second set of step-defs over the
 * very same Gherkin the application-level runner uses. The time-dependent scenarios are realised by
 * steering the {@code test}-environment clock over its control endpoint.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("authenticate.feature")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "html:target/report-http-authenticate.html")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.jrobertgardzinski.security.infrastructure.feature.authentication")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @wip")
public class RunHttpAuthenticateTest {
}
