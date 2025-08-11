<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Monthly Quota Reset Summary - ${companyName} Admin</title>
    <style>
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            line-height: 1.6;
            color: #333;
            max-width: 700px;
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
            border-bottom: 3px solid #dc3545;
            padding-bottom: 20px;
            margin-bottom: 30px;
        }
        .logo {
            font-size: 28px;
            font-weight: bold;
            color: #dc3545;
            margin-bottom: 10px;
        }
        .subtitle {
            color: #6c757d;
            font-size: 16px;
        }
        .admin-badge {
            background: linear-gradient(135deg, #dc3545 0%, #c82333 100%);
            color: white;
            padding: 5px 15px;
            border-radius: 15px;
            font-size: 12px;
            font-weight: bold;
            display: inline-block;
            margin-top: 10px;
        }
        .summary-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
            gap: 15px;
            margin: 25px 0;
        }
        .metric-card {
            background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
            padding: 20px;
            border-radius: 8px;
            text-align: center;
            border-left: 4px solid #007bff;
        }
        .metric-card.success {
            border-left-color: #28a745;
        }
        .metric-card.warning {
            border-left-color: #ffc107;
        }
        .metric-card.danger {
            border-left-color: #dc3545;
        }
        .metric-value {
            font-size: 24px;
            font-weight: bold;
            color: #495057;
            margin-bottom: 5px;
        }
        .metric-label {
            font-size: 12px;
            color: #6c757d;
            text-transform: uppercase;
            font-weight: 600;
        }
        .status-section {
            margin: 30px 0;
        }
        .status-success {
            background-color: #d4edda;
            color: #155724;
            padding: 20px;
            border-radius: 8px;
            border-left: 4px solid #28a745;
        }
        .status-warning {
            background-color: #fff3cd;
            color: #856404;
            padding: 20px;
            border-radius: 8px;
            border-left: 4px solid #ffc107;
        }
        .status-error {
            background-color: #f8d7da;
            color: #721c24;
            padding: 20px;
            border-radius: 8px;
            border-left: 4px solid #dc3545;
        }
        .details-table {
            width: 100%;
            border-collapse: collapse;
            margin: 20px 0;
            background-color: #fff;
            border-radius: 8px;
            overflow: hidden;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .details-table th {
            background: linear-gradient(135deg, #495057 0%, #343a40 100%);
            color: white;
            padding: 15px;
            text-align: left;
            font-weight: 600;
        }
        .details-table td {
            padding: 12px 15px;
            border-bottom: 1px solid #dee2e6;
        }
        .details-table tr:nth-child(even) {
            background-color: #f8f9fa;
        }
        .progress-bar {
            background-color: #e9ecef;
            border-radius: 10px;
            height: 20px;
            overflow: hidden;
            margin: 10px 0;
        }
        .progress-fill {
            height: 100%;
            background: linear-gradient(90deg, #28a745 0%, #20c997 100%);
            border-radius: 10px;
            transition: width 0.3s ease;
        }
        .progress-fill.warning {
            background: linear-gradient(90deg, #ffc107 0%, #fd7e14 100%);
        }
        .progress-fill.danger {
            background: linear-gradient(90deg, #dc3545 0%, #c82333 100%);
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
            color: #dc3545;
            text-decoration: none;
        }
        .footer a:hover {
            text-decoration: underline;
        }
        .timestamp {
            background-color: #f8f9fa;
            padding: 10px;
            border-radius: 5px;
            font-family: 'Courier New', monospace;
            font-size: 14px;
            color: #495057;
            margin: 15px 0;
        }
        @media (max-width: 600px) {
            .summary-grid {
                grid-template-columns: 1fr 1fr;
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
            <div class="admin-badge">ADMIN DASHBOARD</div>
        </div>
        
        <h2>📊 Monthly Quota Reset Summary</h2>
        <h3 style="color: #6c757d; margin-bottom: 30px;">${monthYear} Reset Operation</h3>
        
        <div class="summary-grid">
            <div class="metric-card">
                <div class="metric-value">${totalProcessed}</div>
                <div class="metric-label">Total Processed</div>
            </div>
            <div class="metric-card success">
                <div class="metric-value">${successCount}</div>
                <div class="metric-label">Successful</div>
            </div>
            <div class="metric-card <#if failureCount gt 0>danger<#else>success</#if>">
                <div class="metric-value">${failureCount}</div>
                <div class="metric-label">Failed</div>
            </div>
            <div class="metric-card <#if skippedCount gt 0>warning<#else>success</#if>">
                <div class="metric-value">${skippedCount}</div>
                <div class="metric-label">Skipped</div>
            </div>
        </div>
        
        <div class="status-section">
            <#if resetSuccessful>
                <div class="status-success">
                    <h4>✅ Reset Completed Successfully</h4>
                    <p>The monthly quota reset operation completed successfully with a ${successRate} success rate. All user quotas have been reset for ${monthYear}.</p>
                </div>
            <#elseif hasErrors>
                <div class="status-warning">
                    <h4>⚠️ Reset Completed with Errors</h4>
                    <p>The quota reset operation completed but encountered ${failureCount} errors. Success rate: ${successRate}. Please review the failed records and consider manual intervention if necessary.</p>
                </div>
            <#else>
                <div class="status-error">
                    <h4>❌ Reset Failed</h4>
                    <p>The quota reset operation encountered significant issues. Please review the logs and take immediate action to ensure user quotas are properly reset.</p>
                </div>
            </#if>
        </div>
        
        <h4>📈 Success Rate</h4>
        <div class="progress-bar">
            <div class="progress-fill <#if successRate?number lt 90>danger<#elseif successRate?number lt 95>warning</#if>" 
                 style="width: ${successRate}%"></div>
        </div>
        <p style="text-align: center; margin-top: 5px; font-weight: bold; color: #495057;">${successRate}</p>
        
        <h4>🔍 Execution Details</h4>
        <table class="details-table">
            <tr>
                <th>Attribute</th>
                <th>Value</th>
            </tr>
            <tr>
                <td><strong>Execution Time</strong></td>
                <td>${executionTime}</td>
            </tr>
            <tr>
                <td><strong>Duration</strong></td>
                <td>${executionDuration}</td>
            </tr>
            <tr>
                <td><strong>Triggered By</strong></td>
                <td>${triggeredBy}</td>
            </tr>
            <tr>
                <td><strong>Status</strong></td>
                <td><span style="color: <#if resetSuccessful>#28a745<#elseif hasErrors>#ffc107<#else>#dc3545</#if>; font-weight: bold;">${executionStatus}</span></td>
            </tr>
            <tr>
                <td><strong>Next Reset Date</strong></td>
                <td>${nextResetDate}</td>
            </tr>
        </table>
        
        <h4>📋 Summary Breakdown</h4>
        <ul>
            <li><strong>Total Records Processed:</strong> ${totalProcessed} user quota records</li>
            <li><strong>Successful Resets:</strong> ${successCount} quotas reset successfully</li>
            <li><strong>Failed Resets:</strong> ${failureCount} quotas failed to reset</li>
            <li><strong>Skipped Records:</strong> ${skippedCount} records skipped (already up-to-date)</li>
            <li><strong>Overall Success Rate:</strong> ${successRate}</li>
        </ul>
        
        <#if hasErrors>
        <div style="background-color: #fff3cd; padding: 15px; border-radius: 5px; border-left: 4px solid #ffc107; margin: 20px 0;">
            <h4 style="color: #856404; margin-top: 0;">⚠️ Action Required</h4>
            <p style="color: #856404; margin-bottom: 0;">
                There were ${failureCount} failed reset operations. Please:
            </p>
            <ul style="color: #856404;">
                <li>Review the application logs for detailed error messages</li>
                <li>Check database connectivity and performance</li>
                <li>Consider running a manual reset for failed records</li>
                <li>Monitor user reports for quota-related issues</li>
            </ul>
        </div>
        </#if>
        
        <h4>🔗 Admin Actions</h4>
        <ul>
            <li><a href="${companyWebsite}/admin/quota-management">View Quota Management Dashboard</a></li>
            <li><a href="${companyWebsite}/admin/audit-logs">Review Detailed Audit Logs</a></li>
            <li><a href="${companyWebsite}/admin/user-management">Manage User Accounts</a></li>
            <li><a href="${companyWebsite}/admin/system-health">Check System Health</a></li>
        </ul>
        
        <div class="timestamp">
            <strong>Report Generated:</strong> ${executionTime}<br>
            <strong>System:</strong> ${companyName} API Management Platform
        </div>
        
        <div class="footer">
            <p><strong>${companyName} System Administrator</strong></p>
            <p>
                For technical support: <a href="mailto:${supportEmail}">${supportEmail}</a><br>
                Admin Portal: <a href="${companyWebsite}/admin">Access Admin Dashboard</a>
            </p>
            <p style="margin-top: 15px; font-size: 12px; color: #adb5bd;">
                © ${currentYear} ${companyName}. All rights reserved.<br>
                This is an automated administrative report for quota reset operations.
            </p>
        </div>
    </div>
</body>
</html>