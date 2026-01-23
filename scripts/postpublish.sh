#!/bin/bash
cd $(dirname $0)
CURRENT=$(pwd)
echo $CURRENT

if [ "$1" = "--dry-run" ]; then
  DRY_RUN=true
fi;

# Use PUBLISHED_PACKAGES_FILE if available, otherwise fall back to lerna list
if [ -n "$PUBLISHED_PACKAGES_FILE" ] && [ -f "$PUBLISHED_PACKAGES_FILE" ]; then
  echo "Using packages from $PUBLISHED_PACKAGES_FILE"
  versions=($(jq -r '.[].version' "$PUBLISHED_PACKAGES_FILE" | tr '\n' ' '))
  dirs=($(jq -r '.[].location' "$PUBLISHED_PACKAGES_FILE" | tr '\n' ' '))
elif [ "$DRY_RUN" = "true" ]; then
  versions=($(npx lerna list --since --ndjson | jq -r '[.version] | join(",")'))
  dirs=($(npx lerna list --since --ndjson | jq -r '[.location] | join(",")'))
else
  versions=($(npx lerna list --since HEAD~1 --ndjson | jq -r  '[.version] | join(",")'))
  dirs=($(npx lerna list --since HEAD~1 --ndjson | jq -r '[.location] | join(",")'))
fi;

for ((i = 0 ; i < ${#versions[@]} ; i++)); do
  package=${dirs[$i]}
  version=${versions[$i]}
  if [ "schema" = $(jq -r '.["import-artifact"]' $package/package.json) ]; then
    echo "Running postpublish on $package, version=$version"
    TS_DIR=$package/ts
    if [ ! -d $TS_DIR ]; then
      echo "Unable to find generated typescript code in: $TS_DIR"
      exit 1
    fi;

    cd $TS_DIR
    
    jq --arg version "$version" '.version = $version' package.json > package-version.json
    status=$?
    if [ $status -ne 0 ]; then
      echo "Unable to replace version for $package and version $version"
      exit 1
    fi;
    mv package-version.json package.json
    jq --arg version "$version" '.version = $version | .packages."".version = $version' package-lock.json > package-lock-version.json
    if [ $status -ne 0 ]; then
      echo "Unable to replace version in lock file $package and version $version"
      exit 1
    fi;
    mv package-lock-version.json package-lock.json
    
    if [ "$DRY_RUN" = "true" ]; then
      echo "Running publish in test mode. npm publish --dry-run"
      npm publish --dry-run
    else
      echo "publishing artifact..."
      npm publish
    fi;
    
    status=$?
    if [ $status -ne 0 ]; then
      echo "failed to publish $package"
      exit 1
    fi;
  else
    echo "Not running post-publish against non schema package $package"
  fi;
done


if [ "$1" = "--dry-run" ]; then
  echo "--- This was only a dry-run ---"
	echo "Testing publishing images"
	if [ -n "$PUBLISHED_PACKAGES_FILE" ] && [ -f "$PUBLISHED_PACKAGES_FILE" ]; then
		PACKAGES=$(jq -r '.[] | .name + "@" + .version' "$PUBLISHED_PACKAGES_FILE")
	else
		PACKAGES=$(npx lerna list --since --ndjson | jq -r '(.name + "@" + .version)')
	fi
	for pkg in $PACKAGES; do
		scripts/imagepublish.sh $pkg --dry-run
	done
  exit 0
fi;

echo "Publishing images since $SINCE"
SINCE=${1:-"HEAD~1"}

# Use PUBLISHED_PACKAGES_FILE if available, otherwise use lerna list --since
if [ -n "$PUBLISHED_PACKAGES_FILE" ] && [ -f "$PUBLISHED_PACKAGES_FILE" ]; then
  echo "Using packages from $PUBLISHED_PACKAGES_FILE"
  PACKAGES_JSON=$(cat "$PUBLISHED_PACKAGES_FILE")
else
  echo "Using lerna list --since $SINCE"
  PACKAGES_JSON=$(npx lerna list --since $SINCE --json)
fi

if [ "$(echo "$PACKAGES_JSON" | jq 'length')" -eq 0 ]; then
  echo "No packages to publish, skipping dispatch events"
  exit 0
fi

PAYLOAD=$(echo "$PACKAGES_JSON" | jq -rc '{"event_type": "image-publish", "client_payload": { "packages": [.[] | .name + "@" + .version] }}')
curl -X POST \
	-vvv \
	--fail \
	-H "Authorization: token $DISPATCH_TOKEN" \
	-H "Accept: application/vnd.github.v3+json" \
	-H "Content-type: application/json" https://api.github.com/repos/zerobias-org/module/dispatches \
	-d ''"$PAYLOAD"''

PAYLOAD=$(echo "$PACKAGES_JSON" | jq -rc '{"event_type": "generate-kb", "client_payload": { "code_prefix": "ct", "title_prefix": "Connect to", "packages": [.[] | .name + "@" + .version] }}')
curl -X POST \
	-vvv \
	--fail \
	-H "Authorization: token $DISPATCH_TOKEN" \
	-H "Accept: application/vnd.github.v3+json" \
	-H "Content-type: application/json" https://api.github.com/repos/zerobias-org/kb/dispatches \
	-d ''"$PAYLOAD"''

# client-publish dispatch moved to devops publish-reusable-nx.yml workflow
