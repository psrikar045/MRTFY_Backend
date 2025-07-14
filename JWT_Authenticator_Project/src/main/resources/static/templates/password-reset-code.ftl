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
        .code { font-size: 24px; font-weight: bold; color: #0C0C30; letter-spacing: 2px; padding: 10px; background-color: #f4f4f4; display: inline-block; margin: 10px 0; }
        .footer { background-color: #f4f4f4; padding: 10px; text-align: center; font-size: 12px; color: #666; }
        .footer a { color: #0C0C30; text-decoration: none; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <svg viewBox="0 0 700 200" xmlns="http://www.w3.org/2000/svg" style="max-width: 350px; height: auto; display: block; margin: 0 auto;">
              <rect width="700" height="200" fill="#0C0C30"/>
              <g transform="translate(40,40)" fill="url(#grad4)">
                <rect x="0" y="40" width="8" height="8" rx="2"/>
                <rect x="10" y="30" width="8" height="8" rx="2"/>
                <rect x="20" y="20" width="8" height="8" rx="2"/>
                <rect x="30" y="10" width="8" height="8" rx="2"/>
                <rect x="40" y="0" width="8" height="8" rx="2"/>
                <rect x="50" y="10" width="8" height="8" rx="2"/>
                <rect x="60" y="20" width="8" height="8" rx="2"/>
                <rect x="70" y="30" width="8" height="8" rx="2"/>
              </g>
              <text x="160" y="90" font-family="Orbitron, sans-serif" font-size="48" fill="#B3F5FF">MARKETIFY</text>
              <text x="160" y="140" font-family="Arial, sans-serif" font-size="20" fill="#00D0FF" letter-spacing="4">BRAND ON DEMAND</text>
              <defs>
                <linearGradient id="grad4" x1="0" y1="0" x2="100" y2="100">
                  <stop stop-color="#FF7A18"/>
                  <stop offset="1" stop-color="#AF002D"/>
                </linearGradient>
              </defs>
            </svg>
        </div>
        <div class="content">
            <h1>Reset Your Password</h1>
            <p>We received a request to reset your Marketify account password. Please use the following verification code to proceed:</p>
            <div class="code">${verificationCode}</div>
            <p>Enter this code on the password reset page. It will expire in 10 minutes.</p>
            <p>If you didn't request a password reset, please contact us at <a href="mailto:support@marketify.com">support@marketify.com</a>.</p>
        </div>
        <div class="footer">
            <p>Marketify Inc. | 123 Business Avenue, Bangalore, India</p>
            <p><a href="https://marketify.com">marketify.com</a> | <a href="mailto:support@marketify.com">support@marketify.com</a></p>
        </div>
    </div>
</body>
</html>