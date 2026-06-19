package com.jrobertgardzinski.security.application;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectPackages("com.jrobertgardzinski.security.application")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "html:target/report.html")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.jrobertgardzinski.security.application.feature")
// features tagged @wip (not yet implemented) are excluded from the run
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @wip")
public class RunCucumberTest {
}