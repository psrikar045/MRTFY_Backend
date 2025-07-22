package com.example.jwtauthenticator;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * JUnit 5 Extension for automatic test documentation
 * This extension automatically documents test execution results
 */
public class TestDocumentationExtension implements TestWatcher {
    
    private static final TestDocumentationGenerator generator = new TestDocumentationGenerator();
    
    @Override
    public void testSuccessful(ExtensionContext context) {
        generator.testSuccessful(context);
    }
    
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        generator.testFailed(context, cause);
    }
    
    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        generator.testAborted(context, cause);
    }
    
    @Override
    public void testDisabled(ExtensionContext context, java.util.Optional<String> reason) {
        generator.testDisabled(context, reason);
    }
}