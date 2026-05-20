#!/usr/bin/env bash
# Apply ClusterSecretStore + ExternalSecrets. Prereqs:
# 1) External Secrets Operator already installed on the cluster (CRDs present)
# 2) GKE Workload Identity enabled
# 3) GCP SA with secretmanager.secretAccessor; WI binding to KSA external-secrets/eso-gcp-sm
#
# Required env:
#   GCP_PROJECT_ID
#   ESO_GCP_SERVICE_ACCOUNT  (full email, e.g. eso-gcp-sm@PROJECT.iam.gserviceaccount.com)
#
# Requires: kubectl, envsubst (gettext). Manifests use apiVersion external-secrets.io/v1.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
: "${GCP_PROJECT_ID:?set GCP_PROJECT_ID}"
: "${ESO_GCP_SERVICE_ACCOUNT:?set ESO_GCP_SERVICE_ACCOUNT}"
export GCP_PROJECT_ID ESO_GCP_SERVICE_ACCOUNT

if ! kubectl get crd clustersecretstores.external-secrets.io >/dev/null 2>&1; then
  echo "ClusterSecretStore CRD not found. Install External Secrets Operator on this cluster, then retry." >&2
  exit 1
fi

command -v envsubst >/dev/null 2>&1 || {
  echo "envsubst not found (macOS: brew install gettext && PATH=\"/opt/homebrew/opt/gettext/bin:\$PATH\")" >&2
  exit 1
}

kubectl apply -f "${DIR}/namespace.yaml"
envsubst < "${DIR}/gcp-sm-auth-serviceaccount.yaml" | kubectl apply -f -
envsubst < "${DIR}/cluster-secret-store.yaml" | kubectl apply -f -
kubectl apply -f "${DIR}/external-secret-pub-sub.yaml"
kubectl apply -f "${DIR}/external-secret-file.yaml"
kubectl apply -f "${DIR}/external-secret-firebase.yaml"
kubectl apply -f "${DIR}/external-secret-aws.yaml"
kubectl apply -f "${DIR}/external-secret-database.yaml"

echo "Wait for ClusterSecretStore then check: kubectl get externalsecrets -n chronoflow"
