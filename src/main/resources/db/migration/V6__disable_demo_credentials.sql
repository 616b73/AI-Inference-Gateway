-- Preserve V1-V5 checksums. Disable the public MVP credential and its known hash.
-- Startup additionally detects rehashed copies by verifying active hashes.
UPDATE api_keys SET active = FALSE
WHERE id = 'c3d4e5f6-a7b8-9012-cdef-123456789012'
   OR key_hash = '$2b$10$iemW2pbe8LCoClCT4DdP2ulCHQey3p3JfzX3bM0uFvXWLzU43HyuK';
