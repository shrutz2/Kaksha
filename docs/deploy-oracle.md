# Deploying Kaksha on Oracle Cloud (Always Free)

This guide puts the whole stack (MySQL, backend, web page) on one Always Free ARM virtual
machine with Docker Compose. The images used (Maven, Temurin, MySQL, nginx) all have ARM builds,
so nothing in the project needs to change. Check the current Always Free limits on Oracle's
page before you start, they can change.

## 1. Instance

In the Oracle Cloud console, create a compute instance:

- **Image:** Ubuntu 22.04 or 24.04 (aarch64)
- **Shape:** Ampere `VM.Standard.A1.Flex`, for example 2 OCPU and 12 GB. The Always Free limit is
  4 OCPU and 24 GB across the whole account, so leave room if you run something else.
- **Boot volume:** 50 GB is plenty (the free limit is 200 GB across the account)
- **Networking:** a public IPv4 address. Save the SSH private key when the console offers it.

If the console says "out of capacity", try again later or pick another availability domain.

In the instance's virtual cloud network, add an **ingress rule** to the security list for
TCP port 80 (and 443 if you add a certificate) from `0.0.0.0/0`. Do not open 3306 or 3307.

## 2. Open the firewall inside the machine

Oracle's Ubuntu images block most ports with iptables as well, so the ingress rule alone is not
enough:

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo apt-get install -y iptables-persistent
sudo netfilter-persistent save
```

## 3. Install Docker and Git

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2 git
sudo systemctl enable --now docker
sudo usermod -aG docker ubuntu
```

Log out and back in, then check `docker compose version`.

## 4. Get the code and configure

```bash
git clone https://github.com/shrutz2/Kaksha.git kaksha
cd kaksha
cp .env.example .env
chmod 600 .env
nano .env
```

Set at least `DB_PASSWORD` and `DB_ROOT_PASSWORD` to long random values, `WEB_PORT=80`,
`PUBLIC_URL` to `http://<public-ip>` and `SCREEN_ON_STARTUP=true` for the first start. The other
settings are described in `deploy-ec2.md`.

## 5. Start

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

The first build on an ARM machine takes several minutes. Open `http://<public-ip>` when the log
shows `Screening finished`.

If CelesTrak answers with HTTP 403 to the server's address, copy the two catalog files in by hand,
as described in `deploy-ec2.md`.

## 6. Keeping it running

The services restart by themselves after a reboot (`restart: unless-stopped`). The daily job
runs at 02:30 UTC. Update with `git pull && docker compose up -d --build`.
