#!/bin/bash
# ============================================================
# CampusHanjang — Oracle Cloud Ubuntu(ARM) 서버 초기 세팅
# VM 생성 후 딱 1회 실행: bash setup-server.sh
# ============================================================
set -euo pipefail

echo "=== 1. 타임존 Asia/Seoul (자정 스케줄러·LocalDate.now 기준) ==="
sudo timedatectl set-timezone Asia/Seoul
timedatectl | grep "Time zone"

echo "=== 2. 필수 패키지 (Java 21 / ImageMagick·ffmpeg — HEIC·Live Photo 변환) ==="
sudo apt-get update
sudo apt-get install -y openjdk-21-jre-headless imagemagick ffmpeg libheif1 curl iptables-persistent

echo "--- ImageMagick HEIC 지원 확인 (heic 항목이 보여야 함) ---"
convert -list format | grep -i heic || echo "!! HEIC delegate 미지원 — sudo apt-get install libheif-dev 후 재확인"

echo "=== 3. Caddy 설치 (자동 HTTPS 리버스 프록시) ==="
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
  | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
  | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt-get update && sudo apt-get install -y caddy

echo "=== 4. VM 내부 방화벽 개방 — OCI Ubuntu는 Security List 외에 iptables도 막혀 있음 ==="
sudo iptables -I INPUT 5 -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save

echo "=== 5. 스왑 2G (OOM 보험 — 봄 시즌 t3.micro OOM 재발 방지) ==="
if ! swapon --show | grep -q swapfile; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi
free -h

echo "=== 6. 앱 디렉토리 ==="
mkdir -p /home/ubuntu/app/logs

echo ""
echo "✅ 초기 세팅 완료. 다음 순서:"
echo "  1) /home/ubuntu/app/.env 작성 (deploy/.env.prod.example 참고)"
echo "  2) deploy/campus-hanjang.service → /etc/systemd/system/ 복사 후 enable"
echo "  3) deploy/Caddyfile → /etc/caddy/Caddyfile 교체 후 caddy reload"
