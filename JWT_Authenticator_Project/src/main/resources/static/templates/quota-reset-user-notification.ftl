<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Your API Quota Has Been Reset - ${companyName}</title>
    <style>
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            line-height: 1.6;
            color: #333;
            max-width: 600px;
            margin: 0 auto;
            padding: 20px;
            background-color: #f8f9fa;
        }
        .container {
            background-color: #ffffff;
            border-radius: 10px;
            padding: 30px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
        }
        .header {
            text-align: center;
            border-bottom: 3px solid #007bff;
            padding-bottom: 20px;
            margin-bottom: 30px;
        }
        .logo {
            font-size: 28px;
            font-weight: bold;
            color: #007bff;
            margin-bottom: 10px;
        }
        .subtitle {
            color: #6c757d;
            font-size: 16px;
        }
        .content {
            margin-bottom: 30px;
        }
        .greeting {
            font-size: 18px;
            margin-bottom: 20px;
            color: #495057;
        }
        .highlight-box {
            background: linear-gradient(135deg, #007bff 0%, #0056b3 100%);
            color: white;
            padding: 20px;
            border-radius: 8px;
            text-align: center;
            margin: 25px 0;
        }
        .highlight-box h3 {
            margin: 0 0 10px 0;
            font-size: 20px;
        }
        .highlight-box p {
            margin: 0;
            font-size: 16px;
            opacity: 0.9;
        }
        .info-grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 20px;
            margin: 25px 0;
        }
        .info-item {
            background-color: #f8f9fa;
            padding: 15px;
            border-radius: 6px;
            border-left: 4px solid #007bff;
        }
        .info-label {
            font-weight: bold;
            color: #495057;
            font-size: 14px;
            margin-bottom: 5px;
        }
        .info-value {
            color: #007bff;
            font-size: 16px;
            font-weight: 600;
        }
        .status-success {
            background-color: #d4edda;
            color: #155724;
            padding: 15px;
            border-radius: 6px;
            border-left: 4px solid #28a745;
            margin: 20px 0;
        }
        .status-warning {
            background-color: #fff3cd;
            color: #856404;
            padding: 15px;
            border-radius: 6px;
            border-left: 4px solid #ffc107;
            margin: 20px 0;
        }
        .cta-button {
            display: inline-block;
            background: linear-gradient(135deg, #28a745 0%, #20c997 100%);
            color: white;
            padding: 12px 30px;
            text-decoration: none;
            border-radius: 25px;
            font-weight: bold;
            margin: 20px 0;
            transition: transform 0.2s;
        }
        .cta-button:hover {
            transform: translateY(-2px);
            text-decoration: none;
            color: white;
        }
        .footer {
            border-top: 1px solid #dee2e6;
            padding-top: 20px;
            margin-top: 30px;
            text-align: center;
            color: #6c757d;
            font-size: 14px;
        }
        .footer a {
            color: #007bff;
            text-decoration: none;
        }
        .footer a:hover {
            text-decoration: underline;
        }
        @media (max-width: 600px) {
            .info-grid {
                grid-template-columns: 1fr;
            }
            .container {
                padding: 20px;
            }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div class="logo">${companyName}</div>
            <div class="subtitle">API Management Platform</div>
        </div>
        
        <div class="content">
            <div class="greeting">
                Hello ${userName},
            </div>
            
            <p>We hope this message finds you well! We're writing to inform you that your monthly API quota has been successfully reset for <strong>${monthYear}</strong>.</p>
            
            <div class="highlight-box">
                <h3>🎉 Your Quota is Now Reset!</h3>
                <p>You now have access to your full monthly allowance of <strong>${quotaLimit}</strong></p>
            </div>
            
            <#if resetSuccessful>
                <div class="status-success">
                    <strong>✅ Reset Completed Successfully</strong><br>
                    Your quota was reset on ${resetDate} at ${resetTime}. All systems are operating normally.
                </div>
            <#else>
                <div class="status-warning">
                    <strong>⚠️ Reset Completed with Minor Issues</strong><br>
                    Your quota was reset on ${resetDate}, but there may have been some minor delays. If you experience any issues, please contact our support team.
                </div>
            </#if>
            
            <div class="info-grid">
                <div class="info-item">
                    <div class="info-label">Your Plan</div>
                    <div class="info-value">${userPlan}</div>
                </div>
                <div class="info-item">
                    <div class="info-label">Monthly Allowance</div>
                    <div class="info-value">${quotaLimit}</div>
                </div>
                <div class="info-item">
                    <div class="info-label">Reset Date</div>
                    <div class="info-value">${resetDate}</div>
                </div>
                <div class="info-item">
                    <div class="info-label">Next Reset</div>
                    <div class="info-value">${nextResetDate}</div>
                </div>
            </div>
            
            <p>Your API usage counters have been reset to zero, and you can now make API calls according to your plan limits. All your API keys remain active and functional.</p>
            
            <div style="text-align: center;">
                <a href="${companyWebsite}/dashboard" class="cta-button">
                    View Your Dashboard
                </a>
            </div>
            
            <h4>📊 What This Means for You:</h4>
            <ul>
                <li><strong>Fresh Start:</strong> Your usage counters are now at zero</li>
                <li><strong>Full Access:</strong> You have your complete monthly allowance available</li>
                <li><strong>No Interruption:</strong> All your existing API keys continue to work normally</li>
                <li><strong>Automatic Process:</strong> This happens automatically every month on the 1st</li>
            </ul>
            
            <h4>🔗 Quick Links:</h4>
            <ul>
                <li><a href="${companyWebsite}/dashboard">View Usage Dashboard</a></li>
                <li><a href="${companyWebsite}/api-keys">Manage API Keys</a></li>
                <li><a href="${companyWebsite}/documentation">API Documentation</a></li>
                <li><a href="${companyWebsite}/support">Contact Support</a></li>
            </ul>
            
            <p>If you have any questions about your quota reset or need assistance with your API integration, our support team is here to help!</p>
        </div>
        
        <div class="footer">
            <p><strong>${companyName} API Team</strong></p>
            <p>
                Need help? Contact us at <a href="mailto:${supportEmail}">${supportEmail}</a><br>
                Visit our website: <a href="${companyWebsite}">${companyWebsite}</a>
            </p>
            <p style="margin-top: 15px; font-size: 12px; color: #adb5bd;">
                © ${currentYear} ${companyName}. All rights reserved.<br>
                This is an automated message regarding your API quota reset.
            </p>
        </div>
    </div>
</body>
</html>