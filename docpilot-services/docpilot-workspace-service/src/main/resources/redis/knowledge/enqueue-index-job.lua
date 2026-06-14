-- Atomically enqueue or refresh one document indexing job.
--
-- Keys:
--   KEYS[1] = global due sorted set
--   KEYS[2] = per-document job hash
--
-- Args:
--   ARGV[1] = revision id to index
--   ARGV[2] = current time in millis
--   ARGV[3] = debounce delay in millis
--   ARGV[4] = max delay in millis; 0 disables the cap
--   ARGV[5] = job hash TTL in millis
--   ARGV[6] = document id, stored as the sorted set member
--
-- Flow:
--   1. Ignore stale revisions if a newer revision is already queued.
--   2. Preserve firstQueuedAt so continuous saves cannot postpone indexing forever.
--   3. Write the job hash and due sorted-set score in the same Redis operation.
local currentRevision = redis.call('HGET', KEYS[2], 'revisionId')
if currentRevision and tonumber(currentRevision) and tonumber(currentRevision) > tonumber(ARGV[1]) then
  local score = redis.call('ZSCORE', KEYS[1], ARGV[6])
  if score then
    return tonumber(score)
  end
  return -1
end

local firstQueuedAt = redis.call('HGET', KEYS[2], 'firstQueuedAt')
if not firstQueuedAt then
  firstQueuedAt = ARGV[2]
end

local nextRunAt = tonumber(ARGV[2]) + tonumber(ARGV[3])
local maxDelay = tonumber(ARGV[4])
if maxDelay > 0 then
  local latestRunAt = tonumber(firstQueuedAt) + maxDelay
  if nextRunAt > latestRunAt then
    nextRunAt = latestRunAt
  end
end

redis.call('HSET', KEYS[2],
  'revisionId', ARGV[1],
  'firstQueuedAt', firstQueuedAt,
  'updatedAt', ARGV[2],
  'attempts', '0')
redis.call('PEXPIRE', KEYS[2], ARGV[5])
redis.call('ZADD', KEYS[1], nextRunAt, ARGV[6])
return nextRunAt
