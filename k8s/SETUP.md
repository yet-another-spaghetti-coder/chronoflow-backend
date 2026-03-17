# ChronoFlow Kubernetes setup (GKE)

Use this guide to create a GKE cluster and deploy the manifests in this folder.

---

## 1. Prerequisites

- **Google Cloud SDK** (`gcloud`) – [Install](https://cloud.google.com/sdk/docs/install)
- **kubectl** – `gcloud components install kubectl` or install separately
- **Docker** (for building images; optional if you use existing `e1241986/*` images)

Log in and set your project:

```bash
gcloud auth login
gcloud config set project YOUR_GCP_PROJECT_ID
```

---

## 2. Create the GKE cluster

**Autopilot (pay per pod, no node management):**

```bash
gcloud container clusters create-auto chronoflow-cluster \
  --region=asia-southeast1 \
  --project=YOUR_GCP_PROJECT_ID
```

Then install the GKE auth plugin (required on newer `gcloud` versions), verify `kubectl`, and get credentials so `kubectl` talks to the cluster:

```bash
gcloud components install gke-gcloud-auth-plugin

kubectl --version
kubectl -v  # verbose output, optional

gcloud container clusters get-credentials chronoflow-cluster --region=asia-southeast1
```

---

## 3. Prepare dependencies

Your manifests expect these to exist **before** deploying apps.

### 3.1 MySQL for Nacos

Nacos uses MySQL. Your `k8s/nacos/deployment.yaml` reads from ConfigMap `nacos-cm` and Secret `nacos-mysql`.

- **Option A – Cloud SQL:** Create a Cloud SQL MySQL instance, note private IP. Create a database and user for Nacos. Edit `k8s/nacos/deployment.yaml` ConfigMap `nacos-cm` with that host/user, and create the `nacos-mysql` secret with the password.
- **Option B – MySQL in cluster (recommended for getting started):** Use the manifests in **`k8s/mysql/`**. They provide MySQL + PVC + secrets.
  1. Edit `k8s/mysql/secret.yaml` and set strong values for `root-password` and `nacos-password` (use the same value for the `nacos-mysql` secret).
  2. Apply `k8s/mysql/` (secret, pvc, deployment, service), then Nacos, then `k8s/mysql/nacos-cm-incluster.yaml` and restart Nacos.

### 3.2 GCP / app secrets (from `.env`)
Fill the env vars in .env in k8s/secrets.yaml

| Env variable | → Secret | Key | Used by |
|--------------|----------|-----|--------|
| `PUB_SUB_SERVICE_ACCOUNT_JSON` | `pub-sub` | `pub-sub-service-account` | user, event, task, attendee, notification, wsgateway (must be **base64-encoded** JSON; see below) |
| `GCP_SERVICE_ACCOUNT_JSON` | `file` | `file-service-account` | file-service, notification-service |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | `firebase` | `account-json` | notification-service, wsgateway |
| `AWS_REGION`, `AWS_ACCESS_KEY`, `AWS_SECRET_KEY` | `aws` | `region`, `access-key`, `secret-key` | notification-service |
| `GCP_PROJECT_ID` | ConfigMap `noti-config` | `gcp.project.id` | several services |



### 3.3 ConfigMap `noti-config`

Edit `k8s/notification-service/config.yaml` and set `gcp.project.id` to your project (e.g. `chronoflow-noti-service`) before applying.

---

## 4. Apply manifests in order

From the repo root (so paths below are correct). The sequence below mirrors the exact commands that have been tested end‑to‑end:

```bash
# 1) Namespace
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets.yaml
# 2) Database (Option B only): MySQL; set passwords in k8s/database/secret.yaml first
kubectl apply -f k8s/mysql/

# 2a) Initialize and migrate MySQL schema (from local SQL files)
kubectl exec -n chronoflow deployment/mysql -i -- \
  mysql -uroot -proot < k8s/database/combined-init.sql
kubectl exec -n chronoflow deployment/mysql -i -- \
  mysql -uroot -proot < k8s/database/combined-migrations.sql

# 3) Remaining platform services
kubectl apply -f k8s/nacos/
kubectl apply -f k8s/redis/

# 4) MongoDB (for wsgateway); apply PVC first, then deployment and service
kubectl apply -f k8s/mongodb/pvc.yaml
kubectl apply -f k8s/mongodb/deployment.yaml
kubectl apply -f k8s/mongodb/service.yaml
kubectl wait --for=condition=ready pod -l app=mongodb -n chronoflow --timeout=120s

# 5) Gateways and backend services
kubectl apply -f k8s/gateway/
kubectl apply -f k8s/user-service/
kubectl apply -f k8s/event-service/
kubectl apply -f k8s/task-service/
kubectl apply -f k8s/attendee-service/
kubectl apply -f k8s/wsgateway/
kubectl apply -f k8s/notification-service/
```

---

## 5. Wait for Nacos and apps

- Nacos StatefulSet uses **2 replicas** but `NACOS_SERVERS` lists three hosts (`nacos-0`, `nacos-1`, `nacos-2`). For a 2-node Nacos cluster, either set `replicas: 3` and have three Nacos pods, or change `NACOS_SERVERS` and `NACOS_REPLICAS` in the Nacos StatefulSet to match 2.

Check pods:

```bash
kubectl get pods -n chronoflow -w
```

Ensure Nacos pods are `Running` and ready before the app pods; apps depend on `nacos.chronoflow.svc.cluster.local`.

---

## 6. Expose the gateway (optional)

To reach the API from outside the cluster:

**LoadBalancer (one external IP for gateway):**

```bash
kubectl patch svc gateway -n chronoflow -p '{"spec":{"type":"LoadBalancer"}}'
kubectl get svc gateway -n chronoflow   # wait for EXTERNAL-IP
```

**Ingress (GKE):** Add an Ingress resource that targets the `gateway` service (port 8080). Use the GKE Ingress controller and a static IP if needed.

---

## 7. Image and registry notes

- Manifests use **Docker Hub** images: `shchow/<service>:latest` (gateway, user-service, event-service, task-service, attendee-service, notification-service, file-service, wsgateway).
- For **GCP-only images**, build for amd64 and push to Artifact Registry, then change each deployment’s `image` to e.g. `REGION-docker.pkg.dev/PROJECT/REPO/gateway:latest`. If the registry is private, create an image pull secret and add `imagePullSecrets` to each deployment.
- **ARM machines:** Build for GKE with `docker build --platform linux/amd64 ...` when pushing to the registry used by GKE.

---

## 8. Summary checklist

- [ ] GCP project set; `gcloud` and `kubectl` installed
- [ ] GKE cluster created and `kubectl` configured
- [ ] MySQL for Nacos: Option A (Cloud SQL) or Option B (`k8s/mysql/` applied; `nacos-cm` and `nacos-mysql` set)
- [ ] MongoDB: `k8s/mongodb/` applied (PVC, deployment, service) if using wsgateway
- [ ] Secrets created: `pub-sub`, `file`, `firebase`, `aws` (as needed; see 3.2 to create from `.env`)
- [ ] `k8s/namespace.yaml` applied
- [ ] `k8s/nacos/deployment.yaml` applied; Nacos pods running
- [ ] `k8s/notification-service/config.yaml` applied
- [ ] All other `k8s/*` manifests applied
- [ ] Gateway (or Ingress) exposed if you need external access
