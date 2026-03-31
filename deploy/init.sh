#!/usr/bin/env bash
set -euo pipefail

echo "Installing JDK 21 on Amazon Linux 2023..."
sudo dnf install -y java-21-amazon-corretto-devel

java -version
echo "Done."
