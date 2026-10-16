-- Deterministic data for the Android end-to-end tests.
-- IDs start at 900001 so they never collide with the sample data dump.
-- The bench sits at 0.5,0.5 (Gulf of Guinea), far from every real bench, so `nearest` is predictable.

INSERT INTO `users` (`userID`, `provider`, `providerID`, `name`) VALUES
(900001, 'anon', 'e2e', 'e2e-fixture-user');

INSERT INTO `benches` (`benchID`, `latitude`, `longitude`, `address`, `inscription`, `description`, `present`, `published`, `added`, `userID`, `osmID`) VALUES
(900001, 0.500000, 0.500000, 'E2E Test Location', 'E2E FIXTURE BENCH\nIn memory of Zebra Kumquat', '', 1, 1, '2026-01-01 12:00:00', 900001, NULL);

-- The photo lives at e2e/backend/photos/9/0/<sha1>.jpg.
INSERT INTO `media` (`mediaID`, `benchID`, `userID`, `sha1`, `importURL`, `licence`, `media_type`, `width`, `height`, `datetime`, `make`, `model`) VALUES
(900001, 900001, 900001, '909e8e481c851f1dc14742d61f690243d8f701a7', NULL, 'CC BY-SA 4.0', 'inscription', 490, 319, NULL, NULL, NULL);

-- A bench with no stored address (stored as empty, like the 65 in the sample data), so the API omits it.
INSERT INTO `benches` (`benchID`, `latitude`, `longitude`, `address`, `inscription`, `description`, `present`, `published`, `added`, `userID`, `osmID`) VALUES
(900002, 0.600000, 0.600000, '', 'E2E NO ADDRESS BENCH\nIn memory of Quince Marmalade', '', 1, 1, '2026-01-02 12:00:00', 900001, NULL);
-- Search only finds benches with a photo, so give it the same one.
INSERT INTO `media` (`mediaID`, `benchID`, `userID`, `sha1`, `importURL`, `licence`, `media_type`, `width`, `height`, `datetime`, `make`, `model`) VALUES
(900002, 900002, 900001, '909e8e481c851f1dc14742d61f690243d8f701a7', NULL, 'CC BY-SA 4.0', 'inscription', 490, 319, NULL, NULL, NULL);
