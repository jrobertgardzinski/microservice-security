package com.jrobertgardzinski.password;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectPackages("com.jrobertgardzinski.password")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "html:target/report.html")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.jrobertgardzinski.password.feature")
public class RunCucumberTest {
}
