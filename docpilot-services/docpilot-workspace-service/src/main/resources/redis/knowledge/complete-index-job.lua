-- Mark a job complete only if Redis still points at the processed revision.
--
-- Keys:
--   KEYS[1] = per-document job hash
--   KEYS[2] = global due sorted set
--
-- Args:
--   ARGV[1] = revision id that was just indexed
--   ARGV[2] = document id, stored as the sorted set member
--
-- If another save queued a newer revision during indexing, the revision check
-- fails and the newer job remains scheduled.
if redis.call('HGET', KEYS[1], 'revisionId') == ARGV[1] then
  redis.call('DEL', KEYS[1])
  redis.call('ZREM', KEYS[2], ARGV[2])
  return 1
end
return 0
