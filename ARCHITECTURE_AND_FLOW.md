# Pending Vendor Login API - Architecture & Flow

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         EXTERNAL REQUESTS                           │
│                (Postman, Frontend, Mobile App)                      │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                    POST /api/vendors/auth/login
                    {username, password}
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │      API GATEWAY (port 8079)           │
        │  ┌──────────────────────────────────┐  │
        │  │ JwtAuthenticationFilter          │  │
        │  │ - Checks if path is public       │  │
        │  │ - /api/vendors/auth/login ✓      │  │
        │  │ - Allows request through         │  │
        │  └──────────────────────────────────┘  │
        │              │                          │
        │              │ Route to vendor-service  │
        │              ▼                          │
        └────────────────────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │    VENDOR SERVICE (port 8082)          │
        │  ┌──────────────────────────────────┐  │
        │  │      VendorController            │  │
        │  │   @PostMapping("/auth/login")    │  │
        │  │                                  │  │
        │  │  calls vendorAuthService         │  │
        │  └──────────────────────────────────┘  │
        │              │                          │
        │              ▼                          │
        │  ┌──────────────────────────────────┐  │
        │  │   VendorAuthServiceImpl           │  │
        │  │                                  │  │
        │  │  1. Find vendor by username      │  │
        │  │  2. Validate PENDING status      │  │
        │  │  3. Verify password (BCrypt)     │  │
        │  │  4. Generate JWT token           │  │
        │  │  5. Build response               │  │
        │  └──────────────────────────────────┘  │
        │              │                          │
        │              ▼                          │
        │  ┌──────────────────────────────────┐  │
        │  │    VendorRepository              │  │
        │  │  findByUsername(username)        │  │
        │  │         │                        │  │
        │  │         ▼                        │  │
        │  │  MySQL Database                 │  │
        │  │  vendors table                  │  │
        │  └──────────────────────────────────┘  │
        │              │                          │
        │              ▼                          │
        │  ┌──────────────────────────────────┐  │
        │  │    JwtUtils.generateToken()      │  │
        │  │                                  │  │
        │  │  Creates JWT with:               │  │
        │  │  - subject: username             │  │
        │  │  - vendorId claim                │  │
        │  │  - status claim                  │  │
        │  │  - type: PENDING_VENDOR          │  │
        │  │  - expiration: 1 hour            │  │
        │  └──────────────────────────────────┘  │
        │              │                          │
        │              ▼                          │
        │  ┌──────────────────────────────────┐  │
        │  │   Return LoginResponseDTO        │  │
        │  │  {                               │  │
        │  │    accessToken: "JWT...",        │  │
        │  │    tokenType: "Bearer",          │  │
        │  │    expiresIn: 3600,              │  │
        │  │    vendorId: 1,                  │  │
        │  │    username: "vendor_user",      │  │
        │  │    name: "Vendor Name",          │  │
        │  │    email: "vendor@example.com",  │  │
        │  │    status: "PENDING"             │  │
        │  │  }                               │  │
        │  └──────────────────────────────────┘  │
        │              │                          │
        └──────────────┼──────────────────────────┘
                       │
                       ▼
        Return to Client (200 OK)
        {
          "success": true,
          "message": "Login successful",
          "data": { ...response above... }
        }
```

## Detailed Flow - Step by Step

```
1. CLIENT REQUEST
   ├─ HTTP Method: POST
   ├─ Endpoint: http://localhost:8079/api/vendors/auth/login
   ├─ Content-Type: application/json
   └─ Body: {"username": "vendor_user", "password": "securePass123"}

2. API GATEWAY PROCESSING
   ├─ JwtAuthenticationFilter intercepts request
   ├─ Checks if path is public: YES (/api/vendors/auth/login is public)
   ├─ Allows request to pass through
   └─ Routes to vendor-service at localhost:8082

3. VENDOR SERVICE ROUTING
   ├─ Spring Cloud Gateway routes to vendor-service
   ├─ Request reaches VendorController
   └─ Method: loginPendingVendor(VendorLoginRequestDTO)

4. CONTROLLER LAYER
   ├─ Validates request: @Valid annotation
   │  ├─ @NotBlank username
   │  └─ @NotBlank password
   ├─ Calls vendorAuthService.loginPendingVendor(request)
   └─ Returns ResponseEntity<ApiResponseDTO<VendorLoginResponseDTO>>

5. SERVICE LAYER
   ├─ VendorAuthServiceImpl.loginPendingVendor()
   │
   ├─ Step 5a: Find Vendor by Username
   │  ├─ Calls vendorRepository.findByUsername(request.getUsername())
   │  ├─ If NOT found → throw ResourceNotFoundException (404)
   │  └─ If found → continue to step 5b
   │
   ├─ Step 5b: Validate Vendor Status
   │  ├─ Check: vendor.getStatus() == PENDING
   │  ├─ If NOT pending (ACTIVE/SUSPENDED) → throw BadRequestException (400)
   │  │  Message: "Only vendors with PENDING status can login..."
   │  └─ If PENDING → continue to step 5c
   │
   ├─ Step 5c: Verify Password
   │  ├─ Call: passwordEncoder.matches(inputPassword, vendor.getPasswordHash())
   │  ├─ If match FAILS → throw BadRequestException (400)
   │  │  Message: "Invalid username or password"
   │  └─ If match SUCCEEDS → continue to step 5d
   │
   ├─ Step 5d: Generate JWT Token
   │  ├─ Call: jwtUtils.generateToken(username, vendorId, status)
   │  ├─ JWT Content:
   │  │  ├─ Header: {alg: HS256}
   │  │  ├─ Payload: {
   │  │  │    sub: "vendor_user",
   │  │  │    iat: 1735000000,
   │  │  │    exp: 1735003600,
   │  │  │    vendorId: 1,
   │  │  │    status: "PENDING",
   │  │  │    type: "PENDING_VENDOR"
   │  │  │  }
   │  │  └─ Signature: HMAC256(header.payload, secret)
   │  └─ Token created successfully
   │
   ├─ Step 5e: Build Response
   │  └─ Create VendorLoginResponseDTO with:
   │     ├─ accessToken: "eyJhbGc..."
   │     ├─ tokenType: "Bearer"
   │     ├─ expiresIn: 3600 (seconds)
   │     ├─ vendorId: 1
   │     ├─ username: "vendor_user"
   │     ├─ name: "Vendor Company"
   │     ├─ email: "vendor@example.com"
   │     └─ status: "PENDING"
   │
   └─ Return response to controller

6. RESPONSE HANDLING
   ├─ Controller wraps response in ApiResponseDTO
   ├─ Sets HTTP Status: 200 OK
   ├─ JSON Body:
   │  └─ {
   │       "success": true,
   │       "message": "Login successful",
   │       "data": { ...VendorLoginResponseDTO... }
   │     }
   └─ Sends back through API Gateway

7. CLIENT RECEIVES
   ├─ Status: 200 OK
   ├─ Body: Complete login response with JWT token
   ├─ Token can now be used for:
   │  ├─ Upload documents
   │  ├─ Access vendor endpoints (if needed)
   │  └─ Authenticated operations
   └─ Token expires in 1 hour (3600 seconds)
```

## Database Schema - Vendors Table

```sql
CREATE TABLE vendors (
  vendor_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  contact_info VARCHAR(200),
  email VARCHAR(100) UNIQUE NOT NULL,
  phone VARCHAR(15),
  category VARCHAR(100),
  address VARCHAR(300),
  username VARCHAR(50) UNIQUE,                    -- For login
  password_hash VARCHAR(255),                     -- BCrypt encoded
  status ENUM('PENDING','ACTIVE','SUSPENDED') DEFAULT 'PENDING',
  user_id BIGINT,                                 -- Links to IAM user after approval
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

## Security Implementation

### Password Storage
```
Input Password: "MySecurePass123"
         ↓
BCrypt Encoding (cost=12)
         ↓
Stored: "$2a$12$R9h/cIPz0gi..." (60 characters)
```

### JWT Token Structure
```
Header.Payload.Signature

Example Token:
eyJhbGciOiJIUzI1NiJ9.
eyJzdWIiOiJ2ZW5kb3JfdXNlciIsInZlbmRvcklkIjoxLCJzdGF0dXMiOiJQRU5ESU5HIiwiaWF0IjoxNzM1MDAwMDAwLCJleHAiOjE3MzUwMDM2MDB9.
abcd1234efgh5678...

Decoded Payload:
{
  "sub": "vendor_user",
  "vendorId": 1,
  "status": "PENDING",
  "type": "PENDING_VENDOR",
  "iat": 1735000000,
  "exp": 1735003600
}
```

### Request/Response Validation
```
Request Validation:
├─ @Valid annotation triggers Bean Validation
├─ @NotBlank checks for null/empty strings
├─ ValidationException thrown if invalid
└─ Returns 400 Bad Request with details

Response Validation:
├─ Lombok @Data generates getters/setters
├─ Jackson auto-serializes to JSON
├─ @JsonProperty can customize field names (if needed)
└─ All fields included in JSON response
```

## Security Considerations

### ✅ What's Secure
- Passwords stored as BCrypt hashes (not reversible)
- JWT signed with secret key (cannot be tampered)
- Only PENDING vendors can login
- API Gateway allows public access only for this endpoint
- Bearer token required for subsequent requests
- Token has expiration time (1 hour default)

### ⚠️ What to Monitor
- JWT secret should be stored in environment variables
- HTTPS should be used in production (not HTTP)
- Rate limiting should be implemented on login endpoint
- Failed login attempts should be logged
- Token refresh mechanism might be needed for long sessions

### 🔒 Best Practices Applied
- Password never logged or transmitted in clear text
- Vendor status validated before token generation
- Comprehensive error messages without revealing too much
- Transaction support (@Transactional)
- Logging for audit trail
- Exception handling with custom exceptions

## Integration Timeline

### Request flows:
1. Client → API Gateway (8079)
2. Gateway → Vendor Service (8082)
3. Service → MySQL Database
4. Database → Service
5. Service → Gateway
6. Gateway → Client

### Timing (typical):
- Database lookup: 1-5ms
- Password verification: 50-100ms (BCrypt with cost=12)
- JWT generation: 2-5ms
- Total response time: ~60-110ms

