# Migration Notes

## v2.1.0 -> NEXT

### Data

- The property `knora-base:username` was added to `knora-base:User`. Each instance of `knora-base:User` needs to be
updated by adding this property and giving it a unique value.

### API
- The `POST /admin/user` API has changed due to adding the `username` property. The `username` property needs to be supplied
for user creation requests.