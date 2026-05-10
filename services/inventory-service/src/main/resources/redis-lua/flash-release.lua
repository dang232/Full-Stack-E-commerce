-- Release an unexpired/expired reservation back to flash-sale stock exactly once.
-- KEYS[1] = flash:stock:{productId}
-- KEYS[2] = flash:reservation:{reservationId}
-- ARGV[1] = quantity to restore
if redis.call('EXISTS', KEYS[2]) == 1 then
  redis.call('INCRBY', KEYS[1], ARGV[1])
  redis.call('DEL', KEYS[2])
  return 1
else
  return 0
end
