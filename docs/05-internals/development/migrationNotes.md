#Breaking Changes and Migration Notes

##dsp-api V14 
We are slowly moving towards unifying the form of all entity IRIs (project, user, resource, value, etc.). All these 
entities should end with a unique base64Encoded-UUID without padding as 22-characters string. Following breaking changes 
are implemented:
- Enforce all custom IRIs given to entities during creation to end with a valid base64Encoded UUID 
([PR #1884](https://github.com/dasch-swiss/dsp-api/pull/1884)).