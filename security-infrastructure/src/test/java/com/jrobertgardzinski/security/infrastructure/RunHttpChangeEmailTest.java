package com.jrobertgardzinski.security.infrastructure;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Drives {@code change-email.feature} through the HTTP entry point: request the change, confirm the
 * e-mailed token, and check the user authenticates under the new address, not the old one.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("change-email.feature")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "html:target/report-http-change-email.html")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.jrobertgardzinski.security.infrastructure.feature.emailchange")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @wip")
public class RunHttpChangeEmailTest {
}
