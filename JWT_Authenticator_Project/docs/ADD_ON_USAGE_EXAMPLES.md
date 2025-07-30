# Add-on System Usage Examples

This document provides practical examples of how the professional add-on system works with day-based rate limiting.

## 🎯 **Complete Workflow Examples**

### **Example 1: Basic User Exceeds Daily Limit**

#### **Initial Setup**
```bash
# User creates API key with BASIC tier
POST /api/v1/api-keys
{
  "name": "My App API Key",
  "rateLimitTier": "BASIC"
}

# Response
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "keyValue": "sk-abc123def456...",  # Shown only once!
  "rateLimitTier": "BASIC",
  "description": "100 requests per day"
}
```

#### **Day 1: Normal Usage (50 requests)**
```bash
# Making API calls throughout the day
for i in {1..50}; do
  curl -X POST http://localhost:8080/forward \
    -H "X-API-Key: sk-abc123def456..." \
    -d '{"url": "https://api.example.com"}'
done

# Response headers show remaining requests
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 50
X-RateLimit-Tier: BASIC
X-RateLimit-Additional-Available: 0
X-RateLimit-Total-Remaining: 50
```

#### **Day 2: Heavy Usage (100+ requests)**
```bash
# First 100 requests work fine
for i in {1..100}; do
  curl -X POST http://localhost:8080/forward \
    -H "X-API-Key: sk-abc123def456..." \
    -d '{"url": "https://api.example.com"}'
done

# Request #101 gets rate limited
curl -X POST http://localhost:8080/forward \
  -H "X-API-Key: sk-abc123def456..." \
  -d '{"url": "https://api.example.com"}'

# Response: 429 Too Many Requests
{
  "error": "Rate limit exceeded. Consider purchasing add-on requests.",
  "status": 429,
  "details": {
    "currentTier": "BASIC",
    "dailyLimit": 100,
    "requestsUsed": 100,
    "resetIn": "14 hours",
    "recommendations": [
      {
        "package": "ADDON_SMALL",
        "cost": "$5/month",
        "additionalRequests": 100,
        "description": "Perfect for your current usage pattern"
      }
    ]
  }
}
```

#### **Purchase Add-on Package**
```bash
# User decides to purchase ADDON_SMALL
POST /api/v1/api-keys/addons/purchase
{
  "apiKeyId": "550e8400-e29b-41d4-a716-446655440000",
  "addOnPackage": "ADDON_SMALL",
  "durationMonths": 1,
  "autoRenew": true,
  "reason": "Exceeded daily limits during product launch"
}

# Response
{
  "id": "addon-789-012",
  "apiKeyId": "550e8400-e29b-41d4-a716-446655440000",
  "addOnPackage": "ADDON_SMALL",
  "additionalRequests": 100,
  "requestsRemaining": 100,
  "monthlyPrice": 5.0,
  "activatedAt": "2024-01-15T14:30:00",
  "expiresAt": "2024-02-15T14:30:00",
  "autoRenew": true
}
```

#### **Continued Usage with Add-on**
```bash
# Request #101 now works using add-on
curl -X POST http://localhost:8080/forward \
  -H "X-API-Key: sk-abc123def456..." \
  -d '{"url": "https://api.example.com"}'

# Response: 200 OK
X-RateLimit-Limit: 100                    # Base tier limit
X-RateLimit-Remaining: 0                  # Base tier exhausted
X-RateLimit-Tier: BASIC
X-RateLimit-Additional-Available: 99      # Add-on requests remaining
X-RateLimit-Total-Remaining: 99           # Total available
X-RateLimit-Used-AddOn: true              # Indicates add-on was used

# Can continue up to 200 total requests per day (100 base + 100 add-on)
```

### **Example 2: Enterprise Customer with Seasonal Spikes**

#### **Setup**
```bash
# Enterprise customer with high base tier
POST /api/v1/api-keys
{
  "name": "Enterprise Production Key",
  "rateLimitTier": "ENTERPRISE"
}

# Response: 10,000 requests per day
```

#### **Black Friday Campaign (Need 15,000 requests/day)**
```bash
# Purchase large add-on for campaign period
POST /api/v1/api-keys/addons/purchase
{
  "apiKeyId": "enterprise-key-id",
  "addOnPackage": "ADDON_ENTERPRISE",
  "durationMonths": 1,
  "autoRenew": false,  # Only for campaign period
  "reason": "Black Friday campaign - expecting 50% traffic increase"
}

# Now has 20,000 requests per day (10,000 + 10,000)
```

#### **After Campaign**
```bash
# Add-on expires automatically after 1 month
# Back to 10,000 requests per day
# No unnecessary recurring charges
```

### **Example 3: Smart Recommendations**

#### **Get Personalized Recommendations**
```bash
# User consistently uses 150 requests per day on BASIC plan
GET /api/v1/api-keys/addons/550e8400-e29b-41d4-a716-446655440000/recommendations?overageRequests=50

# Response
{
  "apiKeyId": "550e8400-e29b-41d4-a716-446655440000",
  "overageRequests": 50,
  "recommendedPackage": "ADDON_SMALL",
  "alternativePackages": ["ADDON_SMALL", "ADDON_MEDIUM"],
  "estimatedMonthlySavings": 0.0,
  "recommendationReason": "Based on 50 overage requests, Small Add-on provides the best value at $5.00/month (0.050 per request)",
  "upgradeOptions": [
    {
      "tier": "STANDARD",
      "cost": "$10/month",
      "dailyLimit": 500,
      "description": "Better value for consistent usage - includes 5x more requests"
    }
  ]
}
```

## 📊 **Monitoring and Analytics**

### **Real-time Usage Monitoring**
```bash
# Check current usage
GET /api/v1/api-keys/statistics/550e8400-e29b-41d4-a716-446655440000

# Response
{
  "apiKeyId": "550e8400-e29b-41d4-a716-446655440000",
  "currentUsage": {
    "requestCount": 100,        # Base tier used
    "requestLimit": 100,        # Base tier limit
    "remainingRequests": 0,     # Base tier remaining
    "usagePercentage": 100.0,
    "windowStart": "2024-01-15T00:00:00",
    "windowEnd": "2024-01-15T23:59:59"
  },
  "addOnUsage": {
    "totalAdditionalRequests": 100,
    "additionalRequestsUsed": 25,
    "additionalRequestsRemaining": 75,
    "addOnUsagePercentage": 25.0
  },
  "totalAvailable": 75,
  "isCurrentlyRateLimited": false
}
```

### **Historical Analytics**
```bash
# Get 30-day usage analytics
GET /api/v1/api-keys/statistics/550e8400-e29b-41d4-a716-446655440000/analytics?days=30

# Response includes add-on efficiency metrics
{
  "dailyRequestCounts": {
    "2024-01-01": 85,
    "2024-01-02": 120,  # Used add-on
    "2024-01-03": 95,
    "2024-01-04": 150   # Used add-on
  },
  "addOnMetrics": {
    "totalAddOnRequestsAvailable": 3000,  # 30 days × 100
    "totalAddOnRequestsUsed": 450,
    "addOnEfficiency": 15.0,  # 15% of add-on requests actually used
    "addOnSpending": 5.0,
    "costPerActualRequest": 0.011  # $5 / 450 requests
  },
  "recommendations": {
    "suggestion": "UPGRADE_TO_STANDARD",
    "reason": "You're consistently using 120+ requests/day. STANDARD tier ($10/month) would be more cost-effective than BASIC + add-ons ($5/month)",
    "potentialSavings": 5.0
  }
}
```

## 🔄 **Day-based Reset Behavior**

### **Daily Reset Example**
```
Day 1 (2024-01-15):
├── 00:00 - Window starts
├── 10:00 - Used 45/100 base requests
├── 14:00 - Used 100/100 base requests (base exhausted)
├── 15:00 - Used 25/100 add-on requests
├── 18:00 - Used 50/100 add-on requests
└── 23:59 - Day ends with 50/100 add-on requests remaining

Day 2 (2024-01-16):
├── 00:00 - NEW WINDOW STARTS
├── Base requests: 0/100 (RESET)
├── Add-on requests: 50/100 (CONTINUES from previous day)
└── Total available: 150 requests (100 base + 50 add-on remaining)
```

### **Add-on Expiration Example**
```
Month 1:
├── Add-on purchased: 2024-01-15 14:30:00
├── Add-on expires: 2024-02-15 14:30:00
└── Daily usage: Base (100) + Add-on (100) = 200 total

Month 2 (after expiration):
├── Add-on expired: 2024-02-15 14:30:00
├── Auto-renew: false
└── Daily usage: Base (100) only = 100 total

# User gets notification 3 days before expiration
Email: "Your add-on package expires in 3 days. Renew now to avoid service interruption."
```

## 💰 **Cost Optimization Examples**

### **Scenario Analysis**

#### **User A: Consistent 150 requests/day**
```
Option 1: BASIC + ADDON_SMALL
- Base: $0/month (100 requests/day)
- Add-on: $5/month (100 additional requests/day)
- Total: $5/month for 200 requests/day
- Efficiency: 75% (150/200 used)

Option 2: STANDARD tier
- Base: $10/month (500 requests/day)
- Total: $10/month for 500 requests/day
- Efficiency: 30% (150/500 used)

Recommendation: Keep BASIC + ADDON_SMALL (better value)
```

#### **User B: Consistent 400 requests/day**
```
Option 1: BASIC + ADDON_LARGE
- Base: $0/month (100 requests/day)
- Add-on: $75/month (2000 additional requests/day)
- Total: $75/month for 2100 requests/day
- Efficiency: 19% (400/2100 used)

Option 2: STANDARD tier
- Base: $10/month (500 requests/day)
- Total: $10/month for 500 requests/day
- Efficiency: 80% (400/500 used)

Recommendation: Upgrade to STANDARD tier (much better value)
```

## 🚨 **Error Handling Examples**

### **Graceful Rate Limiting**
```bash
# When both base and add-on limits are exhausted
curl -X POST http://localhost:8080/forward \
  -H "X-API-Key: sk-abc123def456..." \
  -d '{"url": "https://api.example.com"}'

# Response: 429 Too Many Requests
{
  "error": "Rate limit exceeded",
  "status": 429,
  "details": {
    "currentTier": "BASIC",
    "baseLimit": 100,
    "baseUsed": 100,
    "addOnLimit": 100,
    "addOnUsed": 100,
    "totalUsed": 200,
    "resetIn": "8 hours 23 minutes",
    "nextOptions": [
      {
        "action": "PURCHASE_ADDITIONAL_ADDON",
        "package": "ADDON_SMALL",
        "cost": "$5/month",
        "immediateRequests": 100
      },
      {
        "action": "UPGRADE_TIER",
        "tier": "STANDARD",
        "cost": "$10/month",
        "dailyLimit": 500,
        "description": "Better long-term value"
      }
    ]
  }
}
```

This comprehensive add-on system provides:
- ✅ **Flexible scaling** without tier upgrades
- ✅ **Day-based limits** that reset at midnight
- ✅ **Smart recommendations** based on usage patterns
- ✅ **Cost optimization** suggestions
- ✅ **Graceful degradation** when limits are reached
- ✅ **Professional billing** with auto-renewal options

The system follows industry best practices and provides excellent user experience while maximizing revenue opportunities! 🎯