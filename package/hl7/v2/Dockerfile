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

# Copy the uber JAR (listener + materializer + buffer + producer)
COPY java/target/hl7-listener-1.0.0.jar /opt/module/hl7-listener.jar

# Copy OpenAPI specification and connection profile
COPY *.yml /opt/module/

# Copy nginx configuration
COPY nginx.conf /opt/module/nginx.conf

# Copy startup script
COPY startup.sh /opt/module/startup.sh
RUN chmod +x /opt/module/startup.sh

# Create directories for nginx
RUN mkdir -p /var/log/nginx /var/run && \
    chmod 755 /var/log/nginx /var/run

# Extension packs are baked into the image at build time (DESIGN §7.3), NOT
# mounted at runtime. The base image ships none — EXTENSION_DIR exists but is
# empty (the boot loader tolerates that). A vendor image is a derived build that
# adds packs, e.g.:
#     RUN npm install @zerobias-org/hl7-extension-epic-adt
#     COPY extensions-staging/ /opt/module/extensions/
#         # -> /opt/module/extensions/<pack>/extensions/manifest.json …
RUN mkdir -p /opt/module/extensions

# Durable buffer (buffer.db) lives here; Hub Node mounts a named volume.
# Extension packs do NOT live here — they are baked into the image (above).
VOLUME /var/lib/module

# Operations port (Hub-facing, fronted by nginx). The MLLP listener port is
# deliberately NOT EXPOSE'd: EXPOSE is documentation-only and ignored when the
# orchestrator passes explicit port maps, and Hub Node always does (DESIGN §3.3).
EXPOSE 8888

# Default environment (overridable at deploy time)
ENV INTERNAL_PORT=8889
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV HUB_NODE_INSECURE=false
ENV EXTENSION_DIR=/opt/module/extensions
# LISTENER_PORT_MLLP is injected by Hub Node from runtimeConfig.listenerPorts.
# There is NO default — the module refuses to start without it (DESIGN §3.2).

CMD ["/opt/module/startup.sh"]
