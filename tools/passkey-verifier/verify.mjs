import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { verifyRegistrationResponse, verifyAuthenticationResponse } from '@simplewebauthn/server';

const fixtures = JSON.parse(readFileSync(process.argv[2] ?? new URL('../../dnie-passkeys-core/build/webauthn-fixtures.json', import.meta.url)));
let checks = 0;
for (const fixture of fixtures) {
  const args = { expectedOrigin: fixture.origin, expectedRPID: fixture.rpId,
    expectedChallenge: fixture.challenge, requireUserVerification: true };
  const registration = await verifyRegistrationResponse({ ...args, response: fixture.registration });
  assert.equal(registration.verified, true);
  const info = registration.registrationInfo;
  assert.equal(info.credentialDeviceType, fixture.backupEligible ? 'multiDevice' : 'singleDevice');
  assert.equal(info.credentialBackedUp, false);
  assert.equal(info.counter ?? info.credential.counter, 0);
  const authentication = await verifyAuthenticationResponse({ ...args, response: fixture.authentication, credential: info.credential });
  assert.equal(authentication.verified, true);
  assert.equal(authentication.authenticationInfo.credentialBackedUp, fixture.backedUp === true);
  checks += 2;
  for (const extra of [{ expectedRPID: 'other.example.com' }, { expectedOrigin: 'https://evil.example' }, { expectedChallenge: 'different' }]) {
    await assert.rejects(() => verifyAuthenticationResponse({ ...args, ...extra, response: fixture.authentication, credential: info.credential }));
    checks++;
  }
  const tampered = structuredClone(fixture.authentication);
  const signature = Buffer.from(tampered.response.signature, 'base64url');
  signature[signature.length - 1] ^= 1;
  tampered.response.signature = signature.toString('base64url');
  const result = await verifyAuthenticationResponse({ ...args, response: tampered, credential: info.credential });
  assert.equal(result.verified, false); checks++;
}
console.log(`${checks} independent WebAuthn checks passed (native Android and delegated browser)`);
