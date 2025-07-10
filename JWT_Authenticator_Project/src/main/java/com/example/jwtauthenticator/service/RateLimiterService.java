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
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiterService(ForwardConfig config) {
        this.requestsPerMinute = config.getRequestsPerMinute();
    }

    private Bucket newBucket() {
        Refill refill = Refill.intervally(requestsPerMinute, Duration.ofMinutes(1));
        Bandwidth limit = Bandwidth.classic(requestsPerMinute, refill);
        return Bucket4j.builder().addLimit(limit).build();
    }

    public ConsumptionProbe consume(String userId) {
        Bucket bucket = buckets.computeIfAbsent(userId, k -> newBucket());
        return bucket.tryConsumeAndReturnRemaining(1);
    }
}
