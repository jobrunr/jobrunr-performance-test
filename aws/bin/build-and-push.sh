#!/usr/bin/env bash
# Builds the amd64 + arm64 image and pushes it to ECR. jobrunr-pro is resolved from ~/.m2 or the private repo.
#
# Usage: aws/bin/build-and-push.sh            (TOOL_VERSION=1.0.0-SNAPSHOT by default)
#        TOOL_VERSION=8.8.2 aws/bin/build-and-push.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOL_VERSION="${TOOL_VERSION:-1.0.0-SNAPSHOT}"
SHARED_TF=(terraform -chdir="$ROOT_DIR/aws/terraform/shared")
REGION="$("${SHARED_TF[@]}" output -raw region)"
REPOSITORY_URL="$("${SHARED_TF[@]}" output -raw ecr_repository_url)"
BUILD_DIR="$ROOT_DIR/aws/docker/build"

GIT_SHA="$(git -C "$ROOT_DIR" rev-parse --short HEAD)"
git -C "$ROOT_DIR" diff --quiet HEAD -- common jobrunr-pro start || GIT_SHA="$GIT_SHA-dirty"
IMAGE_TAG="${IMAGE_TAG:-$GIT_SHA-pro-$TOOL_VERSION}"

echo "==> Building performance test against JobRunr Pro $TOOL_VERSION"
mvn -q -f "$ROOT_DIR/pom.xml" -P JobRunrPro -pl jobrunr-pro,start -am clean install -DskipTests -Dtool.version="$TOOL_VERSION"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/lib"
mvn -q -f "$ROOT_DIR/start/pom.xml" -P JobRunrPro dependency:copy-dependencies \
  -Dtool.version="$TOOL_VERSION" -DincludeScope=runtime -Dmdep.prependGroupId=true -DoutputDirectory="$BUILD_DIR/lib"
cp "$ROOT_DIR"/start/target/start-*.jar "$BUILD_DIR/lib/"

echo "==> Pushing $REPOSITORY_URL:$IMAGE_TAG"
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "${REPOSITORY_URL%%/*}"
docker buildx build --platform linux/amd64,linux/arm64 \
  -f "$ROOT_DIR/aws/docker/Dockerfile" -t "$REPOSITORY_URL:$IMAGE_TAG" --push "$BUILD_DIR"

echo "$IMAGE_TAG" > "$ROOT_DIR/aws/.image-tag"
echo "==> Done. aws/bin/run-cluster.sh will use image tag $IMAGE_TAG"
