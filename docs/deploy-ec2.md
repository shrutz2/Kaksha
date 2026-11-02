# Deploying Kaksha on AWS EC2

This guide puts the whole stack (MySQL, backend, web page) on one EC2 instance with Docker
Compose. It has not been run on AWS by the author of the code, so read it once before using it
and check each step against the AWS console.

## 1. Instance

- **AMI:** Amazon Linux 2023
- **Size:** `t3.medium` (2 vCPU, 4 GB). The JVM, MySQL and the screening run together need about
  2 GB, and a `t3.small` leaves very little room. Check current prices in the console.
- **Disk:** 20 GB gp3
- **Security group:**
  - SSH (22) from your own IP only
  - HTTP (80) from anywhere
  - HTTPS (443) from anywhere, if you add a certificate (step 6)
  - Do not open 3306 or 3307. The compose file already binds MySQL to `127.0.0.1`.
- Attach an **Elastic IP** so the address does not change when the instance restarts.

## 2. Install Docker and Git

```bash
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user

# Compose plugin
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
```

Log out and back in so the `docker` group applies, then check `docker compose version`.

## 3. Get the code

Clone your own repository, or copy the project folder with `scp`. Do not copy
`backend/data` or `node_modules`.

```bash
git clone <your-repository-url> kaksha
cd kaksha
```

## 4. Configure

```bash
cp .env.example .env
chmod 600 .env
nano .env
```

Set at least:

- `DB_PASSWORD` and `DB_ROOT_PASSWORD` to long random values
- `WEB_PORT=80`
- `PUBLIC_URL` to the address people will use, for example `http://<elastic-ip>` or your domain
- `SMTP_*` and `ALERT_FROM` if you want alert emails. Amazon SES works through its SMTP
  endpoint, and a normal mail account with an app password works too. If `SMTP_HOST` is empty,
  alert emails are only written to the log.
- `SCREEN_ON_STARTUP=true` for the first start, so there is a result without waiting for 02:30 UTC

## 5. Start

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

The first build takes several minutes. The backend then downloads the CelesTrak files, loads
the catalog (about half a minute) and runs a screening (about a minute). Open
`http://<elastic-ip>` when the log shows `Screening finished`.

### If CelesTrak refuses the download

Addresses of cloud providers, or an address that has fetched the file already, can be refused
with HTTP 403. The backend logs this and keeps going. Download the two files on your laptop
(links in the main README, "Starting without a download") and copy them in:

```bash
scp gp-active.csv satcat.csv ec2-user@<elastic-ip>:~/
docker cp ~/gp-active.csv kaksha-backend:/app/data/celestrak/gp-active.csv
docker cp ~/satcat.csv    kaksha-backend:/app/data/celestrak/satcat.csv
docker compose restart backend
```

## 6. HTTPS (optional, recommended)

The repository includes a Caddy setup that gets a certificate from Let's Encrypt and renews it
by itself. It needs a domain name that points at the server (a free name from a dynamic DNS
service works) and port 443 open in the security group. Set these in `.env`:

```
SITE_ADDRESS=your-name.example.org
PUBLIC_URL=https://your-name.example.org
```

Then start the stack with both compose files:

```bash
docker compose -f docker-compose.yml -f docker-compose.https.yml up -d
```

## 7. Operating it

| Task | Command |
|---|---|
| Update to a new version | `git pull && docker compose up -d --build` |
| See what the daily job did | `docker compose logs backend \| grep -E "Catalog|Screening"` |
| Back up the database | `docker exec kaksha-mysql sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" kaksha' > backup.sql` |
| Stop everything | `docker compose down` (add `-v` only if you want to delete the data) |

The database and the downloaded files live in Docker volumes, so they survive restarts and
rebuilds. The services restart by themselves after a reboot (`restart: unless-stopped`).

The daily job runs at 02:30 UTC. It refreshes the catalog and screens every Indian payload.
Only one backend should run at a time, otherwise the job runs twice.
