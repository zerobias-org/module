# Task Output File Schemas

## Overview
Each task in the workflow produces a JSON output file stored in `.claude/.localmemory/{action}-{module}/`. These schemas define the expected structure for each task output.

## Common Schema Elements

All task output files include:
- `status`: "completed" | "failed" | "partial"
- `timestamp`: ISO 8601 datetime
- `confidence`: 0-100 percentage

## Task-01: Product Discovery

**File**: `task-01-output.json`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["status", "productPackage", "moduleName", "confidence"],
  "properties": {
    "status": {
      "type": "string",
      "enum": ["completed", "failed", "partial"]
    },
    "productPackage": {
      "type": "string",
      "pattern": "^@[^/]+/product-",
      "description": "NPM package name for product, e.g., @auditmation/product-{vendor}"
    },
    "moduleName": {
      "type": "string",
      "pattern": "^[a-z-]+$",
      "description": "Module identifier, e.g., {vendor}-{service}, {vendor}-{suite}-{service}"
    },
    "vendorName": {
      "type": "string",
      "description": "Vendor name for display, e.g., [Vendor Name]"
    },
    "suiteName": {
      "type": "string",
      "description": "Suite name if applicable, e.g., [Suite Name]"
    },
    "serviceName": {
      "type": "string",
      "description": "Service name, e.g., [Service Name]"
    },
    "initialUserPrompt": {
      "type": "string",
      "description": "Original user request"
    },
    "confidence": {
      "type": "number",
      "minimum": 0,
      "maximum": 100
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

## Task-02: API Research

**File**: `task-02-output.json`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["status", "apiDocumentationUrl", "authMethod", "operations", "confidence"],
  "properties": {
    "status": {
      "type": "string",
      "enum": ["completed", "failed", "partial"]
    },
    "apiDocumentationUrl": {
      "type": "string",
      "format": "uri",
      "description": "Main API documentation URL"
    },
    "authMethod": {
      "type": "string",
      "enum": ["apiKey", "bearer", "basic", "oauth2", "pat", "custom"],
      "description": "Primary authentication method"
    },
    "baseUrl": {
      "type": "string",
      "format": "uri",
      "description": "API base URL"
    },
    "operations": {
      "type": "array",
      "description": "All discovered operations",
      "items": {
        "type": "object",
        "required": ["operationId", "method", "path", "description"],
        "properties": {
          "operationId": {
            "type": "string"
          },
          "method": {
            "type": "string",
            "enum": ["get", "post", "put", "patch", "delete"]
          },
          "path": {
            "type": "string"
          },
          "description": {
            "type": "string"
          },
          "tag": {
            "type": "string",
            "description": "Resource tag for grouping"
          },
          "required": {
            "type": "boolean",
            "description": "Is this a critical operation?"
          }
        }
      }
    },
    "resources": {
      "type": "array",
      "description": "All discovered resource types",
      "items": {
        "type": "object",
        "required": ["name", "operations"],
        "properties": {
          "name": {
            "type": "string"
          },
          "operations": {
            "type": "array",
            "items": {
              "type": "string"
            }
          }
        }
      }
    },
    "confidence": {
      "type": "number",
      "minimum": 0,
      "maximum": 100
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

## Task-03: Scaffold Generation

**File**: `task-03-output.json`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["status", "modulePath", "packageName", "generatorCommand", "confidence"],
  "properties": {
    "status": {
      "type": "string",
      "enum": ["completed", "failed", "partial"]
    },
    "modulePath": {
      "type": "string",
      "description": "Full path to module, e.g., package/{vendor}/{service}"
    },
    "packageName": {
      "type": "string",
      "pattern": "^@[^/]+/[a-z-]+$",
      "description": "NPM package name, e.g., @auditmation/{vendor}-{service}"
    },
    "generatorCommand": {
      "type": "string",
      "description": "Exact yeoman command used"
    },
    "generatedFiles": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "List of files created by generator"
    },
    "httpClient": {
      "type": "string",
      "enum": ["axios", "node-fetch", "got", "custom"],
      "description": "HTTP client library selected"
    },
    "confidence": {
      "type": "number",
      "minimum": 0,
      "maximum": 100
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

## Task-04: Connection Profile Design

**File**: `task-04-output.json`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["status", "connectionProfileYaml", "requiredFields", "confidence"],
  "properties": {
    "status": {
      "type": "string",
      "enum": ["completed", "failed", "partial"]
    },
    "connectionProfileYaml": {
      "type": "string",
      "description": "Complete YAML content for connectionProfile.yml"
    },
    "requiredFields": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "List of required fields in profile"
    },
    "optionalFields": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "List of optional fields in profile"
    },
    "authMethod": {
      "type": "string",
      "description": "Authentication method from profile"
    },
    "hasConnectionState": {
      "type": "boolean",
      "description": "Whether ConnectionState.yml is needed"
    },
    "confidence": {
      "type": "number",
      "minimum": 0,
      "maximum": 100
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

## Task-05: Credential Management

**File**: `task-05-output.json`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["status", "credentialsSource", "credentialsValid", "confidence"],
  "properties": {
    "status": {
      "type": "string",
      "enum": ["completed", "failed", "partial"]
    },
    "credentialsSource": {
      "type": "string",
      "enum": ["env", "connectionProfile", "user-provided", "none"],
      "description": "Where credentials were obtained"
    },
    "credentialsValid": {
      "type": "boolean",
      "description": "Whether credentials were validated with API"
    },
    "envFilePath": {
      "type": "string",
      "description": "Path to .env file if created"
    },
    "connectionProfilePath": {
      "type": "string",
      "description": "Path to .connectionProfile.json if created"
    },
    "testEndpoint": {
      "type": "string",
      "description": "Endpoint used to validate credentials"
    },
    "confidence": {
      "type": "number",
      "minimum": 0,
      "maximum": 100
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

## Task-06: API Specification Design

**File**: `task-06-output.json`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["status", "apiYamlPath", "operations", "schemas", "confidence"],
  "properties": {
    "status": {
      "type": "string",
      "enum": ["completed", "failed", "partial"]
    },
    "apiYamlPath": {
      "type": "string",
      "description": "Path to api.yml file"
    },
    "operations": {
      "type": "array",
      "description": "Operations defined in spec",
      "items": {
        "type": "object",
        "required": ["operationId", "tag", "method", "path"],
        "properties": {
          "operationId": {
            "type": "string"
          },
          "tag": {
            "type": "string"
          },
          "method": {
            "type": "string"
          },
          "path": {
            "type": "string"
          },
          "xMethodName": {
            "type": "string",
            "description": "x-method-name extension value"
          }
        }
      }
    },
    "schemas": {
      "type": "array",
      "description": "Schemas defined in components",
      "items": {
        "type": "string"
      }
    },
    "validationPassed": {
      "type": "boolean",
      "description": "Whether swagger-cli validate passed"
    },
    "confidence": {
      "type": "number",
      "minimum": 0,
      "maximum": 100
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

## Task-07: Code Generation

**File**: `task-07-output.json`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["status", "generatedInterfaces", "buildPassed", "confidence"],
  "properties": {
    "status": {
      "type": "string",
      "enum": ["completed", "failed", "partial"]
    },
    "generatedInterfaces": {
      "type": "array",
      "description": "List of generated TypeScript interfaces",
      "items": {
        "type": "string"
      }
    },
    "generatedPath": {
      "type": "string",
      "description": "Path to generated/api/index.ts"
    },
    "buildPassed": {
      "type": "boolean",
      "description": "Whether npm run build passed"
    },
    "buildErrors": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "Build errors if any"
    },
    "confidence": {
      "type": "number",
      "minimum": 0,
      "maximum": 100
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

## Task-08: HTTP Client Implementation

**File**: `task-08-output.json`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["status", "clientPath", "connectReturns", "buildPassed", "confidence"],
  "properties": {
    "status": {
      "type": "string",
      "enum": ["completed", "failed", "partial"]
    },
    "clientPath": {
      "type": "string",
      "description": "Path to HTTP client file"
    },
    "connectReturns": {
      "type": "string",
      "enum": ["void", "ConnectionState"],
      "description": "Return type of connect method"
    },
    "authImplementation": {
      "type": "string",
      "description": "How authentication is implemented"
    },
    "buildPassed": {
      "type": "boolean",
      "description": "Whether build passed after implementation"
    },
    "confidence": {
      "type": "number",
      "minimum": 0,
      "maximum": 100
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

## Task-09: Mapper Implementation

**File**: `task-09-output.json`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["status", "mappersPath", "mapperFunctions", "buildPassed", "confidence"],
  "properties": {
    "status": {
      "type": "string",
      "enum": ["completed", "failed", "partial"]
    },
    "mappersPath": {
      "type": "string",
      "description": "Path to Mappers.ts"
    },
    "mapperFunctions": {
      "type": "array",
      "description": "List of mapper functions created",
      "items": {
        "type": "object",
        "required": ["name", "returnType", "fieldCount"],
        "properties": {
          "name": {
            "type": "string"
          },
          "returnType": {
            "type": "string"
          },
          "fieldCount": {
            "type": "number",
            "description": "Number of fields mapped"
          }
        }
      }
    },
    "fieldValidation": {
      "type": "object",
      "properties": {
        "interfaceFieldCount": {
          "type": "number"
        },
        "mappedFieldCount": {
          "type": "number"
        },
        "matched": {
          "type": "boolean"
        }
      }
    },
    "buildPassed": {
      "type": "boolean"
    },
    "confidence": {
      "type": "number",
      "minimum": 0,
      "maximum": 100
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

## Task-10: Operation Implementation

**File**: `task-10-output.json`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["status", "implementedOperations", "buildPassed", "confidence"],
  "properties": {
    "status": {
      "type": "string",
      "enum": ["completed", "failed", "partial"]
    },
    "implementedOperations": {
      "type": "array",
      "description": "Operations implemented",
      "items": {
        "type": "object",
        "required": ["operationId", "producer", "method"],
        "properties": {
          "operationId": {
            "type": "string"
          },
          "producer": {
            "type": "string",
            "description": "Producer class name"
          },
          "method": {
            "type": "string",
            "description": "Method name in producer"
          },
          "tested": {
            "type": "boolean",
            "description": "Whether operation was tested with real API"
          }
        }
      }
    },
    "producerFiles": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "Producer files created"
    },
    "buildPassed": {
      "type": "boolean"
    },
    "confidence": {
      "type": "number",
      "minimum": 0,
      "maximum": 100
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

## Task-11: Test Implementation

**File**: `task-11-output.json`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["status", "unitTests", "integrationTests", "testsPassed", "confidence"],
  "properties": {
    "status": {
      "type": "string",
      "enum": ["completed", "failed", "partial"]
    },
    "unitTests": {
      "type": "object",
      "properties": {
        "files": {
          "type": "array",
          "items": {
            "type": "string"
          }
        },
        "total": {
          "type": "number"
        },
        "passed": {
          "type": "number"
        },
        "failed": {
          "type": "number"
        },
        "skipped": {
          "type": "number"
        }
      }
    },
    "integrationTests": {
      "type": "object",
      "properties": {
        "files": {
          "type": "array",
          "items": {
            "type": "string"
          }
        },
        "total": {
          "type": "number"
        },
        "passed": {
          "type": "number"
        },
        "failed": {
          "type": "number"
        },
        "skipped": {
          "type": "number",
          "description": "Skipped due to missing credentials"
        }
      }
    },
    "testsPassed": {
      "type": "boolean",
      "description": "All tests passed (excluding credential-related skips)"
    },
    "buildPassed": {
      "type": "boolean"
    },
    "lintPassed": {
      "type": "boolean"
    },
    "confidence": {
      "type": "number",
      "minimum": 0,
      "maximum": 100
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

## Task-12: Documentation

**File**: `task-12-output.json`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["status", "userGuidePath", "examplesCreated", "confidence"],
  "properties": {
    "status": {
      "type": "string",
      "enum": ["completed", "failed", "partial"]
    },
    "userGuidePath": {
      "type": "string",
      "description": "Path to USERGUIDE.md"
    },
    "readmePath": {
      "type": "string",
      "description": "Path to README.md if created"
    },
    "examplesCreated": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "Example files created"
    },
    "documentation": {
      "type": "object",
      "properties": {
        "credentialInstructions": {
          "type": "boolean"
        },
        "connectionExample": {
          "type": "boolean"
        },
        "operationExamples": {
          "type": "boolean"
        },
        "troubleshooting": {
          "type": "boolean"
        }
      }
    },
    "finalValidation": {
      "type": "object",
      "properties": {
        "buildPassed": {
          "type": "boolean"
        },
        "testsPassed": {
          "type": "boolean"
        },
        "lintPassed": {
          "type": "boolean"
        },
        "moduleConnects": {
          "type": "boolean"
        },
        "oneOperationWorks": {
          "type": "boolean"
        }
      }
    },
    "confidence": {
      "type": "number",
      "minimum": 0,
      "maximum": 100
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

## Initial Request File

**File**: `initial-request.json`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["userPrompt", "action", "timestamp"],
  "properties": {
    "userPrompt": {
      "type": "string",
      "description": "Original user request"
    },
    "action": {
      "type": "string",
      "enum": ["create", "update", "fix"],
      "description": "Determined action type"
    },
    "module": {
      "type": "string",
      "description": "Module identifier if determined"
    },
    "operations": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "Requested operations if specified"
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

## Recovery Context File

**File**: `recovery-context.json`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["lastSuccessfulTask", "failedTask", "errorDetails", "timestamp"],
  "properties": {
    "lastSuccessfulTask": {
      "type": "string",
      "description": "Last task that completed successfully"
    },
    "failedTask": {
      "type": "string",
      "description": "Task that failed"
    },
    "errorDetails": {
      "type": "object",
      "properties": {
        "message": {
          "type": "string"
        },
        "type": {
          "type": "string",
          "enum": ["build", "test", "validation", "api", "generation", "other"]
        },
        "file": {
          "type": "string",
          "description": "File where error occurred if applicable"
        },
        "command": {
          "type": "string",
          "description": "Command that failed if applicable"
        }
      }
    },
    "attemptedFixes": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "description": {
            "type": "string"
          },
          "successful": {
            "type": "boolean"
          }
        }
      }
    },
    "nextSteps": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "Suggested recovery actions"
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

## Usage Notes

1. **File Location**: All files are stored in `.claude/.localmemory/{action}-{module}/`
   - Example: `.claude/.localmemory/create-{vendor}-{service}/task-01-output.json`

2. **Status Values**:
   - `completed`: Task finished successfully
   - `failed`: Task failed and cannot continue
   - `partial`: Task partially completed, manual intervention may be needed

3. **Confidence Scores**:
   - 90-100: High confidence, proceed automatically
   - 70-89: Medium confidence, may need validation
   - Below 70: Low confidence, should ask user

4. **Reading Previous Outputs**:
   - Tasks should read previous task outputs to understand context
   - Use the schemas to validate the structure
   - Handle missing files gracefully (task may not have run yet)

5. **Error Recovery**:
   - When a task fails, write recovery-context.json
   - Include specific error details and attempted fixes
   - Suggest next steps for recovery