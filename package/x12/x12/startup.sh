#!/bin/sh
set -e

echo "Starting X12 Receiver Module..."

# --- Daemon precondition: the inbox directory must exist and be writable ---
# The module renames consumed files in place (.done / .error); a read-only or
# missing inbox is a misconfiguration, not a fallback case. The path list comes
# from MODULE_CONFIG (runtimeConfig.config.sources[].path); the Java process
# validates every configured source at boot and refuses to start otherwise.
echo "Buffer volume:      /var/lib/module"
# Only whether it is set: the value is the operator's whole config and has no place in logs.
if [ -n "$MODULE_CONFIG" ]; then
    echo "MODULE_CONFIG:      set"
else
    echo "MODULE_CONFIG:      absent (using runtimeConfig.yml defaults)"
fi

# Create SSL directory
mkdir -p /opt/module/ssl

# Check if running in insecure mode
INSECURE=${HUB_NODE_INSECURE:-false}

# Select the nginx config by mode — no runtime rewriting. The HTTPS config
# (default) and the plain-HTTP config (HUB_NODE_INSECURE=true) are two complete,
# committed files; we just point nginx at the right one.
if [ "$INSECURE" = "true" ]; then
    echo "Running operations port in HTTP mode (HUB_NODE_INSECURE=true)"
    NGINX_CONF=/opt/module/nginx-insecure.conf
else
    echo "Running operations port in HTTPS mode (default)"
    NGINX_CONF=/opt/module/nginx.conf
    if [ ! -f /opt/module/ssl/cert.pem ]; then
        echo "Generating self-signed SSL certificate..."
        openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
            -keyout /opt/module/ssl/key.pem \
            -out /opt/module/ssl/cert.pem \
            -subj "/CN=localhost/O=Auditmation/OU=X12 Module" \
            2>/dev/null
        echo "SSL certificate generated (valid for 3650 days)"
    else
        echo "Using existing SSL certificate"
    fi
fi

# Start nginx in background (fronts the operations port only)
echo "Starting nginx ($NGINX_CONF)..."
nginx -c "$NGINX_CONF" &
NGINX_PID=$!
sleep 1
if ! kill -0 $NGINX_PID 2>/dev/null; then
    echo "ERROR: nginx failed to start" >&2
    exit 1
fi
echo "nginx started (PID: $NGINX_PID)"

# Graceful shutdown: wait for Java to finish its shutdown hook (pollers stop, the buffer
# closes) before exiting — exiting right after kill lets the runtime tear the container
# down mid-commit. Then stop nginx the same way.
JAVA_PID=
shutdown() {
    echo "Shutting down..."
    if [ -n "$JAVA_PID" ]; then
        kill "$JAVA_PID" 2>/dev/null || true
        wait "$JAVA_PID" 2>/dev/null || true
    fi
    kill "$NGINX_PID" 2>/dev/null || true
    wait "$NGINX_PID" 2>/dev/null || true
    exit 0
}
trap shutdown TERM INT

# Start the Java process in foreground (HTTP ops on $INTERNAL_PORT + inbox poller)
echo "Starting X12 receiver on operations port $INTERNAL_PORT..."
java $JAVA_OPTS -jar /opt/module/x12-receiver.jar &
JAVA_PID=$!
echo "X12 receiver started (PID: $JAVA_PID)"
echo "X12 Receiver Module ready (operations on 8888)"

wait $JAVA_PID
