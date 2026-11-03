# 8. Login with a signed Bearer token

Status: accepted, November 2026

## Context

Login is optional in the assessment. Without it, every user of the app shares one list of conversations. The clients are a native app and evaluators with `curl`, so cookies add nothing.

## Decision

Login is required. `POST /api/auth/login` verifies an Argon2 hash and returns an HS256 JWT that expires after 12 hours. Every other route except `/health` takes the user from that token, and a conversation of another user answers 404.

An unknown email still verifies against a dummy hash, so timing does not reveal who has an account, and both failures return the same message. The test users live in `seed.py` and in the README, because the assessment asks for them to be documented.

## Consequences

A token cannot be revoked before it expires, which is acceptable with no sign-out from other devices and no real accounts. `.env.example` ships a known `JWT_SECRET` so an evaluator has one value to fill in. That secret must be replaced anywhere other than a local machine.

There is no rate limit on login. Real accounts would need one.
