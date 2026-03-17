# Redis (chronoflow namespace)

Redis for session/cache. The gateway and other services expect a Service named `redis` on port 6379.

## Apply

From repo root:

```bash
kubectl apply -f k8s/redis/
```

Or in order:

```bash
kubectl apply -f k8s/redis/deployment.yaml
kubectl apply -f k8s/redis/service.yaml
```

## Usage

- **Host (in-cluster):** `redis.chronoflow.svc.cluster.local`
- **Port:** `6379`
- No password by default. To add auth, use a Redis secret and set `PROD_REDIS_PASSWORD` (or equivalent) in services that need it.

## Optional: persistent data

The deployment uses no volume by default (data is lost on pod restart). For persistence, add a PVC and mount it at `/data` in the Redis container, and ensure the Redis image is started with persistence enabled (e.g. `redis-server --appendonly yes`).
