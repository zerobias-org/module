# SOAP Mapping

## Overview

How a SOAP / WSDL service surfaces through the DataProducer interface. A
producer parses the WSDL on connection, builds the object hierarchy from the
service's port types and complex-type definitions, and mediates calls by
constructing SOAP envelopes around DataProducer Function invocations. The
SOAP envelope, binding choice, SOAPAction header, MTOM/SwA attachment
encoding, WS-Security, and WS-Addressing are all implementation concerns
**inside** the producer — none of them appear on the interface's Function
objects. Both SOAP 1.1 and 1.2, and WSDL 1.1 and 2.0, are accommodated
by the same model.

For the foundational rules this mapping follows, see
[`../Concepts.md`](../Concepts.md), [`../SchemaIds.md`](../SchemaIds.md),
[`../CoreDataTypes.md`](../CoreDataTypes.md), and
[`../FilterSyntax.md`](../FilterSyntax.md).

## Conceptual Mapping

| WSDL / SOAP concept                       | DataProducer concept                | Notes                                                          |
|-------------------------------------------|-------------------------------------|----------------------------------------------------------------|
| `service` element                          | Root container (`id == "/"`)         | One root per producer connection.                              |
| `port` (WSDL 1.1) / `endpoint` (WSDL 2.0)  | Container                            | Distinct endpoints under one service.                          |
| `portType` (WSDL 1.1) / `interface` (2.0)  | Container                            | Groups operations.                                             |
| Operation (request-response)               | Function                             | `inputSchema` / `outputSchema` derived from `input` / `output` messages. |
| Operation (one-way)                        | Function                             | `outputSchema` is a `schema:shared:empty` or omitted.          |
| Operation fault                            | Entry in `throws`                    | One per `wsdl:fault`.                                          |
| Complex type (XSD)                         | Referenced via `schema:type:…`       | Resolved through `getSchema`.                                  |
| Simple type / restriction                  | Mapped to a core `dataType`          | The XSD restriction informs validation hints.                  |
| Enumerated simple type                     | Referenced via `schema:enum:…`       |                                                                |
| Binding (SOAP, HTTP, MIME, JMS)            | (implementation detail)              | Not exposed on the interface.                                  |
| SOAPAction, namespaces, encoding style     | (implementation detail)              | Not exposed.                                                   |

## Object Mappings

A WSDL with `targetNamespace` `http://example.com/orderservice` is mapped
under a logical name (`order_service`) so schema IDs have a stable
three-part path.

### Service (Root Container)

```yaml
Object:
  id: "/"
  name: "/"
  objectClass: ["container"]
  description: "OrderManagement SOAP service"
  tags: ["soap", "wsdl"]
```

### Endpoint (Container)

```yaml
Object:
  id: "/endpoint:OrderServiceHTTP"
  name: "OrderServiceHTTP"
  objectClass: ["container"]
  description: "Primary endpoint"
  path: ["OrderServiceHTTP"]
  tags: ["endpoint"]
```

### Operation (Function, request-response)

```yaml
Object:
  id: "/endpoint:OrderServiceHTTP/op:createOrder"
  name: "createOrder"
  objectClass: ["function"]
  description: "Create a new order"
  inputSchema:  "schema:function:order_service.OrderPortType.createOrder:input"
  outputSchema: "schema:function:order_service.OrderPortType.createOrder:output"
  throws:
    invalid_order:   "schema:type:order_service.faults.InvalidOrderFault"
    customer_unknown: "schema:type:order_service.faults.UnknownCustomerFault"
  tags: ["operation"]
```

```yaml
Schema:
  id: "schema:function:order_service.OrderPortType.createOrder:input"
  dataTypes:
    - name: "string"
    - name: "decimal"
  properties:
    - name: "customerId"
      dataType: "string"
      required: true
    - name: "items"
      dataType: "string"
      multi: true
      required: true
      description: "Order line items"
      references:
        schemaId: "schema:type:order_service.types.OrderItem"
    - name: "shippingAddress"
      dataType: "string"
      required: true
      references:
        schemaId: "schema:type:order_service.types.Address"

Schema:
  id: "schema:function:order_service.OrderPortType.createOrder:output"
  dataTypes:
    - name: "string"
    - name: "decimal"
  properties:
    - name: "orderId"
      dataType: "string"
      required: true
    - name: "orderNumber"
      dataType: "string"
      required: true
    - name: "totalAmount"
      dataType: "decimal"
      required: true
```

The interface does not expose `soapAction`, `httpMethod`, `httpHeaders`,
attachment encoding, or retry policy. A producer that needs to honor
SOAPAction routing reads it from the WSDL and applies it during envelope
construction.

### Operation (Function, one-way)

```yaml
Object:
  id: "/endpoint:OrderServiceHTTP/op:notifyShipment"
  name: "notifyShipment"
  objectClass: ["function"]
  description: "Fire-and-forget shipment notification"
  inputSchema:  "schema:function:order_service.OrderPortType.notifyShipment:input"
  outputSchema: "schema:shared:empty"
  tags: ["operation", "one-way"]
```

`schema:shared:empty` is a shared schema with no properties — the canonical
representation of "this operation produces no output."

### Complex Type (Referenced Schema)

`Address` is not a browseable Object — it surfaces only via `getSchema`:

```yaml
Schema:
  id: "schema:type:order_service.types.Address"
  dataTypes:
    - name: "string"
    - name: "geoCountry"
    - name: "geoSubdivision"
  properties:
    - name: "street"
      dataType: "string"
      required: true
    - name: "city"
      dataType: "string"
      required: true
    - name: "state"
      dataType: "geoSubdivision"
      description: "ISO 3166-2 subdivision code"
    - name: "postalCode"
      dataType: "string"
    - name: "country"
      dataType: "geoCountry"
      required: true
```

### Operation with Binary Payload

```yaml
Object:
  id: "/endpoint:OrderServiceHTTP/op:uploadInvoice"
  name: "uploadInvoice"
  objectClass: ["function"]
  description: "Upload a PDF invoice"
  inputSchema:  "schema:function:order_service.OrderPortType.uploadInvoice:input"
  outputSchema: "schema:function:order_service.OrderPortType.uploadInvoice:output"
  tags: ["operation", "binary"]
```

```yaml
Schema:
  id: "schema:function:order_service.OrderPortType.uploadInvoice:input"
  dataTypes:
    - name: "string"
    - name: "byte"
    - name: "mimeType"
  properties:
    - name: "documentName"
      dataType: "string"
      required: true
    - name: "content"
      dataType: "byte"
      required: true
      description: "Document contents (base64; producer may negotiate MTOM on the wire)"
    - name: "contentType"
      dataType: "mimeType"
      required: true
```

The wire encoding (base64-inline vs MTOM/XOP vs SwA) is selected by the
producer based on the binding's `wsoap:expectedContentTypes` and the size of
the value. The interface contract is the same in all cases: a `byte` core
type carries the content, a `mimeType` core type identifies it.

## Operation Mappings

| DataProducer operation       | SOAP / WSDL execution                                                                                  |
|------------------------------|--------------------------------------------------------------------------------------------------------|
| `getRootObject`              | (synthetic — producer identity, derived from WSDL `service`).                                          |
| `getChildren` on root        | Endpoints declared by the WSDL.                                                                        |
| `getChildren` on endpoint    | Operations on the bound `portType` / `interface`.                                                      |
| `getObject` on operation     | WSDL operation lookup; populated `inputSchema`, `outputSchema`, `throws`.                              |
| `invokeFunction`             | Construct envelope (parameters → XML per `inputSchema`), apply binding (SOAPAction, security headers, attachments), POST, parse response, demarshal `outputSchema` or `throws[code]`. |
| `validateFunctionInput`      | Validate the JSON input against `inputSchema` / referenced complex types — no network call.            |

`getCollectionElements`, `addCollectionElement`, etc. are not produced by
this mapping by default. A producer **may** synthesize Collections out of
operation pairs (`getOrders` + `createOrder` + `updateOrder` + `deleteOrder`)
when the source service exposes that pattern; otherwise SOAP services
present as Functions only.

## Filter Translation Example

SOAP services do not expose a generic filtering interface — the WSDL defines
exactly the parameters its operations accept. When a producer synthesizes a
Collection from a "list" operation that takes structured search parameters,
it translates the RFC4515 AST into those parameters when their semantics
match.

```
RFC4515:
  (&(status=PENDING)(createdAt>=2025-01-01))

AST (lite-filter):
  AND
    EQ status "PENDING"
    GTE createdAt "2025-01-01"

Mapped onto the searchOrders operation's input:
  {
    "status":         "PENDING",
    "createdSince":   "2025-01-01"
  }
```

When the operation does not accept a parameter that maps to one of the AST
nodes, the producer falls back to `expression.matches(item)` post-fetch over
the operation's results. Producers must document which predicates are
push-down vs in-process.

## Core Types Reference

| XSD type                     | `dataType`         | Notes                                                              |
|------------------------------|--------------------|--------------------------------------------------------------------|
| `xs:string`                  | `string`           | If semantically email/URL/etc., use the matching core type.        |
| `xs:int`, `xs:long`, `xs:short`, `xs:byte` | `integer` |                                                            |
| `xs:decimal`                 | `decimal`          | Use for currency.                                                  |
| `xs:double`, `xs:float`      | `decimal` (currency) or generic number | Currency must be `decimal`.                            |
| `xs:boolean`                 | `boolean`          |                                                                    |
| `xs:date`                    | `date`             |                                                                    |
| `xs:dateTime`                | `date-time`        |                                                                    |
| `xs:duration`                | `duration`         |                                                                    |
| `xs:base64Binary`, `xs:hexBinary` | `byte`        | Encoded in JSON as base64.                                         |
| `xs:anyURI`                  | `url`              |                                                                    |
| `xs:QName`                   | `string`           |                                                                    |
| Enumeration restriction      | `string` + `references.schemaId` to a `schema:enum:…` |                                          |
| Complex type                 | `string` + `references.schemaId` to a `schema:type:…` |                                          |
| `maxOccurs="unbounded"`      | parent `dataType` + `multi: true` |                                                              |
| `minOccurs="0"`              | maps to `required: false` |                                                                       |

See [`../CoreDataTypes.md`](../CoreDataTypes.md) for the full catalog.

## Edge Cases

- **WSDL caching.** WSDL retrieval and parsing is expensive — cache parsed
  definitions under the connection (TTL is implementation-specific; mirror
  the schema-cache TTL from `SchemaIds.md`).
- **Imports and includes.** `wsdl:import` and `xsd:import` may pull in
  additional WSDLs or schemas from external URLs. The producer follows them
  transitively at parse time and surfaces the unified namespace.
- **Multiple bindings.** A `service` may declare more than one `port`/
  `endpoint` for the same `portType` (HTTP, HTTPS, JMS, SMTP). Each becomes
  its own Container child. Operations under different endpoints share their
  Function schemas.
- **Document/literal vs RPC.** Both styles map to the same Function shape;
  the difference is in envelope construction and is not exposed at the
  interface.
- **SOAP Faults.** Each `wsdl:fault` becomes an entry in the operation's
  `throws` map. The error code is the fault's name (or a normalized form);
  the schema body matches the fault detail. Generic `soap:Client` /
  `soap:Server` (1.1) and `soap:Sender` / `soap:Receiver` (1.2) collapse to
  the platform's standard error model — they do **not** appear in `throws`.
- **WS-Security.** Tokens, signatures, encryption, and timestamps live in
  the connection profile and the producer's request pipeline. They do not
  surface as Function schema fields.
- **WS-Addressing.** `MessageID` / `ReplyTo` / `FaultTo` headers are
  generated by the producer when required by the binding. Callers do not
  supply them.
- **WS-ReliableMessaging.** Sequence management is a producer concern; the
  caller sees ordinary Function invocations and ordinary errors.
- **InOut parameters.** WSDL allows a parameter name to appear in both
  `input` and `output` messages. Surface as a property in both schemas with
  matching name; the producer correlates after the call returns.
- **Circular complex types.** Resolve via `references.schemaId` (lazy
  resolution on `getSchema`) — never inline.
- **Stream / async patterns.** Notification (server-initiated) and
  solicit-response patterns are out of scope. If a service uses them, expose
  only the client-initiated half through DataProducer and document the
  callback flow separately.
- **Authentication.** Inherited from the connection. The interface adds no
  separate auth model.
