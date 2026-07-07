{
  description = "DSP-API — Nix dev shell for the Bazel-driven knora-sipi image build (the sbt build is unchanged)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
      ...
    }:
    flake-utils.lib.eachSystem
      [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ]
      (
        system:
        let
          pkgs = import nixpkgs { inherit system; };
        in
        {
          devShells.default = pkgs.mkShell {
            name = "dsp-api";
            packages = with pkgs; [
              # Bazelisk reads `.bazelversion` and drives the Bazel build (the
              # `knora-sipi` OCI image via rules_oci). The wrapper keeps the
              # `bazel` command name working, matching sipi & dsp-repository.
              bazelisk
              (writeShellScriptBin "bazel" ''exec ${bazelisk}/bin/bazelisk "$@"'')

              # JDK 25 so `./sbtx` (the unchanged sbt build for knora-api +
              # dsp-ingest) still works inside the shell — direnv's `use flake`
              # replaces the environment, which would otherwise hide the host
              # JDK. Matches the runtime base `eclipse-temurin:25-jre-noble`.
              # (`temurin-bin`/`jdk` default to 21; the `-25` suffix is required.)
              temurin-bin-25

              just
              cacert
              # `crane` — resolve base-image digests and inspect multi-arch
              # manifests (the same tool sipi uses for publishing).
              go-containerregistry
            ];

            shellHook = ''
              export PS1="\\u@\\h | dsp-api> "
              # gh/crane's Go-based TLS needs an explicit cert bundle on headless Linux.
              export SSL_CERT_FILE=${pkgs.cacert}/etc/ssl/certs/ca-bundle.crt
              # macOS: prepend system paths so Apple's tools (xcrun, curl used by
              # ./sbtx) stay ahead of nix stubs.
              if [ "$(uname)" = "Darwin" ]; then
                export PATH="/usr/bin:/bin:/usr/local/bin:$PATH"
              fi
            '';
          };
        }
      );
}
