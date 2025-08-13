<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Welcome to RIVO9 - Your Account is Ready!</title>
</head>
<body style="margin: 0; padding: 0; background-color: #f8f9fa; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, Arial, sans-serif;">
    <!-- Main Container -->
    <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%" style="background-color: #f8f9fa;">
        <tr>
            <td style="padding: 20px 0;">
                <!-- Email Content Table -->
                <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="600" style="margin: 0 auto; background-color: #ffffff; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.1);">
                    
                    <!-- Success Header -->
                    <tr>
                        <td style="background: linear-gradient(135deg, #28a745 0%, #20c997 100%); padding: 40px 30px; text-align: center; border-radius: 12px 12px 0 0;">
                            <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%">
                                <tr>
                                    <td style="text-align: center;">
                                        <div style="font-size: 48px; margin-bottom: 15px;">🎉</div>
                                        <div style="font-size: 36px; font-weight: bold; color: #ffffff; margin-bottom: 8px; letter-spacing: 2px;">RIVO9</div>
                                        <div style="font-size: 14px; color: #ffffff; font-weight: 600; letter-spacing: 3px; opacity: 0.9;">INTELLIGENT BRAND API ON DEMAND</div>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    
                    <!-- Success Message -->
                    <tr>
                        <td style="padding: 40px 30px;">
                            <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%">
                                <tr>
                                    <td style="text-align: center;">
                                        <h1 style="color: #28a745; font-size: 32px; margin: 0 0 20px 0; font-weight: 700;">Account Successfully Activated!</h1>
                                        <p style="color: #333333; font-size: 20px; line-height: 1.6; margin: 0 0 30px 0;">
                                            Welcome to RIVO9, <strong>${(user.username)!'there'}</strong>!
                                        </p>
                                        <p style="color: #555555; font-size: 16px; line-height: 1.6; margin: 0 0 30px 0;">
                                            Your account is now fully activated and ready to use. We've automatically set up everything you need to start building with our brand intelligence API.
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    
                    <!-- Account Ready Status -->
                    <tr>
                        <td style="padding: 0 30px 30px 30px;">
                            <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%">
                                <tr>
                                    <td style="background-color: #d4edda; padding: 25px; border-radius: 8px; border-left: 4px solid #28a745;">
                                        <h3 style="color: #155724; font-size: 20px; margin: 0 0 15px 0; font-weight: 600;">✅ Your Account Status:</h3>
                                        <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%">
                                            <tr>
                                                <td style="padding: 8px 0; color: #155724; font-size: 16px;">✅ <strong>Email verified</strong></td>
                                            </tr>
                                            <tr>
                                                <td style="padding: 8px 0; color: #155724; font-size: 16px;">✅ <strong>FREE plan activated (100 API calls/month)</strong></td>
                                            </tr>
                                            <tr>
                                                <td style="padding: 8px 0; color: #155724; font-size: 16px;">✅ <strong>API key automatically generated</strong></td>
                                            </tr>
                                            <tr>
                                                <td style="padding: 8px 0; color: #155724; font-size: 16px;">✅ <strong>Ready to make API calls</strong></td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    
                    <!-- API Key Section -->
                    <tr>
                        <td style="padding: 0 30px 30px 30px;">
                            <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%">
                                <tr>
                                    <td style="background-color: #1a1a2e; padding: 25px; border-radius: 8px; color: #ffffff; text-align: center;">
                                        <h3 style="color: #ffffff; font-size: 18px; margin: 0 0 15px 0;">🔑 Your FREE API Key</h3>
                                        <div style="background-color: #16213e; padding: 15px; border-radius: 6px; margin: 15px 0; border: 2px solid #e94560;">
                                            <p style="color: #e94560; font-size: 14px; margin: 0 0 8px 0; font-weight: 600;">API Key:</p>
                                            <p style="color: #ffffff; font-size: 16px; margin: 0; font-family: 'Courier New', monospace; font-weight: bold; letter-spacing: 1px; word-break: break-all;">
                                                ${(apiKey.keyValue)!'[API Key Generation Failed]'}
                                            </p>
                                        </div>
                                        <p style="color: #ffffff; font-size: 14px; margin: 10px 0 0 0; opacity: 0.8;">
                                            Keep this key secure! You can view it anytime in your dashboard.
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    
                    <!-- Plan Details -->
                    <tr>
                        <td style="padding: 0 30px 30px 30px;">
                            <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%">
                                <tr>
                                    <td style="background-color: #f8f9fa; padding: 25px; border-radius: 8px; border: 1px solid #dee2e6;">
                                        <h3 style="color: #1a1a2e; font-size: 18px; margin: 0 0 15px 0; text-align: center;">📋 Your FREE Plan Details</h3>
                                        <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%" style="border-collapse: collapse;">
                                            <tr>
                                                <td style="padding: 12px; border-bottom: 1px solid #dee2e6; color: #666666; font-size: 14px; font-weight: 600;">Plan Type:</td>
                                                <td style="padding: 12px; border-bottom: 1px solid #dee2e6; color: #1a1a2e; font-size: 14px; font-weight: bold;">${(userPlan.displayName)!'FREE'}</td>
                                            </tr>
                                            <tr>
                                                <td style="padding: 12px; border-bottom: 1px solid #dee2e6; color: #666666; font-size: 14px; font-weight: 600;">Monthly API Calls:</td>
                                                <td style="padding: 12px; border-bottom: 1px solid #dee2e6; color: #e94560; font-size: 14px; font-weight: bold;">${(userPlan.monthlyApiCalls)!'100'} calls</td>
                                            </tr>
                                            <tr>
                                                <td style="padding: 12px; border-bottom: 1px solid #dee2e6; color: #666666; font-size: 14px; font-weight: 600;">Max Domains:</td>
                                                <td style="padding: 12px; border-bottom: 1px solid #dee2e6; color: #1a1a2e; font-size: 14px; font-weight: bold;">${(userPlan.maxDomains)!'1'}</td>
                                            </tr>
                                            <tr>
                                                <td style="padding: 12px; color: #666666; font-size: 14px; font-weight: 600;">Support Level:</td>
                                                <td style="padding: 12px; color: #1a1a2e; font-size: 14px; font-weight: bold;">Community Support</td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    
                    <!-- Action Buttons -->
                    <tr>
                        <td style="padding: 0 30px 40px 30px; text-align: center;">
                            <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin: 0 auto;">
                                <tr>
                                    <td style="padding: 0 10px;">
                                        <table role="presentation" cellspacing="0" cellpadding="0" border="0">
                                            <tr>
                                                <td style="background: linear-gradient(135deg, #e94560 0%, #d63447 100%); padding: 14px 30px; border-radius: 50px;">
                                                    <a href="${(loginUrl)!'#'}" style="color: #ffffff; text-decoration: none; font-size: 16px; font-weight: 600;">
                                                        Login to Dashboard
                                                    </a>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                    <td style="padding: 0 10px;">
                                        <table role="presentation" cellspacing="0" cellpadding="0" border="0">
                                            <tr>
                                                <td style="background-color: #1a1a2e; padding: 14px 30px; border-radius: 50px;">
                                                    <a href="${(loginUrl)!'#'}" style="color: #ffffff; text-decoration: none; font-size: 16px; font-weight: 600;">
                                                        View API Docs
                                                    </a>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    
                    <!-- Quick Start Guide -->
                    <tr>
                        <td style="padding: 0 30px 40px 30px;">
                            <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%">
                                <tr>
                                    <td style="background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); padding: 25px; border-radius: 8px; border-left: 4px solid #e94560;">
                                        <h3 style="color: #1a1a2e; font-size: 18px; margin: 0 0 15px 0;">🚀 Quick Start Guide</h3>
                                        <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%">
                                            <tr>
                                                <td style="padding: 8px 0; color: #333333; font-size: 15px;">1️⃣ Copy your API key from above</td>
                                            </tr>
                                            <tr>
                                                <td style="padding: 8px 0; color: #333333; font-size: 15px;">2️⃣ Read our <a href="${(loginUrl)!'#'}" style="color: #e94560; text-decoration: none; font-weight: 600;">API documentation</a></td>
                                            </tr>
                                            <tr>
                                                <td style="padding: 8px 0; color: #333333; font-size: 15px;">3️⃣ Make your first brand extraction call</td>
                                            </tr>
                                            <tr>
                                                <td style="padding: 8px 0; color: #333333; font-size: 15px;">4️⃣ Monitor usage in your <a href="${(dashboardUrl)!'#'}" style="color: #e94560; text-decoration: none; font-weight: 600;">dashboard</a></td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    
                    <!-- Footer -->
                    <tr>
                        <td style="background-color: #f8f9fa; padding: 30px; text-align: center; border-radius: 0 0 12px 12px; border-top: 1px solid #dee2e6;">
                            <p style="color: #666666; font-size: 14px; margin: 0 0 10px 0; font-weight: 600;">RIVO9 Technologies</p>
                            <p style="color: #888888; font-size: 12px; margin: 0 0 15px 0;">
                                Need help getting started? Our team is here to support you!
                            </p>
                            <p style="color: #888888; font-size: 12px; margin: 0;">
                                <a href="https://rivo9.com" style="color: #e94560; text-decoration: none;">rivo9.com</a> | 
                                <a href="mailto:support@rivo9.com" style="color: #e94560; text-decoration: none;">support@rivo9.com</a> | 
                                <a href="${apiDocsUrl!'#'}" style="color: #e94560; text-decoration: none;">API Docs</a>
                            </p>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</body>
</html>