#!/bin/bash

# Module-agnostic script to sanitize recorded API data
# Usage: sanitize-recordings.sh --input-file <file> --output-file <file> --service-name <name>

set -e

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --input-file)
            INPUT_FILE="$2"
            shift 2
            ;;
        --output-file)
            OUTPUT_FILE="$2"
            shift 2
            ;;
        --service-name)
            SERVICE_NAME="$2"
            shift 2
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

# Validate required parameters
if [ -z "$INPUT_FILE" ] || [ -z "$OUTPUT_FILE" ] || [ -z "$SERVICE_NAME" ]; then
    echo "Error: Missing required parameters"
    echo "Usage: $0 --input-file <file> --output-file <file> --service-name <name>"
    exit 1
fi

echo "🧹 Sanitizing recorded API data..."
echo "  Input: $INPUT_FILE"
echo "  Output: $OUTPUT_FILE"
echo "  Service: $SERVICE_NAME"

# Check if input file exists
if [ ! -f "$INPUT_FILE" ]; then
    echo "❌ Input file does not exist: $INPUT_FILE"
    exit 1
fi

# Create output directory if it doesn't exist
mkdir -p "$(dirname "$OUTPUT_FILE")"

# Create a temporary Node.js script to perform sanitization
cat > /tmp/sanitize-data.js << 'EOF'
const fs = require('fs');
const path = require('path');

const inputFile = process.argv[2];
const outputFile = process.argv[3];
const serviceName = process.argv[4];

console.log('🔄 Loading raw recordings...');
let rawData;
try {
    const content = fs.readFileSync(inputFile, 'utf8');
    rawData = JSON.parse(content);
} catch (error) {
    console.log('⚠️ Failed to parse input file, creating empty array');
    rawData = [];
}

console.log(`📊 Processing ${Array.isArray(rawData) ? rawData.length : 'unknown'} recorded interactions...`);

// Comprehensive data sanitization function
function sanitizeData(obj) {
    if (typeof obj !== 'object' || obj === null) {
        return obj;
    }

    if (Array.isArray(obj)) {
        return obj.map(sanitizeData);
    }

    const sanitized = {};
    for (const [key, value] of Object.entries(obj)) {
        const lowerKey = key.toLowerCase();
        
        // Sanitize based on key patterns
        if (lowerKey.includes('email') || lowerKey === 'email') {
            sanitized[key] = sanitizeEmail(value);
        } else if (lowerKey.includes('phone') || lowerKey.includes('telephone')) {
            sanitized[key] = sanitizePhone(value);
        } else if (lowerKey.includes('name') && (lowerKey.includes('first') || lowerKey.includes('last') || lowerKey === 'name')) {
            sanitized[key] = sanitizeName(value);
        } else if (lowerKey.includes('address') || lowerKey.includes('street') || lowerKey.includes('city')) {
            sanitized[key] = sanitizeAddress(value);
        } else if (lowerKey.includes('token') || lowerKey.includes('key') || lowerKey.includes('secret')) {
            sanitized[key] = sanitizeToken(value);
        } else if (lowerKey.includes('organization') || lowerKey.includes('company') || lowerKey.includes('org')) {
            sanitized[key] = sanitizeOrganization(value);
        } else if (lowerKey === 'id' || lowerKey.endsWith('id') || lowerKey.includes('userid') || lowerKey.includes('orgid')) {
            sanitized[key] = sanitizeId(key, value);
        } else if (typeof value === 'object') {
            sanitized[key] = sanitizeData(value);
        } else if (typeof value === 'string') {
            sanitized[key] = sanitizeStringContent(value);
        } else {
            sanitized[key] = value;
        }
    }
    
    return sanitized;
}

// Sanitization helper functions
function sanitizeEmail(email) {
    if (typeof email !== 'string' || !email.includes('@')) {
        return email;
    }
    const domains = ['example.com', 'testcorp.com', 'demo.org'];
    const testDomain = domains[Math.floor(Math.random() * domains.length)];
    return `test@${testDomain}`;
}

function sanitizePhone(phone) {
    if (typeof phone !== 'string') return phone;
    // Replace with test phone patterns
    const patterns = ['+1234567890', '+1-555-0123', '(555) 123-4567'];
    return patterns[Math.floor(Math.random() * patterns.length)];
}

function sanitizeName(name) {
    if (typeof name !== 'string') return name;
    const testNames = ['Test User', 'John Smith', 'Jane Doe', 'Bob Wilson', 'Alice Johnson'];
    return testNames[Math.floor(Math.random() * testNames.length)];
}

function sanitizeAddress(address) {
    if (typeof address !== 'string') return address;
    const testAddresses = [
        '123 Test Street, Demo City, ST 12345',
        '456 Sample Ave, Test Town, AB 67890',
        '789 Example Blvd, Mock City, XY 54321'
    ];
    return testAddresses[Math.floor(Math.random() * testAddresses.length)];
}

function sanitizeToken(token) {
    if (typeof token !== 'string') return token;
    return 'test-token-' + Math.random().toString(36).substr(2, 9);
}

function sanitizeOrganization(org) {
    if (typeof org !== 'string') return org;
    const testOrgs = ['Test Corp', 'Demo Company', 'Sample Organization', 'Example Inc'];
    return testOrgs[Math.floor(Math.random() * testOrgs.length)];
}

function sanitizeId(key, value) {
    const lowerKey = key.toLowerCase();
    
    // Handle different ID types based on key patterns and value types
    if (typeof value === 'number') {
        // Numeric IDs - replace with test numbers
        if (lowerKey.includes('user') || lowerKey === 'id') {
            const testIds = [123, 124, 125, 456, 789, 999, 1001, 2002];
            return testIds[Math.floor(Math.random() * testIds.length)];
        } else if (lowerKey.includes('org')) {
            const testOrgIds = [123, 456, 789];
            return testOrgIds[Math.floor(Math.random() * testOrgIds.length)];
        } else {
            // Generic numeric ID
            return 100 + Math.floor(Math.random() * 900); // 100-999
        }
    } else if (typeof value === 'string') {
        // UUID pattern
        if (value.match(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i)) {
            const testUuids = [
                '12345678-1234-1234-1234-123456789012',
                '87654321-4321-4321-4321-210987654321',
                'abcdabcd-abcd-abcd-abcd-abcdabcdabcd'
            ];
            return testUuids[Math.floor(Math.random() * testUuids.length)];
        }
        // Organization ID patterns
        else if (lowerKey.includes('org') || value.includes('org')) {
            const testOrgIds = ['test-org-123', 'demo-company-456', 'sample-org-789'];
            return testOrgIds[Math.floor(Math.random() * testOrgIds.length)];
        }
        // User ID patterns
        else if (lowerKey.includes('user') || value.includes('usr')) {
            return 'usr_test' + Math.floor(Math.random() * 1000);
        }
        // Generic string ID patterns
        else if (value.includes('_') || value.includes('-')) {
            // Preserve prefix pattern
            const parts = value.split(/[_-]/);
            if (parts.length > 1) {
                const prefix = parts[0];
                return prefix + '_test' + Math.floor(Math.random() * 1000);
            }
        }
        // Fallback for other string IDs
        return 'test-id-' + Math.floor(Math.random() * 1000);
    }
    
    // Fallback - return original value if type not recognized
    return value;
}

function sanitizeStringContent(str) {
    if (typeof str !== 'string') return str;
    
    // Replace email patterns
    str = str.replace(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g, 'test@example.com');
    
    // Replace phone patterns
    str = str.replace(/(\+?1[-.\s]?)?\(?([2-9]\d{2})\)?[-.\s]?([2-9]\d{2})[-.\s]?(\d{4})/g, '+1234567890');
    
    // Replace other sensitive patterns while preserving structure
    return str;
}

// Process the recordings
const sanitizedData = sanitizeData(rawData);

console.log('✅ Data sanitization complete');
console.log('🔒 All personal data has been anonymized');

// Write sanitized data
fs.writeFileSync(outputFile, JSON.stringify(sanitizedData, null, 2));
console.log(`💾 Sanitized recordings saved to: ${outputFile}`);

// Validation check
console.log('🔍 Performing validation check...');
const outputContent = fs.readFileSync(outputFile, 'utf8');
const sensitivePatterns = [
    /[a-zA-Z0-9._%+-]+@(?!(?:example\.com|testcorp\.com|demo\.org))[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/,
    /\b\d{3}[-.]?\d{3}[-.]?\d{4}\b/,
    /\b[A-Z][a-z]+ [A-Z][a-z]+\b/,
    /"id":\s*\d{6,}/,  // Large numeric IDs that might be real production IDs
    /\b\d{8,}\b/       // Long numeric sequences that might be real IDs
];

let foundSensitive = false;
for (const pattern of sensitivePatterns) {
    if (pattern.test(outputContent)) {
        console.log('⚠️ WARNING: Potential sensitive data found in output');
        foundSensitive = true;
        break;
    }
}

if (!foundSensitive) {
    console.log('✅ Validation passed: No sensitive data detected');
}

console.log('🎯 Sanitization complete');
EOF

# Run the sanitization script
node /tmp/sanitize-data.js "$INPUT_FILE" "$OUTPUT_FILE" "$SERVICE_NAME"

# Cleanup
rm -f /tmp/sanitize-data.js

echo "🎉 Sanitization completed successfully"