#!/bin/bash
set -ex
PASSWORD="kafka-ssl-dev-password"

cd /certs

# Clean previous artifacts
rm -f ca-key.pem ca-cert.pem broker.csr broker-signed.pem ca-cert.srl kafka.broker.keystore.jks kafka.truststore.jks

openssl req -new -x509 -keyout ca-key.pem -out ca-cert.pem -days 365 -nodes \
  -subj "/CN=VNShop-Kafka-CA/O=VNShop/L=HCMC/C=VN"

keytool -genkeypair -alias kafka-broker -keyalg RSA -keysize 2048 \
  -keystore kafka.broker.keystore.jks -storepass "$PASSWORD" \
  -dname "CN=kafka,O=VNShop,L=HCMC,C=VN" -validity 365 \
  -ext "SAN=DNS:kafka,DNS:localhost"

keytool -certreq -alias kafka-broker \
  -keystore kafka.broker.keystore.jks -storepass "$PASSWORD" \
  -file broker.csr

openssl x509 -req -CA ca-cert.pem -CAkey ca-key.pem \
  -in broker.csr -out broker-signed.pem -days 365 -CAcreateserial

keytool -importcert -alias ca-root \
  -keystore kafka.broker.keystore.jks -storepass "$PASSWORD" \
  -file ca-cert.pem -noprompt

keytool -importcert -alias kafka-broker \
  -keystore kafka.broker.keystore.jks -storepass "$PASSWORD" \
  -file broker-signed.pem -noprompt

keytool -importcert -alias ca-root \
  -keystore kafka.truststore.jks -storepass "$PASSWORD" \
  -file ca-cert.pem -noprompt

echo "=== Done ==="
ls -la /certs/*.jks
