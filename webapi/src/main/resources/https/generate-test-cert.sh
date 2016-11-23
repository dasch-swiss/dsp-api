#!/usr/bin/env bash

CERTHOST="localhost"

export PW="test"

# Create a server certificate, tied to ${CERTHOST}
keytool -genkeypair -v \
  -alias ${CERTHOST} \
  -dname "CN=${CERTHOST}, O=Localhost, L=Basel, C=CH" \
  -keystore ${CERTHOST}.jks \
  -keypass:env PW \
  -storepass:env PW \
  -keyalg RSA \
  -keysize 2048 \
  -validity 385

# Create a certificate signing request for ${CERTHOST}
keytool -certreq -v \
  -alias ${CERTHOST} \
  -keypass:env PW \
  -storepass:env PW \
  -keystore ${CERTHOST}.jks \
  -file ${CERTHOST}.csr

# Tell exampleCA to sign the ${CERTHOST} certificate. Note the extension is on the request, not the
# original certificate.
# Technically, keyUsage should be digitalSignature for DHE or ECDHE, keyEncipherment for RSA.
keytool -gencert -v \
  -alias exampleca \
  -keypass:env PW \
  -storepass:env PW \
  -keystore exampleca.jks \
  -infile ${CERTHOST}.csr \
  -outfile ${CERTHOST}.crt \
  -ext KeyUsage:critical="digitalSignature,keyEncipherment" \
  -ext EKU="serverAuth" \
  -ext SAN="DNS:${CERTHOST}" \
  -rfc

# Tell ${CERTHOST}.jks it can trust exampleca as a signer.
keytool -import -v \
  -alias exampleca \
  -file exampleca.crt \
  -keystore ${CERTHOST}.jks \
  -storetype JKS \
  -storepass:env PW << EOF
yes
EOF

# Import the signed certificate back into ${CERTHOST}.jks 
keytool -import -v \
  -alias ${CERTHOST} \
  -file ${CERTHOST}.crt \
  -keystore ${CERTHOST}.jks \
  -storetype JKS \
  -storepass:env PW

# List out the contents of ${CERTHOST}.jks just to confirm it.  
# If you are using Play as a TLS termination point, this is the key store you should present as the server.
keytool -list -v \
  -keystore ${CERTHOST}.jks \
  -storepass:env PW
