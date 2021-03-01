

## Overview
The purpose of this form is to disclose the types of personal data stored by each module.  This information enables those hosting FOLIO to better manage and comply with various privacy laws and restrictions, e.g. GDPR.

It's important to note that personal data is not limited to that which can be used to identify a person on it's own (e.g. Social security number), but also data used in conjunction with other data to identify a person (e.g. date of birth + city + gender).

For the purposes of this form, "store" includes the following:
* Persisting to storage - Either internal (e.g. Postgres) or external (e.g. S3, etc.) to FOLIO
* Caching - In-memory, etc.
* Logging
* Sending to an external piece of infrastructure such as a queue (e.g. Kafka), search engine (e.g. Elasticsearch), distributed table, etc.

## Personal Data Stored by This Module
- [ ] This module does not store any personal data.
- [ ] This module provides [custom fields](https://github.com/folio-org/folio-custom-fields).
- [ ] This module stores fields with free-form text (tags, notes, descriptions, etc.)
- [ ] This module caches personal data
---
- [ ] First name
- [ ] Last name
- [ ] Middle name
- [x] Pseudonym / Alias / Nickname / Username / User ID
- [ ] Gender
- [ ] Date of birth
- [ ] Place of birth
- [ ] Racial or ethnic origin
- [ ] Address
- [ ] Location information
- [ ] Phone numbers
- [ ] Passport number / National identification numbers
- [ ] Driver’s license number
- [ ] Social security number
- [ ] Email address
- [ ] Web cookies
- [x] IP address
- [ ] Geolocation data
- [ ] Financial information
- [ ] Logic or algorithms used to build a user/profile
<!--- - [ ] Other personal data - Please list as needed -->
<!--- - [ ] Other personal data - Please list as needed -->

**NOTE** This is not intended to be a comprehensive list, but instead provide a starting point for module developers/maintainers to use.

## Privacy Laws, Regulations, and Policies
The following laws and policies were considered when creating the list of personal data fields above.
* [General Data Protection Regulation (GDPR)](https://gdpr.eu/)
* [California Consumer Privacy Act (CCPA)](https://oag.ca.gov/privacy/ccpa)
* [U.S. Department of Labor: Guidance on the Protection of Personal Identifiable Information](https://www.dol.gov/general/ppii)
* Cybersecurity Law of the People's Republic of China
  * https://www.newamerica.org/cybersecurity-initiative/digichina/blog/translation-cybersecurity-law-peoples-republic-china/
  * http://en.east-concord.com/zygd/Article/20203/ArticleContent_1690.html?utm_source=Mondaq&utm_medium=syndication&utm_campaign=LinkedIn-integration
* [Personal Data Protection Bill, 2019 (India)](https://www.prsindia.org/billtrack/personal-data-protection-bill-2019)
* [Data protection act 2018 (UK)](https://www.legislation.gov.uk/ukpga/2018/12/section/3/enacted)

---

v1.0
