package com.jrobertgardzinski.security.infrastructure;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Drives the shared {@code password-policy.feature} through the HTTP entry point: the minimum
 * password length is set and reported via {@code /admin/settings/password/min-length}, and the
 * "row written at the console" is the in-memory settings level seeded behind the value object's
 * back — exactly what a hand at psql does.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("password-policy.feature")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "html:target/report-http-password-policy.html")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.jrobertgardzinski.security.infrastructure.feature.passwordpolicy")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @wip")
public class RunHttpPasswordPolicyTest {
}
