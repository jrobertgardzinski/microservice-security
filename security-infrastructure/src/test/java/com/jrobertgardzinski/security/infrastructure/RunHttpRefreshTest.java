package com.jrobertgardzinski.security.infrastructure;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Drives the shared {@code refresh-session.feature} through the HTTP entry point — a second set of
 * step-defs over the same Gherkin the application-level runner uses. The refresh token rides in an
 * HttpOnly cookie obtained by really authenticating; session expiry is reached by steering the
 * {@code test}-environment clock.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("refresh-session.feature")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "html:target/report-http-refresh.html")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.jrobertgardzinski.security.infrastructure.feature.session")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @wip")
public class RunHttpRefreshTest {
}
