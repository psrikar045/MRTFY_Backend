package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.util.TestDataFactory;
import com.example.jwtauthenticator.entity.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.test.util.ReflectionTestUtils;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.MessagingException;

import java.io.StringWriter;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private FreeMarkerConfigurer freeMarkerConfigurer;
    
    @Mock
    private Configuration freemarkerConfig;

    @Mock
    private Template template;

    @Mock
    private MimeMessage mimeMessage;

    @Mock
    private MimeMessageHelper mimeMessageHelper;

    @InjectMocks
    private EmailService emailService;

    private User testUser;
    private String testEmail;
    private String testSubject;
    private String testBody;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createTestUser();
        testEmail = "test@example.com";
        testSubject = "Test Subject";
        testBody = "Test email body";
        
        // Set up email configuration using reflection
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@testbrand.com");
        ReflectionTestUtils.setField(emailService, "baseUrl", "http://localhost:8080/myapp");
        ReflectionTestUtils.setField(emailService, "freeMarkerConfigurer", freeMarkerConfigurer);
        
        // Mock FreeMarker configuration - use lenient to avoid unnecessary stubbing errors
        lenient().when(freeMarkerConfigurer.getConfiguration()).thenReturn(freemarkerConfig);
    }

    @Test
    void sendEmail_simpleMessage_success() throws Exception {
        // Arrange
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        // Act
        emailService.sendEmail(testEmail, testSubject, testBody);

        // Assert
        verify(mailSender, times(1)).send(messageCaptor.capture());
        
        SimpleMailMessage capturedMessage = messageCaptor.getValue();
        assertEquals(testEmail, capturedMessage.getTo()[0]);
        assertEquals(testSubject, capturedMessage.getSubject());
        assertEquals(testBody, capturedMessage.getText());
        assertEquals("noreply@testbrand.com", capturedMessage.getFrom());
    }

    @Test
    void sendEmail_withNullEmail_throwsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> emailService.sendEmail(null, testSubject, testBody));
        assertEquals("Email address cannot be null or empty", exception.getMessage());
        
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendEmail_withEmptyEmail_throwsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> emailService.sendEmail("", testSubject, testBody));
        assertEquals("Email address cannot be null or empty", exception.getMessage());
        
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendEmail_withNullSubject_throwsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> emailService.sendEmail(testEmail, null, testBody));
        assertEquals("Subject cannot be null or empty", exception.getMessage());
        
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendEmail_withNullBody_throwsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> emailService.sendEmail(testEmail, testSubject, null));
        assertEquals("Body cannot be null or empty", exception.getMessage());
        
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendEmailWithTemplate_success() throws Exception {
        // Arrange
        String templateName = "verification-email";
        Map<String, Object> model = new HashMap<>();
        model.put("username", testUser.getUsername());
        model.put("verificationUrl", "http://localhost:8080/verify");
        
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(freemarkerConfig.getTemplate("registration-confirmation.ftl")).thenReturn(template);

        // Act
        emailService.sendVerificationEmail(testEmail, testUser.getUsername(), "verification-token", "http://localhost:8080");

        // Assert
        verify(mailSender, times(1)).createMimeMessage();
        verify(freemarkerConfig, times(1)).getTemplate("registration-confirmation.ftl");
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendEmailWithTemplate_templateNotFound_throwsException() throws Exception {
        // Arrange
        String templateName = "nonexistent-template";
        Map<String, Object> model = new HashMap<>();
        
        when(freemarkerConfig.getTemplate(templateName + ".ftl"))
            .thenThrow(new RuntimeException("Template not found"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> emailService.sendEmailWithTemplate(testEmail, testSubject, templateName, model));
        assertEquals("Failed to send email with template: " + templateName, exception.getMessage());
        
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendVerificationEmail_success() throws Exception {
        // Arrange
        String verificationToken = TestDataFactory.createTestVerificationToken();
        String verificationUrl = "http://localhost:8080/api/auth/verify-email?token=" + verificationToken;
        
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(freemarkerConfig.getTemplate("registration-confirmation.ftl")).thenReturn(template);

        // Act
        emailService.sendVerificationEmail(testUser.getEmail(), testUser.getUsername(), "verification-token", "http://localhost:8080");

        // Assert
        verify(mailSender, times(1)).createMimeMessage();
        verify(freemarkerConfig, times(1)).getTemplate("registration-confirmation.ftl");
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendPasswordResetEmail_success() throws Exception {
        // Arrange
        String resetToken = TestDataFactory.createTestPasswordResetToken();
        String resetUrl = "http://localhost:8080/api/auth/reset-password?token=" + resetToken;
        
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(freemarkerConfig.getTemplate("password-reset-email.ftl")).thenReturn(template);
        
        doAnswer(invocation -> {
            StringWriter sw = invocation.getArgument(1);
            sw.write("<html><body>Reset your password</body></html>");
            return null;
        }).when(template).process(any(Map.class), any(StringWriter.class));

        // Act
        emailService.sendPasswordResetEmail(testUser.getEmail(), testUser.getUsername(), "reset-token", "http://localhost:8080");

        // Assert
        verify(mailSender, times(1)).createMimeMessage();
        verify(freemarkerConfig, times(1)).getTemplate("password-reset-email.ftl");
        verify(template, times(1)).process(any(Map.class), any(StringWriter.class));
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendWelcomeEmail_success() throws Exception {
        // Arrange
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(freemarkerConfig.getTemplate("welcome-email.ftl")).thenReturn(template);
        
        doAnswer(invocation -> {
            StringWriter sw = invocation.getArgument(1);
            sw.write("<html><body>Welcome to our platform!</body></html>");
            return null;
        }).when(template).process(any(Map.class), any(StringWriter.class));

        // Act
//        emailService.sendWelcomeEmail(testUser.getEmail(), testUser.getUsername());

        // Assert
        verify(mailSender, times(1)).createMimeMessage();
        verify(freemarkerConfig, times(1)).getTemplate("welcome-email.ftl");
        verify(template, times(1)).process(any(Map.class), any(StringWriter.class));
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendTwoFactorAuthEmail_success() throws Exception {
        // Arrange
        String tfaCode = "123456";
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(freemarkerConfig.getTemplate("2fa-code-email.ftl")).thenReturn(template);
        
        doAnswer(invocation -> {
            StringWriter sw = invocation.getArgument(1);
            sw.write("<html><body>Your 2FA code is: " + tfaCode + "</body></html>");
            return null;
        }).when(template).process(any(Map.class), any(StringWriter.class));

        // Act
        emailService.sendTwoFactorAuthEmail(testUser.getEmail(), testUser.getUsername(), tfaCode);

        // Assert
        verify(mailSender, times(1)).createMimeMessage();
        verify(freemarkerConfig, times(1)).getTemplate("2fa-code-email.ftl");
        verify(template, times(1)).process(any(Map.class), any(StringWriter.class));
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendNotificationEmail_success() throws Exception {
        // Arrange
        String notificationMessage = "Your account has been updated";
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(freemarkerConfig.getTemplate("notification-email.ftl")).thenReturn(template);
        
        doAnswer(invocation -> {
            StringWriter sw = invocation.getArgument(1);
            sw.write("<html><body>" + notificationMessage + "</body></html>");
            return null;
        }).when(template).process(any(Map.class), any(StringWriter.class));

        // Act
        emailService.sendNotificationEmail(testUser.getEmail(), testUser.getUsername(), 
            "Account Update", notificationMessage);

        // Assert
        verify(mailSender, times(1)).createMimeMessage();
        verify(freemarkerConfig, times(1)).getTemplate("notification-email.ftl");
        verify(template, times(1)).process(any(Map.class), any(StringWriter.class));
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void validateEmailAddress_validEmail_success() {
        // Act & Assert
        assertDoesNotThrow(() -> emailService.validateEmailAddress("test@example.com"));
        assertDoesNotThrow(() -> emailService.validateEmailAddress("user.name@domain.co.uk"));
        assertDoesNotThrow(() -> emailService.validateEmailAddress("test123@subdomain.example.com"));
    }

    @Test
    void validateEmailAddress_invalidEmail_throwsException() {
        // Act & Assert
        IllegalArgumentException exception1 = assertThrows(IllegalArgumentException.class, 
            () -> emailService.validateEmailAddress("invalid-email"));
        assertEquals("Invalid email address format", exception1.getMessage());

        IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class, 
            () -> emailService.validateEmailAddress("@domain.com"));
        assertEquals("Invalid email address format", exception2.getMessage());

        IllegalArgumentException exception3 = assertThrows(IllegalArgumentException.class, 
            () -> emailService.validateEmailAddress("test@"));
        assertEquals("Invalid email address format", exception3.getMessage());
    }

    @Test
    void isEmailServiceEnabled_returnsTrue() {
        // Act
        boolean result = emailService.isEmailServiceEnabled();

        // Assert
        assertTrue(result);
    }

    @Test
    void sendEmail_mailSenderThrowsException_throwsRuntimeException() {
        // Arrange
        doThrow(new RuntimeException("Mail server unavailable")).when(mailSender).send(any(SimpleMailMessage.class));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> emailService.sendEmail(testEmail, testSubject, testBody));
        assertEquals("Failed to send email", exception.getMessage());
        assertEquals("Mail server unavailable", exception.getCause().getMessage());
    }

    @Test
    void sendBulkEmail_success() throws Exception {
        // Arrange
        String[] recipients = {"user1@example.com", "user2@example.com", "user3@example.com"};
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        // Act
//        emailService.sendBulkEmail(recipients, testSubject, testBody);

        // Assert
        verify(mailSender, times(3)).send(messageCaptor.capture());
        
        List<SimpleMailMessage> capturedMessages = messageCaptor.getAllValues();
        assertEquals(3, capturedMessages.size());
        
        for (int i = 0; i < recipients.length; i++) {
            assertEquals(recipients[i], capturedMessages.get(i).getTo()[0]);
            assertEquals(testSubject, capturedMessages.get(i).getSubject());
            assertEquals(testBody, capturedMessages.get(i).getText());
        }
    }

    @Test
    void sendBulkEmail_withEmptyRecipients_throwsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> emailService.sendBulkEmail(new String[0], testSubject, testBody));
        assertEquals("Recipients list cannot be empty", exception.getMessage());
        
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendBulkEmail_withValidRecipients_sendsToAll() {
        // Arrange
        String[] recipients = {"user1@example.com", "user2@example.com", "user3@example.com"};

        // Act
        emailService.sendBulkEmail(recipients, testSubject, testBody);

        // Assert
        verify(mailSender, times(3)).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendBulkEmail_withNullRecipients_throwsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> emailService.sendBulkEmail(null, testSubject, testBody));
        assertEquals("Recipients list cannot be empty", exception.getMessage());
        
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendBulkEmail_withMixedValidInvalidRecipients_sendsOnlyToValid() {
        // Arrange
        String[] recipients = {"valid@example.com", "", null, "another@example.com", "   "};

        // Act
        emailService.sendBulkEmail(recipients, testSubject, testBody);

        // Assert - Should only send to 2 valid recipients
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void testEmailConfiguration_success() {
        // Act
        boolean result = emailService.testEmailConfiguration(testEmail);

        // Assert
        assertTrue(result);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void testEmailConfiguration_failure() {
        // Arrange
        doThrow(new RuntimeException("Mail server down")).when(mailSender).send(any(SimpleMailMessage.class));

        // Act
        boolean result = emailService.testEmailConfiguration(testEmail);

        // Assert
        assertFalse(result);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void isEmailServiceEnabled_withNullMailSender_returnsFalse() {
        // Arrange
        EmailService emailServiceWithNullSender = new EmailService();
        ReflectionTestUtils.setField(emailServiceWithNullSender, "mailSender", null);
        ReflectionTestUtils.setField(emailServiceWithNullSender, "fromEmail", "test@example.com");

        // Act
        boolean result = emailServiceWithNullSender.isEmailServiceEnabled();

        // Assert
        assertFalse(result);
    }

    @Test
    void isEmailServiceEnabled_withEmptyFromEmail_returnsFalse() {
        // Arrange
        ReflectionTestUtils.setField(emailService, "fromEmail", "");

        // Act
        boolean result = emailService.isEmailServiceEnabled();

        // Assert
        assertFalse(result);
    }
}