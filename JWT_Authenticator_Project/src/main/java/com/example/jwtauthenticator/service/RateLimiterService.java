package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.config.ForwardConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private final long requestsPerMinute;
    private final long publicRequestsPerMinute;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiterService(ForwardConfig config) {
        this.requestsPerMinute = config.getRequestsPerMinute();
        // For public endpoints, use a more restrictive rate limit (half of the authenticated limit)
        this.publicRequestsPerMinute = Math.max(10, config.getRequestsPerMinute() / 2);
    }

    private Bucket newBucket() {
        Refill refill = Refill.intervally(requestsPerMinute, Duration.ofMinutes(1));
        Bandwidth limit = Bandwidth.classic(requestsPerMinute, refill);
        return Bucket4j.builder().addLimit(limit).build();
    }
    
    private Bucket newPublicBucket() {
        Refill refill = Refill.intervally(publicRequestsPerMinute, Duration.ofMinutes(1));
        Bandwidth limit = Bandwidth.classic(publicRequestsPerMinute, refill);
        return Bucket4j.builder().addLimit(limit).build();
    }

    public ConsumptionProbe consume(String userId) {
        Bucket bucket = buckets.computeIfAbsent(userId, k -> newBucket());
        return bucket.tryConsumeAndReturnRemaining(1);
    }
    
    /**
     * Consume a token from the public rate limiter bucket for the given IP address.
     * Public endpoints have a more restrictive rate limit.
     * 
     * @param ipAddress The client IP address
     * @return A consumption probe indicating whether the request was allowed
     */
    public ConsumptionProbe consumePublic(String ipAddress) {
        // Use a prefix to distinguish public buckets from authenticated user buckets
        String key = "public:" + ipAddress;
        Bucket bucket = buckets.computeIfAbsent(key, k -> newPublicBucket());
        return bucket.tryConsumeAndReturnRemaining(1);
    }
}
