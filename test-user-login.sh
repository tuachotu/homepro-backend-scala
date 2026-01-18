#!/bin/bash

# Test script for user login endpoint
# Replace YOUR_FIREBASE_TOKEN with an actual Firebase ID token

FIREBASE_TOKEN="${1:-YOUR_FIREBASE_TOKEN}"
BASE_URL="${2:-http://localhost:2107}"

echo "=== Testing User Login Endpoint ==="
echo "URL: $BASE_URL/api/users/login"
echo "Token (first 20 chars): ${FIREBASE_TOKEN:0:20}..."
echo ""

# Test 1: With Authorization header
echo "Test 1: With Authorization header"
echo "Response:"
curl -X GET "$BASE_URL/api/users/login" \
  -H "Authorization: Bearer $FIREBASE_TOKEN" \
  -w "\nHTTP Status: %{http_code}\n"

echo -e "\n"
