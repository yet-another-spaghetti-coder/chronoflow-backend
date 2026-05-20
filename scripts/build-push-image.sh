#!/usr/bin/env bash
# Build and push a service image to a Docker registry (e.g. Docker Hub).
# Usage:
#   ./scripts/build-push-image.sh <service-folder> <docker-repo> [tag]
# Examples:
#   ./scripts/build-push-image.sh gateway shchow
#   ./scripts/build-push-image.sh user-service shchow v1.0.0
#   ./scripts/build-push-image.sh event-service ghcr.io/myuser latest

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Service folder (directory containing Dockerfile; also the Maven module name)
SERVICE_DIR="${1:-}"
# Docker repo (username on Docker Hub, or full repo like ghcr.io/user)
DOCKER_REPO="${2:-}"
TAG="${3:-latest}"

if [ -z "$SERVICE_DIR" ] || [ -z "$DOCKER_REPO" ]; then
  echo "Usage: $0 <service-folder> <docker-repo> [tag]"
  echo "  service-folder: Directory containing Dockerfile (e.g. gateway, user-service)"
  echo "  docker-repo:     Docker Hub username (e.g. shchow) or full repo (e.g. ghcr.io/myuser)"
  echo "  tag:             Image tag (default: latest)"
  echo ""
  echo "Examples:"
  echo "  $0 gateway shchow"
  echo "  $0 user-service shchow v1.0.0"
  exit 1
fi

if [ ! -d "$REPO_ROOT/$SERVICE_DIR" ]; then
  echo "Error: Service folder not found: $SERVICE_DIR"
  exit 1
fi

if [ ! -f "$REPO_ROOT/$SERVICE_DIR/Dockerfile" ]; then
  echo "Error: No Dockerfile in $SERVICE_DIR"
  exit 1
fi

# Image = repo/service-name (e.g. shchow/gateway or ghcr.io/myuser/user-service)
IMAGE="${DOCKER_REPO%/}/${SERVICE_DIR}"
IMAGE_TAG="${IMAGE}:${TAG}"

echo "Building $SERVICE_DIR JAR..."
cd "$REPO_ROOT"
mvn -B -pl "$SERVICE_DIR" -am package -DskipTests

echo "Building Docker image for linux/amd64: $IMAGE_TAG"
docker build --platform linux/amd64 -t "$IMAGE_TAG" -f "$SERVICE_DIR/Dockerfile" "./$SERVICE_DIR"

echo "Pushing $IMAGE_TAG"
docker push "$IMAGE_TAG"

echo "Done. Image: $IMAGE_TAG"
