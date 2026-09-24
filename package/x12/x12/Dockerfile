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

# The module runs unprivileged: it reads PHI from the inbox and needs no root for
# anything (nginx listens on 8888). Fixed uid/gid so volume ownership is predictable.
# A host-path inbox (or buffer) bound into the container must be readable, writable and
# renameable by uid/gid 10001 — the boot check refuses to start otherwise. Named volumes
# pick up the ownership set below when first created.
RUN groupadd --system --gid 10001 x12 && \
    useradd --system --uid 10001 --gid 10001 --no-create-home --home-dir /opt/module \
        --shell /usr/sbin/nologin x12

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

# Writable runtime paths for the unprivileged user: the self-signed cert (startup.sh),
# nginx logs, and nginx pid + temp files (the confs point them at /tmp).
RUN mkdir -p /opt/module/ssl /var/log/nginx && \
    chown -R 10001:10001 /opt/module/ssl /var/log/nginx

# Durable buffer (buffer.db) and the X12 inbox; Hub Node mounts named volumes or
# host paths per runtimeConfig.durability. Both must exist even when empty.
RUN mkdir -p /var/lib/module /var/lib/x12/inbox && \
    chown 10001:10001 /var/lib/module /var/lib/x12/inbox
VOLUME /var/lib/module
VOLUME /var/lib/x12/inbox

# Operations port (Hub-facing, fronted by nginx). There are no listener ports.
EXPOSE 8888

# Default environment (overridable at deploy time)
ENV INTERNAL_PORT=8889
# Heap follows the container limit (runtimeConfig resources.memoryMb) instead of a fixed
# -Xmx; the rest is left for metaspace, threads, SQLite and nginx.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"
ENV HUB_NODE_INSECURE=false
# MODULE_CONFIG (runtimeConfig.config as JSON) is injected by Hub Node. Without it
# the module falls back to the defaults baked into runtimeConfig.yml (copied above).

USER 10001:10001

CMD ["/opt/module/startup.sh"]
