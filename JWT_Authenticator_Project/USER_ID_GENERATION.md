# User ID Generation System

This document describes the implementation of the DOMBR user ID generation system.

## Overview

The system generates sequential user IDs with the following format:
- Fixed prefix: "DOMBR"
- 6-digit, zero-padded, incrementing numeric suffix (e.g., 000001, 000002, etc.)
- Example: DOMBR000001, DOMBR000002, DOMBR000003, etc.

These IDs are used as the primary key in the `users` table, replacing the previous numeric ID.

## Implementation Approaches

The system uses a multi-layered approach to ensure sequential ID generation:

1. **PostgreSQL Function**: Uses a custom PostgreSQL function `generate_dombr_id()` that generates sequential IDs
2. **Direct Sequence Access**: Falls back to directly accessing the PostgreSQL sequence if the function fails
3. **In-Memory Counter**: Uses an AtomicLong counter as a last resort to ensure sequential IDs

**Important**: Before using the system, you need to ensure the sequence and function exist in the database. You can use the provided script or API endpoint to initialize them.

All approaches guarantee that IDs will be sequential, starting from DOMBR000001 and incrementing one by one.

## Implementation Details

### PostgreSQL Sequence

The system uses a dedicated PostgreSQL sequence for generating the numeric part of the user IDs:

```sql
CREATE SEQUENCE dombr_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 999999
    CACHE 1;
```

### PostgreSQL Function

A PostgreSQL function is used to generate the user IDs:

```sql
CREATE OR REPLACE FUNCTION generate_dombr_id()
RETURNS VARCHAR AS $$
DECLARE
    next_val INTEGER;
    formatted_id VARCHAR;
BEGIN
    -- Get the next value from the sequence
    SELECT nextval('dombr_user_id_seq') INTO next_val;
    
    -- Format the ID with leading zeros
    formatted_id := 'DOMBR' || LPAD(next_val::TEXT, 6, '0');
    
    RETURN formatted_id;
END;
$$ LANGUAGE plpgsql;
```

### Java Implementation

The system uses a multi-layered approach for generating sequential IDs:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public String generateDombrUserId() {
    try {
        // Use the PostgreSQL function to generate a sequential ID
        String userId = jdbcTemplate.queryForObject(
            "SELECT generate_dombr_id()", String.class);
        
        log.info("Generated sequential DOMBR user ID: {}", userId);
        return userId;
    } catch (Exception e) {
        log.error("Error using generate_dombr_id() function: {}", e.getMessage());
        
        // Fallback: Direct sequence access
        try {
            Long nextVal = jdbcTemplate.queryForObject(
                "SELECT nextval('dombr_user_id_seq')", Long.class);
            
            String userId = String.format("DOMBR%06d", nextVal);
            log.info("Generated DOMBR user ID using direct sequence: {}", userId);
            return userId;
        } catch (Exception e2) {
            // Last resort: Use in-memory counter
            return generateSimpleDombrUserId();
        }
    }
}
```

## Concurrency Handling

The system handles concurrent user registrations in the following ways:

1. **PostgreSQL Sequence**: PostgreSQL sequences are inherently thread-safe and designed for concurrent access.

2. **Pessimistic Locking**: The Java fallback implementation uses pessimistic locking to prevent race conditions:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM IdSequence s WHERE s.prefix = :prefix")
Optional<IdSequence> findByPrefixWithLock(@Param("prefix") String prefix);
```

3. **Transactional Boundaries**: All ID generation operations are wrapped in transactions to ensure atomicity.

## Edge Cases

### Maximum Value Reached

If the sequence reaches the maximum value (999999), the system will throw an exception:

```
Maximum user ID limit reached (DOMBR999999)
```

### Sequence Reset

The sequence can be reset for testing purposes using the API endpoint:

```
POST /api/id-generator/reset/DOMBR?startNumber=0
```

## API Endpoints

The system provides the following API endpoints for managing user IDs:

- `POST /api/id-generator/user-id/init-sequence`: Initialize the DOMBR sequence and function (run this first!)
- `POST /api/id-generator/user-id/generate`: Generate a new DOMBR user ID using the sequence-based approach
- `GET /api/id-generator/user-id/simple`: Generate a new DOMBR user ID using the simple approach (no database dependency)
- `GET /api/id-generator/user-id/preview`: Preview the next DOMBR user ID without generating it
- `GET /api/id-generator/current/DOMBR`: Get the current sequence value for DOMBR
- `POST /api/id-generator/reset/DOMBR`: Reset the DOMBR sequence (use with caution!)

## Initialization

Before using the system, you need to initialize the sequence and function. You can do this in one of two ways:

1. **Using the API endpoint**:
   ```
   curl -X POST http://localhost:8080/api/id-generator/user-id/init-sequence
   ```

2. **Using database migrations**:
   - The sequence and function are automatically created during application startup through Flyway migrations
   - Migration file: `V8__Add_DOMBR_Sequence_And_Function.sql`

Note: The initialization process only creates the sequence and function needed for ID generation. It does not modify the existing id_sequences table.

## Configuration

The user ID prefix and padding are configurable in `application.properties`:

```properties
user.id.prefix=DOMBR
user.id.number.padding=6
```

## Integration with User Registration

During user registration, the system automatically generates a sequential DOMBR user ID and assigns it as the primary key (`id` field) of the `User` entity:

```java
// Generate sequential user ID
String dombrId = idGeneratorService.generateDombrUserId();
log.info("Generated sequential user ID: {}", dombrId);

User newUser = User.builder()
        .id(dombrId) // Set as primary key
        .username(request.username())
        .password(request.password())
        // ... other fields ...
        .build();
```

The system ensures that IDs are generated sequentially, starting from DOMBR000001 and incrementing one by one.

## Testing

The system includes comprehensive tests to verify:
- Correct ID format
- Sequential generation
- Concurrent access safety
- Edge case handling

## Monitoring

The current sequence value can be monitored using the API endpoint:

```
GET /api/id-generator/current/DOMBR
```

This returns the current sequence value and other metadata.

## Troubleshooting

If you encounter issues with ID generation, try the following steps:

1. **Initialize the sequence**: Run the initialization endpoint or script
2. **Check the logs**: Look for error messages related to sequence or function creation
3. **Use the simple method**: If database issues persist, use the simple ID generation method
4. **Reset the sequence**: If needed, reset the sequence to start from 1

### Common Errors

If you see errors like:

```
Error using generate_dombr_id() function: StatementCallback; bad SQL grammar [SELECT generate_dombr_id()]
Error using direct sequence access: StatementCallback; uncategorized SQLException for SQL [SELECT nextval('dombr_user_id_seq')]
```

It means the sequence and function don't exist in the database. Run the initialization endpoint or script to create them.