#!/bin/bash

# Test script for the new export functionality
# Testing export of Lemma class from MLS ontology in project 0807

echo "Testing DSP-API Resource Export..."

# Configuration
API_BASE="http://localhost:3333"
PROJECT_IRI="http://rdfh.ch/projects/0807"
RESOURCE_CLASS="http://www.knora.org/ontology/0807/mls#Lemma"

# First, you'll need to authenticate and get a JWT token
# Replace with your actual credentials
USERNAME="root@example.com"
PASSWORD="test"

echo "Step 1: Authenticate..."
AUTH_RESPONSE=$(curl -s -X POST "${API_BASE}/v2/authenticate" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"${USERNAME}\",
    \"password\": \"${PASSWORD}\"
  }")

# Extract token (you may need to adjust this based on actual response format)
TOKEN=$(echo $AUTH_RESPONSE | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "Authentication failed. Please check your credentials."
  echo "Response: $AUTH_RESPONSE"
  exit 1
fi

echo "Authentication successful!"

echo "Step 2: Get class properties (to know what properties are available)..."
curl -s -X GET "${API_BASE}/v2/ontologies/http%3A%2F%2Fwww.knora.org%2Fontology%2F0807%2Fmls" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Accept: application/json" \
  | jq '.["@graph"][] | select(.["@id"] | contains("Lemma")) | keys' || echo "Could not fetch ontology info"

echo ""
echo "Step 3: Test export with common properties..."

# Test the export endpoint
curl -X POST "${API_BASE}/v2/resources/export" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "X-Knora-Accept-Project: ${PROJECT_IRI}" \
  -d '{
    "resourceClass": "'${RESOURCE_CLASS}'",
    "selectedProperties": [
      "http://www.knora.org/ontology/knora-base#hasStillImageFileValue",
      "http://api.knora.org/ontology/knora-api/v2#hasIncomingLinkValue",
      "http://www.knora.org/ontology/0807/mls#hasLemmaText",
      "http://www.knora.org/ontology/0807/mls#hasLexicalEntries"
    ],
    "format": "CSV"
  }' \
  --output lemma_export.csv \
  --write-out "HTTP Status: %{http_code}\nContent-Type: %{content_type}\n"

echo ""
echo "Step 4: Check export result..."
if [ -f "lemma_export.csv" ]; then
  echo "Export file created: lemma_export.csv"
  echo "First few lines:"
  head -5 lemma_export.csv
  echo ""
  echo "File size: $(wc -c < lemma_export.csv) bytes"
  echo "Number of rows: $(wc -l < lemma_export.csv)"
else
  echo "Export file not created. Check the API response above for errors."
fi

echo ""
echo "Test completed!"