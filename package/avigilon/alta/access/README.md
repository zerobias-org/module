# @zerobias-org/module-avigilon-alta-access

A TypeScript client library for integrating with the Avigilon Alta Access API. Provides type-safe operations for managing people, groups, and access control units with read-only access.

For credential setup, see the [User Guide](USER_GUIDE.md).

**Note**: API operations and data models are automatically appended to this README during the publishing process based on the OpenAPI specification.

## Installation

```bash
npm install @zerobias-org/module-avigilon-alta-access
```

Requires Node.js 18+ and an Avigilon Alta Access account with email/password credentials.

## Quick Start

```typescript
import { newAvigilonAltaAccess } from '@zerobias-org/module-avigilon-alta-access';
import { Email } from '@auditmation/types-core-js';

const client = newAvigilonAltaAccess();

await client.connect({
  email: new Email(process.env.AVIGILON_EMAIL!),
  password: process.env.AVIGILON_PASSWORD!,
  totpCode: process.env.AVIGILON_TOTP_CODE // Optional, for MFA
});

const users = await client.getUserProducerApi().listUsers();
console.log(users);

await client.disconnect();
```

See [User Guide](USER_GUIDE.md) for detailed setup instructions.

## Usage

```typescript
import { Email } from '@auditmation/types-core-js';

const client = newAvigilonAltaAccess();
await client.connect({
  email: new Email('your-email@domain.com'),
  password: 'your-password',
  totpCode: '123456' // Optional, if MFA is enabled
});

// Available APIs
const userApi = client.getUserProducerApi();
const groupApi = client.getGroupProducerApi();  
const acuApi = client.getAcuProducerApi();

// List operations
await userApi.listUsers();
await groupApi.listGroups();
await acuApi.listAcus();

// Get single item operations
await userApi.getUser('user-id');
await groupApi.getGroup('group-id');
await acuApi.getAcu('acu-id');
```


---

## External API Schema

[External API Schema Diagram](https://mermaid.live/view#pako:H4sIAKsAsGgCA9Uca2_aSvavIKRb7a62f6DfKKENGwIRkFv16krWYA8wt7bHOzOmob3973vmYePHGQOhCd1-aKucMw-f92vyvR_yiPbf9am4YWQjSPJn2oM_s_nHwXT8x2A5nk0Xve_2h_qPVIKlmx4Xm4BFvYe73p_9f_3Zb8FTklAPKKIyFCxTjKctmMxXEU8Ia0MUS-g3ntIDYMV5TEnak5IHNCWrmEatVTHf8CAX8QHwl-SwhCoFYHn4cUQU1Uf0QkHhv1FAVPP6JUqeRR4Ud2qxx2pvQT_sP_bvxXg5wigqmaKdJHUk__CTSU6iSFAp2x_B1L59SQUf1vppxuHncaAlqb0Nz1Ml2jtl2xoz1zEHcsZEMZVH7Z_zdNMAHBULEiq2axHk6uz_YzZF2a8_opP9hXz8bP6bg9U-wzRrS0RVqUrSpooFGZFyRcIvbd1jqaIbKnohyYiWoiBmCVNtCYDNUxUUH_7h7hfi0mi6nI9RPlEtzZ2MOnyQB4GE-Usw0t5Mc9InQVp5fRtzLgLQY1bfuuC4oE9tPhfAPI05yAFPA_pU5XMhBw6u2dJenPCUKa7v0D7AiAGwKaIiuKY0PC5Gc0wWcgkXu8RkU3B1sQe2ZkKqoEMWYtINb1jY8sgs5nuqJRSRLtBJlYAgIXZWxRSVqFw2zy9Ym3Gh3QIJw5p_KcB8R4VgEQ0yKhImJSbTGUtxt0KfFBUp7M6a8hLmUvEkWDMaR5i0wJ2FCqKaHyuBNI18IENuiCeqscnrieDH-ezxAZPBjeB5do24wR7scRx7qWgSGJS2PUhosgLFMZHBL2TzB8NHjMDOWr-6XwbbwkC-01wTqwVlWeACN8_WCQkDX2gHpiX5Ct43AA1E1Q78ftSJ0K34PI1Z6tUhSWnaYHtK1VcuviBG3qo0BBIrFoNvoleRjOF8dAMRwXgwwQQElkZgMpkxRn45KZyFV04q-xxx4rmP6SHwzScxa4jFYh2NofY04UBgaoI6zDOEMc-j4Avd15Mpr5U-GNSnjIEMAuERoAnRHV980gJkQ0JL-ClqPxKqCKwnV0nshrejm8cJntyFWxrl8VUSvPJsTKoM1fTHay_MeOSTrHPzrIMArNfUYARrwZNOBMUb96JPITXfdJ0c4HfQeTQF2Om8pYuTZZJQTWpaduD8cNEc3GEcQNPyWJ1mX5DFJtDWUoYYDxbn4A8AR_L0dKXTf4HFSjIvjzLBdXjYsAGFYSmBKIc-jd7fzmZ3GI--0tWW8y-XeW6wdefom5XZkkc-VZIUOKG0MT1VmQqrB8vEvmn3Gs41b0TbdbDjY-POW8P56wRd89EAjbpg0WVZ9rnW8ljVooTnwtQteBjmGUnD_ZH6iDdIKtZ7i5eGBnVVtSJmrEvG4UayZTGZwiHFtbX5kb9Q1A38v8Gza2ePuoTgWCWlyw4_05u6W3VFaJ2B-9HY-7zQuiM-PsZaJBh_PbZPZsO7m9mnafAwGaANhiwm6TWiJXMuntV2VJQVERtqa5kS1VZTe5Nta5JB0CVqlXa7n2CbzZVM8u_jxXg5Q1Vyx6Qu1v2_1bxCnoClbfcgttwmGL54rPhcNFzC9bTkgVkbmGLTERyaRj424VmXkRBbVWtK3CvabU-mI66U5YjODCfkaWRK2xIFa81GYKdFZW0VrptYp8tYGutA3pDu5cOvx5vxMpjMPmKsjPnmIk52aBYkKjwXIT2SzFgcpB5hGXZWFmQYvaJrDr630ca0MrBWus2ANjgP1Tb8I8mGovzzpj9I3tSIih5m8yUeFemy-lU0zJ7s1bGMCNgYiOhLfID0CZLkdVa01roqVbN-JXGB5lT4q0edlaeXLxnOpsvBcBksRtMF7kslTeURV1oJX392bdmcfXaZ0ReFVr1zQ4PqtjDcknRDf6Wi_8M4uBt9xhik_e7PMX_nM0-fvSVyiyta2S-TZ3HvNNVAqq5V9rLsGmx6GM3vx4uFZxjpQJDr2MXjvqzTX8mQdzTRsO7o5YSve5uZJ5bjrxzLNT5dn3-67He3F1-xDzBaLsfTj7jNN5buNJp21Cu9G4Pd8FYlLMaOxLmXJxCMdElxCB-94cgw1ynMdBd4DrkLBJTcy_H9KPBNVRVti-fOKqpvsFIXzdcMJMsjcLkKA75ewwf6_GUklbfK10m7duZR-_bb2WR8M0C915bHLCL7n66-mhPl5hEyUHQYGdIlxxrHS4Npi5Gh7jcqPcdxKVFeV8Gns-X4w3jonY1NuZYXUJZLHVJHAmXmcXytf8hTICPxMbV6u-c2mj1lq45OkNTFZzTg0BXVc6L0evw2GeF5EonphWmS3aGz0LujmgjelqmfRRX5fg75KwGHD9aai7Kf4-vTFTWKLq3RgU68wzt1JWy1fx4jR_eD8SRYju4fJgN8QFrp0TXyckPSMl_9RUOfRVjxCEJylcRdcEWflP_e3sx5RwTTzkH-erZucb8IflZA47UlfAdOVuC5jgNCOJeu2cbnbJxz9SpqajtJzSq748xVMs_xdDn6OPc6ERNciMt9SIfAV4842xEUZVVgSm73wDHKoQN_C2qfhp5SAkACe3yzZKAhVAgurlY1eKGiKclY4CoPiNsHQTbdXa_nV1se-eJUS0kzqeWfMJAZpFa0Maz9vDKoG9_-b05l0_E4kDvsnOGRunX6PB0G_5m9xxjxF19dxIiGAiLM0CecrTf1OWRf8VJ3rGLaBB-4FHIRyaAcj_Gj6IGP1ki9UZwggmCNxU3N0h911gQdNpHzXOWoTO0cGYLypMEoyQuabJXKWuakAOpEJMmUa9v7OwtWYrW3b9_MkNVF39i4oz2i2zClEEIEdtCnYH2Nwh_G8_tPg_koeHy48QRK1rZdJPwR3bHuilYxT-IbY3AdcQc-37W49fYi6By_G2v0aNAz1Muc64hne0jnOZdTPMfN6PfxcBTcjgaT5S2WtlMSq20n7xxvLmMfTnn72i7M8iCvy7AFJDThIJcoTIu2bsfUpsuK8KyY74Z4IYUQu2qQ6rEAfL5QK0qUr2EmWCixxEai-Yq2gd0GazpafprN7y6NcU_gSYfzhMwDKBQkRH5pP7OAr_lKkJJbqu0_S4jAYZLqnncNWlajtmEWeCLmsqnNRfttBui8Hozr6NdcVsqbDYePD4Pp8HOwnA-Gd8APLA0UJPxyjB_F-GCbG80xPlMm9paSyVNQzAOiYz1mNTqE14Q0-iuWDKiUb5nUD-JCEgee7vBgOph8Xo6HaN0jJfFegZZcZP-tpnUZEDsu3qGm5vL-sV-z_Mh8jENCBmQO1pjEYR57ZO5Yl304u3-YjEHgRsHDbDIe4i8-Mx6zcP9SSZjb3Vsb0PEzE1Q_yjtzPsVlaODpGNG1Vj0hIzsH8H2P4nS7cO-Dvniy9R5MweNDMJxNP4xRA62nbF_uDZzbvUMV1ibHScM9ygGWhnGuvVDRJ_Q3bbkAxxrE3BZnT65y1E2LvS-eULtvQVNqiDl1og7ZjhXJ12O1_c9vv_VmYkNS9s18PYl7twwCChFu99ivpPj777dv-Xf32xTewWHg7iCfSWVxpoU4NNsfwtAsxKEVz76PItqx9aPHmieFGsvw1Iab_cprQ8-xgscSwytmpR2eDaX0s5l-_dU6gr4l0oPVnFTR2O5Rdrmk5NAjpPm9e_AxG2ORem96CxcVdPDIPp_W-9rHx-W2KLZ76YrRFsUvx1XNZ3KputFtr1vjRnTN9PBkv_rIuyBK5cVfg3w1PP_hNbRyykSj8a9pA-sHb1yNSMk2KYTJ_WqD3qFVZyE0sjMwbWYNzIhob2glqvcP4Nv-reJv9b__RM6vUN710Pm62NTBHOZBnYAw7oF3RQ5rux5wI_BjoWqjl0RsrdBJBRhFnQs3F5VfubB5oLYtb3pLbYMO8olc5_BKTx-w5nHMv0r8KztRCy06BRcVxLJ3a2RiBYq1O_DwsJ3bv4ZNn-ocr7TAC7NYuw7JIAagKPVGOxNYvOndl7-HAbUS7jGc3q2Yu8NFvYJYDrE3-XwctWZFy2deGlPX8ku0ElLHtOUna3dhW-Ns-rVHfT785j0OUubMHFBqfoijUMY2njYgpgY3TI8ew9TYr6UkxMRvJfq8KjZtEteogV7EtVRRVjuYw6w3wo3FNt69Tb5hrVGgvUY1hcQ9e5GRO09g1lNcQo7govs3G456aS6PLKq1w_DDym8eHwrI8MXgArq-ttYNqvj37uuUfQjLYIjuSvzahsXty3K5tSIg01XeFk4K27wlCeVn3rrfFdAyu9XIpV72wkKMIlg5eUEzbjl5YfVerfJPjadVVWnc7_SFKONaVVxzUUNAxI_rV7dahKKdTuSihmOvPqR02yOFlFJCapezqwpjgYShwRocWaC2guebLbrqEN1ZAQwyUAYz3HOIHmqXw1xJUHkNaTwSGkpUom5bvYn37jQVsLRbU8qSCSrN6BqsPGAUJ11zncd1Lm5krZa_rB7Plhyem9F6G8IM8qh4dor7Cfc04PTvqDzyaNuJepxax8yo0DP7OsKzr_CxNW0nAFEeZTtEkBv53eIw94ob21oQc4oPrbm76lei2M1Aujyh_28IgAWwK-q_-95XWzBq_XcaTPJY9X_8-B-nfr89NVEAAA)
<!-- external-api-hash: 8d4b4af4bb20f0e6375fd9980628410c0dfeb6eb -->

## Development

### Testing

Run integration tests:

```bash
npm run test:integration
```

### Test Fixtures

Integration tests use fixtures stored in `test/fixtures/integration/` for consistent test data. These fixtures are **not automatically updated** during test runs.

To update fixtures when API responses change:

```bash
SAVE_FIXTURES=true npm run test:integration
```

**When to update fixtures:**
- API response format changes
- New test scenarios require different data
- Debugging integration issues

**⚠️ Important:** Only update fixtures intentionally. Review changes before committing to ensure they reflect expected API behavior.

---

📋 **Important**: This documentation is auto-generated. Please verify code examples work with the current version.
