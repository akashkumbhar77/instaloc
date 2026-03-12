# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven files first for better caching
COPY pom.xml .
COPY src ./src

# Install Maven and build
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install system dependencies
RUN apk add --no-cache \
        ffmpeg \
        python3 \
        py3-pip \
        curl

# Install yt-dlp (using --break-system-packages for recent Alpine versions)
RUN pip3 install --no-cache-dir yt-dlp --break-system-packages || pip3 install --no-cache-dir yt-dlp

# Copy the built artifact
COPY --from=builder /app/target/*.jar app.jar

# Copy entrypoint script
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Create temp directory for video processing
RUN mkdir -p /tmp/instaloc && chmod 755 /tmp/instaloc

# Expose port
EXPOSE 8080

# Environment variables
ENV JAVA_OPTS="-Xmx256m -Xms128m"
ENV TMP_DIR=/tmp/instaloc

# Run the application
ENTRYPOINT ["/entrypoint.sh"]
