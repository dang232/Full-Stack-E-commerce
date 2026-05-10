-- Atomically reserve flash-sale stock only when enough units remain.
-- KEYS[1] = flash:stock:{productId}
-- KEYS[2] = flash:waiting:{productId}
-- ARGV[1] = requested quantity
-- ARGV[2] = buyerId
local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
if stock >= tonumber(ARGV[1]) then
  redis.call('DECRBY', KEYS[1], ARGV[1])
  redis.call('SADD', KEYS[2], ARGV[2])
  return 1
else
  return 0
end
