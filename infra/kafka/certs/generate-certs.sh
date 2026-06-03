#!/bin/bash
# Generate self-signed CA and broker certificates for Kafka SASL_SSL (dev only).
# Production: use cert-manager, Vault PKI, or a proper CA.

set -euo pipefail

CERTS_DIR="$(cd "$(dirname "$0")" && pwd)"
PASSWORD="kafka-ssl-dev-password"

echo "=== Generating CA ==="
openssl req -new -x509 -keyout "$CERTS_DIR/ca-key.pem" -out "$CERTS_DIR/ca-cert.pem" \
    -days 365 -nodes -subj "/CN=VNShop-Kafka-CA/O=VNShop/L=HCMC/C=VN"

echo "=== Generating Broker Keystore ==="
keytool -genkeypair -alias kafka-broker -keyalg RSA -keysize 2048 \
    -keystore "$CERTS_DIR/kafka.broker.keystore.jks" -storepass "$PASSWORD" \
    -dname "CN=kafka,O=VNShop,L=HCMC,C=VN" -validity 365 -ext "SAN=DNS:kafka,DNS:localhost"

echo "=== Generating Broker CSR and signing with CA ==="
keytool -certreq -alias kafka-broker -keystore "$CERTS_DIR/kafka.broker.keystore.jks" \
    -storepass "$PASSWORD" -file "$CERTS_DIR/broker.csr" -ext "SAN=DNS:kafka,DNS:localhost"
openssl x509 -req -CA "$CERTS_DIR/ca-cert.pem" -CAkey "$CERTS_DIR/ca-key.pem" \
    -in "$CERTS_DIR/broker.csr" -out "$CERTS_DIR/broker-signed.pem" \
    -days 365 -CAcreateserial -extfile <(printf "subjectAltName=DNS:kafka,DNS:localhost")

echo "=== Importing CA and signed cert into broker keystore ==="
keytool -importcert -alias ca-root -keystore "$CERTS_DIR/kafka.broker.keystore.jks" \
    -storepass "$PASSWORD" -file "$CERTS_DIR/ca-cert.pem" -noprompt
keytool -importcert -alias kafka-broker -keystore "$CERTS_DIR/kafka.broker.keystore.jks" \
    -storepass "$PASSWORD" -file "$CERTS_DIR/broker-signed.pem" -noprompt

echo "=== Creating Truststore with CA cert ==="
keytool -importcert -alias ca-root -keystore "$CERTS_DIR/kafka.truststore.jks" \
    -storepass "$PASSWORD" -file "$CERTS_DIR/ca-cert.pem" -noprompt

echo "=== Done ==="
ls -la "$CERTS_DIR"/*.jks "$CERTS_DIR"/*.pem 2>/dev/null || true
echo "Keystore/Truststore password: $PASSWORD"
