## Quick Testing Guide - Pending Vendor Login API

### Endpoint Details
- **URL**: `POST http://localhost:8079/api/vendors/auth/login`
- **Port**: 8079 (API Gateway)
- **Service**: vendor-service (8082 internally)
- **Authentication**: None required (public endpoint)

### cURL Examples

#### 1. Successful Login
```bash
curl -X POST http://localhost:8079/api/vendors/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "pending_vendor1",
    "password": "securePassword123"
  }'
```

#### 2. With Verbose Output
```bash
curl -v -X POST http://localhost:8079/api/vendors/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "vendor_user", "password": "password123"}'
```

### Postman Steps

1. **Create New Request**
   - Method: POST
   - URL: `http://localhost:8079/api/vendors/auth/login`

2. **Headers Tab**
   - Content-Type: `application/json`

3. **Body Tab** (raw JSON)
   ```json
   {
     "username": "vendor_username",
     "password": "vendor_password"
   }
   ```

4. **Send** and review response

### Expected Scenarios

#### ✅ Success (200)
- Status: 200 OK
- Response includes: accessToken, tokenType, expiresIn, vendorId, etc.
- Use token as: `Authorization: Bearer <accessToken>`

#### ❌ Invalid Credentials (400)
- Status: 400 Bad Request
- Message: "Invalid username or password"

#### ❌ Vendor Not Found (404)
- Status: 404 Not Found
- Message: "Vendor not found with username: ..."

#### ❌ Not Pending Status (400)
- Status: 400 Bad Request
- Message: "Only vendors with PENDING status can login. Your status is: ACTIVE"

### Using the Token

Once you receive a token, include it in subsequent requests:

```bash
curl -X GET http://localhost:8079/api/vendors/auth/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

### Prerequisites

1. **Vendor must exist** in `buildledger_vendor.vendors` table
2. **Status must be** `PENDING`
3. **Username and passwordHash** must be set (populated during registration)
4. **Services running**:
   - api-gateway (port 8079)
   - vendor-service (port 8082)
   - eureka-server (port 8761)

### Swagger Documentation

Access API documentation at:
- http://localhost:8079/vendor/swagger-ui.html

The endpoint will appear under "Vendor Management" tag as:
- `POST /vendors/auth/login` - Pending vendor login [PUBLIC]

### Database Query to Check Vendor

```sql
SELECT vendor_id, username, status, name, email, password_hash 
FROM buildledger_vendor.vendors 
WHERE status = 'PENDING' 
LIMIT 5;
```

### Troubleshooting

| Issue | Solution |
|-------|----------|
| 404 Gateway Not Found | Check if api-gateway is running on 8079 |
| Connection refused | Start vendor-service on 8082 |
| Unauthorized 401 | Endpoint is public, shouldn't get 401. Check gateway filter |
| Invalid credentials 400 | Verify username/password in database |
| "Only PENDING vendors" error | Change vendor status to PENDING in database |

### Integration with Frontend

```javascript
const loginVendor = async (username, password) => {
  const response = await fetch('http://localhost:8079/api/vendors/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  
  const data = await response.json();
  
  if (data.success) {
    localStorage.setItem('vendorToken', data.data.accessToken);
    return data.data; // Contains vendorId, name, email, status
  } else {
    throw new Error(data.message);
  }
};
```

### Common Test Cases

1. **Create vendor via registration**
   ```bash
   POST /api/vendors/register
   { "name": "Test Vendor", "username": "test_vendor", "password": "pass123", ... }
   ```

2. **Login as pending vendor**
   ```bash
   POST /api/vendors/auth/login
   { "username": "test_vendor", "password": "pass123" }
   ```

3. **Upload document (requires token)**
   ```bash
   POST /api/vendors/{vendorId}/documents
   Content-Type: multipart/form-data
   Authorization: Bearer <token>
   ```

4. **Get vendor by ID (no token needed)**
   ```bash
   GET /api/vendors/{vendorId}
   ```

