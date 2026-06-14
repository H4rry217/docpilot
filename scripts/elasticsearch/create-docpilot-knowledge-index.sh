#!/usr/bin/env sh
set -eu

# Creates the DocPilot knowledge chunk index.
# Keep EMBEDDING_DIMS equal to docpilot.ai.embeddings.<id>.dimensions.
ES_URL="${ES_URL:-http://127.0.0.1:9200}"
INDEX_NAME="${INDEX_NAME:-docpilot_knowledge_chunk_search}"
EMBEDDING_DIMS="${1:-${EMBEDDING_DIMS:-}}"

if [ -z "${EMBEDDING_DIMS}" ]; then
  echo "Usage: EMBEDDING_DIMS=2048 $0"
  echo "   or: $0 2048"
  exit 1
fi

AUTH_ARGS=""
if [ -n "${ES_USERNAME:-}" ]; then
  AUTH_ARGS="-u ${ES_USERNAME}:${ES_PASSWORD:-}"
fi

if curl -fsS ${AUTH_ARGS} -I "${ES_URL}/${INDEX_NAME}" >/dev/null 2>&1; then
  echo "Index ${INDEX_NAME} already exists on ${ES_URL}."
  exit 0
fi

curl -fsS ${AUTH_ARGS} \
  -X PUT "${ES_URL}/${INDEX_NAME}" \
  -H "Content-Type: application/json" \
  --data-binary @- <<JSON
{
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "workspaceId": { "type": "long" },
      "ownerUserId": { "type": "long" },
      "documentId": { "type": "long" },
      "revisionId": { "type": "long" },
      "title": { "type": "text" },
      "chunkType": { "type": "keyword" },
      "blockId": { "type": "keyword" },
      "blockType": { "type": "keyword" },
      "chunkIndex": { "type": "integer" },
      "headingPath": { "type": "keyword" },
      "content": { "type": "text" },
      "embedding": {
        "type": "dense_vector",
        "dims": ${EMBEDDING_DIMS},
        "index": true,
        "similarity": "cosine"
      },
      "createTime": { "type": "date" },
      "createBy": { "type": "keyword" },
      "creatorId": { "type": "long" },
      "updateTime": { "type": "date" },
      "updateBy": { "type": "keyword" },
      "updaterId": { "type": "long" },
      "isDeleted": { "type": "boolean" }
    }
  }
}
JSON

echo
echo "Created index ${INDEX_NAME} on ${ES_URL} with embedding dims ${EMBEDDING_DIMS}."
