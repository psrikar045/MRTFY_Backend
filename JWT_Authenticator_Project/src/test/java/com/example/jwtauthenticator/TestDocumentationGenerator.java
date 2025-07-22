package com.example.jwtauthenticator;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test Documentation Generator
 * Automatically generates documentation for test execution results
 */
public class TestDocumentationGenerator implements TestWatcher, TestExecutionListener {
    
    private static final String DOCS_DIR = "target/test-documentation";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final Map<String, TestResult> testResults = new HashMap<>();
    private final List<String> testExecutionLog = new ArrayList<>();
    private LocalDateTime executionStartTime;
    private LocalDateTime executionEndTime;
    
    // TestWatcher methods for individual test results
    @Override
    public void testSuccessful(ExtensionContext context) {
        recordTestResult(context, "PASSED", null);
    }
    
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        recordTestResult(context, "FAILED", cause.getMessage());
    }
    
    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        recordTestResult(context, "ABORTED", cause.getMessage());
    }
    
    @Override
    public void testDisabled(ExtensionContext context, java.util.Optional<String> reason) {
        recordTestResult(context, "DISABLED", reason.orElse("No reason provided"));
    }
    
    // TestExecutionListener methods for overall test plan
    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        executionStartTime = LocalDateTime.now();
        testExecutionLog.add("Test execution started at: " + executionStartTime.format(TIMESTAMP_FORMAT));
        testExecutionLog.add("Total tests planned: " + testPlan.countTestIdentifiers(TestIdentifier::isTest));
    }
    
    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        executionEndTime = LocalDateTime.now();
        testExecutionLog.add("Test execution finished at: " + executionEndTime.format(TIMESTAMP_FORMAT));
        generateDocumentation();
    }
    
    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            testExecutionLog.add("Started: " + testIdentifier.getDisplayName() + " at " + 
                LocalDateTime.now().format(TIMESTAMP_FORMAT));
        }
    }
    
    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testIdentifier.isTest()) {
            String status = testExecutionResult.getStatus().toString();
            String message = testExecutionResult.getThrowable()
                .map(Throwable::getMessage)
                .orElse("No message");
            
            testExecutionLog.add("Finished: " + testIdentifier.getDisplayName() + 
                " - Status: " + status + " at " + LocalDateTime.now().format(TIMESTAMP_FORMAT));
        }
    }
    
    private void recordTestResult(ExtensionContext context, String status, String message) {
        String testClass = context.getTestClass().map(Class::getSimpleName).orElse("Unknown");
        String testMethod = context.getTestMethod().map(method -> method.getName()).orElse("Unknown");
        String displayName = context.getDisplayName();
        
        TestResult result = new TestResult(testClass, testMethod, displayName, status, message, LocalDateTime.now());
        testResults.put(testClass + "." + testMethod, result);
    }
    
    private void generateDocumentation() {
        try {
            createDocsDirectory();
            generateSummaryReport();
            generateDetailedReport();
            generateTestCaseDocumentation();
            generateExecutionLog();
        } catch (IOException e) {
            System.err.println("Failed to generate test documentation: " + e.getMessage());
        }
    }
    
    private void createDocsDirectory() throws IOException {
        Path docsPath = Paths.get(DOCS_DIR);
        if (!Files.exists(docsPath)) {
            Files.createDirectories(docsPath);
        }
    }
    
    private void generateSummaryReport() throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String filename = DOCS_DIR + "/test-summary-" + timestamp + ".md";
        
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write("# Test Execution Summary Report\n\n");
            writer.write("**Generated:** " + LocalDateTime.now().format(TIMESTAMP_FORMAT) + "\n\n");
            
            if (executionStartTime != null && executionEndTime != null) {
                writer.write("**Execution Time:** " + executionStartTime.format(TIMESTAMP_FORMAT) + 
                    " to " + executionEndTime.format(TIMESTAMP_FORMAT) + "\n\n");
            }
            
            // Count results by status
            Map<String, Integer> statusCounts = new HashMap<>();
            testResults.values().forEach(result -> 
                statusCounts.merge(result.status, 1, Integer::sum));
            
            writer.write("## Test Results Summary\n\n");
            writer.write("| Status | Count |\n");
            writer.write("|--------|-------|\n");
            statusCounts.forEach((status, count) -> {
                try {
                    writer.write("| " + status + " | " + count + " |\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            
            writer.write("\n## Test Classes Coverage\n\n");
            Map<String, List<TestResult>> resultsByClass = new HashMap<>();
            testResults.values().forEach(result -> 
                resultsByClass.computeIfAbsent(result.testClass, k -> new ArrayList<>()).add(result));
            
            resultsByClass.forEach((className, results) -> {
                try {
                    writer.write("### " + className + "\n");
                    writer.write("- Total tests: " + results.size() + "\n");
                    writer.write("- Passed: " + results.stream().mapToInt(r -> "PASSED".equals(r.status) ? 1 : 0).sum() + "\n");
                    writer.write("- Failed: " + results.stream().mapToInt(r -> "FAILED".equals(r.status) ? 1 : 0).sum() + "\n");
                    writer.write("- Other: " + results.stream().mapToInt(r -> !"PASSED".equals(r.status) && !"FAILED".equals(r.status) ? 1 : 0).sum() + "\n\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
    
    private void generateDetailedReport() throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String filename = DOCS_DIR + "/test-detailed-" + timestamp + ".md";
        
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write("# Detailed Test Results Report\n\n");
            writer.write("**Generated:** " + LocalDateTime.now().format(TIMESTAMP_FORMAT) + "\n\n");
            
            Map<String, List<TestResult>> resultsByClass = new HashMap<>();
            testResults.values().forEach(result -> 
                resultsByClass.computeIfAbsent(result.testClass, k -> new ArrayList<>()).add(result));
            
            resultsByClass.forEach((className, results) -> {
                try {
                    writer.write("## " + className + "\n\n");
                    
                    for (TestResult result : results) {
                        writer.write("### " + result.displayName + "\n");
                        writer.write("- **Method:** `" + result.testMethod + "`\n");
                        writer.write("- **Status:** " + result.status + "\n");
                        writer.write("- **Execution Time:** " + result.executionTime.format(TIMESTAMP_FORMAT) + "\n");
                        
                        if (result.message != null && !result.message.isEmpty()) {
                            writer.write("- **Message:** " + result.message + "\n");
                        }
                        writer.write("\n");
                    }
                    writer.write("\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
    
    private void generateTestCaseDocumentation() throws IOException {
        String filename = DOCS_DIR + "/test-cases-documentation.md";
        
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write("# Test Cases Documentation\n\n");
            writer.write("**Last Updated:** " + LocalDateTime.now().format(TIMESTAMP_FORMAT) + "\n\n");
            writer.write("This document provides comprehensive documentation of all test cases in the project.\n\n");
            
            Map<String, List<TestResult>> resultsByClass = new HashMap<>();
            testResults.values().forEach(result -> 
                resultsByClass.computeIfAbsent(result.testClass, k -> new ArrayList<>()).add(result));
            
            resultsByClass.forEach((className, results) -> {
                try {
                    writer.write("## " + className + "\n\n");
                    writer.write("**Purpose:** Testing functionality of " + className.replace("Test", "") + "\n\n");
                    
                    writer.write("### Test Methods\n\n");
                    writer.write("| Test Method | Display Name | Purpose |\n");
                    writer.write("|-------------|--------------|----------|\n");
                    
                    for (TestResult result : results) {
                        String purpose = generateTestPurpose(result.testMethod, result.displayName);
                        writer.write("| `" + result.testMethod + "` | " + result.displayName + " | " + purpose + " |\n");
                    }
                    writer.write("\n");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
    
    private void generateExecutionLog() throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String filename = DOCS_DIR + "/execution-log-" + timestamp + ".txt";
        
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write("Test Execution Log\n");
            writer.write("==================\n\n");
            
            for (String logEntry : testExecutionLog) {
                writer.write(logEntry + "\n");
            }
        }
    }
    
    private String generateTestPurpose(String methodName, String displayName) {
        // Generate purpose based on method name patterns
        if (methodName.contains("Success") || methodName.contains("Valid")) {
            return "Validates successful operation with valid inputs";
        } else if (methodName.contains("Fail") || methodName.contains("Invalid") || methodName.contains("Error")) {
            return "Tests error handling with invalid inputs";
        } else if (methodName.contains("Null") || methodName.contains("Empty")) {
            return "Tests behavior with null or empty inputs";
        } else if (methodName.contains("Security") || methodName.contains("Auth")) {
            return "Validates security and authentication mechanisms";
        } else if (methodName.contains("Performance") || methodName.contains("Load")) {
            return "Tests performance and load handling";
        } else {
            return "Tests specific functionality as described in display name";
        }
    }
    
    private static class TestResult {
        final String testClass;
        final String testMethod;
        final String displayName;
        final String status;
        final String message;
        final LocalDateTime executionTime;
        
        TestResult(String testClass, String testMethod, String displayName, 
                  String status, String message, LocalDateTime executionTime) {
            this.testClass = testClass;
            this.testMethod = testMethod;
            this.displayName = displayName;
            this.status = status;
            this.message = message;
            this.executionTime = executionTime;
        }
    }
}