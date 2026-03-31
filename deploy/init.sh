#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

install_jdk() {
    echo "Installing JDK 21 on Amazon Linux 2023..."
    sudo dnf install -y java-21-amazon-corretto-devel
    java -version
}

install_and_configure_nginx() {
    echo "Installing nginx..."
    sudo dnf install -y nginx

    echo "Copying nginx config..."
    sudo cp "${SCRIPT_DIR}/nginx-sofia.conf" /etc/nginx/conf.d/sofia.conf
    sudo rm -f /etc/nginx/conf.d/welcome.conf /etc/nginx/sites-enabled/default 2>/dev/null || true

    echo "Validating nginx config..."
    sudo nginx -t

    echo "Enabling and starting nginx..."
    sudo systemctl enable --now nginx
}

# --- Main ---
install_jdk
install_and_configure_nginx
echo "Done."
