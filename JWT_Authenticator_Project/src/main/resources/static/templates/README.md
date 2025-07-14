# Email Templates for MRTFY

This directory contains the email templates used by the MRTFY application for various email notifications.

## Templates

1. **registration-confirmation.ftl** - Email sent to users to verify their email address after registration
2. **password-reset-code.ftl** - Email sent to users with a verification code for password reset
3. **password-reset-confirmation.ftl** - Email sent to users after their password has been reset
4. **password-reset-link.ftl** - Email sent to users with a link to reset their password

## Configuration

The email templates use FreeMarker as the templating engine. The configuration is defined in:

1. `application.properties` - Contains the FreeMarker and email server configuration
2. `FreemarkerConfig.java` - Contains the FreeMarker configuration bean

### Email Configuration

To configure the email server, update the following properties in `application.properties`:

```properties
# Email Configuration
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

For Gmail, you need to use an App Password instead of your regular password. To generate an App Password:

1. Go to your Google Account settings
2. Navigate to Security > 2-Step Verification
3. At the bottom, click on "App passwords"
4. Select "Mail" as the app and "Other" as the device
5. Enter "MRTFY" as the name and click "Generate"
6. Use the generated password in the `spring.mail.password` property

### FreeMarker Configuration

The FreeMarker configuration is defined in `application.properties`:

```properties
# FreeMarker Configuration
spring.freemarker.template-loader-path=classpath:/static/templates/
spring.freemarker.suffix=.ftl
spring.freemarker.cache=false
spring.freemarker.charset=UTF-8
spring.freemarker.check-template-location=true
spring.freemarker.content-type=text/html
```

## Testing

You can test the email functionality using the `EmailTestController`. The following endpoints are available:

1. `GET /api/test/email/test?email=your-email@example.com` - Test the email configuration
2. `GET /api/test/email/verification?email=your-email@example.com` - Test the verification email template
3. `GET /api/test/email/reset-code?email=your-email@example.com` - Test the password reset code email template
4. `GET /api/test/email/reset-link?email=your-email@example.com` - Test the password reset link email template
5. `GET /api/test/email/reset-confirmation?email=your-email@example.com` - Test the password reset confirmation email template

**Note:** The test controller should be disabled in production.

## Customization

To customize the email templates:

1. Edit the `.ftl` files in this directory
2. Update the styles in the `<style>` section of each template
3. Update the content in the `<div class="content">` section of each template
4. Update the footer in the `<div class="footer">` section of each template

The logo is referenced as `cid:logo` in the templates and is automatically attached as an inline attachment by the `EmailService`.

## Troubleshooting

If you encounter issues with the email functionality:

1. Check the application logs for error messages
2. Verify that the email configuration is correct
3. Ensure that the SMTP server is accessible from the application
4. Check that the templates are correctly formatted
5. Verify that the email templates contain the embedded HTML/CSS logo (no external logo files needed)