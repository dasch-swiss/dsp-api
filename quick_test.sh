#!/bin/bash

# Quick test to see if the API is running and accessible
echo "Testing DSP-API availability..."

# Test 1: Check if API is running
echo "1. Checking if API is running on localhost:3333..."
curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" "http://localhost:3333/health" || echo "API not accessible on localhost:3333"

echo ""

# Test 2: Check API version/info
echo "2. Getting API info..."
curl -s "http://localhost:3333/v2" | head -5 || echo "Could not get API info"

echo ""

# Test 3: Try to access the MLS ontology info (without auth - might be public)
echo "3. Checking MLS ontology accessibility..."
curl -s -w "HTTP Status: %{http_code}\n" -o /dev/null "http://localhost:3333/v2/ontologies/http%3A%2F%2Fwww.knora.org%2Fontology%2F0807%2Fmls"

echo ""
echo "If the API is running, you can proceed with the authentication and export test."
echo "Run the test_export.sh script or follow the steps in test_export_simple.md"