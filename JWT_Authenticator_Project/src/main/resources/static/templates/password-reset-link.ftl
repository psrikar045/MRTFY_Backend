<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Reset Your Marketify Password</title>
    <style>
        body { font-family: Arial, sans-serif; background-color: #f4f4f4; color: #333; margin: 0; padding: 0; }
        .container { max-width: 600px; margin: 20px auto; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        .header { background-color: #0C0C30; padding: 20px; text-align: center; border-top-left-radius: 8px; border-top-right-radius: 8px; }
        .header img { max-width: 350px; height: auto; display: block; margin: 0 auto; }
        .content { padding: 20px; text-align: center; }
        .content h1 { font-size: 24px; color: #0C0C30; }
        .content p { font-size: 16px; line-height: 1.5; }
        .cta-button { display: inline-block; padding: 12px 24px; background-color: #0C0C30; color: #ffffff; text-decoration: none; border-radius: 5px; font-size: 16px; margin: 20px 0; }
        .cta-button:hover { background-color: #00D0FF; }
        .footer { background-color: #f4f4f4; padding: 10px; text-align: center; font-size: 12px; color: #666; }
        .footer a { color: #0C0C30; text-decoration: none; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div style="max-width: 350px; margin: 0 auto; position: relative; height: 100px;">
                <!-- Icon representation using CSS -->
                <div style="position: absolute; left: 20px; top: 20px; width: 60px; height: 40px;">
                    <div style="position: absolute; left: 0px; top: 30px; width: 8px; height: 8px; background: #FF7A18; border-radius: 2px;"></div>
                   <div style="position: absolute; left: 8px; top: 22px; width: 8px; height: 8px; background: #E8651A; border-radius: 2px;"></div>
                   <div style="position: absolute; left: 16px; top: 14px; width: 8px; height: 8px; background: #D1501C; border-radius: 2px;"></div>
                   <div style="position: absolute; left: 24px; top: 6px; width: 8px; height: 8px; background: #BA3B1E; border-radius: 2px;"></div>
                   <div style="position: absolute; left: 32px; top: 0px; width: 8px; height: 8px; background: #A32620; border-radius: 2px;"></div>
                   <div style="position: absolute; left: 40px; top: 6px; width: 8px; height: 8px; background: #BA3B1E; border-radius: 2px;"></div>
                   <div style="position: absolute; left: 48px; top: 14px; width: 8px; height: 8px; background: #D1501C; border-radius: 2px;"></div>
                  <div style="position: absolute; left: 56px; top: 22px; width: 8px; height: 8px; background: #E8651A; border-radius: 2px;"></div>
                </div>
                <!-- Text -->
                <div style="position: absolute; left: 90px; top: 15px;">
                    <div style="font-family: Arial, sans-serif; font-size: 32px; font-weight: bold; color: #B3F5FF; margin: 0; line-height: 1;">MARKETIFY</div>
                    <div style="font-family: Arial, sans-serif; font-size: 12px; color: #00D0FF; letter-spacing: 2px; margin-top: 5px;">BRAND ON DEMAND</div>
                </div>
            </div>
        </div>
        <div class="content">
            <h1>Reset Your Password</h1>
            <p>Hello ${username},</p>
            <p>We received a request to reset your Marketify account password. Please click the button below to set a new password:</p>
            <a href="${resetUrl}" class="cta-button">Reset Password</a>
            <p>If the button doesn't work, copy and paste this link into your browser:</p>
            <p><a href="${resetUrl}">${resetUrl}</a></p>
            <p>This link will expire in 1 hour for security reasons.</p>
            <p>If you didn't request this password reset, please ignore this email or contact us at <a href="mailto:support@marketify.com">support@marketify.com</a>.</p>
        </div>
        <div class="footer">
            <p>Marketify Inc. | #1-58/5/A, 2nd Floor, Opp. Furniture Palace, Above Bank of India, Gachibowli, Hyderabad, India</p>
            <p><a href="https://marketify.com">marketify.com</a> | <a href="mailto:support@marketify.com">support@marketify.com</a></p>
        </div>
    </div>
</body>
</html>