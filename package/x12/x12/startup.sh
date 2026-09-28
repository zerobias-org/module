#!/bin/sh
set -e

# The module's unprivileged identity (Dockerfile: user x12). nginx and java only ever run as it.
MODULE_UID=10001
MODULE_GID=10001
# The two volumes the image declares (runtimeConfig.yml durability).
BUFFER_VOLUME=/var/lib/module
INBOX_DIR=/var/lib/x12/inbox
BUFFER_DB=${BUFFER_DB:-$BUFFER_VOLUME/buffer.db}
BUFFER_DIR=$(dirname "$BUFFER_DB")

# True when every path the buffer writes is writable by "$@" (a command prefix: none for the
# current user, as_module_user for 10001): the directory and each SQLite file that exists (the
# -wal and -shm files count: SQLite writes them too). UNWRITABLE names the first that is not.
buffer_writable() {
    for f in "$BUFFER_DIR" "$BUFFER_DB" "$BUFFER_DB-wal" "$BUFFER_DB-shm"; do
        if [ "$f" = "$BUFFER_DIR" ] || [ -e "$f" ]; then
            if ! "$@" test -w "$f"; then
                UNWRITABLE=$f
                return 1
            fi
        fi
    done
}

as_module_user() {
    setpriv --reuid="$MODULE_UID" --regid="$MODULE_GID" --init-groups -- "$@"
}

# --- Started as root: repair volume ownership, then drop to 10001 for good ---
# The images before this one ran as root, so the named volumes they created (x12-buffer,
# x12-inbox) are root-owned and stay so across the upgrade. Rather than refuse to boot until
# someone chowns them by hand, the entrypoint starts as root, fixes only what 10001 cannot
# write, and re-executes itself as 10001:10001; nginx and java never run as root. A volume
# 10001 can already write (a fresh one takes the image's ownership) is not touched, so after
# the first boot this does nothing.
#   - Buffer volume: module-owned data (buffer.db, its WAL), so chown -R — of the volume
#     itself, and only when BUFFER_DB is in it: a root chown -R never follows an env value
#     elsewhere (a buffer placed outside the volume gets the error below instead).
#   - Inbox: never its owner (often the producer, e.g. a host SFTP user bound in, which must
#     keep writing). Group 10001 with g+rwx on the directory only: the module writes through
#     its group (setpriv --regid/--init-groups), and renaming a file to .done/.error needs
#     write on the directory, not on the file, so the files in it stay as their producer left
#     them. A mount that refuses even that (read-only, root_squash) gets a warning with the
#     fix, and the Java boot probe then refuses to start, naming the source.
# Only the default inbox is repaired: the image has no JSON tool (no jq, no python3) to read
# other MODULE_CONFIG sources[].path, and the Java boot probe names any of those it cannot
# write. Started as non-root (docker --user, or a runtime that sets one) none of this runs;
# the check below says what is not writable and how to fix it.
if [ "$(id -u)" = 0 ]; then
    case "$BUFFER_DIR" in
        "$BUFFER_VOLUME" | "$BUFFER_VOLUME"/*)
            if ! buffer_writable as_module_user; then
                echo "Fixing ownership for uid $MODULE_UID: $BUFFER_VOLUME"
                chown -R "$MODULE_UID:$MODULE_GID" "$BUFFER_VOLUME" \
                    || echo "WARNING: could not chown $BUFFER_VOLUME" >&2
            fi
            ;;
    esac
    if [ -d "$INBOX_DIR" ] && ! as_module_user test -w "$INBOX_DIR"; then
        echo "Fixing group write for gid $MODULE_GID: $INBOX_DIR"
        { chgrp "$MODULE_GID" "$INBOX_DIR" && chmod g+rwx "$INBOX_DIR" \
            && as_module_user test -w "$INBOX_DIR"; } \
            || echo "WARNING: $INBOX_DIR is still not writable by uid $MODULE_UID; give group $MODULE_GID write on it (chgrp $MODULE_GID + chmod g+rwx)" >&2
    fi
    # What USER 10001 would have set (the x12 user's home); root's HOME is not writable by 10001.
    export HOME=/opt/module
    exec setpriv --reuid="$MODULE_UID" --regid="$MODULE_GID" --init-groups -- "$0" "$@"
fi

echo "Starting X12 Receiver Module..."

# --- Daemon precondition: the inbox directory must exist and be writable ---
# The module renames consumed files in place (.done / .error); a read-only or
# missing inbox is a misconfiguration, not a fallback case. The path list comes
# from MODULE_CONFIG (runtimeConfig.config.sources[].path); the Java process
# validates every configured source at boot and refuses to start otherwise.
#
# From here on the script runs unprivileged (10001 after the drop above, or whatever uid the
# container was started with), so the buffer volume must be writable by it. When it is not
# (a root-owned volume the root branch could not fix, or a non-root start that skipped it),
# SQLite would fail with a bare "unable to open database file"; say what is wrong and how to
# fix it instead.
echo "Buffer volume:      $BUFFER_DIR"
if ! buffer_writable; then
    echo "ERROR: $UNWRITABLE is not writable by uid $(id -u); chown the buffer volume to $(id -u):$(id -g)" >&2
    exit 1
fi
# Only WHETHER it is set, never the value: MODULE_CONFIG is the operator's whole config
# (paths, and whatever a future field carries) and has no place in container logs. The
# old `${MODULE_CONFIG:+set}${MODULE_CONFIG:-...}` form printed the value whenever it was set.
if [ -n "${MODULE_CONFIG}" ]; then
    echo "MODULE_CONFIG:      set"
else
    echo "MODULE_CONFIG:      absent (using runtimeConfig.yml defaults)"
fi

# SSL directory: owned by the unprivileged user in the image (the rest of /opt/module is
# root-owned); the committed nginx.conf reads the cert from here, so it cannot move.
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
        # set -e would end the script here with no word of why; say it.
        if ! openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
            -keyout /opt/module/ssl/key.pem \
            -out /opt/module/ssl/cert.pem \
            -subj "/CN=localhost/O=Auditmation/OU=X12 Module" \
            2>/dev/null; then
            echo "ERROR: cannot write the self-signed certificate to /opt/module/ssl as uid $(id -u)" >&2
            exit 1
        fi
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

# Graceful shutdown. Signal the children and WAIT for them: the Java shutdown hook stops the
# HTTP routes and the pollers and then closes the buffer (a commit in flight finishes), and
# exiting right after `kill` would let the runtime tear the container down under it. nginx
# goes last so the ops port answers until Java is gone.
JAVA_PID=
shutdown() {
    echo "Shutting down..."
    if [ -n "$JAVA_PID" ]; then
        kill -TERM "$JAVA_PID" 2>/dev/null || true
        wait "$JAVA_PID" 2>/dev/null || true
    fi
    kill -TERM "$NGINX_PID" 2>/dev/null || true
    wait "$NGINX_PID" 2>/dev/null || true
    exit 0
}
trap shutdown TERM INT

# Start the Java process (HTTP ops on $INTERNAL_PORT + inbox poller) in the background so
# this shell stays free to take the signal; the heap is sized from the container limit by
# JAVA_OPTS (-XX:MaxRAMPercentage, Dockerfile), not a fixed -Xmx.
echo "Starting X12 receiver on operations port $INTERNAL_PORT..."
java $JAVA_OPTS -jar /opt/module/x12-receiver.jar &
JAVA_PID=$!
echo "X12 receiver started (PID: $JAVA_PID)"
echo "X12 Receiver Module ready (operations on 8888)"

# Java exiting on its own (a boot failure, a crash) ends the container with its status;
# nginx is stopped first so nothing is left answering for a dead receiver.
STATUS=0
wait "$JAVA_PID" || STATUS=$?
JAVA_PID=
kill -TERM "$NGINX_PID" 2>/dev/null || true
wait "$NGINX_PID" 2>/dev/null || true
exit "$STATUS"
