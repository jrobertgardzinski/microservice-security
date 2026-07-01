package com.jrobertgardzinski.security.infrastructure;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Drives {@code verify-email.feature} through the HTTP entry point: request verification, read back
 * the e-mailed token via the test notifier, confirm it, and reject a garbage token.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("verify-email.feature")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "html:target/report-http-verify-email.html")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.jrobertgardzinski.security.infrastructure.feature.verification")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @wip")
public class RunHttpVerifyEmailTest {
}
