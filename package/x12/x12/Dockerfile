FROM eclipse-temurin:17-jre
LABEL org.opencontainers.image.source="https://github.com/zerobias-org/module"

# Install nginx, openssl, and ca-certificates
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        nginx \
        openssl \
        ca-certificates && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /opt/module

# Copy the uber JAR (poller + parser/materializer + buffer + producer)
COPY java/target/x12-receiver-1.0.0.jar /opt/module/x12-receiver.jar

# Copy OpenAPI specification and connection profile
COPY *.yml /opt/module/

# Copy nginx configurations (HTTPS default + plain-HTTP for HUB_NODE_INSECURE)
COPY nginx.conf /opt/module/nginx.conf
COPY nginx-insecure.conf /opt/module/nginx-insecure.conf

# Copy startup script
COPY startup.sh /opt/module/startup.sh
RUN chmod +x /opt/module/startup.sh

# Create directories for nginx
RUN mkdir -p /var/log/nginx /var/run && \
    chmod 755 /var/log/nginx /var/run

# Durable buffer (buffer.db) and the X12 inbox; Hub Node mounts named volumes or
# host paths per runtimeConfig.durability. Both must exist even when empty.
RUN mkdir -p /var/lib/module /var/lib/x12/inbox
VOLUME /var/lib/module
VOLUME /var/lib/x12/inbox

# Operations port (Hub-facing, fronted by nginx). There are no listener ports.
EXPOSE 8888

# Default environment (overridable at deploy time)
ENV INTERNAL_PORT=8889
# Heap follows the container limit (runtimeConfig.yml resources.memoryMb) instead of a fixed
# -Xmx that silently stops tracking it: 70% leaves room for metaspace, threads, SQLite's
# page cache and the parser's off-heap buffers.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"
ENV HUB_NODE_INSECURE=false
# MODULE_CONFIG (runtimeConfig.config as JSON) is injected by Hub Node. Without it
# the module falls back to the defaults baked into runtimeConfig.yml (copied above).

CMD ["/opt/module/startup.sh"]
