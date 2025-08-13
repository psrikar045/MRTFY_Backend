<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>RIVO9 Password Reset Successful</title>
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
                <!-- Text -->
                <div style="position: absolute; left: 90px; top: 15px;">
                    <div style="font-family: Arial, sans-serif; font-size: 32px; font-weight: bold; color: #B3F5FF; margin: 0; line-height: 1;">RIVO9</div>
                    <div style="font-family: Arial, sans-serif; font-size: 12px; color: #00D0FF; letter-spacing: 2px; margin-top: 5px;">INTELLIGENT BRAND API ON DEMAND</div>
                </div>
            </div>
        </div>
        <div class="content">
            <h1>Password Reset Successful</h1>
            <p>Your RIVO9 account password has been successfully updated, ${user.firstName}.</p>
            <p>You can now log in with your new password.</p>
            <a href="${loginUrl}" class="cta-button">Log In to RIVO9</a>
            <p>If you didn't change your password, please contact us immediately at <a href="mailto:support@rivo9.com">support@rivo9.com</a>.</p>
        </div>
        <div class="footer">
            <p>RIVO9 Technologies | Intelligent Brand API Solutions</p>
            <p><a href="https://rivo9.com">rivo9.com</a> | <a href="mailto:support@rivo9.com">support@rivo9.com</a></p>
        </div>
    </div>
</body>
</html>