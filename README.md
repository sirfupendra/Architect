# PARSE: Parameter Automated Refinement and Schema Extraction

This repository implements **PARSE** (from the EMNLP 2025 Industry Track paper *"LLM-Driven Schema Optimization for Reliable Entity Extraction"* by Shrimal et al., Amazon). The system improves structured information extraction from unstructured text for LLM agents (Software 3.0) by **optimizing JSON schemas for LLM consumption** and applying **reflection-based guardrails** during extraction.

---

## Overview

PARSE has two main phases:

1. **Build Phase — ARCHITECT**  
   Optimizes a user-provided JSON schema for LLM consumption (one-time or on-demand), then generates a **RELAY** transformation so downstream systems still receive data in the original format.

2. **Extract Phase — SCOPE**  
   Performs information extraction from text using the optimized schema, with **multi-stage validation** and **retry with reflection** on errors.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           BUILD PHASE (ARCHITECT)                            │
├─────────────────────────────────────────────────────────────────────────────┤
│  User Schema + Task + Optional Seed Data                                     │
│         │                                                                    │
│         ▼                                                                    │
│  ┌──────────────────┐    ┌─────────────────────┐    ┌──────────────────┐  │
│  │ Schema Generator  │───▶│ Synthetic Data Gen   │───▶│ Evaluate (SCOPE)  │  │
│  │ (LLM)             │    │ (LLM)                │    │ on each example   │  │
│  └──────────────────┘    └─────────────────────┘    └────────┬─────────┘  │
│         │                            │                           │           │
│         │                            │              accuracy < τ? │           │
│         │                            │                   │ yes   │           │
│         │                            │                   ▼       │           │
│         │                            │         ┌─────────────────┐│          │
│         │                            └─────────│ Refine Schema   ││          │
│         │                                     │ (LLM + failures) ││          │
│         │                                     └────────┬─────────┘│          │
│         │                                              │         │          │
│         │                                              └─────────┘          │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────────┐                                                      │
│  │ RELAY: Generate   │  →  Transformation spec (optimized → original)     │
│  │ mapping spec     │                                                      │
│  └────────┬─────────┘                                                      │
│           ▼                                                                 │
│  Persist: originalSchema, optimizedSchema, relayTransformSpec, modelName    │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                           EXTRACT PHASE (SCOPE)                              │
├─────────────────────────────────────────────────────────────────────────────┤
│  Text + schemaId                                                              │
│         │                                                                    │
│         ▼                                                                    │
│  Load SchemaDefinition (optimizedSchema, relayTransformSpec)                 │
│         │                                                                    │
│         ▼                                                                    │
│  ┌──────────────────┐                                                       │
│  │ LLM Extraction    │  →  Raw JSON from text                                 │
│  └────────┬─────────┘                                                       │
│           ▼                                                                  │
│  ┌──────────────────┐     pass?     ┌──────────────────┐                   │
│  │ Guardrails:       │──────yes─────▶│ Apply RELAY       │──▶ Final JSON     │
│  │ 1. Missing attrs  │               │ (if spec present)│                   │
│  │ 2. Grounding      │     fail      │                   │                   │
│  │ 3. Rules (pattern,│──────▼────────│                   │                   │
│  │    enum, length)  │  Retry with   │                   │                   │
│  └──────────────────┘  error msgs   └──────────────────┘                   │
│           ▲                    │                                             │
│           └────────────────────┘ (up to 3 retries)                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Implemented Components

### 1. ARCHITECT (Build Phase)

- **Schema generator**  
  LLM produces an initial JSON schema from a task description and optional original schema (see `ParsePrompts.SCHEMA_GENERATOR_*`).

- **Synthetic data generator**  
  LLM generates adversarial/challenging examples `(input_text, ground_truth, challenge)` to stress-test the schema (see `ParsePrompts.SYNTHETIC_DATA_*`).

- **Evaluation loop**  
  For each synthetic example, extraction is run with the current schema via SCOPE (no RELAY). Accuracy is computed; failure cases are summarized.

- **Refinement**  
  If accuracy is below a threshold (default 0.85) and there are failures, the LLM refines the schema using the failure summary (see `ParsePrompts.REFINEMENT_*`). Loop continues for up to 5 iterations.

- **RELAY spec**  
  After optimization, the LLM generates a mapping from optimized-schema output back to the original schema (see `ParsePrompts.RELAY_*`). This is stored as `relayTransformSpec` and applied at extraction time.

**Code:** `AlgorithService.refineJsonForBetterPerformance()`, `LlmConnectionService.generateSchema()`, `generateSyntheticData()`, `refineSchema()`, `generateRelaySpec()`.

### 2. SCOPE (Extract Phase)

- **Extraction**  
  One LLM call with the optimized schema and source text (see `ParsePrompts.SCOPE_EXTRACTOR_*`).

- **Guardrails (three stages)**  
  Implemented in `SchemaGuardrailsService.validate()`:
  1. **Missing attribute check** — all required fields (from schema `required` or all top-level properties) must be present.
  2. **Grounding** — string values in the extraction must appear (after normalization) in the source text.
  3. **Rule compliance** — `pattern`, `enum`, `minLength`, `maxLength`, and types are checked per schema.

- **Reflection / retry**  
  If validation fails, the extraction output and error list are sent back to the LLM (see `ParsePrompts.SCOPE_RETRY_*`). Up to 3 retries.

**Code:** `ExtractionJsonService.JsonFromText()`, `SchemaGuardrailsService`, `LlmConnectionService.JsonFromText()`, `retryWithErrors()`.

### 3. RELAY

- **Spec generation**  
  In the Build Phase, the LLM produces a JSON “mapping” describing how each original-schema field is obtained from the optimized output (direct key or `concat` of keys).

- **Application**  
  At extraction time, if `relayTransformSpec` is present, `RelayService.applyTransform()` converts the SCOPE output from optimized format to the original format before returning.

**Code:** `RelayService.applyTransform()`, `LlmConnectionService.generateRelaySpec()`.

---

## API

### Build: Optimize schema (ARCHITECT + RELAY)

**`POST /parseJson`**

Request body (JSON):

- `jsonData` (required): Original JSON schema (or example structure).
- `Description` (required): Task description for the schema.
- `LlmModel` (required): Model name (must exist in `LlmModels` table).
- `seedDataSet` (optional): Array of example JSONs for synthetic data generation.

Response:

- `schemaId`: ID of the saved `SchemaDefinition`. Use this for extraction.
- `message`: Confirmation message.

### Extract: Get structured data from text (SCOPE + optional RELAY)

**`POST /extractJsonForText`**

Request body (JSON):

- `Text` (required): Unstructured text (e.g. conversation or document).
- `schemaId` (required): ID from `/parseJson`.

Response:

- JSON string: extracted data in **original schema format** if RELAY spec is present, otherwise in optimized schema format.

---

## Configuration

- **Database:** MySQL; connection and JPA settings in `application.properties`.
- **LLM models:** Stored in `LlmModels` (modelName, modelUrl, myConnectionKey). ARCHITECT and SCOPE use the model name from the request / stored schema.
- **Tunables:** In `AlgorithService`: `MAX_REFINEMENT_ITERATIONS` (5), `ACCURACY_THRESHOLD` (0.85).

---

## Project layout (main pieces)

| Path | Role |
|------|------|
| `Controllers/AlgorithmController.java` | `POST /parseJson` → ARCHITECT pipeline. |
| `Controllers/ExtractionController.java` | `POST /extractJsonForText` → SCOPE + RELAY. |
| `Services/AlgorithService.java` | ARCHITECT: schema gen, synthetic data, evaluation loop, refinement, RELAY spec, persist. |
| `Services/ExtractionJsonService.java` | SCOPE: load schema, LLM extract, guardrails, retry, optional RELAY. |
| `Services/SchemaGuardrailsService.java` | Missing-attribute, grounding, and rule checks. |
| `Services/RelayService.java` | Apply RELAY mapping (optimized → original). |
| `Services/LlmConnectionService.java` | All LLM calls (schema gen, synthetic, refine, RELAY, extract, retry). |
| `Prompts/ParsePrompts.java` | Paper-aligned prompt constants. |
| `Util/LlmResponseUtils.java` | Extract JSON from LLM output (tags, markdown). |
| `Entites/SchemaDefinition.java` | Persisted schema (original, optimized, relayTransformSpec, modelName). |

---

## References

- Paper: *PARSE: LLM-Driven Schema Optimization for Reliable Entity Extraction* (EMNLP 2025 Industry Track).  
- Concepts: ARCHITECT (schema optimization), SCOPE (extraction + guardrails), RELAY (backward-compatible transformation).

---

## License and usage

See project and paper for terms. This implementation is for research and reference; adapt prompts and thresholds as needed for your use case.
