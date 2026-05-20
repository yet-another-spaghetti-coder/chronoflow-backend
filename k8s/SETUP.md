# ChronoFlow Kubernetes setup (GKE)

Use this guide to create a GKE cluster and deploy the manifests in this folder.

---

## 1. Prerequisites

- **Google Cloud SDK** (`gcloud`) – [Install](https://cloud.google.com/sdk/docs/install)
- **kubectl** – `gcloud components install kubectl` or install separately
- **Docker** (for building images; optional if you use existing `e1241986/`* images)

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

### 3.1 MySQL (GCP-hosted)

**Production uses MySQL on GCP** (for example [Cloud SQL for MySQL](https://cloud.google.com/sql/docs/mysql)). Microservices and Nacos read connection details from Kubernetes; nothing in this repo provisions Cloud SQL for you.

- **Nacos:** `k8s/nacos/deployment.yaml` uses ConfigMap `nacos-cm` and Secret `nacos-mysql`. Point `MYSQL_SERVICE_*` at your Cloud SQL instance (host, port, user, database name `nacos`) and put the MySQL password in the `nacos-mysql` secret. Ensure GKE can reach the instance (private IP + VPC peering or [Cloud SQL Auth Proxy](https://cloud.google.com/sql/docs/mysql/connect-kubernetes-engine) as you prefer).
- **Application databases:** The `database` Kubernetes Secret (keys such as `prod-master-db-host`, `prod-master-db-password`, …) is what deployments expect. **External Secrets** syncs those values from Google Secret Manager using `k8s/external-secrets/external-secret-database.yaml` and Terraform-managed secrets `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD` (see `tf-secrets/`).
- **Schema:** Run `k8s/mysql/combined-init.sql` and `k8s/mysql/combined-migrations.sql` against Cloud SQL using `mysql` from your workstation, a CI job, or a short-lived Job in the cluster—not `kubectl exec` into an in-cluster MySQL pod (unless you use the optional in-cluster path below).

**Optional – MySQL inside the cluster (local / legacy):** The manifests under `k8s/mysql/` can still run MySQL as a Deployment for experimentation. Edit `k8s/mysql/secret.yaml`, apply `k8s/mysql/`, then use `k8s/mysql/nacos-cm-incluster.yaml` for Nacos and the `kubectl exec … mysql` commands in §4.B if you rely on that pod.

### 3.2 App secrets (Google Secret Manager + External Secrets)

**Recommended on GKE:** This repo assumes **[External Secrets Operator](https://external-secrets.io/) is already installed** on the cluster (for example in the `external-secrets` namespace). Create secrets in Google Secret Manager (see `tf-secrets/`), wire **GCP Workload Identity** for the Kubernetes SA used by the ClusterSecretStore (`eso-gcp-sm` in `k8s/external-secrets/gcp-sm-auth-serviceaccount.yaml`), then apply `k8s/external-secrets/` (ClusterSecretStore + ExternalSecrets). That populates Kubernetes Secrets `pub-sub`, `file`, `firebase`, `aws`, and `database` without checking values into git. For a new cluster without ESO, install the operator first (Helm chart in the upstream docs), then continue here.

```bash
kubectl apply -f k8s/namespace.yaml
export GCP_PROJECT_ID=YOUR_GCP_PROJECT_ID
export ESO_GCP_SERVICE_ACCOUNT=eso-gcp-sm@${GCP_PROJECT_ID}.iam.gserviceaccount.com
./k8s/external-secrets/apply.sh
```

Google Secret Manager secret ids (Terraform defaults in `tf-secrets/`) map to Kubernetes Secrets as follows. **Pub/Sub and GCP file credentials** must be **base64-encoded** JSON where Spring expects `encoded-key`.

| GSM secret id                     | K8s `Secret` | Key(s)                               | Used by                                                                                           |
| --------------------------------- | ------------ | ------------------------------------ | ------------------------------------------------------------------------------------------------- |
| `PUB_SUB_SERVICE_ACCOUNT_JSON`    | `pub-sub`    | `pub-sub-service-account`            | user, event, task, attendee, notification, wsgateway                                            |
| `GCP_SERVICE_ACCOUNT_JSON`        | `file`       | `file-service-account`               | file-service, notification-service                                                                |
| `FIREBASE_SERVICE_ACCOUNT_JSON`   | `firebase`   | `account-json`                       | notification-service, wsgateway                                                                   |
| `MOBILE_CLIENT_ID`                | `firebase`   | `mobile-client-id`                   | user-service                                                                                      |
| `AWS_ACCESS_KEY`, `AWS_SECRET_KEY` | `aws`        | `access-key`, `secret-key`           | notification-service (`region` is set in `k8s/external-secrets/external-secret-aws.yaml`)         |
| `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD` | `database` | `prod-master-db-*`, `prod-slave-db-*` | app services; Nacos uses `prod-master-db-*` when configured                                       |
| (not GSM)                         | ConfigMap `noti-config` | `gcp.project.id`            | several services — edit `k8s/notification-service/config.yaml`                                    |

### 3.3 MongoDB (GCP-hosted)

**Production uses MongoDB outside the cluster** (for example MongoDB Atlas, or a GCP-hosted compatible endpoint such as Firestore with the MongoDB-compatible API). **wsgateway** reads **`MONGODB_URI`** in `k8s/wsgateway/deployment.yaml`; set that to your managed instance connection string. You do **not** need to apply `k8s/mongodb/` unless you intentionally run MongoDB as pods inside the cluster.

### 3.4 ConfigMap `noti-config`

Edit `k8s/notification-service/config.yaml` and set `gcp.project.id` to your project (e.g. `chronoflow-noti-service`) before applying.

---

## 4. Apply manifests in order

From the repo root (so paths below are correct).

### 4.A Recommended: GCP MySQL + GCP / external MongoDB

Skip in-cluster `k8s/mysql/` and `k8s/mongodb/` when databases are hosted on GCP. After Cloud SQL (and Nacos/MySQL wiring), External Secrets, `MONGODB_URI` on wsgateway, and schema migrations are done:

```bash
kubectl apply -f k8s/namespace.yaml
# External Secrets: ./k8s/external-secrets/apply.sh (see §3.2)

kubectl apply -f k8s/nacos/
kubectl apply -f k8s/redis/

# Gateways and backend services
kubectl apply -f k8s/gateway/
kubectl apply -f k8s/user-service/
kubectl apply -f k8s/event-service/
kubectl apply -f k8s/task-service/
kubectl apply -f k8s/attendee-service/
kubectl apply -f k8s/wsgateway/
kubectl apply -f k8s/notification-service/
```

Apply any other folders you use (`k8s/file-service/`, monitoring, HPA, etc.) in dependency order.

### 4.B Optional: in-cluster MySQL and MongoDB (dev / legacy)

Only if you run databases as pods in `chronoflow`:

```bash
# MySQL: set passwords in k8s/mysql/secret.yaml first
kubectl apply -f k8s/mysql/

kubectl exec -n chronoflow deployment/mysql -i -- \
  mysql -uroot -proot < k8s/mysql/combined-init.sql
kubectl exec -n chronoflow deployment/mysql -i -- \
  mysql -uroot -proot < k8s/mysql/combined-migrations.sql

kubectl apply -f k8s/nacos/
kubectl apply -f k8s/redis/

kubectl apply -f k8s/mongodb/pvc.yaml
kubectl apply -f k8s/mongodb/deployment.yaml
kubectl apply -f k8s/mongodb/service.yaml
kubectl wait --for=condition=ready pod -l app=mongodb -n chronoflow --timeout=120s

# Then gateways and services as in §4.A
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

- GCP project set; `gcloud` and `kubectl` installed
- GKE cluster created and `kubectl` configured
- **MySQL on GCP:** Cloud SQL (or equivalent) reachable from GKE; Nacos `nacos-cm` + `nacos-mysql` aligned; app DB credentials in Secret `database` (External Secrets + GSM or manual); schema migrations applied to Cloud SQL
- **MongoDB on GCP (or managed outside cluster):** `MONGODB_URI` set on wsgateway; skip `k8s/mongodb/` unless running Mongo in-cluster
- **External Secrets Operator** installed on the cluster (prerequisite for the GSM flow)
- **App secrets:** GSM (`tf-secrets/`) + `k8s/external-secrets/apply.sh` after Workload Identity is wired for `eso-gcp-sm`
- `k8s/namespace.yaml` applied
- `k8s/nacos/deployment.yaml` applied; Nacos pods running
- `k8s/notification-service/config.yaml` applied
- All other `k8s/`* manifests you need applied
- Gateway (or Ingress) exposed if you need external access
