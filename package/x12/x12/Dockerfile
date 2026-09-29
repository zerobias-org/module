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

# The module runs unprivileged: it reads PHI from the inbox and needs root for nothing
# (nginx listens on 8888). A fixed uid/gid keeps volume ownership predictable: a host-path
# inbox or buffer bound into the container must be readable, writable and renameable by
# 10001, or the boot check refuses to start. Named volumes take the ownership set below
# when first created; one created by an older, root-running image keeps root ownership,
# which startup.sh repairs on its way to 10001 (see the note above CMD).
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

# Writable runtime paths for the unprivileged user: the self-signed cert (startup.sh) and the
# nginx logs; nginx's pid and temp files go to /tmp (both confs). The code and configs
# above stay root-owned, so the process cannot rewrite what it runs.
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
# Heap follows the container limit (runtimeConfig.yml resources.memoryMb) instead of a fixed
# -Xmx that silently stops tracking it: 70% leaves room for metaspace, threads, SQLite's
# page cache and the parser's off-heap buffers.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"
ENV HUB_NODE_INSECURE=false
# MODULE_CONFIG (runtimeConfig.config as JSON) is injected by Hub Node. Without it
# the module falls back to the defaults baked into runtimeConfig.yml (copied above).

# No USER: startup.sh starts as root only to repair the ownership of volumes an older,
# root-running image created, and only what 10001 cannot write (buffer: chown -R; inbox:
# group 10001 with g+rwx on the directory, owner untouched), then re-executes itself as
# 10001:10001 with setpriv (util-linux, in the base image) before nginx or java start. Started with --user 10001 instead, it repairs
# nothing and names whatever 10001 cannot write, with the chown that fixes it.
CMD ["/opt/module/startup.sh"]
