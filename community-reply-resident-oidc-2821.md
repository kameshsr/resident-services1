Hi Shashwat,

Thanks for the detailed debugging notes — your root-cause analysis is correct. The OAuth client creation fails in `publishClientData()` because the backing `Auth_Partner` has no partner certificate (`certificate_alias = NULL`), so Keymanager's `getPartnerCertificate` returns 404. And yes, the browser-side `401` on `/resident/v1/profile` and the `/null 404` are expected symptoms when the Resident OIDC client is missing — the eSignet login flow never completes, so the portal has no session. The `/null` in the URL is the UI receiving a null authorization/redirect URL because the client isn't configured.

The issue with your certificate uploads (`KER-PCM-006`) is that you're uploading externally generated / self-signed certificates that don't chain to any CA known to PMS. For the Resident OIDC client, you should not generate your own certificates — the intended flow uses certificates already issued by MOSIP's Keymanager, which chain correctly by design.

Please follow the documented procedure here:
https://docs.mosip.io/1.2.0/id-lifecycle-management/identity-management/resident-services/deploy/resident-services-configure-resident-oidc-client

In summary, the correct onboarding sequence is:

1. Create a policy group and Auth policy for the Resident OIDC client, then publish the policy (policymanager APIs).
2. Self-register the Auth Partner (`partnerType: Auth_Partner`).
3. Get the **ROOT** certificate from Keymanager (`getCertificate` with `AppID: ROOT, refID: ""`) and upload it as a **CA certificate** with `partnerDomain: Auth`.
4. Get the **RESIDENT** certificate from Keymanager (`AppID: RESIDENT, refID: ""`) and upload it also as a **CA certificate** with `partnerDomain: Auth`.
5. Get the **RESIDENT : IDP_USER_INFO** certificate from Keymanager (`AppID: RESIDENT, refID: IDP_USER_INFO`) and upload it as the **Partner certificate** for your Auth Partner with `partnerDomain: Auth`. This is what populates `certificate_alias` for the partner.
6. Map the policy to the partner and approve the mapping.
7. Prepare the JWKS public key JSON from the RESIDENT certificate (with the correct `kid` from the `getAllCertificates` API) and create the OIDC client via the client management createClient API, with `authPartnerId` set to your Auth Partner.
8. Set the returned `clientId` in `resident-default.properties` as `mosip.iam.module.clientID` and restart the resident service.

Answering your specific questions:

1. Yes — the Auth Partner must have a partner certificate uploaded before OAuth/OIDC client creation; publish fetches it from Keymanager.
2. The sequence above (from the linked doc) is the intended one — your proposed sequence was close, but the certificates must be the Keymanager-issued ones, not external ones.
3. Upload the `RESIDENT : IDP_USER_INFO` certificate from Keymanager as the partner certificate.
4. No separate sample certificate material is needed — Keymanager in your own deployment already has the required certs.
5. `KER-PCM-006` occurs because your partner certificate's issuer chain doesn't match the CA certs registered in PMS. Using the Keymanager ROOT + RESIDENT certs as the CA chain resolves this.
6. Yes, expected — once the OIDC client is created and configured, the login flow should complete and those errors should go away.

**Alternative — automated onboarding:** Instead of running these steps manually, you can use the `partner-onboarder` Helm chart from the eSignet repo, which automates the entire resident-oidc onboarding (partner registration, certificate upload from Keymanager, policy mapping, and OIDC client creation). Enable the `resident-oidc` module in its `values.yaml`:

https://github.com/mosip/esignet/blob/4a1055b70c9c1c0ec764b92c9a02d38778d5f509/partner-onboarder/values.yaml#L17

```yaml
- name: resident-oidc
  enabled: true
```

The underlying onboarding scripts live in the mosip-onboarding repo, which can also be used standalone (Docker-based, with shell scripts and Postman collections, plus HTML result reports):

https://github.com/mosip/mosip-onboarding/tree/master

Since your environment uses self-signed SSL certificates, set `ENABLE_INSECURE=true` when running the onboarder so the scripts can work with your certificates.

Run the onboarder job against your environment, then check the job's output/report for the generated `clientId` and set it in `resident-default.properties` (`mosip.iam.module.clientID`). This is usually the easier path for a sandbox/POC deployment and avoids manual certificate-chain mistakes like the `KER-PCM-006` you hit.

**Module version compatibility:** Also, please make sure you are using compatible module versions. The latest Resident Services release is v0.9.1 (resident-services 1.2.1.1 with resident-ui 0.9.1), and its tested/certified compatibility matrix is here:

https://docs.mosip.io/1.2.0/roadmap-and-releases/releases/resident-services-v0.9.1#id-6.-compatible-modules

Key ones relevant to this issue: partner-management-service and policy-management-service 1.2.1.0, kernel-keymanager-service 1.2.0.1, mosip-config 1.2.3.1. Could you share which versions of resident-services, resident-ui, PMS, keymanager, and eSignet you have deployed? Version mismatches between these modules can also cause failures in the partner/certificate/OIDC flow.

**A note for your no-DNS setup:** the errors you're seeing are HTTP 401/404 responses, not DNS failures, so hostname resolution itself is working. However, when you create the OIDC client, make sure `redirectUris` and `logoUri` in the client creation request, and the `mosip.iam.*` properties in resident config, all use the exact hostnames your environment actually resolves (hosts file / internal DNS). A mismatch there will break the redirect back from eSignet after login even once the client is created correctly.

If you prefer starting clean, you can register a fresh Auth Partner per the doc instead of reusing `mpart-res-oidc-auth`.

Hope this helps — let us know how it goes.
