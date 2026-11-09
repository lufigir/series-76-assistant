# 8. Login with a signed Bearer token

Status: accepted, November 2026

## Context

Login is optional in the assessment. Without it, every user of the app shares one list of conversations. The clients are a native app and evaluators with `curl`, so cookies add nothing.

## Decision

Login is required. `POST /api/auth/login` verifies an Argon2 hash and returns an HS256 JWT that expires after 12 hours. Every other route except `/health` takes the user from that token, and a conversation of another user answers 404.

An unknown email still verifies against a dummy hash, so timing does not reveal who has an account, and both failures return the same message. The test users live in `seed.py` and in the README, because the assessment asks for them to be documented.

## Consequences

A token cannot be revoked before it expires, which is acceptable with no sign-out from other devices and no real accounts. `.env.example` ships a known `JWT_SECRET` so an evaluator has one value to fill in. That secret must be replaced anywhere other than a local machine.

Wrong passwords are capped per email: 10 in 15 minutes, then 429 until the oldest leaves the window, and a successful login clears the count. The cap counts the email as typed, whether the account exists or not, so it does not reveal accounts. It goes per email and not per IP because behind Docker's port mapping every client can arrive from the same gateway address. The price is that anyone can lock someone else's account for 15 minutes, which with test users only is acceptable.
