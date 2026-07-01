package com.jrobertgardzinski.security.infrastructure;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Drives {@code delete-account.feature} through the HTTP entry point: authenticate, close the
 * account, and check the token, authentication and email are all gone/free.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("delete-account.feature")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "html:target/report-http-delete-account.html")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.jrobertgardzinski.security.infrastructure.feature.deleteaccount")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @wip")
public class RunHttpDeleteAccountTest {
}
