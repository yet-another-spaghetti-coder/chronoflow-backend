# In-cluster MySQL for Nacos (Option B)

This folder provides a single-instance MySQL deployment for Nacos. Apply it **before** Nacos when using Option B (MySQL in cluster).

## Prerequisites

- Namespace `chronoflow` exists (`kubectl apply -f ../namespace.yaml`).

## 1. Set MySQL passwords

Edit `secret.yaml` and replace `changeme` with a strong password for:

- `root-password` – MySQL root user.
- `nacos-password` – Nacos DB user; use the **same** value for the `nacos-mysql` secret’s `password` key (Nacos uses this to connect).

Or create the secrets manually:

```bash
kubectl create secret generic mysql -n chronoflow \
  --from-literal=root-password='YOUR_ROOT_PASSWORD' \
  --from-literal=nacos-password='YOUR_NACOS_PASSWORD'

kubectl create secret generic nacos-mysql -n chronoflow \
  --from-literal=password='YOUR_NACOS_PASSWORD'
```

## 2. Apply order

From the repo root:

```bash
# 1) Namespace
kubectl apply -f k8s/namespace.yaml

# 2) Database (MySQL + secrets)
kubectl apply -f k8s/database/secret.yaml
kubectl apply -f k8s/database/pvc.yaml
kubectl apply -f k8s/database/deployment.yaml
kubectl apply -f k8s/database/service.yaml

# 3) Wait for MySQL to be ready
kubectl wait --for=condition=ready pod -l app=mysql -n chronoflow --timeout=120s

# 4) Nacos (will use its default nacos-cm; we overwrite next)
kubectl apply -f k8s/nacos/deployment.yaml

# 5) Point Nacos at in-cluster MySQL (overwrites nacos-cm)
kubectl apply -f k8s/database/nacos-cm-incluster.yaml

# 6) Restart Nacos so it picks up the new ConfigMap
kubectl rollout restart statefulset nacos -n chronoflow
```

## Resources

- **Service:** `mysql.chronoflow.svc.cluster.local:3306`
- **Storage:** 10Gi PVC `mysql-data` (single replica; data survives pod restarts)
- **Secrets:** `mysql` (root + nacos user), `nacos-mysql` (password for Nacos app)
