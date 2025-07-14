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
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            // Add logo as inline attachment
            try {
                // Try multiple possible locations for the logo
                String[] logoPaths = {
                    "static/images/logo_new.svg",
                    "static/images/logo.svg",
                    "static/logo.svg",
                    "static/images/logo.png",
                    "static/logo.png"
                };
                
                boolean logoAttached = false;
                for (String path : logoPaths) {
                    org.springframework.core.io.Resource logoResource = 
                        new org.springframework.core.io.ClassPathResource(path);
                    logger.debug("Checking logo path: {} - exists: {}", path, logoResource.exists());
                    if (logoResource.exists()) {
                        helper.addInline("logo", logoResource);
                        logger.info("Logo attached successfully from path: {}", path);
                        logoAttached = true;
                        break;
                    }
                }
                
                if (!logoAttached) {
                    // Create the Marketify logo as a DataSource fallback
                    logger.warn("No logo file found. Using inline SVG logo fallback.");
                    
                    try {
                        final String logoText = "<svg viewBox=\"0 0 700 200\" xmlns=\"http://www.w3.org/2000/svg\">" +
                                               "<rect width=\"700\" height=\"200\" fill=\"#0C0C30\"/>" +
                                               "<g transform=\"translate(40,40)\" fill=\"url(#grad4)\">" +
                                               "<rect x=\"0\" y=\"40\" width=\"8\" height=\"8\" rx=\"2\"/>" +
                                               "<rect x=\"10\" y=\"30\" width=\"8\" height=\"8\" rx=\"2\"/>" +
                                               "<rect x=\"20\" y=\"20\" width=\"8\" height=\"8\" rx=\"2\"/>" +
                                               "<rect x=\"30\" y=\"10\" width=\"8\" height=\"8\" rx=\"2\"/>" +
                                               "<rect x=\"40\" y=\"0\" width=\"8\" height=\"8\" rx=\"2\"/>" +
                                               "<rect x=\"50\" y=\"10\" width=\"8\" height=\"8\" rx=\"2\"/>" +
                                               "<rect x=\"60\" y=\"20\" width=\"8\" height=\"8\" rx=\"2\"/>" +
                                               "<rect x=\"70\" y=\"30\" width=\"8\" height=\"8\" rx=\"2\"/>" +
                                               "</g>" +
                                               "<text x=\"160\" y=\"90\" font-family=\"Orbitron, sans-serif\" font-size=\"48\" fill=\"#B3F5FF\">MARKETIFY</text>" +
                                               "<text x=\"160\" y=\"140\" font-family=\"Arial, sans-serif\" font-size=\"20\" fill=\"#00D0FF\" letter-spacing=\"4\">BRAND ON DEMAND</text>" +
                                               "<defs>" +
                                               "<linearGradient id=\"grad4\" x1=\"0\" y1=\"0\" x2=\"100\" y2=\"100\">" +
                                               "<stop stop-color=\"#FF7A18\"/>" +
                                               "<stop offset=\"1\" stop-color=\"#AF002D\"/>" +
                                               "</linearGradient>" +
                                               "</defs>" +
                                               "</svg>";
                        
                        DataSource dataSource = new DataSource() {
                            @Override
                            public InputStream getInputStream() throws IOException {
                                return new ByteArrayInputStream(logoText.getBytes(StandardCharsets.UTF_8));
                            }
                            
                            @Override
                            public OutputStream getOutputStream() throws IOException {
                                throw new UnsupportedOperationException("Read-only data source");
                            }
                            
                            @Override
                            public String getContentType() {
                                return "image/svg+xml";
                            }
                            
                            @Override
                            public String getName() {
                                return "logo.svg";
                            }
                        };
                        
                        helper.addInline("logo", dataSource);
                        logger.info("Successfully added inline SVG Marketify logo as fallback");
                        logoAttached = true;
                    } catch (Exception e) {
                        logger.error("Failed to create fallback logo: {}", e.getMessage());
                    }
                }
                
                if (!logoAttached) {
                    logger.error("No logo could be attached to email");
                }
            } catch (Exception e) {
                // Log but continue if logo can't be attached
                logger.warn("Could not attach logo: {}", e.getMessage());
            }
            
            mailSender.send(message);
            logger.info("HTML email sent successfully to: {}", to);
        } catch (Exception e) {
            logger.error("Failed to send HTML email to: {}", to, e);
            throw e; // Rethrow so the calling method can handle it
        }
    }
    
    public void sendVerificationEmail(String to, String username, String verificationToken, String baseUrl) {
        logger.info("Sending verification email to: {}", to);
        try {
            User user = new User();
            user.setUsername(username);
            user.setEmail(to);
            user.setFirstName(username); // Using username as firstName if not available
            
            String verificationUrl = baseUrl + "/auth/verify-email?token=" + verificationToken;
            logger.debug("Verification URL: {}", verificationUrl);
            
            Map<String, Object> model = new HashMap<>();
            model.put("user", user);
            model.put("confirmationUrl", verificationUrl);
            model.put("baseUrl", baseUrl);
            
            Configuration configuration = freeMarkerConfigurer.getConfiguration();
            Template template = configuration.getTemplate("registration-confirmation.ftl");
            
            String emailContent = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
            
            sendHtmlEmail(to, "Welcome to Marketify - Confirm Your Email", emailContent);
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
        String subject = "Email Verification - Marketify";
        String verificationUrl = baseUrl + "/auth/verify-email?token=" + verificationToken;
        
        String emailBody = """
            Hello %s,
            
            Welcome to Marketify! Please verify your email address by clicking the link below:
            
            %s
            
            This link will expire in 24 hours for security reasons.
            
            If you didn't create this account, please ignore this email.
            
            Best regards,
            Marketify Team
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
            String resetUrl = baseUrl + "/auth/reset-password?token=" + resetToken;
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
        String resetUrl = baseUrl + "/auth/reset-password?token=" + resetToken;
        
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
}
