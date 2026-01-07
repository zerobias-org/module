# SOAP/WSDL Mapping to Dynamic Data Producer Interface

## Overview

This document describes how SOAP (Simple Object Access Protocol) web services and WSDL (Web Services Description Language) specifications map to the Dynamic Data Producer Interface, enabling unified access to SOAP-based services through the generic object model. The interface is implemented via a translation layer that converts generic operations into SOAP envelope construction, WSDL parsing, and protocol binding.

**SOAP Versions Supported**: SOAP 1.1 and SOAP 1.2
**WSDL Versions Supported**: WSDL 1.1 and WSDL 2.0

### Core Types Reference

This mapping uses DataType definitions from `@zerobias-org/types-core`. Common types referenced in SOAP mappings:

- **`email`**: Email addresses (validated format)
- **`phoneNumber`**: Phone numbers with international format support
- **`url`**: URLs and URIs (format aliases: `uri`)
- **`geoCountry`**: ISO 3166-1 alpha-2 country codes (e.g., "US", "DE", "FR")
- **`geoSubdivision`**: ISO 3166-2 subdivision codes (e.g., "US-CA", "US-TX", "DE-BE")
- **`date-time`**: ISO 8601 date-time strings (RFC3339 format, format aliases: `time`, `timestamp`)
- **`date`**: ISO 8601 date strings (YYYY-MM-DD format)
- **`decimal`**: Decimal numbers (suitable for currency, precise calculations)
- **`byte`**: Base64-encoded binary data (format aliases: `b64`, `base64`)
- **`mimeType`**: MIME type strings (e.g., "application/json", "text/xml")
- **`string`**: General string values
- **`integer`**: Integer numbers
- **`boolean`**: Boolean values (true/false)

See `@zerobias-org/types-core` for the complete list of available DataTypes and their validation rules.

## Conceptual Mapping

### WSDL Service → Object Model

```
WSDL Service Root
├── Service Definition                → Container Object (Root)
│   ├── Service Endpoint 1           → Container Object (Port/Endpoint)
│   │   ├── PortType/Interface       → Container Object (Operation Group)
│   │   │   ├── Operation 1          → Function Object (SOAP Operation)
│   │   │   ├── Operation 2          → Function Object (SOAP Operation)
│   │   │   └── Operation 3          → Function Object (SOAP Operation)
│   │   └── Binding                  → Document Object (Protocol Details)
│   ├── Service Endpoint 2           → Container Object (Alt Transport)
│   │   └── ...
│   ├── Types/Schemas                → Container Object (Type Definitions)
│   │   ├── ComplexType 1            → Document Object (Schema)
│   │   ├── ComplexType 2            → Document Object (Schema)
│   │   └── SimpleType 1             → Document Object (Schema)
│   └── Messages                     → Container Object (Message Definitions)
│       ├── Request Message 1        → Document Object (Message Schema)
│       └── Response Message 1       → Document Object (Message Schema)
```

## Detailed Object Mappings

### 1. WSDL Service → Root Container

```yaml
Object:
  id: "/"
  name: "OrderManagement Service"
  objectClass: ["container"]
  description: "SOAP web service for order management (WSDL 1.1)"
  tags: ["soap", "wsdl", "webservice", "soap11"]
  # Service-level metadata
  metadata:
    wsdlVersion: "1.1"
    soapVersion: "1.1"
    targetNamespace: "http://example.com/orderservice"
    serviceUrl: "http://api.example.com/services/orders?wsdl"
```

### 2. Service Port/Endpoint → Container Object

```yaml
Object:
  id: "port_order_http"
  name: "OrderServiceHTTP"
  objectClass: ["container"]
  description: "SOAP 1.1 endpoint over HTTP"
  path: ["OrderManagement Service", "OrderServiceHTTP"]
  tags: ["port", "endpoint", "http"]
  metadata:
    endpointAddress: "http://api.example.com/services/orders"
    binding: "OrderServiceSOAPBinding"
    transport: "http://schemas.xmlsoap.org/soap/http"
```

### 3. PortType/Interface → Container Object

```yaml
Object:
  id: "porttype_order_operations"
  name: "OrderPortType"
  objectClass: ["container"]
  description: "Collection of order-related operations"
  path: ["OrderManagement Service", "OrderServiceHTTP", "OrderPortType"]
  tags: ["porttype", "interface", "operations"]
```

### 4. SOAP Operation → Function Object

#### Request-Response Pattern (Most Common)

```yaml
Object:
  id: "func_create_order"
  name: "createOrder"
  objectClass: ["function"]
  description: "Create a new order (SOAP request-response)"
  inputSchema: "create_order_input_schema"
  outputSchema: "create_order_output_schema"
  # SOAP-specific properties
  soapAction: "http://example.com/orderservice/createOrder"
  operationPattern: "request-response"
  soapStyle: "document"  # or "rpc"
  soapUse: "literal"     # or "encoded"
  # HTTP properties for SOAP over HTTP
  httpMethod: "POST"
  httpPath: "/services/orders"
  httpHeaders:
    "Content-Type": "text/xml; charset=utf-8"  # SOAP 1.1
    "SOAPAction": "http://example.com/orderservice/createOrder"
  timeout: 60000
  retryPolicy:
    maxRetries: 2
    backoffStrategy: "exponential"
    retryableErrors: ["timeout", "soap:Server", "network_error"]
  # Fault definitions
  throws:
    "soap:Client": "client_fault_schema"
    "soap:Server": "server_fault_schema"
    "InvalidOrderFault": "invalid_order_fault_schema"
  tags: ["operation", "request-response", "document-literal"]

Schema (Input):
  id: "create_order_input_schema"
  properties:
    - name: "customerId"
      dataType: "string"
      required: true
      description: "Customer identifier"
    - name: "items"
      dataType: "array"
      multi: true
      references:
        schemaId: "order_item_schema"
    - name: "shippingAddress"
      dataType: "object"
      references:
        schemaId: "address_schema"

Schema (Output):
  id: "create_order_output_schema"
  properties:
    - name: "orderId"
      dataType: "string"
      required: true
      description: "Generated order identifier"
    - name: "orderNumber"
      dataType: "string"
    - name: "totalAmount"
      dataType: "decimal"
      description: "Total order amount in decimal currency format"
```

#### One-Way Pattern

```yaml
Object:
  id: "func_notify_shipment"
  name: "notifyShipment"
  objectClass: ["function"]
  description: "Send shipment notification (SOAP one-way)"
  inputSchema: "shipment_notification_schema"
  outputSchema: null  # No output for one-way
  soapAction: "http://example.com/orderservice/notifyShipment"
  operationPattern: "one-way"
  soapStyle: "document"
  soapUse: "literal"
  httpMethod: "POST"
  httpPath: "/services/orders"
  httpHeaders:
    "Content-Type": "text/xml; charset=utf-8"
    "SOAPAction": "http://example.com/orderservice/notifyShipment"
  tags: ["operation", "one-way", "notification"]
```

#### Notification Pattern (Server-Initiated)

```yaml
Object:
  id: "func_order_status_update"
  name: "orderStatusUpdate"
  objectClass: ["function"]
  description: "Server notification of order status change"
  inputSchema: null  # Server sends, no client input
  outputSchema: "order_status_schema"
  operationPattern: "notification"
  soapStyle: "document"
  soapUse: "literal"
  tags: ["operation", "notification", "callback"]
  metadata:
    note: "Requires callback endpoint registration"
```

### 5. Complex Type Definition → Document Object

```yaml
Object:
  id: "doc_address_type"
  name: "Address"
  objectClass: ["document"]
  description: "Address complex type definition"
  path: ["OrderManagement Service", "Types", "Address"]
  documentSchema: "address_schema"
  tags: ["complextype", "schema", "type-definition"]

Schema:
  id: "address_schema"
  properties:
    - name: "street"
      dataType: "string"
      required: true
    - name: "city"
      dataType: "string"
      required: true
    - name: "state"
      dataType: "geoSubdivision"
      description: "State or province code (ISO 3166-2 subdivision)"
    - name: "zipCode"
      dataType: "string"
      description: "Postal/ZIP code"
    - name: "country"
      dataType: "geoCountry"
      description: "Country code (ISO 3166-1 alpha-2)"
```

### 6. SOAP Array/Collection Handling

```yaml
# SOAP Array Type (maxOccurs="unbounded")
Schema:
  id: "order_items_schema"
  properties:
    - name: "items"
      dataType: "array"
      multi: true
      description: "Order line items (unbounded sequence)"
      references:
        schemaId: "order_item_schema"

# Individual Item Schema
Schema:
  id: "order_item_schema"
  properties:
    - name: "productId"
      dataType: "string"
      required: true
    - name: "quantity"
      dataType: "integer"
      required: true
    - name: "unitPrice"
      dataType: "decimal"
      description: "Unit price in decimal currency format"
```

### 7. SOAP Binding → Document Object

```yaml
Object:
  id: "doc_soap_binding"
  name: "OrderServiceSOAPBinding"
  objectClass: ["document"]
  description: "SOAP 1.1 binding configuration"
  path: ["OrderManagement Service", "Bindings", "OrderServiceSOAPBinding"]
  documentSchema: "soap_binding_schema"
  tags: ["binding", "soap", "protocol"]
  metadata:
    bindingStyle: "document"
    bindingUse: "literal"
    transport: "http://schemas.xmlsoap.org/soap/http"
```

### 8. SOAP Fault → Error Schema

```yaml
Schema:
  id: "soap_fault_schema"
  description: "SOAP 1.1 Fault structure"
  properties:
    - name: "faultcode"
      dataType: "string"
      required: true
      description: "Fault code (Client, Server, MustUnderstand, VersionMismatch)"
    - name: "faultstring"
      dataType: "string"
      required: true
      description: "Human-readable fault description"
    - name: "faultactor"
      dataType: "string"
      description: "URI of fault actor"
    - name: "detail"
      dataType: "object"
      description: "Application-specific error details"

Schema:
  id: "soap12_fault_schema"
  description: "SOAP 1.2 Fault structure"
  properties:
    - name: "Code"
      dataType: "object"
      required: true
      references:
        schemaId: "soap12_fault_code_schema"
    - name: "Reason"
      dataType: "array"
      required: true
      multi: true
      description: "Human-readable explanations (multi-language)"
    - name: "Node"
      dataType: "url"
      description: "URI of SOAP node that faulted"
    - name: "Role"
      dataType: "url"
      description: "Role being performed when fault occurred"
    - name: "Detail"
      dataType: "object"
      description: "Application-specific error details"
```

## SOAP Message Pattern Mappings

### Document/Literal Wrapped (Recommended Standard)

```yaml
Object:
  id: "func_get_order"
  name: "getOrder"
  soapStyle: "document"
  soapUse: "literal"
  messagePattern: "wrapped"
  # Message is schema-validated, parameters wrapped in operation element
  inputSchema: "get_order_request_schema"
  outputSchema: "get_order_response_schema"
```

**Example SOAP Request**:
```xml
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <getOrder xmlns="http://example.com/orderservice">
      <orderId>12345</orderId>
    </getOrder>
  </soap:Body>
</soap:Envelope>
```

### RPC/Literal

```yaml
Object:
  id: "func_calculate_tax"
  name: "calculateTax"
  soapStyle: "rpc"
  soapUse: "literal"
  # RPC style: operation is method call, parameters as children
  inputSchema: "calculate_tax_params_schema"
  outputSchema: "calculate_tax_return_schema"
```

**Example SOAP Request**:
```xml
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <calculateTax xmlns="http://example.com/orderservice">
      <amount>100.00</amount>
      <state>CA</state>
    </calculateTax>
  </soap:Body>
</soap:Envelope>
```

### RPC/Encoded (Deprecated - Avoid)

```yaml
Object:
  id: "func_legacy_operation"
  name: "legacyOperation"
  soapStyle: "rpc"
  soapUse: "encoded"
  metadata:
    deprecated: true
    deprecationReason: "RPC/encoded is deprecated, use document/literal"
```

## SOAP Attachment Handling

### MTOM (Message Transmission Optimization Mechanism)

```yaml
Object:
  id: "func_upload_document"
  name: "uploadDocument"
  objectClass: ["function"]
  description: "Upload document with MTOM attachment"
  inputSchema: "upload_document_input_schema"
  outputSchema: "upload_document_output_schema"
  soapAttachmentMode: "mtom"
  httpHeaders:
    "Content-Type": "multipart/related; type=\"application/xop+xml\""
  tags: ["operation", "mtom", "binary"]

Schema (Input):
  id: "upload_document_input_schema"
  properties:
    - name: "documentName"
      dataType: "string"
      required: true
    - name: "documentContent"
      dataType: "byte"
      description: "Binary content optimized via MTOM/XOP (base64-encoded)"
    - name: "mimeType"
      dataType: "mimeType"
      description: "MIME type of the document"
```

### SwA (SOAP with Attachments)

```yaml
Object:
  id: "func_send_email"
  name: "sendEmail"
  objectClass: ["function"]
  description: "Send email with SwA attachments"
  inputSchema: "send_email_input_schema"
  outputSchema: "send_email_output_schema"
  soapAttachmentMode: "swa"
  httpHeaders:
    "Content-Type": "multipart/related; type=\"text/xml\""
  tags: ["operation", "swa", "mime"]

Schema (Input):
  id: "send_email_input_schema"
  properties:
    - name: "to"
      dataType: "email"
    - name: "subject"
      dataType: "string"
    - name: "body"
      dataType: "string"
    - name: "attachments"
      dataType: "array"
      multi: true
      description: "MIME attachments referenced by CID URIs"
```

## Transport Binding Variations

### SOAP over HTTP (Default)

```yaml
Object:
  id: "port_http"
  name: "OrderServiceHTTP"
  metadata:
    transport: "http://schemas.xmlsoap.org/soap/http"
    endpointAddress: "http://api.example.com/services/orders"
    httpMethod: "POST"
```

### SOAP over HTTPS

```yaml
Object:
  id: "port_https"
  name: "OrderServiceHTTPS"
  metadata:
    transport: "http://schemas.xmlsoap.org/soap/http"
    endpointAddress: "https://secure.example.com/services/orders"
    httpMethod: "POST"
    requiresTLS: true
    tlsVersion: "1.2"
```

### SOAP over JMS (Java Message Service)

```yaml
Object:
  id: "port_jms"
  name: "OrderServiceJMS"
  metadata:
    transport: "http://www.w3.org/2010/soapjms/"
    endpointAddress: "jms:jndi:OrderQueue?jndiConnectionFactoryName=ConnectionFactory"
    jmsDeliveryMode: "PERSISTENT"
    jmsTimeToLive: 3600000
    jmsPriority: 4
  tags: ["jms", "async", "messaging"]
```

### SOAP over SMTP (Email-based)

```yaml
Object:
  id: "port_smtp"
  name: "OrderServiceSMTP"
  metadata:
    transport: "http://www.pocketsoap.com/specs/smtpbinding/"
    endpointAddress: "mailto:orders@example.com"
    smtpServer: "smtp.example.com"
  tags: ["smtp", "email", "async"]
```

## WS-* Specifications Integration

### WS-Security

```yaml
Object:
  id: "func_secure_operation"
  name: "processPayment"
  objectClass: ["function"]
  inputSchema: "payment_input_schema"
  outputSchema: "payment_output_schema"
  # WS-Security properties
  securityRequirements:
    - type: "ws-security"
      features:
        - "message-signature"   # XML Signature
        - "message-encryption"  # XML Encryption
        - "timestamp"
        - "username-token"
      tokenFormats:
        - "SAML2.0"
        - "X.509"
        - "Username"
  httpHeaders:
    "Content-Type": "text/xml; charset=utf-8"
  tags: ["operation", "ws-security", "encrypted"]
```

**SOAP Header Example**:
```xml
<soap:Header>
  <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/...">
    <wsse:UsernameToken>
      <wsse:Username>user123</wsse:Username>
      <wsse:Password Type="PasswordDigest">...</wsse:Password>
      <wsse:Nonce>...</wsse:Nonce>
      <wsu:Created>2025-10-23T12:00:00Z</wsu:Created>
    </wsse:UsernameToken>
  </wsse:Security>
</soap:Header>
```

### WS-ReliableMessaging

```yaml
Object:
  id: "func_reliable_notification"
  name: "sendCriticalNotification"
  objectClass: ["function"]
  inputSchema: "notification_input_schema"
  outputSchema: "notification_output_schema"
  # WS-ReliableMessaging properties
  reliableMessaging:
    enabled: true
    sequenceExpiration: 3600000
    inactivityTimeout: 600000
    acknowledgmentInterval: 5000
    retransmissionInterval: 10000
    exponentialBackoff: true
  tags: ["operation", "ws-rm", "reliable"]
```

### WS-Addressing

```yaml
Object:
  id: "func_routable_operation"
  name: "routeOrder"
  objectClass: ["function"]
  inputSchema: "route_order_input_schema"
  outputSchema: "route_order_output_schema"
  # WS-Addressing properties
  addressing:
    enabled: true
    requiresMessageId: true
    replyToSupported: true
    faultToSupported: true
  tags: ["operation", "ws-addressing", "routing"]
```

**SOAP Header Example**:
```xml
<soap:Header>
  <wsa:MessageID xmlns:wsa="http://www.w3.org/2005/08/addressing">
    uuid:12345678-1234-1234-1234-123456789012
  </wsa:MessageID>
  <wsa:To>http://api.example.com/services/orders</wsa:To>
  <wsa:Action>http://example.com/orderservice/routeOrder</wsa:Action>
  <wsa:ReplyTo>
    <wsa:Address>http://client.example.com/callback</wsa:Address>
  </wsa:ReplyTo>
</soap:Header>
```

## WSDL Import/Include Handling

### External Schema Import

```yaml
Object:
  id: "doc_imported_schema"
  name: "CommonTypes"
  objectClass: ["document"]
  description: "Imported schema from external XSD"
  path: ["OrderManagement Service", "Types", "Imported", "CommonTypes"]
  documentSchema: "common_types_schema"
  metadata:
    importSource: "http://schemas.example.com/common/v1.0/types.xsd"
    importNamespace: "http://schemas.example.com/common/v1.0"
    importType: "xsd:import"
  tags: ["schema", "imported", "external"]
```

### WSDL Include

```yaml
Object:
  id: "doc_included_wsdl"
  name: "OrderOperations"
  objectClass: ["document"]
  description: "Included WSDL from same namespace"
  path: ["OrderManagement Service", "Includes", "OrderOperations"]
  metadata:
    includeSource: "http://api.example.com/wsdl/order-operations.wsdl"
    includeNamespace: "http://example.com/orderservice"
    includeType: "wsdl:include"
  tags: ["wsdl", "included", "modular"]
```

## API Usage Examples

### 1. Discover WSDL Service Structure

```http
GET /objects/
# Returns root container with service metadata

GET /objects/{root}/children
# Returns service endpoints, types, operations containers
```

### 2. List Available Operations

```http
GET /objects/porttype_order_operations/children
# Returns all SOAP operations as Function objects
```

### 3. Invoke SOAP Operation (Request-Response)

```http
POST /objects/func_create_order/invoke
Content-Type: application/json

{
  "customerId": "CUST-12345",
  "items": [
    {
      "productId": "PROD-001",
      "quantity": 2,
      "unitPrice": 29.99
    }
  ],
  "shippingAddress": {
    "street": "123 Main St",
    "city": "San Francisco",
    "state": "CA",
    "zipCode": "94102",
    "country": "US"
  }
}

# Behind the scenes: constructs SOAP envelope, sends to endpoint
# Returns parsed SOAP response
```

**Response**:
```json
{
  "orderId": "ORD-789012",
  "orderNumber": "2025-0001234",
  "totalAmount": 59.98
}
```

### 4. Invoke SOAP Operation (One-Way)

```http
POST /objects/func_notify_shipment/invoke
Content-Type: application/json

{
  "orderId": "ORD-789012",
  "trackingNumber": "1Z999AA10123456784",
  "carrier": "UPS"
}

# Returns 202 Accepted (no response body for one-way)
```

### 5. Get Type Definition

```http
GET /objects/doc_address_type
# Returns Document object with schema definition

GET /schemas/address_schema
# Returns full Property definitions
```

### 6. Handle SOAP Fault

```http
POST /objects/func_create_order/invoke
Content-Type: application/json

{
  "customerId": "INVALID"
}

# Returns 400 Bad Request with SOAP Fault details
```

**Error Response**:
```json
{
  "error": {
    "code": "soap:Client",
    "message": "InvalidCustomerFault",
    "details": {
      "faultcode": "soap:Client",
      "faultstring": "Customer ID 'INVALID' not found",
      "faultactor": "http://api.example.com/services/orders",
      "detail": {
        "customerId": "INVALID",
        "errorCode": "CUSTOMER_NOT_FOUND"
      }
    }
  }
}
```

### 7. Upload Binary with MTOM

```http
POST /objects/func_upload_document/invoke
Content-Type: application/json

{
  "documentName": "invoice.pdf",
  "documentContent": "<base64-encoded-binary-data>",
  "mimeType": "application/pdf"
}

# Translation layer converts to MTOM multipart message
```

### 8. Low-Level SOAP Execution (Raw XML)

For cases requiring direct SOAP envelope control:

```http
POST /objects/func_create_order/rest
Content-Type: text/xml; charset=utf-8
SOAPAction: "http://example.com/orderservice/createOrder"

<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:ord="http://example.com/orderservice">
  <soap:Header>
    <!-- Optional SOAP headers -->
  </soap:Header>
  <soap:Body>
    <ord:createOrder>
      <ord:customerId>CUST-12345</ord:customerId>
      <ord:items>
        <ord:item>
          <ord:productId>PROD-001</ord:productId>
          <ord:quantity>2</ord:quantity>
          <ord:unitPrice>29.99</ord:unitPrice>
        </ord:item>
      </ord:items>
    </ord:createOrder>
  </soap:Body>
</soap:Envelope>

# Returns raw SOAP XML response
```

## Translation Layer Implementation Notes

### WSDL Parsing

1. **Fetch WSDL**: Download WSDL from service URL
2. **Parse Definitions**: Extract services, ports, bindings, portTypes, messages, types
3. **Resolve Imports**: Fetch and merge imported schemas/WSDLs
4. **Build Object Hierarchy**: Create Container/Function/Document objects
5. **Generate Schemas**: Convert XSD types to Property definitions

### SOAP Envelope Construction

1. **Validate Input**: Check against inputSchema
2. **Build Envelope**: Create SOAP envelope with namespaces
3. **Marshal Parameters**: Convert JSON to XML elements per schema
4. **Add Headers**: Include WS-* headers if configured (Security, Addressing, etc.)
5. **Set SOAPAction**: Add SOAPAction HTTP header (SOAP 1.1)

### SOAP Response Processing

1. **Parse XML**: Parse SOAP response envelope
2. **Check Fault**: Detect and process SOAP Fault
3. **Unmarshal Body**: Convert XML to JSON per outputSchema
4. **Extract Headers**: Process SOAP headers if needed
5. **Return Result**: Return JSON response or error

### Error Handling

| SOAP Fault Code | HTTP Status | Error Type |
|----------------|-------------|------------|
| `soap:VersionMismatch` | 400 | `invalid_soap_version` |
| `soap:MustUnderstand` | 400 | `must_understand_failed` |
| `soap:Client` (1.1) / `soap:Sender` (1.2) | 400 | `client_error` |
| `soap:Server` (1.1) / `soap:Receiver` (1.2) | 500 | `server_error` |
| Custom Fault | 400/500 | Per fault definition |
| `DataEncodingUnknown` | 400 | `encoding_error` |

### Attachment Handling

**MTOM**:
1. Detect `base64Binary` schema elements
2. Extract binary data from JSON
3. Create XOP Include reference in SOAP body
4. Package as multipart/related with binary parts
5. Set Content-Type with XOP media type

**SwA**:
1. Detect attachment references (CID URIs)
2. Create multipart/related message
3. SOAP envelope as root part
4. Attachments as separate MIME parts
5. Reference via `href="cid:attachment-id"`

## Special Considerations

### WSDL Version Differences

| Feature | WSDL 1.1 | WSDL 2.0 |
|---------|----------|----------|
| **Operation Container** | `portType` | `interface` |
| **Endpoint** | `port` | `endpoint` |
| **Message Construct** | Separate `message` elements | Inline in `input`/`output` |
| **HTTP Binding** | Extension via `http:binding` | Native `whttp:binding` |
| **Fault Handling** | `fault` in operation | `outfault`/`infault` |

### SOAP Version Differences

| Feature | SOAP 1.1 | SOAP 1.2 |
|---------|----------|----------|
| **Namespace** | `http://schemas.xmlsoap.org/soap/envelope/` | `http://www.w3.org/2003/05/soap-envelope` |
| **Content-Type** | `text/xml` | `application/soap+xml` |
| **SOAPAction** | Required HTTP header | Optional (can be in Content-Type) |
| **Fault Code** | `Client`, `Server` | `Sender`, `Receiver` |
| **Fault Reason** | `faultstring` (single) | `Reason/Text` (multi-language) |

### Parameter Mode Handling

While WSDL doesn't explicitly define in/out/inout modes:

- **Input**: Parameters in `input` message
- **Output**: Parameters in `output` message
- **InOut**: Parameters appearing in both `input` and `output` messages with same name

```yaml
# InOut parameter example
Schema (Input):
  id: "update_customer_input"
  properties:
    - name: "customer"
      dataType: "object"
      references:
        schemaId: "customer_schema"

Schema (Output):
  id: "update_customer_output"
  properties:
    - name: "customer"  # Same parameter name - treated as inout
      dataType: "object"
      references:
        schemaId: "customer_schema"
```

### Circular References

WSDL schemas can contain circular type references:

```yaml
# Handle via lazy loading and reference IDs
Schema:
  id: "person_schema"
  properties:
    - name: "name"
      dataType: "string"
    - name: "manager"
      dataType: "object"
      references:
        schemaId: "person_schema"  # Circular reference
```

**Implementation**: Use schema IDs to avoid infinite recursion during parsing.

### Namespace Management

WSDL services heavily use XML namespaces:

```yaml
Object:
  metadata:
    targetNamespace: "http://example.com/orderservice"
    namespaces:
      "soap": "http://schemas.xmlsoap.org/soap/envelope/"
      "xsd": "http://www.w3.org/2001/XMLSchema"
      "ord": "http://example.com/orderservice"
      "common": "http://schemas.example.com/common/v1.0"
```

**Translation**: Namespace prefixes are normalized during JSON conversion; fully qualified names stored in metadata.

### Performance Considerations

1. **WSDL Caching**: Cache parsed WSDL to avoid re-parsing on every request
2. **Schema Validation**: Validate against schemas only when requested (performance vs. safety)
3. **Large Payloads**: Stream MTOM attachments rather than loading into memory
4. **Connection Pooling**: Reuse HTTP connections for SOAP over HTTP
5. **Async Operations**: Support async invocation for one-way and notification patterns

## Migration from Legacy SOAP Clients

### Traditional SOAP Client
```java
// Old: Generated stub code
OrderService service = new OrderService();
OrderPortType port = service.getOrderServiceHTTP();
CreateOrderRequest request = new CreateOrderRequest();
request.setCustomerId("CUST-12345");
CreateOrderResponse response = port.createOrder(request);
```

### Dynamic Data Producer Interface
```javascript
// New: Generic interface
const response = await fetch('/objects/func_create_order/invoke', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    customerId: 'CUST-12345',
    items: [{ productId: 'PROD-001', quantity: 2, unitPrice: 29.99 }]
  })
});
const order = await response.json();
```

**Benefits**:
- No code generation required
- Dynamic service discovery
- Unified interface across REST, SOAP, GraphQL
- Runtime WSDL updates without recompilation

## Summary

The SOAP/WSDL mapping provides:

- **Unified Access**: SOAP services accessible via same interface as REST, GraphQL, SQL
- **Full WSDL Support**: Both WSDL 1.1 and 2.0, SOAP 1.1 and 1.2
- **Operation Patterns**: Request-response, one-way, notification, solicit-response
- **WS-* Specifications**: Security, ReliableMessaging, Addressing support
- **Attachment Handling**: MTOM and SwA for binary data
- **Transport Flexibility**: HTTP, HTTPS, JMS, SMTP bindings
- **Error Handling**: Standardized SOAP Fault translation
- **Low-Level Access**: Raw SOAP envelope control when needed

This enables legacy SOAP services to participate in modern data integration workflows without requiring specialized SOAP client libraries.
