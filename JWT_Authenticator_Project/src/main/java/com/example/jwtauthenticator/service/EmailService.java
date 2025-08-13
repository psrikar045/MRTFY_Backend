package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.User;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.activation.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;
    
    @Autowired
    private FreeMarkerConfigurer freeMarkerConfigurer;
    
    @Value("${spring.mail.username}")
    private String fromEmail;
    @Value("${app.base-url}")
    private String baseUrl;

    public void sendEmail(String to, String subject, String text) {
        // Validate inputs
        if (to == null || to.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address cannot be null or empty");
        }
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be null or empty");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Email body cannot be null or empty");
        }
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            logger.info("Plain text email sent successfully to: {}", to);
        } catch (Exception e) {
            logger.error("Failed to send plain text email to: {}", to, e);
            // Don't rethrow - we don't want to break the application flow if email fails
        }
    }
    
    public void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        // Validate inputs
        if (to == null || to.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address cannot be null or empty");
        }
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be null or empty");
        }
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            throw new IllegalArgumentException("HTML content cannot be null or empty");
        }
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            logger.info("HTML email sent successfully to: {}", to);
        } catch (Exception e) {
            logger.error("Failed to send HTML email to: {}", to, e);
            throw e; // Rethrow so the calling method can handle it
        }
    }
    
    public void sendEmailWithTemplate(String to, String subject, String templateName, Map<String, Object> model) {
        // Validate inputs
        if (to == null || to.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address cannot be null or empty");
        }
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be null or empty");
        }
        if (templateName == null || templateName.trim().isEmpty()) {
            throw new IllegalArgumentException("Template name cannot be null or empty");
        }
        if (model == null) {
            model = new HashMap<>();
        }
        
        try {
            Configuration configuration = freeMarkerConfigurer.getConfiguration();
            Template template = configuration.getTemplate(templateName + ".ftl");
            
            String emailContent = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
            
            sendHtmlEmail(to, subject, emailContent);
            logger.info("Template email sent successfully to: {} using template: {}", to, templateName);
        } catch (IOException e) {
            logger.error("Template not found: {} for email to: {}", templateName, to, e);
            throw new RuntimeException("Failed to send email with template: " + templateName, e);
        } catch (TemplateException e) {
            logger.error("Template processing error for template: {} and email to: {}", templateName, to, e);
            throw new RuntimeException("Failed to send email with template: " + templateName, e);
        } catch (MessagingException e) {
            logger.error("Messaging error when sending template email to: {} using template: {}", to, templateName, e);
            throw new RuntimeException("Failed to send email with template: " + templateName, e);
        } catch (Exception e) {
            logger.error("Unexpected error when sending template email to: {} using template: {}", to, templateName, e);
            throw new RuntimeException("Failed to send email with template: " + templateName, e);
        }
    }
    
    public void sendVerificationEmail(String to, String username, String verificationToken, String baseUrl) {
        logger.info("Sending verification email to: {}", to);
        try {
            User user = new User();
            user.setUsername(username);
            user.setEmail(to);
            user.setFirstName(username); // Using username as firstName if not available
            
            String verificationUrl = buildUrl("/auth/verify-email?token=" + verificationToken);
            logger.debug("Verification URL: {}", verificationUrl);
            Map<String, Object> model = new HashMap<>();
            model.put("user", user);
            model.put("confirmationUrl", verificationUrl);
            model.put("baseUrl", baseUrl);
            model.put("loginUrl", buildUrl("/login"));
            
            Configuration configuration = freeMarkerConfigurer.getConfiguration();
            Template template = configuration.getTemplate("registration-confirmation-rivo9.ftl");
            
            String emailContent = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
            
            sendHtmlEmail(to, "Welcome to RIVO9 - Activate Your Account", emailContent);
            logger.info("Verification email sent successfully to: {}", to);
        } catch (IOException e) {
            logger.error("Template error when sending verification email to {}: {}", to, e.getMessage());
            sendFallbackVerificationEmail(to, username, verificationToken, baseUrl);
        } catch (TemplateException e) {
            logger.error("Template processing error when sending verification email to {}: {}", to, e.getMessage());
            sendFallbackVerificationEmail(to, username, verificationToken, baseUrl);
        } catch (MessagingException e) {
            logger.error("Messaging error when sending verification email to {}: {}", to, e.getMessage());
            sendFallbackVerificationEmail(to, username, verificationToken, baseUrl);
        } catch (Exception e) {
            logger.error("Unexpected error when sending verification email to {}: {}", to, e.getMessage());
            sendFallbackVerificationEmail(to, username, verificationToken, baseUrl);
        }
    }
    
    private void sendFallbackVerificationEmail(String to, String username, String verificationToken, String baseUrl) {
        logger.info("Sending fallback plain text verification email to: {}", to);
        String subject = "Welcome to RIVO9 - Activate Your Account";
        String verificationUrl = baseUrl + "/auth/verify-email?token=" + verificationToken;
        
        String emailBody = """
            Welcome to RIVO9!
            
            Hi %s,
            
            Thank you for joining RIVO9! You're just one click away from accessing our powerful brand intelligence API platform.
            
            🚀 Your FREE Account Includes:
            ✅ 100 API calls per month
            ✅ Auto-generated API key (rivo9 prefix)
            ✅ Brand intelligence extraction
            ✅ Community support access
            
            Activate your account: %s
            
            What happens next?
            1. Click the activation link above
            2. Receive your activation success email
            3. Get your FREE API key automatically
            4. Start making API calls immediately!
            
            This link expires in 24 hours for security.
            
            If you didn't create this account, please ignore this email.
            
            Best regards,
            RIVO9 Team
            
            --
            RIVO9 Technologies
            Building intelligent brand APIs for the modern web
            https://rivo9.com | support@rivo9.com
            """.formatted(username, verificationUrl);
            
        sendEmail(to, subject, emailBody);
    }
    
    public void sendPasswordResetCode(String to, String username, String verificationCode) {
        logger.info("Sending password reset code to: {}", to);
        try {
            User user = new User();
            user.setUsername(username);
            user.setEmail(to);
            user.setFirstName(username); // Using username as firstName if not available
            
            Map<String, Object> model = new HashMap<>();
            model.put("user", user);
            model.put("verificationCode", verificationCode);
            model.put("baseUrl", baseUrl);
            
            Configuration configuration = freeMarkerConfigurer.getConfiguration();
            Template template = configuration.getTemplate("password-reset-code.ftl");
            
            String emailContent = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
            
            sendHtmlEmail(to, "Reset Your Marketify Password", emailContent);
            logger.info("Password reset code email sent successfully to: {}", to);
        } catch (IOException e) {
            logger.error("Template error when sending password reset code to {}: {}", to, e.getMessage());
            sendFallbackPasswordResetCode(to, username, verificationCode);
        } catch (TemplateException e) {
            logger.error("Template processing error when sending password reset code to {}: {}", to, e.getMessage());
            sendFallbackPasswordResetCode(to, username, verificationCode);
        } catch (MessagingException e) {
            logger.error("Messaging error when sending password reset code to {}: {}", to, e.getMessage());
            sendFallbackPasswordResetCode(to, username, verificationCode);
        } catch (Exception e) {
            logger.error("Unexpected error when sending password reset code to {}: {}", to, e.getMessage());
            sendFallbackPasswordResetCode(to, username, verificationCode);
        }
    }
    
    private void sendFallbackPasswordResetCode(String to, String username, String verificationCode) {
        logger.info("Sending fallback plain text password reset code to: {}", to);
        String subject = "Password Reset Verification Code - Marketify";
        
        String emailBody = """
            Hello %s,
            
            You have requested to reset your password. Use the following verification code:
            
            %s
            
            This code will expire in 10 minutes for security reasons.
            
            If you didn't request this password reset, please ignore this email.
            
            Best regards,
            Marketify Team
            """.formatted(username, verificationCode);
            
        sendEmail(to, subject, emailBody);
    }
    
    public void sendPasswordResetConfirmation(String to, String username) {
        logger.info("Sending password reset confirmation to: {}", to);
        try {
            User user = new User();
            user.setUsername(username);
            user.setFirstName(username); // Using username as firstName if not available
            user.setEmail(to);
            
            Map<String, Object> model = new HashMap<>();
            model.put("user", user);
            model.put("baseUrl", baseUrl);
            
            Configuration configuration = freeMarkerConfigurer.getConfiguration();
            Template template = configuration.getTemplate("password-reset-confirmation.ftl");
            
            String emailContent = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
            
            sendHtmlEmail(to, "Your Marketify Password Has Been Reset", emailContent);
            logger.info("Password reset confirmation email sent successfully to: {}", to);
        } catch (IOException e) {
            logger.error("Template error when sending password reset confirmation to {}: {}", to, e.getMessage());
            sendFallbackPasswordResetConfirmation(to, username);
        } catch (TemplateException e) {
            logger.error("Template processing error when sending password reset confirmation to {}: {}", to, e.getMessage());
            sendFallbackPasswordResetConfirmation(to, username);
        } catch (MessagingException e) {
            logger.error("Messaging error when sending password reset confirmation to {}: {}", to, e.getMessage());
            sendFallbackPasswordResetConfirmation(to, username);
        } catch (Exception e) {
            logger.error("Unexpected error when sending password reset confirmation to {}: {}", to, e.getMessage());
            sendFallbackPasswordResetConfirmation(to, username);
        }
    }
    
    private void sendFallbackPasswordResetConfirmation(String to, String username) {
        logger.info("Sending fallback plain text password reset confirmation to: {}", to);
        String subject = "Password Reset Confirmation - Marketify";
        
        String emailBody = """
            Hello %s,
            
            Your Marketify account password has been successfully reset.
            
            You can now log in with your new password.
            
            If you didn't reset your password, please contact us immediately at support@marketify.com.
            
            Best regards,
            Marketify Team
            """.formatted(username);
            
        sendEmail(to, subject, emailBody);
    }
    
    public void sendPasswordResetEmail(String to, String username, String resetToken, String baseUrl) {
        logger.info("Sending password reset email to: {}", to);
        try {
String resetUrl = buildUrl("/auth/reset-password?token=" + resetToken);
            logger.debug("Reset URL: {}", resetUrl);
            
            Map<String, Object> model = new HashMap<>();
            model.put("username", username);
            model.put("resetUrl", resetUrl);
            model.put("baseUrl", baseUrl);
            
            Configuration configuration = freeMarkerConfigurer.getConfiguration();
            Template template = configuration.getTemplate("password-reset-link.ftl");
            
            String emailContent = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
            
            sendHtmlEmail(to, "Reset Your Marketify Password", emailContent);
            logger.info("Password reset email sent successfully to: {}", to);
        } catch (IOException e) {
            logger.error("Template error when sending password reset email to {}: {}", to, e.getMessage());
            sendFallbackPasswordResetEmail(to, username, resetToken, baseUrl);
        } catch (TemplateException e) {
            logger.error("Template processing error when sending password reset email to {}: {}", to, e.getMessage());
            sendFallbackPasswordResetEmail(to, username, resetToken, baseUrl);
        } catch (MessagingException e) {
            logger.error("Messaging error when sending password reset email to {}: {}", to, e.getMessage());
            sendFallbackPasswordResetEmail(to, username, resetToken, baseUrl);
        } catch (Exception e) {
            logger.error("Unexpected error when sending password reset email to {}: {}", to, e.getMessage());
            sendFallbackPasswordResetEmail(to, username, resetToken, baseUrl);
        }
    }
    
    private void sendFallbackPasswordResetEmail(String to, String username, String resetToken, String baseUrl) {
        logger.info("Sending fallback plain text password reset email to: {}", to);
        String subject = "Password Reset - Marketify";
String resetUrl = buildUrl("/auth/reset-password?token=" + resetToken);        
        String emailBody = """
            Hello %s,
            
            You have requested to reset your Marketify password. Click the link below to reset your password:
            
            %s
            
            This link will expire in 1 hour for security reasons.
            
            If you didn't request this password reset, please ignore this email.
            
            Best regards,
            Marketify Team
            """.formatted(username, resetUrl);
            
        sendEmail(to, subject, emailBody);
    }
    
    public void sendTwoFactorAuthEmail(String to, String username, String tfaCode) {
        logger.info("Sending 2FA code email to: {}", to);
        try {
            Map<String, Object> model = new HashMap<>();
            model.put("username", username);
            model.put("tfaCode", tfaCode);
            model.put("baseUrl", baseUrl);
            
            Configuration configuration = freeMarkerConfigurer.getConfiguration();
            Template template = configuration.getTemplate("2fa-code-email.ftl");
            
            String emailContent = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
            
            sendHtmlEmail(to, "Your Two-Factor Authentication Code", emailContent);
            logger.info("2FA code email sent successfully to: {}", to);
        } catch (IOException e) {
            logger.error("Template error when sending 2FA code email to {}: {}", to, e.getMessage());
            sendFallback2FAEmail(to, username, tfaCode);
        } catch (TemplateException e) {
            logger.error("Template processing error when sending 2FA code email to {}: {}", to, e.getMessage());
            sendFallback2FAEmail(to, username, tfaCode);
        } catch (MessagingException e) {
            logger.error("Messaging error when sending 2FA code email to {}: {}", to, e.getMessage());
            sendFallback2FAEmail(to, username, tfaCode);
        } catch (Exception e) {
            logger.error("Unexpected error when sending 2FA code email to {}: {}", to, e.getMessage());
            sendFallback2FAEmail(to, username, tfaCode);
        }
    }
    
    private void sendFallback2FAEmail(String to, String username, String tfaCode) {
        logger.info("Sending fallback plain text 2FA code email to: {}", to);
        String subject = "Your Two-Factor Authentication Code";
        
        String emailBody = """
            Hello %s,
            
            Your two-factor authentication code is: %s
            
            This code will expire in 5 minutes for security reasons.
            
            If you didn't request this code, please ignore this email.
            
            Best regards,
            Marketify Team
            """.formatted(username, tfaCode);
            
        sendEmail(to, subject, emailBody);
    }
    
    public void sendNotificationEmail(String to, String username, String subject, String message) {
        logger.info("Sending notification email to: {}", to);
        try {
            Map<String, Object> model = new HashMap<>();
            model.put("username", username);
            model.put("message", message);
            model.put("baseUrl", baseUrl);
            
            Configuration configuration = freeMarkerConfigurer.getConfiguration();
            Template template = configuration.getTemplate("notification-email.ftl");
            
            String emailContent = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
            
            sendHtmlEmail(to, subject, emailContent);
            logger.info("Notification email sent successfully to: {}", to);
        } catch (IOException e) {
            logger.error("Template error when sending notification email to {}: {}", to, e.getMessage());
            sendFallbackNotificationEmail(to, username, subject, message);
        } catch (TemplateException e) {
            logger.error("Template processing error when sending notification email to {}: {}", to, e.getMessage());
            sendFallbackNotificationEmail(to, username, subject, message);
        } catch (MessagingException e) {
            logger.error("Messaging error when sending notification email to {}: {}", to, e.getMessage());
            sendFallbackNotificationEmail(to, username, subject, message);
        } catch (Exception e) {
            logger.error("Unexpected error when sending notification email to {}: {}", to, e.getMessage());
            sendFallbackNotificationEmail(to, username, subject, message);
        }
    }
    
    private void sendFallbackNotificationEmail(String to, String username, String subject, String message) {
        logger.info("Sending fallback plain text notification email to: {}", to);
        
        String emailBody = """
            Hello %s,
            
            %s
            
            Best regards,
            Marketify Team
            """.formatted(username, message);
            
        sendEmail(to, subject, emailBody);
    }
    
    public boolean validateEmailAddress(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        // Basic email validation regex
        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        return email.matches(emailRegex);
    }
    
    /**
     * Send bulk emails to multiple recipients
     * @param recipients Array of email addresses
     * @param subject Email subject
     * @param text Email body
     */
    public void sendBulkEmail(String[] recipients, String subject, String text) {
        if (recipients == null || recipients.length == 0) {
            throw new IllegalArgumentException("Recipients list cannot be empty");
        }
        
        for (String recipient : recipients) {
            if (recipient != null && !recipient.trim().isEmpty()) {
                sendEmail(recipient, subject, text);
            }
        }
        logger.info("Bulk email sent to {} recipients", recipients.length);
    }
    
    /**
     * Check if email service is enabled and configured
     * @return true if email service is enabled, false otherwise
     */
    public void sendActivationSuccessEmail(String to, String username, Map<String, Object> model) {
        logger.info("Sending activation success email with API key to: {}", to);
        logger.debug("Model data - User: {}, ApiKey: {}, UserPlan: {}", 
                    model.get("user") != null, 
                    model.get("apiKey") != null, 
                    model.get("userPlan") != null);
        try {
            Configuration configuration = freeMarkerConfigurer.getConfiguration();
            Template template = configuration.getTemplate("activation-success-with-apikey.ftl");
            
            String emailContent = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
            
            sendHtmlEmail(to, "🎉 Welcome to RIVO9 - Your Account is Ready!", emailContent);
            logger.info("Activation success email sent successfully to: {}", to);
        } catch (IOException e) {
            logger.error("Template I/O error when sending activation success email to {}: {}", to, e.getMessage());
            sendFallbackActivationSuccessEmail(to, username, (com.example.jwtauthenticator.dto.ApiKeyGeneratedResponseDTO) model.get("apiKey"));
        } catch (freemarker.template.TemplateException e) {
            logger.error("Template processing error when sending activation success email to {}: {}", to, e.getMessage());
            sendFallbackActivationSuccessEmail(to, username, (com.example.jwtauthenticator.dto.ApiKeyGeneratedResponseDTO) model.get("apiKey"));
        } catch (MessagingException e) {
            logger.error("Messaging error when sending activation success email to {}: {}", to, e.getMessage());
            sendFallbackActivationSuccessEmail(to, username, (com.example.jwtauthenticator.dto.ApiKeyGeneratedResponseDTO) model.get("apiKey"));
        } catch (Exception e) {
            logger.error("Unexpected error when sending activation success email to {}: {}", to, e.getMessage());
            sendFallbackActivationSuccessEmail(to, username, (com.example.jwtauthenticator.dto.ApiKeyGeneratedResponseDTO) model.get("apiKey"));
        }
    }

    private void sendFallbackActivationSuccessEmail(String to, String username, com.example.jwtauthenticator.dto.ApiKeyGeneratedResponseDTO apiKey) {
        logger.info("Sending fallback activation success email to: {}", to);
        String subject = "🎉 Welcome to RIVO9 - Your Account is Ready!";
        
        String emailBody = String.format("""
            Hi %s,
            
            Welcome to RIVO9! Your account is now fully activated and ready to use.
            
            ACCOUNT STATUS:
            ✅ Email verified
            ✅ FREE plan activated (100 API calls/month)
            ✅ API key automatically generated
            ✅ Ready to make API calls
            
            YOUR FREE API KEY:
            %s
            
            Keep this key secure! You can view it anytime in your dashboard.
            
            QUICK START:
            1. Copy your API key from above
            2. Read our API documentation: %s/docs
            3. Make your first brand extraction call
            4. Monitor usage in your dashboard: %s/dashboard
            
            Login to your account: %s/login
            
            Need help? Contact us at support@rivo9.com
            
            --
            RIVO9 Technologies
            Building intelligent brand APIs for the modern web
            https://rivo9.com
            """, 
            username, 
            apiKey != null ? apiKey.getKeyValue() : "[API Key Generation Failed]",
            baseUrl,
            baseUrl,
            baseUrl
        );
        
        sendEmail(to, subject, emailBody);
    }

    public boolean isEmailServiceEnabled() {
        return mailSender != null && fromEmail != null && !fromEmail.trim().isEmpty();
    }
    
    /**
     * Test method to verify email configuration is working correctly
     * @param to Email address to send test email to
     * @return true if email was sent successfully, false otherwise
     */
    public boolean testEmailConfiguration(String to) {
        logger.info("Testing email configuration by sending test email to: {}", to);
        try {
            String subject = "Marketify Email Configuration Test";
            String text = "This is a test email to verify that the Marketify email configuration is working correctly.";
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            
            mailSender.send(message);
            logger.info("Test email sent successfully to: {}", to);
            return true;
        } catch (Exception e) {
            logger.error("Failed to send test email to {}: {}", to, e.getMessage());
            return false;
        }
    }
   
    /**
     * Helper method to properly construct URLs by avoiding double slashes
     */
    public String buildUrl(String path) {
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String cleanPath = path.startsWith("/") ? path : "/" + path;
        return cleanBaseUrl + cleanPath;
    }
}
