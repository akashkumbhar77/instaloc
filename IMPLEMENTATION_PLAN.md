# InstLoc Backend Implementation Plan

**Project**: InstLoc - Reel-to-Map Travel Utility
**Target**: MVP by mid-March 2026 for Vietnam field testing
**Priority**: Core "URL in → Coordinates out" pipeline

---

## Completed Phases

### Phase 1: Project Setup & Environment (Day 1)
- [x] pom.xml with all dependencies
- [x] application.yml configuration
- [x] .env template
- [x] Database entities and repositories
- [x] Spring AI configuration

### Phase 2: FFmpeg Bridge (Day 2)
- [x] FfmpegService for video frame extraction
- [x] 1 fps frame extraction
- [x] Temp file cleanup

### Phase 3: Spring AI Vision Integration (Day 3)
- [x] VisionExtractionService with GPT-4o-mini
- [x] Location extraction from video frames
- [x] JSON parsing with Jackson

### Phase 4: Google Places Grounding (Day 4)
- [x] GroundingService with Google Places API
- [x] Location search with Vietnam context
- [x] Coordinate storage

### Phase 5: API Controllers (Day 5)
- [x] POST /api/v1/extract (async)
- [x] GET /api/v1/extract/{jobId}/status
- [x] GET /api/v1/locations
- [x] DELETE /api/v1/locations/{id}

### Phase 6: Supabase Integration
- [x] Spring Security with OAuth2 Resource Server
- [x] JWT validation with Supabase JWT secret
- [x] User ownership for locations

---

## API Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/v1/health` | GET | No | Health check |
| `/api/v1/status` | GET | No | FFmpeg/yt-dlp status |
| `/api/v1/extract` | POST | Yes | Async extraction |
| `/api/v1/extract/{jobId}/status` | GET | Yes | Poll job status |
| `/api/v1/extract/sync` | POST | Yes | Sync extraction |
| `/api/v1/locations` | GET | Yes | User's locations |
| `/api/v1/locations/{id}` | GET | Yes | Single location |
| `/api/v1/locations/{id}` | DELETE | Yes | Delete location |

---

## Testing with Supabase JWT

### Get JWT Token from Supabase

1. Go to your Supabase Dashboard
2. Open **SQL Editor** and run:
```sql
select auth.jwt();
```
Or use the **JWT Debugger** at https://jwt.io to decode

### Test Commands

```bash
# Set environment variables
export OPENAI_API_KEY="sk-..."
export GOOGLE_MAPS_API_KEY="..."
export SUPABASE_JWT_SECRET="your-jwt-secret-from-supabase"
export DB_URL="jdbc:h2:mem:testdb"
export DB_USER="sa"

# Start the server
./mvnw spring-boot:run

# Test with JWT header
curl -X POST http://localhost:8080/api/v1/extract \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE" \
  -d '{"reelUrl": "https://www.instagram.com/reel/..."}'

# Test public endpoints (no auth)
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/api/v1/status
```

### How to get JWT Secret

1. Go to Supabase Dashboard
2. Settings → API
3. Copy **JWT Secret**

---

## Environment Variables Required

```
OPENAI_API_KEY=sk-...
GOOGLE_MAPS_API_KEY=...
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_JWT_SECRET=your-jwt-secret
DB_URL=jdbc:h2:mem:testdb
DB_USER=sa
```

---

## Technical Notes

### Database Schema (PostgreSQL + PostGIS)
```sql
CREATE TABLE locations (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    address TEXT,
    place_id VARCHAR(255) UNIQUE,
    category VARCHAR(50),
    confidence DOUBLE PRECISION,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    reel_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE extraction_jobs (
    id BIGSERIAL PRIMARY KEY,
    url VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    result_json TEXT,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reel_cache (
    id BIGSERIAL PRIMARY KEY,
    reel_url VARCHAR(500) UNIQUE NOT NULL,
    location_ids TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```
