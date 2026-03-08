#!/bin/sh

echo "DEBUG DB_URL=$DB_URL"
echo "DEBUG SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL"

if [ -n "$DB_URL" ] && [ -z "$SPRING_DATASOURCE_URL" ]; then
    # Convert postgres:// to jdbc:postgresql:// if needed
    if echo "$DB_URL" | grep -q "^postgres://"; then
        export SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_URL#postgres://}"
    else
        export SPRING_DATASOURCE_URL="$DB_URL"
    fi
fi

echo "DEBUG FINAL SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL"

exec java $JAVA_OPTS -jar app.jar