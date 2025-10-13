#!/usr/bin/env node

/**
 * Template for testing API endpoints with Node.js
 * Usage: node node-test.js <endpoint> [method] [data]
 */

const https = require('https');
const fs = require('fs');
const path = require('path');

// Load environment variables
if (fs.existsSync('.env')) {
  require('dotenv').config();
}

// Configuration
const BASE_URL = process.env.BASE_URL || 'https://api.example.com';
const TOKEN = process.env.API_TOKEN || process.env.TOKEN;

// Parse arguments
const [,, endpoint, method = 'GET', data] = process.argv;

if (!endpoint) {
  console.error('Usage: node node-test.js <endpoint> [method] [data]');
  process.exit(1);
}

if (!TOKEN) {
  console.error('Error: No API token found');
  console.error('Set API_TOKEN in .env file');
  process.exit(1);
}

// Parse URL
const url = new URL(BASE_URL + endpoint);

// Request options
const options = {
  hostname: url.hostname,
  port: url.port || 443,
  path: url.pathname + url.search,
  method: method,
  headers: {
    'Authorization': `Bearer ${TOKEN}`,
    'Accept': 'application/json',
    'Content-Type': 'application/json',
    'User-Agent': 'ModuleTest/1.0'
  }
};

// Make request
console.log(`Testing: ${method} ${endpoint}`);
console.log('---');

const req = https.request(options, (res) => {
  let responseData = '';

  res.on('data', (chunk) => {
    responseData += chunk;
  });

  res.on('end', () => {
    console.log(`Status: ${res.statusCode}`);
    console.log('Headers:', JSON.stringify(res.headers, null, 2));
    console.log('---');

    try {
      const parsed = JSON.parse(responseData);
      console.log('Response:', JSON.stringify(parsed, null, 2));

      // Save response
      const safeName = endpoint.replace(/\//g, '_');
      const filename = `test-response-${safeName}.json`;
      fs.writeFileSync(filename, JSON.stringify(parsed, null, 2));
      console.log(`\nResponse saved to: ${filename}`);
    } catch (e) {
      console.log('Response:', responseData);
    }
  });
});

req.on('error', (error) => {
  console.error('Request failed:', error.message);
  process.exit(1);
});

// Send data if provided
if (data && method !== 'GET') {
  req.write(typeof data === 'string' ? data : JSON.stringify(data));
}

req.end();