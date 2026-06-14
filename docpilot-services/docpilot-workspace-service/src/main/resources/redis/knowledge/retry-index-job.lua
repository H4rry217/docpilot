-- Reschedule a failed job only if it is still the active revision.
--
-- Keys:
--   KEYS[1] = per-document job hash
--   KEYS[2] = global due sorted set
--
-- Args:
--   ARGV[1] = revision id that failed
--   ARGV[2] = current time in millis
--   ARGV[3] = retry due time in millis
--   ARGV[4] = job hash TTL in millis
--   ARGV[5] = document id, stored as the sorted set member
--
-- Return:
--   Current attempt count, or -1 when a newer revision has replaced this job.
if redis.call('HGET', KEYS[1], 'revisionId') == ARGV[1] then
  local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
  redis.call('HSET', KEYS[1], 'updatedAt', ARGV[2])
  redis.call('PEXPIRE', KEYS[1], ARGV[4])
  redis.call('ZADD', KEYS[2], ARGV[3], ARGV[5])
  return attempts
end
return -1
