package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.QuotaResetAudit;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.enums.UserPlan;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.scheduler.MonthlyQuotaResetScheduler.QuotaResetResult;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Email notification service for quota reset operations
 * Sends professional emails to users and administrators about quota resets
 * 
 * @author RIVO9 Development Team
 * @version 1.0
 * @since Java 21
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuotaResetNotificationService {
    
    private final JavaMailSender mailSender;
    private final Configuration freemarkerConfig;
    private final UserRepository userRepository;
    
    @Value("${spring.mail.username}")
    private String fromEmail;
    
    @Value("${app.company.name:RIVO9}")
    private String companyName;
    
    @Value("${app.company.support-email:support@rivo9.com}")
    private String supportEmail;
    
    @Value("${app.company.website:https://rivo9.com}")
    private String companyWebsite;
    
    @Value("${app.admin.notification-emails:admin@rivo9.com}")
    private List<String> adminEmails;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM dd, yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' HH:mm:ss 'UTC'");
    
    /**
     * Send quota reset notification to all users
     */
    @Async
    public CompletableFuture<Void> sendUserNotifications(QuotaResetResult result, QuotaResetAudit audit) {
        log.info("📧 Sending quota reset notifications to users for month: {}", result.getMonthYear());
        
        try {
            List<User> activeUsers = userRepository.findByIsActiveTrue();
            
            for (User user : activeUsers) {
                try {
                    sendUserQuotaResetEmail(user, result, audit);
                    Thread.sleep(100); // Rate limiting to avoid overwhelming email server
                } catch (Exception e) {
                    log.error("❌ Failed to send notification to user {}: {}", user.getEmail(), e.getMessage());
                }
            }
            
            log.info("✅ User notifications sent successfully to {} users", activeUsers.size());
            
        } catch (Exception e) {
            log.error("❌ Failed to send user notifications: {}", e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Send admin notification about quota reset completion
     */
    @Async
    public CompletableFuture<Void> sendAdminNotification(QuotaResetResult result, QuotaResetAudit audit) {
        log.info("📧 Sending admin notification for quota reset: {}", result.getMonthYear());
        
        try {
            for (String adminEmail : adminEmails) {
                sendAdminQuotaResetEmail(adminEmail, result, audit);
            }
            
            log.info("✅ Admin notifications sent successfully to {} administrators", adminEmails.size());
            
        } catch (Exception e) {
            log.error("❌ Failed to send admin notifications: {}", e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Send individual user quota reset email
     */
    private void sendUserQuotaResetEmail(User user, QuotaResetResult result, QuotaResetAudit audit) throws Exception {
        Map<String, Object> model = createUserEmailModel(user, result, audit);
        
        String subject = String.format("%s - Your API Quota Has Been Reset for %s", 
            companyName, formatMonthYear(result.getMonthYear()));
        
        String htmlContent = processTemplate("quota-reset-user-notification.ftl", model);
        
        sendEmail(user.getEmail(), subject, htmlContent, true);
        
        log.debug("📧 Quota reset notification sent to user: {} ({})", user.getEmail(), user.getPlan());
    }
    
    /**
     * Send admin quota reset summary email
     */
    private void sendAdminQuotaResetEmail(String adminEmail, QuotaResetResult result, QuotaResetAudit audit) throws Exception {
        Map<String, Object> model = createAdminEmailModel(result, audit);
        
        String subject = String.format("%s Admin - Monthly Quota Reset Summary for %s", 
            companyName, formatMonthYear(result.getMonthYear()));
        
        String htmlContent = processTemplate("quota-reset-admin-summary.ftl", model);
        
        sendEmail(adminEmail, subject, htmlContent, true);
        
        log.debug("📧 Admin summary sent to: {}", adminEmail);
    }
    
    /**
     * Create email model for user notifications
     */
    private Map<String, Object> createUserEmailModel(User user, QuotaResetResult result, QuotaResetAudit audit) {
        Map<String, Object> model = new HashMap<>();
        
        // User information
        model.put("userName", user.getUsername());
        model.put("userEmail", user.getEmail());
        model.put("userPlan", user.getPlan().getDisplayName());
        model.put("quotaLimit", getQuotaLimitForPlan(user.getPlan()));
        
        // Reset information
        model.put("monthYear", formatMonthYear(result.getMonthYear()));
        model.put("resetDate", audit.getResetDate().format(DATE_FORMATTER));
        model.put("resetTime", audit.getExecutionTimestamp().format(DATETIME_FORMATTER));
        model.put("nextResetDate", getNextResetDate(result.getMonthYear()));
        
        // Company information
        model.put("companyName", companyName);
        model.put("supportEmail", supportEmail);
        model.put("companyWebsite", companyWebsite);
        
        // Status
        model.put("resetSuccessful", result.isSuccessful());
        model.put("currentYear", LocalDate.now().getYear());
        
        return model;
    }
    
    /**
     * Create email model for admin notifications
     */
    private Map<String, Object> createAdminEmailModel(QuotaResetResult result, QuotaResetAudit audit) {
        Map<String, Object> model = new HashMap<>();
        
        // Reset statistics
        model.put("monthYear", formatMonthYear(result.getMonthYear()));
        model.put("totalProcessed", result.getTotalProcessed());
        model.put("successCount", result.getSuccessCount());
        model.put("failureCount", result.getFailureCount());
        model.put("skippedCount", result.getSkippedCount());
        model.put("successRate", String.format("%.2f%%", result.getSuccessRate()));
        
        // Execution details
        model.put("executionTime", audit.getExecutionTimestamp().format(DATETIME_FORMATTER));
        model.put("executionDuration", audit.getExecutionDurationFormatted());
        model.put("triggeredBy", audit.getTriggeredBy());
        model.put("executionStatus", audit.getExecutionStatus().getDescription());
        
        // Company information
        model.put("companyName", companyName);
        model.put("supportEmail", supportEmail);
        model.put("currentYear", LocalDate.now().getYear());
        
        // Status indicators
        model.put("resetSuccessful", result.isSuccessful());
        model.put("hasErrors", result.getFailureCount() > 0);
        model.put("nextResetDate", getNextResetDate(result.getMonthYear()));
        
        return model;
    }
    
    /**
     * Process FreeMarker template
     */
    private String processTemplate(String templateName, Map<String, Object> model) throws Exception {
        Template template = freemarkerConfig.getTemplate(templateName);
        StringWriter writer = new StringWriter();
        template.process(model, writer);
        return writer.toString();
    }
    
    /**
     * Send email using JavaMailSender
     */
    private void sendEmail(String to, String subject, String content, boolean isHtml) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        try {
            helper.setFrom(fromEmail, companyName + " API Team");
        } catch (UnsupportedEncodingException | MessagingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, isHtml);
        
        mailSender.send(message);
    }
    
    /**
     * Get quota limit for user plan
     */
    private String getQuotaLimitForPlan(UserPlan plan) {
        return switch (plan) {
            case FREE -> "100 API calls";
            case PRO -> "1,000 API calls";
            case BUSINESS -> "Unlimited API calls";
        };
    }
    
    /**
     * Format month-year for display
     */
    private String formatMonthYear(String monthYear) {
        String[] parts = monthYear.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        return LocalDate.of(year, month, 1).format(DateTimeFormatter.ofPattern("MMMM yyyy"));
    }
    
    /**
     * Get next reset date
     */
    private String getNextResetDate(String currentMonthYear) {
        String[] parts = currentMonthYear.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        
        LocalDate nextReset = LocalDate.of(year, month, 1).plusMonths(1);
        return nextReset.format(DATE_FORMATTER);
    }
}