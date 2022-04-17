Release instructions (for my future self):

1) Push an annotated tag with the version number, wait for the GitHub Actions build to finish
2) In the resulting publish-linux build, find the URL and SHA printed by the "Print values needed..." step
3) In homebrew-ghpush, run update_url_and_sha.sh with the values from (2) as arguments
4) Commit these changes, push, and make a PR. Do not merge this PR.
5) If/when the actions testing the PR are successful, add the "pr-pull" label. GHA will take it from there.
