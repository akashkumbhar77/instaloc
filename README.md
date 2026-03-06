# InstLoc - Instagram Reel to Map

Extract locations from Instagram Reels and visualize them on a map. A travel utility app that transforms social media content into actionable location data.

## Features

- **Video Frame Extraction** - Uses FFmpeg with scene detection to extract key frames from videos
- **Instagram Download** - Downloads Instagram Reels via yt-dlp with caption/description capture
- **AI Location Extraction** - Two-step AI extraction (caption-first, vision fallback) using GPT-4o-mini
- **Google Places Grounding** - Validates and enriches locations with Google Places API
- **Async Processing** - Job queue system for long-running extractions
- **URL Caching** - Caches results for repeat URL lookups
- **JWT Authentication** - Secure API with Supabase JWT validation
- **Production Ready** - Docker + Render.com deployment config

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 3.2.5 |
| AI | Spring AI + OpenAI GPT-4o-mini |
| Video Processing | FFmpeg + yt-dlp |
| Database | PostgreSQL + PostGIS |
| Auth | Supabase JWT |
| Deployment | Docker + Render.com |

## Quick Start

### Prerequisites

- Java 21
- Maven 3.9+
- FFmpeg
- yt-dlp
- Python 3

### Local Development

```bash
# Clone the repository
git clone https://github.com/akashkumbhar77/instaloc.git
cd instaloc

# Copy environment template
cp .env.template .env

# Edit .env with your API keys
# - OPENAI_API_KEY
# - GEMINI_API_KEY
# - GOOGLE_MAPS_API_KEY
# - SUPABASE_JWT_SECRET

# Run the application
./mvnw spring-boot:run
```

### API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/extract` | Submit extraction job (async) |
| GET | `/api/v1/extract/{jobId}/status` | Get job status |
| POST | `/api/v1/extract/sync` | Extract locations (sync) |
| POST | `/api/v1/upload` | Upload video file |
| GET | `/api/v1/health` | Health check |

### Example Request

```bash
curl -X POST http://localhost:8080/api/v1/extract \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{"reelUrl": "https://www.instagram.com/reel/..."}'
```

## Docker

```bash
# Build the image
docker build -t instaloc .

# Run the container
docker run -p 8080:8080 \
  -e OPENAI_API_KEY=your_key \
  -e GEMINI_API_KEY=your_key \
  -e GOOGLE_MAPS_API_KEY=your_key \
  -e SUPABASE_JWT_SECRET=your_secret \
  instaloc
```

## Deployment (Render.com)

1. Connect GitHub repository to Render.com
2. Create a new Blueprint deployment
3. Add required secrets:
   - `openai_api_key`
   - `gemini_api_key`
   - `google_maps_api_key`
   - `supabase_jwt_secret`
4. Render will create:
   - Web service (Docker container)
   - PostgreSQL database with PostGIS

See `render.yaml` for infrastructure configuration.

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  Instagram  │────▶│  yt-dlp      │────▶│  FFmpeg     │
│  Reel URL   │     │  Download    │     │  Frames     │
└─────────────┘     └──────────────┘     └─────────────┘
                                                 │
                                                 ▼
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  Google     │◀────│  AI Vision   │◀────│  Caption    │
│  Places     │     │  Extraction  │     │  First      │
└─────────────┘     └──────────────┘     └─────────────┘
       │
       ▼
┌─────────────┐
│  Location   │
│  Entities  │
└─────────────┘
```

## Token Optimization

The extraction pipeline optimizes AI token usage:

1. **Caption-First** - Try text-only extraction from Instagram caption (cheap)
2. **Vision Fallback** - Only use vision AI if caption yields no results (expensive)
3. **Scene Detection** - Extract max 5 key frames using FFmpeg scene detection
4. **Image Downscaling** - Downscale frames to 768px width

## License

MIT License - See LICENSE file for details.
