#!/bin/sh
set -e

echo "Starting HL7 v2 Receiver Module..."

# --- Daemon precondition: MLLP listener port must be injected by Hub Node ---
# A daemon listener with no port is a misconfiguration, not a fallback case.
if [ -z "$LISTENER_PORT_MLLP" ]; then
    echo "FATAL: LISTENER_PORT_MLLP is not set. Hub Node must inject it from" >&2
    echo "       runtimeConfig.listenerPorts[name=mllp]. Refusing to start." >&2
    exit 1
fi
echo "MLLP listener port: $LISTENER_PORT_MLLP"
echo "Extension dir:      ${EXTENSION_DIR:-/opt/module/extensions}"

# Create SSL directory
mkdir -p /opt/module/ssl

# Check if running in insecure mode
INSECURE=${HUB_NODE_INSECURE:-false}

if [ "$INSECURE" = "true" ]; then
    echo "Running operations port in HTTP mode (HUB_NODE_INSECURE=true)"

    # Enable the HTTP server block and disable the HTTPS one.
    sed -i '/# INCLUDE_HTTP_SERVER_START/,/# INCLUDE_HTTP_SERVER_END/{
        /# INCLUDE_HTTP_SERVER_START/d
        /# INCLUDE_HTTP_SERVER_END/d
        s/^    # //
    }' /opt/module/nginx.conf

    sed -i '/server {/,/^    }$/{
        /listen 8888 ssl/,/^    }$/{
            s/^/    # /
        }
    }' /opt/module/nginx.conf
else
    echo "Running operations port in HTTPS mode (default)"
    if [ ! -f /opt/module/ssl/cert.pem ]; then
        echo "Generating self-signed SSL certificate..."
        openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
            -keyout /opt/module/ssl/key.pem \
            -out /opt/module/ssl/cert.pem \
            -subj "/CN=localhost/O=Auditmation/OU=HL7 v2 Module" \
            2>/dev/null
        echo "SSL certificate generated (valid for 3650 days)"
    else
        echo "Using existing SSL certificate"
    fi
fi

# Start nginx in background (fronts the operations port only; MLLP is direct)
echo "Starting nginx..."
nginx -c /opt/module/nginx.conf &
NGINX_PID=$!
sleep 1
if ! kill -0 $NGINX_PID 2>/dev/null; then
    echo "ERROR: nginx failed to start" >&2
    exit 1
fi
echo "nginx started (PID: $NGINX_PID)"

# Graceful shutdown
shutdown() {
    echo "Shutting down..."
    kill $JAVA_PID 2>/dev/null || true
    kill $NGINX_PID 2>/dev/null || true
    exit 0
}
trap shutdown TERM INT

# Start the Java process in foreground (HTTP ops on $INTERNAL_PORT + MLLP listener)
echo "Starting HL7 listener on operations port $INTERNAL_PORT / MLLP port $LISTENER_PORT_MLLP..."
java $JAVA_OPTS -jar /opt/module/hl7-listener.jar &
JAVA_PID=$!
echo "HL7 listener started (PID: $JAVA_PID)"
echo "HL7 v2 Receiver Module ready (operations on 8888)"

wait $JAVA_PID
