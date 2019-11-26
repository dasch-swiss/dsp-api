How to encrypt and decrypt the license file used in CI:

```shell script
$ tar -cf secrets.tar graphdb.license
$ gpg --cipher-algo AES256 --symmetric secrets.tar
$ gpg --quiet --batch --yes --decrypt --passphrase SECRET_PASSPHRASE --output=secrets.tar secrets.tar.gpg
$ tar -xvf secrets.tar
```
