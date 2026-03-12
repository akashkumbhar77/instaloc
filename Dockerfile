# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven files first for better caching
# Based on your repo structure, these are in the root
COPY pom.xml .
COPY src ./src

# Install Maven and build the application
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install system dependencies essential for video processing
RUN apk add --no-cache \
    ffmpeg \
    python3 \
    py3-pip \
    curl

# Install yt-dlp
RUN pip3 install --no-cache-dir yt-dlp --break-system-packages || pip3 install --no-cache-dir yt-dlp

# Copy the built artifact from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Copy entrypoint script from the root of your repo
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Create temp directory for video processing
RUN mkdir -p /tmp/instaloc && chmod 755 /tmp/instaloc

# Expose port 8080 for Spring Boot
EXPOSE 8080

# Environment variables for JVM and internal paths
ENV JAVA_OPTS="-Xmx256m -Xms128m"
ENV TMP_DIR=/tmp/instaloc

# Run the application using the entrypoint script
ENTRYPOINT ["/entrypoint.sh"]